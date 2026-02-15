package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;

/**
 * A mode of node manipulation. This controls what happens when the Player presses or holds the right-click button.
 */
@FunctionalInterface
public interface NodeManipulationMode {
    NodeManipulationMode NONE = state -> {};
    NodeManipulationMode POSITION_ORIENTATION = PlayerEditState::dragManipulationUpdate;
    NodeManipulationMode CREATE_TRACK = PlayerEditState::createTrack;
    NodeManipulationMode DELETE_TRACK = PlayerEditState::deleteTrack;
    NodeManipulationMode SET_RAIL_BLOCK = PlayerEditState::setRailBlock;
    NodeManipulationMode OBJECT = p -> p.getObjects().createObject();

    /**
     * Called periodically while the Player is holding down right-click.
     *
     * @param state PlayerEditState
     * @throws ChangeCancelledException When the manipulation failed (permissions) and was cancelled.
     *                                  De-selects all selected nodes when thrown.
     */
    void update(PlayerEditState state) throws ChangeCancelledException;
}
