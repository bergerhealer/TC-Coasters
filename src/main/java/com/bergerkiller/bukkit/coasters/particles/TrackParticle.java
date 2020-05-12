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
    private int flags = 0;

    protected static final int FLAG_APPEARANCE_DIRTY = (1<<0);
    protected static final int FLAG_ALWAYS_VISIBLE   = (1<<1);

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

    /**
     * Sets or clears a flag of this particle. Returns true if the flag changed.
     * 
     * @param flag
     * @param set Whether to set (true) or clear (flag) the flag
     * @return True if the flag changed
     */
    protected final boolean setFlag(int flag, boolean set) {
        if (((this.flags & flag) != 0) != set) {
            this.flags ^= flag;
            return true;
        }
        return false;
    }

    /**
     * Sets a flag of this particle. Returns true if the flag changed.
     * 
     * @param flag
     * @return True if the flag changed (was cleared before)
     */
    protected final boolean setFlag(int flag) {
        if ((this.flags & flag) == 0) {
            this.flags |= flag;
            return true;
        }
        return false;
    }

    /**
     * Clears a flag of this particle. Returns true if the flag changed.
     * @param flag
     * @return True if the flag changed (was set before)
     */
    protected final boolean clearFlag(int flag) {
        if ((this.flags & flag) != 0) {
            this.flags &= ~flag;
            return true;
        }
        return false;
    }

    /**
     * Reads the current set-state of a flag of this particle
     * 
     * @param flag
     * @return True if flag is set
     */
    protected final boolean getFlag(int flag) {
        return (this.flags & flag) != 0;
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
        return this.stateSource.getState(this.getWorld().getPlugin().getEditState(viewer));
    }

    /**
     * Called when the particle state changes for a viewer
     */
    public void onStateUpdated(Player viewer) {
    }

    /**
     * Gets whether this particle is always visible, even when the viewer is not in the
     * TC-Coasters edit mode.
     * 
     * @return True if always visible
     */
    public final boolean isAlwaysVisible() {
        return getFlag(FLAG_ALWAYS_VISIBLE);
    }

    /**
     * Sets whether this particle is always visible, even when the viewer is not in the
     * TC-Coasters edit mode.
     * 
     * @param alwaysVisible True if this particle is always visible
     */
    public final void setAlwaysVisible(boolean alwaysVisible) {
        setFlag(FLAG_ALWAYS_VISIBLE, alwaysVisible);
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
        if (setFlag(FLAG_APPEARANCE_DIRTY)) {
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
