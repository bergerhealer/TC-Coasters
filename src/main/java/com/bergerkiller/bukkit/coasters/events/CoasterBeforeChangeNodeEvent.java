package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Event fired before a player starts changing a node's position,
 * orientation or rail block, or other property of the node.
 */
public class CoasterBeforeChangeNodeEvent extends CoasterNodeEvent {
    /**
     * Initializer for the CoasterBeforeMoveNodeEvent
     * 
     * @param who   The player that is about to change a node
     * @param node  The node that is going to be changed
     */
    public CoasterBeforeChangeNodeEvent(Player who, TrackNode node) {
        super(who, node);
    }
}
