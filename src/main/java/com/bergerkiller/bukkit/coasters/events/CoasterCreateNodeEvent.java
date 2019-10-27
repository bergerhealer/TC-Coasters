package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Event fired after a new track node is created for a coaster by a player
 */
public class CoasterCreateNodeEvent extends CoasterNodeEvent {
    /**
     * Initializer for the CoasterCreateNodeEvent
     * 
     * @param who   The player that created a new node
     * @param node  The node that was created
     */
    public CoasterCreateNodeEvent(Player who, TrackNode node) {
        super(who, node);
    }
}
