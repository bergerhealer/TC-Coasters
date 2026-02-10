package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.TCCoastersUtil;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Base logic for all manipulators. Adds some common logic, such as handling
 * saving the changes on finish into the player's history.
 *
 * @param <N> Type of ManipulatedTrackNode used
 */
public abstract class NodeManipulatorBase<N extends ManipulatedTrackNode> implements NodeManipulator {
    protected final PlayerEditState state;
    protected final List<N> manipulatedNodes;

    public NodeManipulatorBase(PlayerEditState state, List<ManipulatedTrackNode> manipulatedNodes, Function<ManipulatedTrackNode, N> converter) {
        this.state = state;
        this.manipulatedNodes = manipulatedNodes.stream().map(converter).collect(Collectors.toList());
    }

    public NodeManipulatorBase(PlayerEditState state, List<N> manipulatedNodes) {
        this.state = state;
        this.manipulatedNodes = manipulatedNodes;
    }

    /**
     * Moves a single node according to the drag event. This is used in position manipulation mode.
     *
     * @param draggedNode ManipulatedTrackNode
     * @param event Node drag event
     * @param isSingleNode Whether only a single node is dragged. Has special meaning for snapping against blocks.
     * @return Node drag position after move
     */
    protected NodeDragPosition handleDrag(ManipulatedTrackNode draggedNode, NodeDragEvent event, boolean isSingleNode) {
        Player player = state.getPlayer();

        // Recover null
        if (draggedNode.dragPosition == null) {
            draggedNode.dragPosition = draggedNode.node.getPosition().clone();
        }

        // Transform position and compute direction using player view position relative to the node
        event.change().transformPoint(draggedNode.dragPosition);
        Vector position = draggedNode.dragPosition.clone();
        Vector orientation = draggedNode.startState.orientation.clone();
        Vector direction = position.clone().subtract(player.getEyeLocation().toVector()).normalize();
        if (Double.isNaN(direction.getX())) {
            direction = player.getEyeLocation().getDirection();
        }

        // Snap position against the side of a block
        // Then, look for other rails blocks and attach to it
        // When sneaking, disable this functionality
        // When more than 1 node is selected, only do this for nodes with 1 or less connections
        // This is to avoid severe performance problems when moving a lot of track at once
        if (!state.isSneaking() && (isSingleNode || draggedNode.node.getConnections().size() <= 1)) {
            Vector eyePos = player.getEyeLocation().toVector();
            TCCoastersUtil.snapToBlock(state.getBukkitWorld(), eyePos, position, orientation);

            if (TCCoastersUtil.snapToCoasterRails(draggedNode.node, position, orientation, n -> !state.isEditing(n))) {
                // Play particle effects to indicate we are snapping to the coaster rails
                PlayerUtil.spawnDustParticles(player, position, Color.RED);
            } else if (TCCoastersUtil.snapToRails(state.getBukkitWorld(), draggedNode.node.getRailBlock(true), position, direction, orientation)) {
                // Play particle effects to indicate we are snapping to the rails
                PlayerUtil.spawnDustParticles(player, position, Color.PURPLE);
            }
        }

        // Return result
        return new NodeDragPosition(position, orientation);
    }

    /**
     * If dragging only a single node around, attempts to merge this node with another nearby node.
     * This is done when releasing drag for a single selected node.
     *
     * @param history History change collection to record finalized manipulation changes into
     * @return True if a merge was performed (and other logic should be skipped)
     * @throws ChangeCancelledException If the change is cancelled
     */
    protected boolean tryMergeSingleNode(HistoryChangeCollection history) throws ChangeCancelledException {
        if (!state.isEditingSingleNode()) {
            return false;
        }

        final TrackWorld tracks = state.getWorld().getTracks();
        final ManipulatedTrackNode draggedNode = manipulatedNodes.iterator().next();

        // Get all nodes nearby the position, sorted from close to far
        // Pick first (closest) node that is not the node(s) dragged
        final Vector pos = draggedNode.node.getPosition();
        final TrackNode initialDroppedNode = tracks.findNodesNear(new ArrayList<>(), pos, 1e-2).stream()
                .filter(n -> n != draggedNode.node && n != draggedNode.node_zd)
                .sorted(Comparator.comparingDouble(o -> o.getPosition().distanceSquared(pos)))
                .filter(n -> !state.isEditing(n))
                .findFirst()
                .orElse(null);

        // Merge if found
        if (initialDroppedNode != null) {
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
        }

        return false;
    }

    protected void recordEditedNodesInHistory(HistoryChangeCollection history) throws ChangeCancelledException {
        // Before processing, fire an event for all nodes that changed. If any of them fail (permissions!),
        // cancel the entire move operation for all other nodes, too.
        {
            HistoryChange changes = null;
            for (ManipulatedTrackNode manipulatedNode : manipulatedNodes) {
                try {
                    if (changes == null) {
                        changes = history.addChangeGroup();
                    }
                    if (!manipulatedNode.node.isRemoved()) {
                        changes.addChangeAfterChangingNode(state.getPlayer(), manipulatedNode.node, manipulatedNode.startState);
                    }
                    if (manipulatedNode.node_zd != null && !manipulatedNode.node_zd.isRemoved()) {
                        changes.addChangeAfterChangingNode(state.getPlayer(), manipulatedNode.node_zd, manipulatedNode.startState);
                    }
                } catch (ChangeCancelledException ex) {
                    // Undo all changes that were already executed or are going to be executed for other nodes
                    // Ignore the one that was already cancelled
                    for (ManipulatedTrackNode prevModifiedNode : manipulatedNodes) {
                        if (prevModifiedNode != manipulatedNode) {
                            if (!prevModifiedNode.node.isRemoved()) {
                                prevModifiedNode.node.setState(prevModifiedNode.startState);
                            }
                            if (prevModifiedNode.node_zd != null && !prevModifiedNode.node_zd.isRemoved()) {
                                prevModifiedNode.node_zd.setState(prevModifiedNode.startState);
                            }
                        }
                    }
                    throw ex;
                }
            }
        }

        // Update position and orientation of animation state, if one is selected
        String selectedAnimation = state.getSelectedAnimation();
        if (selectedAnimation != null) {
            for (ManipulatedTrackNode draggedNode : manipulatedNodes) {
                TrackNodeAnimationState animState = draggedNode.node.findAnimationState(selectedAnimation);
                if (animState != null) {
                    draggedNode.node.setAnimationState(animState.name,
                            draggedNode.node.getState().changeRail(animState.state.railBlock),
                            animState.connections);
                }
            }
        }
    }

    protected static class NodeDragPosition {
        public final Vector position;
        public final Vector orientation;

        public NodeDragPosition(Vector position, Vector orientation) {
            this.position = position;
            this.orientation = orientation;
        }

        public void applyTo(ManipulatedTrackNode node) {
            node.setPosition(position);
            node.setOrientation(orientation);
        }
    }
}
