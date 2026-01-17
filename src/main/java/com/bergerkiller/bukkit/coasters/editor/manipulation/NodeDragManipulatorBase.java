package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base logic for all manipulators. Adds some common logic, such as handling
 * saving the changes on finish into the player's history.
 */
public abstract class NodeDragManipulatorBase implements NodeDragManipulator {

    /**
     * If dragging only a single node around, attempts to merge this node with another nearby node.
     * This is done when releasing drag for a single selected node.
     *
     * @param state PlayerEditState
     * @param history History change collection to record finalized manipulation changes into
     * @param editedNodes Edited nodes
     * @return True if a merge was performed (and other logic should be skipped)
     * @throws ChangeCancelledException If the change is cancelled
     */
    protected boolean tryMergeSingleNode(PlayerEditState state, HistoryChangeCollection history, Collection<PlayerEditNode> editedNodes) throws ChangeCancelledException {
        if (!state.isEditingSingleNode()) {
            return false;
        }

        TrackWorld tracks = state.getWorld().getTracks();
        PlayerEditNode draggedNode = editedNodes.iterator().next();

        // If we never even began moving this node, do nothing
        if (!draggedNode.hasMoveBegun()) {
            return false;
        }

        // Get all nodes nearby the position, sorted from close to far
        // Pick first (closest) node that is not the node(s) dragged
        final Vector pos = draggedNode.node.getPosition();
        final TrackNode initialDroppedNode = tracks.findNodesNear(new ArrayList<TrackNode>(), pos, 1e-2).stream()
                .sorted(Comparator.comparingDouble(o -> o.getPosition().distanceSquared(pos)))
                .filter(n -> !state.isEditing(n))
                .findFirst()
                .orElse(null);

        // Merge if found
        if (initialDroppedNode != null) {
            try {
                // Save all connections the dragged node had
                List<TrackConnectionState> previousConnections = draggedNode.node.getConnections().stream()
                        .filter(c -> !c.isConnected(initialDroppedNode))
                        .map(TrackConnectionState::create)
                        .collect(Collectors.toCollection(ArrayList::new));

                // Undo the changes to the node position as a result of the drag
                draggedNode.node.setState(draggedNode.startState);

                // Track all the changes we are doing down below.
                // Delete the original node, and connections to the node, the player was dragging
                HistoryChange changes = history.addChangeBeforeDeleteNode(state.getPlayer(), draggedNode.node);
                draggedNode.node.remove();

                // If the dropped node has a zero-distance neighbour, special care must be taken
                // If it or itself is an orphan, purge the right orphan node accordingly
                // If both nodes have connections already, it becomes a junction, so then
                // one of the two nodes must be removed and connections transferred over.
                final TrackNode initialDroppedZDNode = initialDroppedNode.getZeroDistanceNeighbour();

                // Prefer connecting with a zero-distance orphan node
                TrackNode droppedZDNode = initialDroppedZDNode;
                TrackNode droppedNode = initialDroppedNode;
                if (droppedZDNode != null && droppedZDNode.isZeroDistanceOrphan()) {
                    // If both are orphans, there is no way to resolve this through connecting later
                    // Get rid of the duplicate orphan
                    if (droppedNode.isZeroDistanceOrphan()) {
                        previousConnections.removeIf(c -> c.isConnected(initialDroppedZDNode));
                        droppedZDNode.remove();
                        droppedZDNode = null;
                    } else {
                        previousConnections.removeIf(c -> c.isConnected(initialDroppedNode));
                        droppedZDNode = droppedNode;
                        droppedNode = initialDroppedZDNode;
                    }
                }

                if (droppedZDNode != null) {
                    // If node being dropped on is an orphan, and there's nothing to connect to
                    // after, then just fix up the orphan situation
                    if (droppedNode.isZeroDistanceOrphan() && previousConnections.isEmpty()) {
                        droppedNode.remove();
                        state.selectNode(droppedZDNode);
                        return true; // Skip everything
                    }

                    // If there's connections and we got two non-orphaned straightened nodes,
                    // then it would turn into a junction. Make sure to make it curved first.
                    if (previousConnections.size() > 1 || (!previousConnections.isEmpty() && !droppedNode.isZeroDistanceOrphan())) {
                        state.makeNodeConnectionsCurved(history, droppedNode);
                        droppedZDNode = null; // Removed
                    }
                }

                // Connect all that was connected to it, with the one dropped on
                for (TrackConnectionState connectionState : previousConnections) {
                    TrackConnection connection = tracks.connect(connectionState.replaceNode(draggedNode.node, droppedNode), true);
                    if (connection != null) {
                        changes.addChangeAfterConnect(state.getPlayer(), connection);
                        state.addConnectionForAnimationStates(droppedNode, connection.getOtherNode(droppedNode));
                        state.addConnectionForAnimationStates(connection.getOtherNode(droppedNode), droppedNode);
                    }
                }

                // Select the node it was dropped on
                state.selectNode(droppedNode);
                if (droppedZDNode != null) {
                    state.selectNode(droppedZDNode);
                }

                // Do not do the standard position/orientation change saving down below
                return true;
            } finally {
                draggedNode.moveEnd();
            }
        }

        return false;
    }

    protected void recordEditedNodesInHistory(PlayerEditState state, HistoryChangeCollection history, Collection<PlayerEditNode> editedNodes) throws ChangeCancelledException {
        try {
            // Before processing, fire an event for all nodes that changed. If any of them fail (permissions!),
            // cancel the entire move operation for all other nodes, too.
            {
                HistoryChange changes = null;
                for (PlayerEditNode editNode : editedNodes) {
                    if (editNode.hasMoveBegun()) {
                        try {
                            if (changes == null) {
                                changes = history.addChangeGroup();
                            }
                            changes.addChangeAfterChangingNode(state.getPlayer(), editNode.node, editNode.startState);
                        } catch (ChangeCancelledException ex) {
                            // Undo all changes that were already executed or are going to be executed for other nodes
                            // Ignore the one that was already cancelled
                            for (PlayerEditNode prevModifiedNode : editedNodes) {
                                if (prevModifiedNode != editNode) {
                                    prevModifiedNode.node.setState(prevModifiedNode.startState);
                                }
                            }
                            throw ex;
                        }
                    }
                }
            }

            // Update position and orientation of animation state, if one is selected
            String selectedAnimation = state.getSelectedAnimation();
            if (selectedAnimation != null) {
                for (PlayerEditNode editNode : editedNodes) {
                    if (editNode.hasMoveBegun()) {
                        TrackNodeAnimationState animState = editNode.node.findAnimationState(selectedAnimation);
                        if (animState != null) {
                            editNode.node.setAnimationState(animState.name,
                                    editNode.node.getState().changeRail(animState.state.railBlock),
                                    animState.connections);
                        }
                    }
                }
            }
        } finally {
            for (PlayerEditNode editNode : editedNodes) {
                editNode.moveEnd();
            }
        }
    }
}
