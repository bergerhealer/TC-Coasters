package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.util.QueuedTask;
import com.bergerkiller.bukkit.common.collections.ImmutablePlayerSet;
import com.bergerkiller.bukkit.common.collections.octree.DoubleOctree;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.generated.net.minecraft.network.protocol.PacketHandle;

/**
 * A single particle of a track that is rendered (or not) for a player.
 * When players are editing tracks AND are nearby this particle, the
 * particle is spawned and kept updated for the player.
 */
public abstract class TrackParticle implements TrackParticleLifecycle {
    protected TrackParticleWorld world;
    private ImmutablePlayerSet viewers = ImmutablePlayerSet.EMPTY;
    private TrackParticleState.Source stateSource = TrackParticleState.SOURCE_NONE;
    private int flags = 0;

    protected static final int FLAG_APPEARANCE_DIRTY = (1<<0);
    protected static final int FLAG_ALWAYS_VISIBLE   = (1<<1);

    private static final QueuedTask<TrackParticle> UPDATE_APPEARANCE_TASK = QueuedTask.create(TrackParticle::isAdded, particle -> {
        particle.clearFlag(TrackParticle.FLAG_APPEARANCE_DIRTY);
        particle.updateAppearance();
    });

    public TrackParticleWorld getWorld() {
        return this.world;
    }

    public void remove() {
        if (this.world != null) {
            this.world.removeParticle(this);
        } else {
            throw new IllegalStateException("Particle was already removed");
        }
    }

    /**
     * Called when the particle is added to a world
     */
    protected abstract void onAdded();

    /**
     * Called right before a particle is removed from a world
     */
    protected abstract void onRemoved();

    /**
     * Gets whether this particle was added to a world
     * 
     * @return True if added
     */
    public final boolean isAdded() {
        return this.world != null;
    }

    /**
     * Gets the {@link TrackParticleLifecycle} used to display this particle. By default
     * returns the default lifecycle, which is this particle itself. Different values can
     * be returned to change the appearance.<br>
     * <br>
     * Primarily used for LOD (level-of-detail)
     *
     * @param state Current state that controls the lifecycle
     * @return TrackParticleLifecycle to use
     */
    public TrackParticleLifecycle getLifecycle(TrackParticleLifecycle.State state) {
        return this;
    }

    // Internal use only
    final void addNewViewer(Player viewer) {
        this.viewers = this.viewers.add(viewer);
    }

    // Internal use only
    final void removeOldViewer(Player viewer) {
        this.viewers = this.viewers.remove(viewer);
    }

    // Internal use only
    final ImmutablePlayerSet clearAllViewers() {
        ImmutablePlayerSet old = this.viewers;
        this.viewers = old.clear();
        return old;
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

    public boolean hasViewers() {
        return !this.viewers.isEmpty();
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
     * Whether this type of particle tracks viewers in range of the particle
     * when updating appearance. If the particle alters state, such as a physical
     * block, light or spawns particles continuously, then knowing what viewers
     * exist nearby isn't important anymore.
     * 
     * @return True if this particle tracks viewers
     */
    public boolean isUsingViewers() {
        return true;
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
            UPDATE_APPEARANCE_TASK.schedule(this);
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

    protected void addWithoutViewers() {
        if (this.world != null) {
            this.world.particlesWithoutViewers.add(this);
        }
    }

    protected void removeWithoutViewers() {
        if (this.world != null) {
            this.world.particlesWithoutViewers.remove(this);
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
