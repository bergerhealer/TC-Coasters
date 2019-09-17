package com.bergerkiller.bukkit.coasters.tracks;

/**
 * Lockable object
 */
public interface Lockable {
    /**
     * Gets whether this object is locked.
     * A locked object cannot be modified by players until unlocked.
     * 
     * @return True if locked
     */
    boolean isLocked();
}
