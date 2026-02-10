package com.bergerkiller.bukkit.coasters.editor.manipulation.modes;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.ManipulatedTrackNode;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragEvent;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeManipulatorBase;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Node drag manipulator that alters the orientation of the edited nodes
 * based on where the player is looking.
 */
public class NodeManipulatorOrientation extends NodeManipulatorBase<ManipulatedTrackNode> {
    public static final Initializer INITIALIZER = NodeManipulatorOrientation::new;

    /** Virtual point of the node handle (lever) being dragged */
    private Vector handlePosition;

    public NodeManipulatorOrientation(PlayerEditState state, List<ManipulatedTrackNode> manipulatedNodes) {
        super(state, manipulatedNodes);
    }

    @Override
    public void onDragStarted(NodeDragEvent event) {
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
    public void onDragUpdate(NodeDragEvent event) {
        if (!event.isStart()) {
            event.change().transformPoint(this.handlePosition);
        }

        for (ManipulatedTrackNode manipulatedNode : manipulatedNodes) {
            manipulatedNode.setOrientation(this.handlePosition.clone().subtract(manipulatedNode.node.getPosition()));
        }
    }

    @Override
    public void onDragFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException {
        recordEditedNodesInHistory(history);
    }
}
