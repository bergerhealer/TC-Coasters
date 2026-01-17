package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditInput;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.common.math.Matrix4x4;

import java.util.Collection;

/**
 * Tracks the change in where the player looks, and makes a 4x4 transform matrix available
 * of the change in the current update. Produces {@link NodeDragEvent}s for node dragging manipulations.
 */
public class NodeDragHandler {
    private final PlayerEditInput input;
    private Matrix4x4 editStartTransform = null;
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
     * Sets the manipulator to use for handling drag events.
     *
     * @param manipulator Manipulator
     */
    public void setManipulator(NodeDragManipulator manipulator) {
        this.manipulator = manipulator;
        this.editStartTransform = null; // Force restart
    }

    /**
     * Called every tick while a drag is happening
     *
     * @param state PlayerEditState
     * @param editedNodes Edited nodes
     */
    public void next(PlayerEditState state, Collection<PlayerEditNode> editedNodes) {
        if (manipulator == null) {
            return;
        }

        NodeDragEvent event = this.nextEvent(state.getHeldDownTicks() == 0);
        if (event.isStart()) {
            manipulator.onStarted(state, editedNodes, event);
        }
        manipulator.onUpdate(state, editedNodes, event);
    }

    /**
     * Called once the player releases the drag, finalizing the changes
     *
     * @param state PlayerEditState
     * @param history History change collection
     * @param editedNodes Edited nodes
     */
    public void finish(PlayerEditState state, HistoryChangeCollection history, Collection<PlayerEditNode> editedNodes) throws ChangeCancelledException {
        if (manipulator == null) {
            return;
        }

        NodeDragEvent event = this.nextEvent(false);
        try {
            manipulator.onFinished(state, history, editedNodes, event);
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
}
