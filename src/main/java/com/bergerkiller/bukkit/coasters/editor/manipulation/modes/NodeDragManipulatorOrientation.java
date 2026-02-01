package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.DraggedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulatorBase;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Node drag manipulator that alters the orientation of the edited nodes
 * based on where the player is looking.
 */
public class NodeDragManipulatorOrientation extends NodeDragManipulatorBase<DraggedTrackNode> {
    public static final Initializer INITIALIZER = (state, draggedNodes, event) -> new NodeDragManipulatorOrientation(state, draggedNodes);

    /** Virtual point of the node handle (lever) being dragged */
    private Vector handlePosition;

    public NodeDragManipulatorOrientation(PlayerEditState state, List<DraggedTrackNode> draggedNodes) {
        super(state, draggedNodes);
    }

    @Override
    public void onStarted(NodeDragEvent event) {
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
    public void onUpdate(NodeDragEvent event) {
        if (!event.isStart()) {
            event.change().transformPoint(this.handlePosition);
        }

        for (DraggedTrackNode draggedNode : draggedNodes) {
            draggedNode.setOrientation(this.handlePosition.clone().subtract(draggedNode.node.getPosition()));
        }
    }

    @Override
    public void onFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        recordEditedNodesInHistory(history);
    }
}
