package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

/**
 * Tracks and updates all the particle items on a single world
 */
public class TrackParticleWorld implements CoasterWorldComponent {
    private final CoasterWorld _world;
    public DoubleOctree<TrackParticle> particles = new DoubleOctree<TrackParticle>();
    private final Map<Player, ViewerParticleList> viewers = new ConcurrentHashMap<>(16, 0.75f, 1);
    private int updateCtr = 0;
    private boolean forceViewerUpdate = false;
    protected final List<TrackParticle> appearanceUpdates = new ArrayList<>();

    public TrackParticleWorld(CoasterWorld world) {
        this._world = world;
    }

    @Override
    public final CoasterWorld getWorld() {
        return this._world;
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

    public TrackParticleText addParticleTextNoItem(Vector position, String text) {
        return addParticle(new TrackParticleText(position, text, false));
    }

    public TrackParticleText addParticleText(Vector position, String text) {
        return addParticle(new TrackParticleText(position, text, true));
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

    protected <T extends TrackParticle> T addParticle(T particle) {
        particle.world = this;
        particle.onAdded();
        this.forceViewerUpdate = true;
        return particle;
    }

    public void removeParticle(TrackParticle particle) {
        particle.makeHiddenForAll();
        particle.onRemoved();
        particle.world = null;
    }

    public void removeAll() {
        for (TrackParticle particle : this.particles.values()) {
            particle.makeHiddenForAll();
        }
        this.particles.clear();
        this.viewers.clear();
        this.appearanceUpdates.clear();
        this.updateCtr = 0;
        this.forceViewerUpdate = false;
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
                if (!iter.next().isOnline()) {
                    iter.remove();
                }
            }
        }

        // For all particles that need an update, update the appearance now
        // Note: use a a for i loop because new items may be added to the list while iterating
        if (!this.appearanceUpdates.isEmpty()) {
            int size = this.appearanceUpdates.size();
            for (int i = 0; i < size; i++) {
                TrackParticle particle = this.appearanceUpdates.get(i);
                particle.clearFlag(TrackParticle.FLAG_APPEARANCE_DIRTY);
                particle.updateAppearance();
            }
            this.appearanceUpdates.subList(0, size).clear();
        }
    }

    public void update(Player viewer) {
        if (viewer.getWorld() == this.getBukkitWorld()) {
            // Get whether view is in edit mode or not
            boolean isInEditMode = this.getPlugin().getEditState(viewer).getMode() != PlayerEditMode.DISABLED;

            // Get state
            ViewerParticleList viewed = this.viewers.get(viewer);
            if (viewed == null) {
                viewed = new ViewerParticleList();
                this.viewers.put(viewer, viewed);
            }

            // Check player changed block (input to cuboid function)
            IntVector3 viewerBlock = new IntVector3(viewer.getEyeLocation());
            if (!this.forceViewerUpdate && viewerBlock.equals(viewed.block)) {
                return; // player did not move, no need to update
            }

            // Detect all the particles currently in range of the viewer
            // This uses the octree to do so efficiently
            Integer updateObject = Integer.valueOf(this.updateCtr++);
            viewed.block = viewerBlock;
            int cuboid_range = this.getPlugin().getParticleViewRange();
            int maxParticles = this.getPlugin().getMaximumParticleCount();
            IntVector3 range_min = viewerBlock.subtract(cuboid_range, cuboid_range, cuboid_range);
            IntVector3 range_max = viewerBlock.add(cuboid_range, cuboid_range, cuboid_range);
            int numParticles = 0;
            boolean reachedLimit = false;
            for (TrackParticle particle : this.particles.cuboid(range_min, range_max)) {
                if (!isInEditMode && !particle.isAlwaysVisible()) {
                    continue;
                }

                if (!particle.isVisible(viewer)) {
                    continue;
                }

                // Limit what can be displayed
                if (++numParticles > maxParticles) {
                    reachedLimit = true;
                    break;
                }

                // Add to the particle mapping. If adding for the first time, make it visible.
                if (viewed.particles.put(particle, updateObject) == null) {
                    particle.changeVisibility(viewer, true);
                }
            }

            // If limit is reached (for the first time) and not too short of a time passed, send a message
            if (reachedLimit) {
                long now = System.currentTimeMillis();
                if (!viewed.reachedLimit && (viewed.reachedLimitAt == 0 || (now - viewed.reachedLimitAt) > 30000)) {
                    viewer.sendMessage(ChatColor.RED + "[TC-Coasters] You have reached the particle limit of " + maxParticles + "!");
                }
                viewed.reachedLimitAt = now;
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
    }
}
