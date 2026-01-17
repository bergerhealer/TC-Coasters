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
     * @param state Player edit state
     * @param editedNodes Edited nodes to be manipulated
     * @param event Node drag event
     */
    void onStarted(PlayerEditState state, Collection<PlayerEditNode> editedNodes, NodeDragEvent event);

    /**
     * Called after {@link #onStarted(PlayerEditState, Collection, NodeDragEvent)}
     * and repeatedly to update manipulation.
     *
     * @param state Player edit state
     * @param editedNodes Edited nodes to be manipulated
     * @param event Node drag event
     */
    void onUpdate(PlayerEditState state, Collection<PlayerEditNode> editedNodes, NodeDragEvent event);

    /**
     * Called when node dragging has finished (player releases mouse button).
     *
     * @param state Player edit state
     * @param history History change collection to record finalized manipulation changes into
     * @param editedNodes Edited nodes to be manipulated
     * @param event Node drag event
     * @throws ChangeCancelledException If the change is cancelled
     */
    void onFinished(PlayerEditState state, HistoryChangeCollection history, Collection<PlayerEditNode> editedNodes, NodeDragEvent event) throws ChangeCancelledException;
}
