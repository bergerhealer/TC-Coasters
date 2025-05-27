package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.objects.lod.LODItemStack;
import com.bergerkiller.bukkit.common.collections.ImmutablePlayerSet;
import com.bergerkiller.bukkit.common.wrappers.Brightness;
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
        return addParticle(new TrackParticleArmorStandItem(position, orientation, LODItemStack.createList(item)));
    }

    public TrackParticleArmorStandItem addParticleArmorStandItem(Vector position, Quaternion orientation, LODItemStack.List lodList) {
        return addParticle(new TrackParticleArmorStandItem(position, orientation, lodList));
    }

    public TrackParticleDisplayItem addParticleDisplayItem(Vector position, Quaternion orientation, double clip, Brightness brightness, Vector size, ItemStack item) {
        return addParticle(new TrackParticleDisplayItem(position, orientation, clip, brightness, size, LODItemStack.createList(item)));
    }

    public TrackParticleDisplayItem addParticleDisplayItem(Vector position, Quaternion orientation, double clip, Brightness brightness, Vector size, LODItemStack.List lodList) {
        return addParticle(new TrackParticleDisplayItem(position, orientation, clip, brightness, size, lodList));
    }

    public TrackParticleDisplayBlock addParticleDisplayBlock(Vector position, Quaternion orientation, double clip, Brightness brightness, Vector size, BlockData blockData) {
        return addParticle(new TrackParticleDisplayBlock(position, orientation, clip, brightness, size, blockData));
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
                    for (Player viewer : particle.clearAllViewers()) {
                        ViewerParticleList viewed = this.viewers.get(viewer);
                        if (viewed != null) {
                            viewed.makeHidden(particle, viewer);
                        }
                    }
                } catch (Throwable t) {
                    getPlugin().getLogger().log(Level.SEVERE, "TrackParticle.makeHidden() failed for "
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

    /**
     * Re-spawns a particle for all viewers of a particle. This method ensures the
     * correct LOD (level-of-detail) is used in this respawning operation.
     *
     * @param particle TrackParticle to hide and then show again
     */
    public void hideAndDisplayParticle(TrackParticle particle) {
        for (Player viewer : particle.getViewers()) {
            hideAndDisplayParticle(particle, viewer);
        }
    }

    /**
     * Re-spawns a particle for all viewers of that particle. This method ensures the
     * correct LOD (level-of-detail) is used in this respawning operation.
     *
     * @param particle TrackParticle to hide and then show again
     * @param action An action to perform between when the particle is hidden and when it is
     *               shown again to the viewer
     */
    public <P extends TrackParticle> void hideAndDisplayParticle(P particle, Consumer<P> action) {
        ImmutablePlayerSet viewers = particle.getViewers();
        if (viewers.isEmpty()) {
            action.accept(particle);
            return;
        }

        // Collect all active lifecycles for this particle
        List<ViewerParticleList.ActiveLifecycle> lifecycles = new ArrayList<>(viewers.size());
        for (Player viewer : viewers) {
            ViewerParticleList viewed = this.viewers.get(viewer);
            if (viewed != null) {
                viewed.addLifecycle(particle, viewer, lifecycles);
            }
        }

        lifecycles.forEach(ViewerParticleList.ActiveLifecycle::makeHidden);
        action.accept(particle);
        lifecycles.forEach(ViewerParticleList.ActiveLifecycle::makeVisible);
    }

    /**
     * Re-spawns a particle for a viewer of that particle. This method ensures the
     * correct LOD (level-of-detail) is used in this respawning operation.
     *
     * @param particle TrackParticle to hide and then show again
     * @param viewer Player viewer
     */
    public void hideAndDisplayParticle(TrackParticle particle, Player viewer) {
        ViewerParticleList viewed = this.viewers.get(viewer);
        if (viewed != null) {
            viewed.hideAndDisplay(particle, viewer);
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
            ViewerLifecycleState lifecycleState = new ViewerLifecycleState(viewer, viewerBlock, this.updateCtr++);
            viewed.block = viewerBlock;
            int cuboid_range = this.getPlugin().getEditState(viewer).getParticleViewRange();
            int maxParticles = this.getPlugin().getMaximumParticleCount();
            IntVector3 range_min = viewerBlock.subtract(cuboid_range, cuboid_range, cuboid_range);
            IntVector3 range_max = viewerBlock.add(cuboid_range, cuboid_range, cuboid_range);
            long timeNow = System.currentTimeMillis();
            boolean reachedLimit = false;
            boolean reachedLimitRecently = (viewed.reachedLimitAt != 0 && (timeNow - viewed.reachedLimitAt) <= 5000);

            // This runs for the first time, or if we haven't hit a particle limit in a while
            if (!reachedLimitRecently) {
                int numParticles = 0;
                DoubleOctreeIterator<TrackParticle> iter = this.particles.cuboid(range_min, range_max).iterator();
                lifecycleState.cuboidIterator = iter; // For access to x/y/z
                while (iter.hasNext()) {
                    TrackParticle particle = (TrackParticle) iter.next();
                    if ((!canViewAllParticles && !particle.isAlwaysVisible()) || !particle.isVisible(viewer)) {
                        continue;
                    }

                    // Limit what can be displayed
                    if (++numParticles > maxParticles) {
                        reachedLimitRecently = true;
                        reachedLimit = true;

                        // Reset state, try again
                        // Changing the updateCounter causes it to de-spawn particles we spawned before
                        lifecycleState = new ViewerLifecycleState(viewer, viewerBlock, this.updateCtr++);
                        numParticles = 0;
                        break;
                    }

                    lifecycleState.resetViewDistance();
                    viewed.spawnOrRefresh(particle, lifecycleState);
                }
            }

            // This runs if there are a lot of particles around the player, and some will need to be trimmed off
            // This is done by sorting all particles by distance and omitting everything farthest away
            if (reachedLimitRecently) {
                ArrayList<ParticleWithBlockDistance> particlesSortedList = this.particlesSortedList;
                try {
                    DoubleOctreeIterator<TrackParticle> iter = this.particles.cuboid(range_min, range_max).iterator();
                    lifecycleState.cuboidIterator = iter; // For access to x/y/z
                    while (iter.hasNext()) {
                        TrackParticle particle = iter.next();
                        if ((!isInEditMode && !particle.isAlwaysVisible()) || !particle.isVisible(viewer)) {
                            continue;
                        }

                        int manhattanDistance = lifecycleState.calcViewDistance();
                        particlesSortedList.add(new ParticleWithBlockDistance(particle, manhattanDistance));
                    }
                    lifecycleState.cuboidIterator = null; // Avoid weirdness

                    // Sort so that furthest particles are at the end of the list
                    Collections.sort(particlesSortedList);

                    // Iterate the items, avoid exceeding limit
                    int numParticles = 0;
                    for (ParticleWithBlockDistance p : particlesSortedList) {
                        if (++numParticles > maxParticles) {
                            reachedLimit = true;
                            break;
                        } else {
                            lifecycleState.setViewDistance(p.distance);
                            viewed.spawnOrRefresh(p.particle, lifecycleState);
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

            // Particles that are no longer in view have an outdated update counter value
            // De-spawn all these
            viewed.despawnOutdated(lifecycleState);
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
            viewed.despawnAll(viewer);
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
        public final Map<TrackParticle, DisplayedState> particles = new ConcurrentHashMap<>(16, 0.75f, 1);
        public boolean reachedLimit = false;
        public long reachedLimitAt = 0;

        public ViewerParticleList(Player viewer) {
        }

        /**
         * Makes a particle visible for the first time, or updates its lifecycle-controlled visible state
         *
         * @param particle Particle to spawn in
         * @param lifecycleState State controlling what to spawn in (LOD, level of detail)
         */
        public void spawnOrRefresh(TrackParticle particle, ViewerLifecycleState lifecycleState) {
            particles.compute(particle, (p, state) -> {
                if (state == null) {
                    return DisplayedState.spawn(p, lifecycleState);
                } else {
                    state.refresh(p, lifecycleState);
                    return state;
                }
            });
        }

        /**
         * De-spawns all the particles for which {@link #spawnOrRefresh(TrackParticle, ViewerLifecycleState)}
         * was not called the last time. This is done by comparing against the update counter.
         *
         * @param lifecycleState State controlling what to spawn in (LOD, level of detail)
         */
        public void despawnOutdated(ViewerLifecycleState lifecycleState) {
            Iterator<Map.Entry<TrackParticle, DisplayedState>> iter = particles.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<TrackParticle, DisplayedState> entry = iter.next();
                DisplayedState state = entry.getValue();
                if (state.updateCounter != lifecycleState.updateCounter) {
                    state.despawn(entry.getKey(), lifecycleState.getViewer());
                    iter.remove();
                }
            }
        }

        /**
         * De-spawns all particles shown to this player
         *
         * @param viewer Viewer owner of this list of particles
         */
        public void despawnAll(Player viewer) {
            if (!particles.isEmpty()) {
                for (Map.Entry<TrackParticle, DisplayedState> entry : particles.entrySet()) {
                    entry.getValue().despawn(entry.getKey(), viewer);
                }
                particles.clear();
            }
        }

        /**
         * De-spawns the particle for a viewer, but does not update the particle viewers list
         *
         * @param particle TrackParticle
         * @param viewer Player
         */
        public void makeHidden(TrackParticle particle, Player viewer) {
            DisplayedState state = particles.get(particle);
            if (state != null) {
                state.particleLifecycle.makeHiddenFor(viewer);
                //state.despawn(particle, viewer);
            }
        }

        /**
         * Adds the lifecycle of a particle, if present, to a list of lifecycles
         *
         * @param particle TrackParticle
         * @param viewer Player viewer of the particles
         * @param lifecycles List of lifecycles (for later processing)
         */
        public void addLifecycle(TrackParticle particle, Player viewer, List<ActiveLifecycle> lifecycles) {
            DisplayedState state = particles.get(particle);
            if (state != null) {
                lifecycles.add(new ActiveLifecycle(state.particleLifecycle, viewer));
            }
        }

        /**
         * De-spawns and then re-spawns a particle for a viewer. Only does so if this particle
         * has been shown to the viewer before.
         *
         * @param particle TrackParticle
         * @param viewer Player viewer
         */
        public void hideAndDisplay(TrackParticle particle, Player viewer) {
            DisplayedState state = particles.get(particle);
            if (state != null) {
                state.particleLifecycle.makeHiddenFor(viewer);
                state.particleLifecycle.makeVisibleFor(viewer);
            }
        }

        public static class DisplayedState {
            /** Used to de-spawn particles that have not been made visible this tick */
            public int updateCounter;
            /** Keeps track of the displayed state to the player (LOD) */
            public TrackParticleLifecycle particleLifecycle;

            public static DisplayedState spawn(TrackParticle particle, ViewerLifecycleState lifecycleState) {
                particle.addNewViewer(lifecycleState.getViewer());

                DisplayedState state = new DisplayedState();
                state.updateCounter = lifecycleState.updateCounter;
                state.particleLifecycle = particle.getLifecycle(lifecycleState);
                state.particleLifecycle.makeVisibleFor(lifecycleState.getViewer());
                return state;
            }

            public void refresh(TrackParticle particle, ViewerLifecycleState lifecycleState) {
                this.updateCounter = lifecycleState.updateCounter;
                if (!this.particleLifecycle.isLifecycleValid(lifecycleState)) {
                    TrackParticleLifecycle newLifeCycle = particle.getLifecycle(lifecycleState);
                    if (this.particleLifecycle != newLifeCycle) {
                        newLifeCycle.switchFromLifecycle(this.particleLifecycle, lifecycleState.getViewer());
                        this.particleLifecycle = newLifeCycle;
                    }
                }
            }

            public void despawn(TrackParticle particle, Player viewer) {
                particle.removeOldViewer(viewer);

                particleLifecycle.makeHiddenFor(viewer);
            }
        }

        public static class ActiveLifecycle {
            public final TrackParticleLifecycle lifecycle;
            public final Player viewer;

            public ActiveLifecycle(TrackParticleLifecycle lifecycle, Player viewer) {
                this.lifecycle = lifecycle;
                this.viewer = viewer;
            }

            public void makeHidden() {
                lifecycle.makeHiddenFor(viewer);
            }

            public void makeVisible() {
                lifecycle.makeVisibleFor(viewer);
            }
        }
    }

    private static class ViewerLifecycleState implements TrackParticleLifecycle.State {
        public final Player viewer;
        public final IntVector3 viewerBlock;
        public final int updateCounter;
        public DoubleOctreeIterator<?> cuboidIterator; // Used for x/y/z
        private int cachedViewDistance = -1;

        public ViewerLifecycleState(Player viewer, IntVector3 viewerBlock, int updateCounter) {
            this.viewer = viewer;
            this.viewerBlock = viewerBlock;
            this.updateCounter = updateCounter;
        }

        @Override
        public Player getViewer() {
            return viewer;
        }

        public void resetViewDistance() {
            cachedViewDistance = -1;
        }

        public void setViewDistance(int distance) {
            cachedViewDistance = distance;
        }

        public int calcViewDistance() {
            DoubleOctreeIterator<?> iter = this.cuboidIterator;
            int dx = Math.abs(viewerBlock.x - iter.getBlockX());
            int dy = Math.abs(viewerBlock.y - iter.getBlockY());
            int dz = Math.abs(viewerBlock.z - iter.getBlockZ());
            return cachedViewDistance = (dx + dy + dz); // Manhattan distance
        }

        @Override
        public int getViewDistance() {
            int view = this.cachedViewDistance;
            if (view != -1) {
                return view;
            } else {
                return calcViewDistance();
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
