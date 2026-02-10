package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.DraggedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.DraggedTrackNodeSpacingEqualizer;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulatorBase;

import java.util.List;

/**
 * Node drag manipulator that alters the position of the edited nodes
 * based on where the player is dragging.
 */
public class NodeDragManipulatorPosition extends NodeDragManipulatorBase<DraggedTrackNode> {
    public static final Initializer INITIALIZER = NodeDragManipulatorPosition::new;

    public NodeDragManipulatorPosition(PlayerEditState state, List<DraggedTrackNode> draggedNodes) {
        super(state, draggedNodes);
        for (DraggedTrackNode node : draggedNodes) {
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

        for (DraggedTrackNode draggedNode : draggedNodes) {
            handleDrag(draggedNode, event, isSingleNode).applyTo(draggedNode);
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
        DraggedTrackNodeSpacingEqualizer<DraggedTrackNode> equalizer = new DraggedTrackNodeSpacingEqualizer<>(draggedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.equalizeSpacing(state, history);
    }

    @Override
    public void makeFiner(HistoryChangeCollection history) throws ChangeCancelledException {
        DraggedTrackNodeSpacingEqualizer<DraggedTrackNode> equalizer = new DraggedTrackNodeSpacingEqualizer<>(draggedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.makeFiner(state, history);
    }

    @Override
    public void makeCourser(HistoryChangeCollection history) throws ChangeCancelledException {
        DraggedTrackNodeSpacingEqualizer<DraggedTrackNode> equalizer = new DraggedTrackNodeSpacingEqualizer<>(draggedNodes);
        equalizer.findChains();
        if (equalizer.chains.isEmpty()) {
            throw new ChangeCancelledException();
        }

        equalizer.makeCourser(state, history);
    }
}
