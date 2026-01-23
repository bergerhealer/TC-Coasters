package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditInput;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Matrix4x4;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

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
     * Called every tick while a drag is happening
     *
     * @param initializer Manipulator initializer. Creates a new manipulator if no manipulation is active yet.
     * @param state PlayerEditState
     * @param editedNodes Edited nodes
     */
    public void next(NodeDragManipulator.Initializer initializer, PlayerEditState state, Collection<PlayerEditNode> editedNodes) throws ChangeCancelledException {
        NodeDragEvent event = this.nextEvent(manipulator == null || state.getHeldDownTicks() == 0);

        // If a manipulator is active, verify that the edited nodes are still the same
        // If not, finish the previous manipulator and resume with a newly created one
        if (manipulator != null && !areEditedNodesEqual(this.editedNodesSaveState, editedNodes)) {
            // This could throw (fail to commit), in which case no drag is started
            // Caller will reset selected nodes to resolve the problem.
            this.finish(state.getHistory());
        }

        // If no manipulator is set yet, create one
        if (manipulator == null) {
            editedNodesSaveState = editedNodes.stream().map(en -> en.node).collect(Collectors.toSet());
            manipulator = initializer.start(state, editedNodes, event);
            manipulator.onStarted(event);
        }

        manipulator.onUpdate(event);
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

    private static boolean areEditedNodesEqual(Collection<TrackNode> editedNodesSaveState, Collection<PlayerEditNode> editedNodes) {
        if (editedNodesSaveState.size() != editedNodes.size()) {
            return false;
        }

        for (PlayerEditNode node : editedNodes) {
            if (!editedNodesSaveState.contains(node.node)) {
                return false;
            }
        }

        // Nodes won't be duplicate and size are equal, so we know they are the same
        return true;
    }
}
