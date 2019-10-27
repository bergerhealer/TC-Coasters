package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Event fired after a player finished changing a node, or a player
 * aborts changing the node in other ways such as by logging off.
 */
public class CoasterAfterChangeNodeEvent extends CoasterNodeEvent {
    private final TrackNodeState _previous;

    /**
     * Initializer for the CoasterAfterMoveNodeEvent
     * 
     * @param who       The player that finished changing a node
     * @param node      The node that was changed
     * @param previous  The previous state of the node before changing
     */
    public CoasterAfterChangeNodeEvent(Player who, TrackNode node, TrackNodeState previous) {
        super(who, node);
        _previous = previous;
    }

    /**
     * Gets the previous position of the node before moving it
     * 
     * @return previous position
     */
    public Vector getPreviousPosition() {
        return _previous.position;
    }

    /**
     * Gets the previous orientation of the node before moving it
     * 
     * @return previous orientation
     */
    public Vector getPreviousOrientation() {
        return _previous.orientation;
    }

    /**
     * Gets the previous rail block coordinates, null if no rail block was set
     * 
     * @return previous rail block
     */
    public IntVector3 getPreviousRailBlock() {
        return _previous.railBlock;
    }
}
