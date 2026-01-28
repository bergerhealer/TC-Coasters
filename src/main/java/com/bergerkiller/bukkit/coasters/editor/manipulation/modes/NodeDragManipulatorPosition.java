package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulatorBase;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

import java.util.Map;

/**
 * Node drag manipulator that alters the position of the edited nodes
 * based on where the player is dragging.
 */
public class NodeDragManipulatorPosition extends NodeDragManipulatorBase {
    public static final Initializer INITIALIZER = (state, editedNodes, event) -> new NodeDragManipulatorPosition(state, editedNodes);

    public NodeDragManipulatorPosition(PlayerEditState state, Map<TrackNode, PlayerEditNode> editedNodes) {
        super(state, editedNodes);
    }

    @Override
    public void onStarted(NodeDragEvent event) {
        for (PlayerEditNode node : editedNodes) {
            node.dragPosition = node.node.getPosition().clone();
        }
    }

    @Override
    public void onUpdate(NodeDragEvent event) {
        // Check whether the player is moving only a single node or not
        // Count two zero-connected nodes as one node
        final boolean isSingleNode = state.isEditingSingleNode();

        for (PlayerEditNode editNode : editedNodes) {
            handleDrag(editNode, event, isSingleNode).applyTo(editNode.node);
        }
    }

    @Override
    public void onFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        if (tryMergeSingleNode(history)) {
            return;
        }

        recordEditedNodesInHistory(history);
    }
}
