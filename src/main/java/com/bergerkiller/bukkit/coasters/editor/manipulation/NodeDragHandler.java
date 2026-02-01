package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditInput;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.events.CoasterBeforeChangeNodeEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks the change in where the player looks, and makes a 4x4 transform matrix available
 * of the change in the current update. Produces {@link NodeDragEvent}s for node dragging manipulations.
 */
public class NodeDragHandler {
    private final PlayerEditInput input;
    private Matrix4x4 editStartTransform = null;

    /** Currently edited nodes. If different, drag manipulations are shifted out. */
    private Collection<TrackNode> editedNodesSaveState = Collections.emptyList();
    /** Current manipulator, or null if no manipulation is active */
    private NodeDragManipulator manipulator = null;

    public NodeDragHandler(PlayerEditInput input) {
        this.input = input;
    }

    /**
     * Resets the drag state to the current input transform, and restarting
     * the manipulations.
     */
    public void reset() {
        this.editStartTransform = null;
        this.manipulator = null;
        this.editedNodesSaveState = Collections.emptyList();
    }

    /**
     * Gets whether a manipulator is currently active.
     *
     * @return True if manipulating
     */
    public boolean isManipulating() {
        return manipulator != null;
    }

    /**
     * Called every tick while a drag is happening. Automatically initializes and re-initializes
     * the drag operation as needed.
     *
     * @param initializer Manipulator initializer. Creates a new manipulator if no manipulation is active yet.
     * @param state PlayerEditState
     * @return Drag result
     */
    public DragResult drag(NodeDragManipulator.Initializer initializer, PlayerEditState state) throws ChangeCancelledException {
        Set<TrackNode> editedNodes = state.getEditedNodes();
        if (editedNodes.isEmpty()) {
            return new DragResult(Collections.emptyList(), null);
        }

        // If a manipulator is active, verify that the edited nodes are still the same
        // If not, finish the previous manipulator and resume with a newly created one
        if (manipulator != null && !areEditedNodesEqual(this.editedNodesSaveState, editedNodes)) {
            // This could throw (fail to commit), in which case no drag is started
            // Caller will reset selected nodes to resolve the problem.
            this.finish(state.getHistory());
        }

        // If no manipulator is set yet, create one
        if (manipulator == null) {
            // Fire start events for all nodes to be edited and keep track of cancelled ones
            List<TrackNode> cancelledNodes = new ArrayList<>();
            List<TrackNode> editableNodes = new ArrayList<>();
            for (TrackNode node : editedNodes) {
                if (CommonUtil.callEvent(new CoasterBeforeChangeNodeEvent(input.getPlayer(), node)).isCancelled()) {
                    cancelledNodes.add(node);
                } else {
                    editableNodes.add(node);
                }
            }
            if (!editableNodes.isEmpty()) {
                NodeDragEvent event = this.nextEvent(true);
                editedNodesSaveState = new HashSet<>(editedNodes);
                manipulator = initializer.start(state, DraggedTrackNode.listOfNodes(editableNodes), event);
                manipulator.onStarted(event);
                manipulator.onUpdate(event);
            }

            return new DragResult(cancelledNodes, manipulator);
        }

        NodeDragEvent event = this.nextEvent(state.getHeldDownTicks() == 0);
        manipulator.onUpdate(event);
        return new DragResult(Collections.emptyList(), manipulator);
    }

    /**
     * Called once the player releases the drag, finalizing the changes
     *
     * @param history History change collection to commit changes to
     */
    public void finish(HistoryChangeCollection history) throws ChangeCancelledException {
        if (!isManipulating()) {
            return;
        }

        NodeDragEvent event = this.nextEvent(false);
        try {
            manipulator.onFinished(history, event);
        } finally {
            this.reset();
        }
    }

    /**
     * Gets the next drag event based on the current input and edit state
     *
     * @param isStart Whether this is the start of the drag.
     *                Is overrided if this handler also sees the start of a drag, for example
     *                because {@link #reset()} was called.
     * @return change transform
     */
    private NodeDragEvent nextEvent(boolean isStart) {
        // Get current input
        Matrix4x4 current = input.get();
        // Computed changes for current drag update
        Matrix4x4 changes;

        if (isStart || this.editStartTransform == null) {
            // First update
            isStart = true;
            changes = new Matrix4x4();
        } else {
            // Subsequent updates
            changes = current.clone();
            {
                Matrix4x4 m = this.editStartTransform.clone();
                m.invert();
                changes.multiply(m);
            }
        }

        this.editStartTransform = current.clone();

        return new NodeDragEvent(current, changes, isStart);
    }

    private static boolean areEditedNodesEqual(Collection<TrackNode> editedNodesSaveState, Collection<TrackNode> editedNodes) {
        if (editedNodesSaveState.size() != editedNodes.size()) {
            return false;
        }

        for (TrackNode node : editedNodes) {
            if (!editedNodesSaveState.contains(node)) {
                return false;
            }
        }

        // Nodes won't be duplicate and size are equal, so we know they are the same
        return true;
    }

    /**
     * Result of a drag operation, including nodes for which the drag was cancelled
     */
    public static class DragResult {
        /** Nodes that could not be edited because the change was cancelled */
        public final List<TrackNode> cancelledNodes;
        /** Manipulator used for this drag operation. Null if no manipulator is active (no nodes to edit). */
        public final NodeDragManipulator manipulator;

        public DragResult(List<TrackNode> cancelledNodes, NodeDragManipulator manipulator) {
            this.cancelledNodes = cancelledNodes;
            this.manipulator = manipulator;
        }
    }
}
