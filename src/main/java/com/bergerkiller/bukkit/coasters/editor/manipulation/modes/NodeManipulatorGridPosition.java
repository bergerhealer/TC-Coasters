package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNodeSpacingEqualizer;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeManipulatorBase;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Node drag manipulator that alters the position of the edited nodes
 * based on where the player is dragging. The positions are snapped to a half-block grid.
 * When the player is sneaking, the positions along the connection direction are not snapped,
 * allowing for finer control of the node spacing on straight tracks.
 */
public class NodeManipulatorGridPosition extends NodeManipulatorBase<ManipulatedTrackNode> {
    public static final Initializer INITIALIZER = NodeManipulatorGridPosition::new;

    public NodeManipulatorGridPosition(PlayerEditState state, List<ManipulatedTrackNode> manipulatedNodes) {
        super(state, manipulatedNodes);
        for (ManipulatedTrackNode node : manipulatedNodes) {
            node.dragPosition = node.node.getPosition().clone();
        }
    }

    @Override
    public void onDragStarted(NodeDragEvent event) {
    }

    @Override
    public void onDragUpdate(NodeDragEvent event) {
        // Check whether the player is moving only a single node or not
        // Count two zero-connected nodes as one node
        final boolean isSingleNode = state.isEditingSingleNode();

        for (ManipulatedTrackNode manipulatedNode : manipulatedNodes) {
            // Get raw drag result (position + orientation) and flags
            NodeDragPosition result = handleDrag(manipulatedNode, event, isSingleNode);

            // If snapping to rails, use the exact position returned (rails take over)
            Vector snapped;
            if (result.snappedToRails) {
                snapped = result.position.clone();
            } else {
                // Snap to half-block grid (nearest 0.5)
                snapped = result.position.clone();
                snapped.setX(Math.round(snapped.getX() * 2.0) / 2.0);
                snapped.setY(Math.round(snapped.getY() * 2.0) / 2.0);
                snapped.setZ(Math.round(snapped.getZ() * 2.0) / 2.0);

                // If snapping to a block face, preserve the axis component along the
                // returned orientation vector (so the node can be placed flush to the wall/floor)
                if (result.snappedToBlock) {
                    // orientation is expected to be a face normal (components -1/0/1)
                    if (Math.abs(result.orientation.getX()) > 0.5) {
                        snapped.setX(result.position.getX());
                    }
                    if (Math.abs(result.orientation.getY()) > 0.5) {
                        snapped.setY(result.position.getY());
                    }
                    if (Math.abs(result.orientation.getZ()) > 0.5) {
                        snapped.setZ(result.position.getZ());
                    }
                }
            }

            // Apply to node(s)
            manipulatedNode.setPosition(snapped);
            manipulatedNode.setOrientation(result.orientation);
        }
    }

    @Override
    public void onDragFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        if (tryMergeSingleNode(history)) {
            return;
        }

        recordEditedNodesInHistory(history);
    }

    @Override
    public void equalizeNodeSpacing(HistoryChangeCollection history) throws ChangeCancelledException {
        ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNode> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.equalizeSpacing(state, history);
    }

    @Override
    public void makeFiner(HistoryChangeCollection history) throws ChangeCancelledException {
        ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNode> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.makeFiner(state, history);
    }

    @Override
    public void makeCourser(HistoryChangeCollection history) throws ChangeCancelledException {
        ManipulatedTrackNodeSpacingEqualizer<ManipulatedTrackNode> equalizer = new ManipulatedTrackNodeSpacingEqualizer<>(manipulatedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.makeCourser(state, history);
    }
}
