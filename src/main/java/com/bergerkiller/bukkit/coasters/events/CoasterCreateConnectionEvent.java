package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Event fired when a connection is made between two nodes by a player
 */
public class CoasterCreateConnectionEvent extends CoasterConnectionEvent {
    /**
     * Initializer for the CoasterCreateConnectionEvent
     * 
     * @param who          The player that created a connection between two nodes
     * @param connection   The connection that was created
     */
    public CoasterCreateConnectionEvent(Player who, TrackConnection connection) {
        super(who, connection);
    }
}
