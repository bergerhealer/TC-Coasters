package com.bergerkiller.bukkit.coasters.particles;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.generated.net.minecraft.server.PacketHandle;

/**
 * A single particle of a track that is rendered (or not) for a player.
 * When players are editing tracks AND are nearby this particle, the
 * particle is spawned and kept updated for the player.
 */
public abstract class TrackParticle {
    private final ArrayList<Player> viewers = new ArrayList<Player>();
    private final TrackParticleWorld world;
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
        if (visible == this.viewers.contains(viewer)) {
            return;
        }
        LogicUtil.addOrRemove(this.viewers, viewer, visible);
        if (visible) {
            makeVisibleFor(viewer);
        } else {
            makeHiddenFor(viewer);
        }
    }

    public Collection<Player> getViewers() {
        return this.viewers;
    }

    public void makeHiddenForAll() {
        for (Player viewer : this.viewers) {
            this.makeHiddenFor(viewer);
        }
        this.viewers.clear();
    }

    public void broadcastPacket(PacketHandle packet) {
        for (Player viewer : this.viewers) {
            PacketUtil.sendPacket(viewer, packet);
        }
    }

    public final void setStateSource(TrackParticleState.Source stateSource) {
        this.stateSource = stateSource;
    }

    public final TrackParticleState getState(Player viewer) {
        return this.stateSource.getState(viewer);
    }

    /**
     * Called when the particle state changes for a viewer
     */
    public void onStateUpdated(Player viewer) {
    }

    public abstract boolean isVisible(Vector viewerPosition);
    public abstract void makeVisibleFor(Player viewer);
    public abstract void makeHiddenFor(Player viewer);
    public abstract void updateAppearance();
}
