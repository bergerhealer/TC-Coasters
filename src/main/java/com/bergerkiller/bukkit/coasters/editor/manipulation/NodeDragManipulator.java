package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditNode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;

import java.util.Collection;

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
     * Initializer for creating NodeDragManipulator instances.
     */
    interface Initializer {
        /**
         * Creates a NodeDragManipulator instance. Implementation can be decided based on the type of selection
         * the user created.
         *
         * @param state Player edit state
         * @param editedNodes Edited nodes to be manipulated
         * @param event Node drag event
         * @return NodeDragManipulator instance
         */
        NodeDragManipulator start(PlayerEditState state, Collection<PlayerEditNode> editedNodes, NodeDragEvent event);
    }
}
