package com.bergerkiller.bukkit.coasters.events;

import java.util.Collection;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Fired after a player has imported a new coaster. If the event is cancelled,
 * the coaster is removed in its entirety. The coaster can also be modified,
 * such as removing or adding nodes.
 */
public class CoasterImportEvent extends CoasterEvent {
    private static final HandlerList handlers = new HandlerList();
    private final TrackCoaster _coaster;

    /**
     * Initializes a new CoasterImportEvent
     * 
     * @param who      The player that imported a new coaster
     * @param coaster  The coaster that was imported
     */
    public CoasterImportEvent(Player who, TrackCoaster coaster) {
        super(who);
        _coaster = coaster;
    }

    /**
     * Gets the coaster that was imported
     * 
     * @return coaster
     */
    public TrackCoaster getCoaster() {
        return _coaster;
    }

    /**
     * Gets all the nodes of the coaster that were imported
     * 
     * @return nodes
     */
    public Collection<TrackNode> getNodes() {
        return _coaster.getNodes();
    }

    @Override
    public World getWorld() {
        return getCoaster().getWorld();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
