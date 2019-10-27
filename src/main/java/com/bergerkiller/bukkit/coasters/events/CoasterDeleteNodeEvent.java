package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Event fired before a track node is deleted for a coaster by a player
 */
public class CoasterDeleteNodeEvent extends CoasterNodeEvent {
    /**
     * Initializer for the CoasterDeleteNodeEvent
     * 
     * @param who   The player that is deleting a node
     * @param node  The node that is about to be deleted
     */
    public CoasterDeleteNodeEvent(Player who, TrackNode node) {
        super(who, node);
    }
}
