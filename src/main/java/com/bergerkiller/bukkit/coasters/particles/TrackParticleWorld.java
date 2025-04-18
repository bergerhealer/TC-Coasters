package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldComponent;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctreeIterator;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * Tracks and updates all the particle items on a single world
 */
public class TrackParticleWorld implements CoasterWorldComponent {
    private final CoasterWorld _world;
    public DoubleOctree<TrackParticle> particles = new DoubleOctree<TrackParticle>();
    public List<TrackParticle> particlesWithoutViewers = new ArrayList<TrackParticle>();
    private final Map<Player, ViewerParticleList> viewers = new ConcurrentHashMap<>(16, 0.75f, 1);
    private final ArrayList<ParticleWithBlockDistance> particlesSortedList = new ArrayList<>();
    private int updateCtr = 0;
    private boolean forceViewerUpdate = false;
    private boolean visibleToEveryone = false;

    public TrackParticleWorld(CoasterWorld world) {
        this._world = world;
    }

    @Override
    public final CoasterWorld getWorld() {
        return this._world;
    }

    public TrackParticleLight addParticleLight(Vector position, TrackParticleLight.LightType type, int level) {
        return addParticle(new TrackParticleLight(position, type, level));
    }

    public TrackParticleWidthMarker addParticleWidthMarker(Vector position, Quaternion orientation, double width) {
        return addParticle(new TrackParticleWidthMarker(position, orientation, width));
    }

    public TrackParticleFallingBlock addParticleFallingBlock(Vector position, Quaternion orientation, BlockData material) {
        return addParticle(new TrackParticleFallingBlock(position, orientation, material));
    }

    public TrackParticleArmorStandItem addParticleArmorStandItem(Vector position, Quaternion orientation, ItemStack item) {
        return addParticle(new TrackParticleArmorStandItem(position, orientation, item));
    }

    public TrackParticleDisplayItem addParticleDisplayItem(Vector position, Quaternion orientation, double clip, Vector size, ItemStack item) {
        return addParticle(new TrackParticleDisplayItem(position, orientation, clip, size, item));
    }

    public TrackParticleDisplayBlock addParticleDisplayBlock(Vector position, Quaternion orientation, double clip, Vector size, BlockData blockData) {
        return addParticle(new TrackParticleDisplayBlock(position, orientation, clip, size, blockData));
    }

    public TrackParticleText addParticleTextNoItem(Vector position, String text) {
        return addParticle(new TrackParticleText(position, text, false));
    }

    public TrackParticleText addParticleText(Vector position, String text) {
        return addParticle(new TrackParticleText(position, text, true));
    }

    public TrackParticleSignText addParticleSignText(Vector position, String[][] signLines) {
        return addParticle(new TrackParticleSignText(position, signLines));
    }

    public TrackParticleLine addParticleLine(Vector p1, Vector p2) {
        return addParticle(new TrackParticleLine(p1, p2));
    }

    public TrackParticleItem addParticleItem(Vector position) {
        return addParticle(new TrackParticleItem(position));
    }

    public TrackParticleArrow addParticleArrow(Vector position, Vector direction, Vector up) {
        return addParticle(new TrackParticleArrow(position, Quaternion.fromLookDirection(direction, up)));
    }

    public TrackParticleArrow addParticleArrow(Vector position, Quaternion orientation) {
        return addParticle(new TrackParticleArrow(position, orientation));
    }

    public TrackParticleLitBlock addParticleLitBlock(IntVector3 block) {
        return addParticle(new TrackParticleLitBlock(block));
    }

    /**
     * Adds a new particle to this world, firing the required callbacks and refreshing
     * viewers to make the particle visible to them.
     *
     * @param <T> Type of particle
     * @param particle The particle to add
     * @return the input particle
     */
    public <T extends TrackParticle> T addParticle(T particle) {
        particle.world = this;
        try {
            particle.onAdded();
        } catch (Error | RuntimeException ex) {
            try {
                particle.onRemoved();
            } catch (Throwable ignore) {}
            particle.world = null;
            throw ex;
        }
        if (particle.isUsingViewers()) {
            this.forceViewerUpdate = true;
        }
        return particle;
    }

    public void removeParticle(TrackParticle particle) {
        if (particle.world == this) {
            if (particle.isUsingViewers()) {
                try {
                    particle.makeHiddenForAll();
                } catch (Throwable t) {
                    getPlugin().getLogger().log(Level.SEVERE, "TrackParticle.makeHiddenForAll() failed for "
                            + particle.getClass().getName(), t);
                }
                try {
                    particle.onRemoved();
                } catch (Throwable t) {
                    getPlugin().getLogger().log(Level.SEVERE, "TrackParticle.onRemoved() failed for "
                            + particle.getClass().getName(), t);
                }
                particle.world = null;
                this.forceViewerUpdate = true;
            } else {
                try {
                    particle.onRemoved();
                } catch (Throwable t) {
                    getPlugin().getLogger().log(Level.SEVERE, "TrackParticle.onRemoved() failed for "
                            + particle.getClass().getName(), t);
                }
                particle.world = null;
            }
        }
    }

    public void removeAll() {
        {
            for (TrackParticle particle : this.particles.values()) {
                removeParticle(particle);
            }
            this.particles.clear();
        }
        {
            List<TrackParticle> withoutViewers = new ArrayList<TrackParticle>(this.particlesWithoutViewers);
            this.particlesWithoutViewers.clear();
            for (TrackParticle particle : withoutViewers) {
                removeParticle(particle);
            }
        }
        this.particlesWithoutViewers.clear();
        this.viewers.clear();
        this.updateCtr = 0;
        this.forceViewerUpdate = true;
    }

    /**
     * Sets whether all particles are visible to everyone. This makes it so that players
     * that do not have edit permissions or have not activated their TCC editor map ('disabled')
     * can still see all particles such as wires and levers.
     *
     * @param set Whether visible to everyone
     */
    public void setVisibleToEveryone(boolean set) {
        this.visibleToEveryone = set;
        this.forceViewerUpdate = true;
    }

    /**
     * Forces a search for new particles around a player.
     * Normally this search only happens when a player moves.
     * 
     * @param viewer
     */
    public void scheduleViewerUpdate(Player viewer) {
        ViewerParticleList viewed = this.viewers.get(viewer);
        if (viewed != null) {
            viewed.block = null;
        }
    }

    public void updateAll() {
        // Refresh for all players that are online
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.update(viewer);
        }
        this.forceViewerUpdate = false;

        // Cleanup the players map and remove players that aren't online
        {
            Iterator<Player> iter = viewers.keySet().iterator();
            while (iter.hasNext()) {
                if (!TCCoastersUtil.isPlayerConnected(iter.next())) {
                    iter.remove();
                }
            }
        }
    }

    public void update(Player viewer) {
        if (viewer.getWorld() == this.getBukkitWorld()) {
            // Get whether view is in edit mode or not
            boolean isInEditMode = this.getPlugin().getEditState(viewer).getMode() != PlayerEditMode.DISABLED;
            boolean canViewAllParticles = isInEditMode || this.visibleToEveryone;

            // Get state
            ViewerParticleList viewed = this.viewers.computeIfAbsent(viewer, ViewerParticleList::new);

            // Check player changed block (input to cuboid function)
            IntVector3 viewerBlock = new IntVector3(viewer.getEyeLocation());
            if (!this.forceViewerUpdate && viewerBlock.equals(viewed.block)) {
                return; // player did not move, no need to update
            }

            // Detect all the particles currently in range of the viewer
            // This uses the octree to do so efficiently
            Integer updateObject = Integer.valueOf(this.updateCtr++);
            viewed.block = viewerBlock;
            int cuboid_range = this.getPlugin().getEditState(viewer).getParticleViewRange();
            if (cuboid_range < 0) {
                cuboid_range = this.getPlugin().getParticleViewRange();
            }
            int maxParticles = this.getPlugin().getMaximumParticleCount();
            IntVector3 range_min = viewerBlock.subtract(cuboid_range, cuboid_range, cuboid_range);
            IntVector3 range_max = viewerBlock.add(cuboid_range, cuboid_range, cuboid_range);
            long timeNow = System.currentTimeMillis();
            boolean reachedLimit = false;
            boolean reachedLimitRecently = (viewed.reachedLimitAt != 0 && (timeNow - viewed.reachedLimitAt) <= 5000);

            // This runs for the first time, or if we haven't hit a particle limit in a while
            if (!reachedLimitRecently) {
                int numParticles = 0;
                for (TrackParticle particle : this.particles.cuboid(range_min, range_max)) {
                    if ((!canViewAllParticles && !particle.isAlwaysVisible()) || !particle.isVisible(viewer)) {
                        continue;
                    }

                    // Limit what can be displayed
                    if (++numParticles > maxParticles) {
                        reachedLimitRecently = true;
                        reachedLimit = true;

                        // Reset state, try again
                        // Changing the updateObject causes it to de-spawn particles we spawned before
                        updateObject = Integer.valueOf(this.updateCtr++);
                        numParticles = 0;
                        break;
                    }

                    viewed.makeVIsible(viewer, updateObject, particle);
                }
            }

            // This runs if there are a lot of particles around the player, and some will need to be trimmed off
            // This is done by sorting all particles by distance and omitting everything farthest away
            if (reachedLimitRecently) {
                ArrayList<ParticleWithBlockDistance> particlesSortedList = this.particlesSortedList;
                try {
                    DoubleOctreeIterator<TrackParticle> iter = this.particles.cuboid(range_min, range_max).iterator();
                    while (iter.hasNext()) {
                        TrackParticle particle = iter.next();
                        if ((!isInEditMode && !particle.isAlwaysVisible()) || !particle.isVisible(viewer)) {
                            continue;
                        }

                        int dx = Math.abs(viewerBlock.x - iter.getBlockX());
                        int dy = Math.abs(viewerBlock.y - iter.getBlockY());
                        int dz = Math.abs(viewerBlock.z - iter.getBlockZ());
                        int manhattanDistance = dx + dy + dz;

                        particlesSortedList.add(new ParticleWithBlockDistance(particle, manhattanDistance));
                    }

                    // Sort so that furthest particles are at the end of the list
                    Collections.sort(particlesSortedList);

                    // Iterate the items, avoid exceeding limit
                    int numParticles = 0;
                    for (ParticleWithBlockDistance p : particlesSortedList) {
                        if (++numParticles > maxParticles) {
                            reachedLimit = true;
                            break;
                        } else {
                            viewed.makeVIsible(viewer, updateObject, p.particle);
                        }
                    }
                } finally {
                    particlesSortedList.clear();
                }
            }

            // If limit is reached (for the first time) and not too short of a time passed, send a message
            if (reachedLimit) {
                if (getPlugin().isMaximumParticleWarningEnabled() && !viewed.reachedLimit &&
                        (viewed.reachedLimitAt == 0 || (timeNow - viewed.reachedLimitAt) > 30000)
                ) {
                    viewer.sendMessage(ChatColor.RED + "[TC-Coasters] You have reached the particle limit of " + maxParticles + "!");
                }
                viewed.reachedLimitAt = timeNow;
            }
            viewed.reachedLimit = reachedLimit;

            // Particles that are no longer in view have an outdated Integer update object
            {
                Iterator<Map.Entry<TrackParticle, Integer>> iter = viewed.particles.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<TrackParticle, Integer> entry = iter.next();
                    if (entry.getValue() != updateObject) {
                        entry.getKey().changeVisibility(viewer, false);
                        iter.remove();
                    }
                }
            }
        } else {
            hideAllFor(viewer);
        }
    }

    /**
     * Gets a set of all particles a player can see.
     * This method is thread-safe, it can be called from another thread.
     * The returned set is readonly.
     * 
     * @param viewer
     * @return set of viewed particles
     */
    public Set<TrackParticle> getViewedParticles(Player viewer) {
        ViewerParticleList viewed = this.viewers.get(viewer);
        return (viewed == null) ? Collections.emptySet() : Collections.unmodifiableSet(viewed.particles.keySet());
    }

    /**
     * Despawns all particles a viewer sees
     * 
     * @param viewer
     */
    public void hideAllFor(Player viewer) {
        ViewerParticleList viewed = this.viewers.remove(viewer);
        if (viewed != null) {
            for (TrackParticle particle : viewed.particles.keySet()) {
                particle.changeVisibility(viewer, false);
            }
        }
    }

    /**
     * Checks whether a particular entity Id is a particle a player can see.
     * This method is thread-safe, it can be called from another thread.
     * 
     * @param viewer
     * @param entityId
     * @return True if the entityId is that of a particle
     */
    public boolean isParticle(Player viewer, int entityId) {
        for (TrackParticle particle : getViewedParticles(viewer)) {
            if (particle.usesEntityId(entityId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a particle is nearby the player somewhere.
     * This method is thread-safe, it can be called from another thread.
     * 
     * @param viewer
     * @return True if a particle is nearby
     */
    public boolean isParticleNearby(Player viewer) {
        Vector pos = viewer.getEyeLocation().toVector();
        for (TrackParticle particle : getViewedParticles(viewer)) {
            if (particle.isNearby(pos)) {
                return true;
            }
        }
        return false;
    }

    private static class ViewerParticleList {
        public IntVector3 block = null;
        public final Map<TrackParticle, Integer> particles = new ConcurrentHashMap<>(16, 0.75f, 1);
        public boolean reachedLimit = false;
        public long reachedLimitAt = 0;

        public ViewerParticleList(Player viewer) {
        }

        public void makeVIsible(Player viewer, Integer updateObject, TrackParticle particle) {
            // Add to the particle mapping. If adding for the first time, make it visible.
            if (particles.put(particle, updateObject) == null) {
                particle.changeVisibility(viewer, true);
            }
        }
    }

    private static final class ParticleWithBlockDistance implements Comparable<ParticleWithBlockDistance> {
        public final TrackParticle particle;
        public final int distance;

        public ParticleWithBlockDistance(TrackParticle particle, int distance) {
            this.particle = particle;
            this.distance = distance;
        }

        @Override
        public int compareTo(ParticleWithBlockDistance o) {
            return this.distance - o.distance;
        }
    }
}
