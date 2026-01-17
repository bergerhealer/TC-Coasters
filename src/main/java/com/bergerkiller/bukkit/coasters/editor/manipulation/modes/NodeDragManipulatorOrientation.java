package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulatorBase;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Node drag manipulator that alters the orientation of the edited nodes
 * based on where the player is looking.
 */
public class NodeDragManipulatorOrientation extends NodeDragManipulatorBase {
    /** Virtual point of the node handle (lever) being dragged */
    private Vector handlePosition;

    @Override
    public void onStarted(PlayerEditState state, Collection<PlayerEditNode> editedNodes, NodeDragEvent event) {
        this.handlePosition = event.current().toVector();

        // Is used to properly alter the orientation of a node looked at
        TrackNode lookingAt = state.findLookingAt();
        if (lookingAt != null) {
            Vector forward = event.current().getRotation().forwardVector();
            double distanceTo = lookingAt.getPosition().distance(this.handlePosition);
            this.handlePosition.add(forward.multiply(distanceTo));
        }
    }

    @Override
    public void onUpdate(PlayerEditState state, Collection<PlayerEditNode> editedNodes, NodeDragEvent event) {
        if (!event.isStart()) {
            event.change().transformPoint(this.handlePosition);
        }

        for (PlayerEditNode editNode : editedNodes) {
            editNode.node.setOrientation(this.handlePosition.clone().subtract(editNode.node.getPosition()));
        }
    }

    @Override
    public void onFinished(PlayerEditState state, HistoryChangeCollection history, Collection<PlayerEditNode> editedNodes, NodeDragEvent event) throws ChangeCancelledException {
        recordEditedNodesInHistory(state, history, editedNodes);
    }
}
