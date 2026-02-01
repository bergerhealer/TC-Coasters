package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;

import java.util.List;

/**
 * Handles node drag events and manipulates the selected nodes accordingly.
 */
public interface NodeDragManipulator {

    /**
     * Called when node dragging is started.
     *
     * @param event Node drag event
     */
    void onStarted(NodeDragEvent event);

    /**
     * Called after {@link #onStarted(NodeDragEvent)}
     * and repeatedly to update manipulation.
     *
     * @param event Node drag event
     */
    void onUpdate(NodeDragEvent event);

    /**
     * Called when node dragging has finished (player releases mouse button).
     *
     * @param history History change collection to record finalized manipulation changes into
     * @param event Node drag event
     * @throws ChangeCancelledException If the change is cancelled
     */
    void onFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException;

    /**
     * Called when the equalize node spacing action is triggered.
     * This should adjust the positions of the manipulated nodes so that they are
     * evenly spaced between each other.<br>
     * <br>
     * Depending on what kind of manipulation mode is active, different rules can be used
     * to compute the required spacing.
     *
     * @throws ChangeCancelledException If the change is cancelled
     */
    default void equalizeNodeSpacing() throws ChangeCancelledException {
        throw new ChangeCancelledException();
    }

    /**
     * Initializer for creating NodeDragManipulator instances.
     */
    interface Initializer {
        /**
         * Creates a NodeDragManipulator instance. Implementation can be decided based on the type of selection
         * the user created.
         *
         * @param state Player edit state
         * @param draggedNodes Edited nodes to be manipulated
         * @param event Node drag event
         * @return NodeDragManipulator instance
         */
        NodeDragManipulator start(PlayerEditState state, List<DraggedTrackNode> draggedNodes, NodeDragEvent event);
    }
}
