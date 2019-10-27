package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerEvent;

/**
 * Base class for all coaster event types
 */
public abstract class CoasterEvent extends PlayerEvent implements Cancellable {
    private boolean _cancelled = false;

    protected CoasterEvent(Player who) {
        super(who);
    }

    /**
     * Gets the world in which the event occurred
     * 
     * @return world
     */
    public abstract World getWorld();

    /**
     * Gets whether this coaster event is cancelled. A cancelled event means
     * this particular change will be reverted.
     *
     * @return true if this event is cancelled
     */
    public boolean isCancelled() {
        return _cancelled;
    }

    /**
     * Sets whether this coaster event is cancelled. A cancelled event means
     * this particular change will be reverted.
     *
     * @param cancel true if you wish to cancel this event
     */
    public void setCancelled(boolean cancel) {
        _cancelled = cancel;
    }
}
