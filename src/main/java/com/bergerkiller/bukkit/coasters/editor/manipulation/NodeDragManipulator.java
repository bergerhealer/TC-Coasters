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
     * Minimum distance kept between nodes while dragging to avoid nodes merging.
     * Nodes will move aside to maintain this distance. And the finer operation will
     * not insert more nodes if the distance between two nodes is smaller than this value.
     */
    double MINIMUM_CONNECTION_DISTANCE = 0.2;

    /**
     * Called when the user initiates drag while this manipulator is active.
     * Is not called before other manipulation methods, like {@link #equalizeNodeSpacing(HistoryChangeCollection)},
     * are called.
     *
     * @param event Node drag event
     */
    void onDragStarted(NodeDragEvent event);

    /**
     * Called after {@link #onDragStarted(NodeDragEvent)}
     * and repeatedly to update manipulation while the user is dragging.
     *
     * @param event Node drag event
     */
    void onDragUpdate(NodeDragEvent event);

    /**
     * Called when node dragging has finished (player releases mouse button).
     * This should record the finalized manipulation changes into the history collection,
     * so that they can be undone later on if needed.
     *
     * @param history History change collection to record finalized manipulation changes into
     * @param event Node drag event
     * @throws ChangeCancelledException If the change is cancelled
     */
    void onDragFinished(HistoryChangeCollection history, NodeDragEvent event) throws ChangeCancelledException;

    /**
     * Called when the equalize node spacing action is triggered.
     * This should adjust the positions of the manipulated nodes so that they are
     * evenly spaced between each other.<br>
     * <br>
     * Depending on what kind of manipulation mode is active, different rules can be used
     * to compute the required spacing.
     *
     * @param history History change collection to record finalized manipulation changes into
     * @throws ChangeCancelledException If the change is cancelled
     */
    default void equalizeNodeSpacing(HistoryChangeCollection history) throws ChangeCancelledException {
        throw new ChangeCancelledException();
    }

    /**
     * Inserts an additional node where possible, making the manipulated shape finer.
     * This is used when the user presses the "Make Finer" button. Depending on the manipulation mode,
     * different rules can be used to determine where the new node is inserted and how it is positioned.
     * Nodes are created where there is maximal distance between two other connected nodes.<br>
     * <br>
     * The newly created nodes are selected automatically.
     *
     * @param history History change collection to record finalized manipulation changes into
     * @throws ChangeCancelledException If the change is cancelled
     */
    default void makeFiner(HistoryChangeCollection history) throws ChangeCancelledException {
        throw new ChangeCancelledException();
    }

    /**
     * Removes nodes where possible, making the manipulated shape coarser.
     * This is used when the user presses the "Make Coarser" button. Depending on the manipulation mode,
     * different rules can be used to determine which nodes are removed and how the remaining nodes are repositioned.
     *
     * @param history History change collection to record finalized manipulation changes into
     * @throws ChangeCancelledException If the change is cancelled
     */
    default void makeCourser(HistoryChangeCollection history) throws ChangeCancelledException {
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
         * @return NodeDragManipulator instance
         */
        NodeDragManipulator start(PlayerEditState state, List<DraggedTrackNode> draggedNodes);
    }
}
