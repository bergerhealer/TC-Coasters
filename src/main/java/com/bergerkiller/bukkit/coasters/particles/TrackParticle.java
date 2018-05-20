package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.collections.ImmutablePlayerSet;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.generated.net.minecraft.server.PacketHandle;

/**
 * A single particle of a track that is rendered (or not) for a player.
 * When players are editing tracks AND are nearby this particle, the
 * particle is spawned and kept updated for the player.
 */
public abstract class TrackParticle {
    private final TrackParticleWorld world;
    private ImmutablePlayerSet viewers = ImmutablePlayerSet.EMPTY;
    private TrackParticleState.Source stateSource = TrackParticleState.SOURCE_NONE;

    public TrackParticle(TrackParticleWorld world) {
        this.world = world;
    }

    public TrackParticleWorld getWorld() {
        return this.world;
    }

    public void remove() {
        this.world.removeParticle(this);
    }

    /**
     * Updates whether a particle is visible or not to a player, spawning or de-spawning it
     * 
     * @param viewer
     * @param viewerPosition of the viewer, null to hide forcibly
     */
    public final void updateFor(Player viewer, Vector viewerPosition) {
        boolean visible = (viewer.getWorld() == this.world.getWorld()) && viewerPosition != null && isVisible(viewerPosition);
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

    public final boolean isVisible(Vector viewerPosition) {
        double d = getViewDistance();
        return distanceSquared(viewerPosition) <= (d * d);
    }

    public final boolean isNearby(Vector viewerPosition) {
        return distanceSquared(viewerPosition) <= (5.0 * 5.0);
    }

    public abstract double distanceSquared(Vector viewerPosition);
    public abstract double getViewDistance();
    public abstract void makeVisibleFor(Player viewer);
    public abstract void makeHiddenFor(Player viewer);
    public abstract void updateAppearance();
    public abstract boolean usesEntityId(int entityId);
}
