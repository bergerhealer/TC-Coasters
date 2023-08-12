package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.util.Vector;

import java.util.function.Consumer;
import java.util.function.Predicate;

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

    /**
     * Sets this event as cancelled if any of the positions that are part of this coaster event's context
     * are invalid (predicate returns false). Can be used as a quick way to filter positions a player
     * is allowed to make changes in.
     *
     * @param positionFilter Filter predicate for positions where changes occurred
     * @see #testPositions(Predicate) 
     */
    public final void setCancelledIfPositionInvalid(Predicate<Vector> positionFilter) {
        if (!testPositions(positionFilter)) {
            setCancelled(true);
        }
    }

    /**
     * Calls the callback with all 3D node positions relevant for this event. For node changes, this is the
     * node position. For connections, it's the positions of the two nodes. For animation state changes,
     * it's the node position and the position of embedded connections and state positions.<br>
     * <br>
     * Basically, if you need to restrict changes at a particular position, you use this. If the predicate
     * returns false, the method returns immediately with a false value.
     *
     * @param positionFilter Filter predicate for positions where changes occurred
     */
    public boolean testPositions(Predicate<Vector> positionFilter) {
        return true;
    }
}
