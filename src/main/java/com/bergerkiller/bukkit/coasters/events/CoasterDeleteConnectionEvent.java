package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Event fired before a connection is deleted between two nodes by a player
 */
public class CoasterDeleteConnectionEvent extends CoasterConnectionEvent {
    /**
     * Initializer for the CoasterDeleteConnectionEvent
     * 
     * @param who          The player that is deleting a connection between two nodes
     * @param connection   The connection that is about to be deleted
     */
    public CoasterDeleteConnectionEvent(Player who, TrackConnection connection) {
        super(who, connection);
    }
}
