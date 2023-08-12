package com.bergerkiller.bukkit.coasters.events;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

/**
 * Event fired before a new or updated animation state is added to a node. Important is that
 * this animation state might have a different position than the node's current position,
 * so it's important to check this animation state position separately as well. Connections
 * with other nodes should also be evaluated.
 */
public class CoasterBeforeUpdateAnimationStateEvent extends CoasterNodeEvent {
    private final TrackNodeAnimationState currAnimationState;
    private final TrackNodeAnimationState updatedAnimationState;

    /**
     * Initializer for the CoasterBeforeAddAnimationStateEvent
     *
     * @param who   The player that is about to add a new animation state
     * @param node  The node to which the animation state is going to be added
     * @param currAnimationState The current animation state that is being updated. Null if there is none.
     * @param updatedAnimationState The new animation state that is going to be added or stored
     */
    public CoasterBeforeUpdateAnimationStateEvent(Player who,
                                                  TrackNode node,
                                                  TrackNodeAnimationState currAnimationState,
                                                  TrackNodeAnimationState updatedAnimationState
    ) {
        super(who, node);
        this.currAnimationState = currAnimationState;
        this.updatedAnimationState = updatedAnimationState;
    }

    /**
     * Gets the current animation state that is going to be updated. Returns null
     * if a new animation state is added.
     *
     * @return new animation state information
     */
    public TrackNodeAnimationState getCurrentAnimationState() {
        return currAnimationState;
    }

    /**
     * Gets the animation state that is going to be added or updated to the node
     *
     * @return new animation state information
     */
    public TrackNodeAnimationState getUpdatedAnimationState() {
        return updatedAnimationState;
    }

    @Override
    public boolean testPositions(Predicate<Vector> positionFilter) {
        // Node itself
        if (!positionFilter.test(getNode().getPosition())) {
            return false;
        }

        // State position
        if (!positionFilter.test(updatedAnimationState.state.position)) {
            return false;
        }

        // Connections to other nodes
        for (TrackConnectionState conn : updatedAnimationState.connections) {
            if (!positionFilter.test(conn.getOtherNode(getNode()).getPosition())) {
                return false;
            }
        }

        return true;
    }
}
