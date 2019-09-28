package com.bergerkiller.bukkit.coasters.particles;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.math.Quaternion;

/**
 * Tracks and updates all the particle items on a single world
 */
public class TrackParticleWorld extends CoasterWorldAccess.Component {
    public DoubleOctree<TrackParticle> particles = new DoubleOctree<TrackParticle>();
    private final Map<Player, ViewerParticleList> viewers = new HashMap<>();
    private int updateCtr = 0;
    private boolean forceViewerUpdate = false;

    public TrackParticleWorld(CoasterWorldAccess world) {
        super(world);
    }

    public TrackParticleText addParticleText(Vector position, String text) {
        return addParticle(new TrackParticleText(this, position, text));
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

        for (TrackParticle particle : this.particles.values()) {
            particle.updateAppearance();
        }
    }

    public void update(Player viewer) {
        boolean viewerSeesWorld = (viewer.getWorld() == this.getWorld() &&
                this.getPlugin().getEditState(viewer).getMode() != PlayerEditState.Mode.DISABLED);

        if (viewerSeesWorld) {
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
            IntVector3 range_min = viewerBlock.subtract(cuboid_range, cuboid_range, cuboid_range);
            IntVector3 range_max = viewerBlock.add(cuboid_range, cuboid_range, cuboid_range);
            for (TrackParticle particle : this.particles.cuboid(range_min, range_max)) {
                if (particle.isVisible(viewer) && viewed.particles.put(particle, updateObject) == null) {
                    particle.changeVisibility(viewer, true);
                }
            }

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
     * Gets a set of all particles a player can see
     * 
     * @param viewer
     * @return set of viewed particles
     */
    public Set<TrackParticle> getViewedParticles(Player viewer) {
        ViewerParticleList viewed = this.viewers.get(viewer);
        return (viewed == null) ? Collections.emptySet() : viewed.particles.keySet();
    }

    /**
     * Despawns all particles a viewer sees
     * 
     * @param viewer
     */
    public void hideAllFor(Player viewer) {
        for (TrackParticle particle : getViewedParticles(viewer)) {
            particle.changeVisibility(viewer, false);
        }
    }

    /**
     * Checks whether a particular entity Id is a particle a player can see
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
     * This is used as a check when fixing interaction with blocks.
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
        public Map<TrackParticle, Integer> particles = new HashMap<>();
    }
}
