package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.ImmutablePlayerSet;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.generated.net.minecraft.server.PacketHandle;

/**
 * A single particle of a track that is rendered (or not) for a player.
 * When players are editing tracks AND are nearby this particle, the
 * particle is spawned and kept updated for the player.
 */
public abstract class TrackParticle {
    protected TrackParticleWorld world;
    private ImmutablePlayerSet viewers = ImmutablePlayerSet.EMPTY;
    private TrackParticleState.Source stateSource = TrackParticleState.SOURCE_NONE;
    protected boolean updateAppearanceQueued = false;

    public TrackParticleWorld getWorld() {
        return this.world;
    }

    public void remove() {
        this.world.removeParticle(this);
    }

    /**
     * Called when the particle is added to a world
     */
    protected void onAdded() {
    }

    /**
     * Called right before a particle is removed from a world
     */
    protected void onRemoved() {
    }

    /**
     * Updates whether a particle is visible or not to a player, spawning or de-spawning it
     * 
     * @param viewer
     * @param viewerPosition of the viewer, null to hide forcibly
     */
    public final void changeVisibility(Player viewer, boolean visible) {
        ImmutablePlayerSet new_viewers = this.viewers.addOrRemove(viewer, visible);
        if (this.viewers != new_viewers) {
            this.viewers = new_viewers;
            if (visible) {
                makeVisibleFor(viewer);
            } else {
                makeHiddenFor(viewer);
            }
        }
    }

    public ImmutablePlayerSet getViewers() {
        return this.viewers;
    }

    public void makeHiddenForAll() {
        for (Player viewer : this.viewers) {
            this.makeHiddenFor(viewer);
        }
        this.viewers = this.viewers.clear();
    }

    public void broadcastPacket(PacketHandle packet) {
        for (Player viewer : this.viewers) {
            PacketUtil.sendPacket(viewer, packet);
        }
    }

    public final void setStateSource(TrackParticleState.Source stateSource) {
        this.stateSource = stateSource;
    }

    public final TrackParticleState.Source getStateSource() {
        return this.stateSource;
    }

    public final TrackParticleState getState(Player viewer) {
        return this.stateSource.getState(viewer);
    }

    /**
     * Called when the particle state changes for a viewer
     */
    public void onStateUpdated(Player viewer) {
    }

    /**
     * Checks whether this particle is being displayed for a player.
     * By default returns true, can be overridden to conditionally show or hide particles.
     * 
     * @param viewer
     * @return True if visible
     */
    public boolean isVisible(Player viewer) {
        return true;
    }

    public final boolean isNearby(Vector viewerPosition) {
        return distanceSquared(viewerPosition) <= (5.0 * 5.0);
    }

    public abstract double distanceSquared(Vector viewerPosition);
    public abstract void makeVisibleFor(Player viewer);
    public abstract void makeHiddenFor(Player viewer);
    public abstract void updateAppearance();
    public abstract boolean usesEntityId(int entityId);

    protected void scheduleUpdateAppearance() {
        if (!this.updateAppearanceQueued) {
            this.updateAppearanceQueued = true;
            this.world.appearanceUpdates.add(this);
        }
    }

    protected void addPosition(DoubleOctree.Entry<TrackParticle> pos) {
        if (this.world != null) {
            this.world.particles.addEntry(pos);
        }
    }

    protected void removePosition(DoubleOctree.Entry<TrackParticle> pos) {
        if (this.world != null) {
            this.world.particles.removeEntry(pos);
        }
    }

    protected DoubleOctree.Entry<TrackParticle> updatePosition(DoubleOctree.Entry<TrackParticle> oldPos, Vector newPos) {
        return updatePosition(oldPos, DoubleOctree.Entry.create(newPos, this));
    }

    protected DoubleOctree.Entry<TrackParticle> updatePosition(DoubleOctree.Entry<TrackParticle> oldPos, double newX, double newY, double newZ) {
        return updatePosition(oldPos, DoubleOctree.Entry.create(newX, newY, newZ, this));
    }

    protected DoubleOctree.Entry<TrackParticle> updatePosition(DoubleOctree.Entry<TrackParticle> oldPos, DoubleOctree.Entry<TrackParticle> newPos) {
        if (this.world != null) {
            this.world.particles.moveEntry(oldPos, newPos);
        }
        return newPos;
    }
}
