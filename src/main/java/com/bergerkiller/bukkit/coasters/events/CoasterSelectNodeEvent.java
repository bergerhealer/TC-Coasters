package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Event fired when a node is selected by a player
 */
public class CoasterSelectNodeEvent extends CoasterNodeEvent {
    /**
     * Initializer for the CoasterSelectNodeEvent
     * 
     * @param who   The player that selected a new node
     * @param node  The node that was selected
     */
    public CoasterSelectNodeEvent(Player who, TrackNode node) {
        super(who, node);
    }
}
