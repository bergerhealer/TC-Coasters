package com.bergerkiller.bukkit.coasters.editor.manipulation;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * A mode of node manipulation. This controls what happens when the Player presses or holds the right-click button.
 */
@FunctionalInterface
public interface NodeManipulationMode {
    NodeManipulationMode NONE = state -> {};
    NodeManipulationMode POSITION_ORIENTATION = PlayerEditState::dragManipulationUpdate;
    NodeManipulationMode CREATE_TRACK = new NodeManipulationMode() {
        @Override
        public void update(PlayerEditState state) throws ChangeCancelledException {
            state.createTrack();
        }

        @Override
        public int getAutoActivateDelay() {
            return 20;
        }

        @Override
        public int getAutoActivateInterval() {
            return 4;
        }
    };
    NodeManipulationMode DELETE_TRACK =  new NodeManipulationMode() {
        @Override
        public void update(PlayerEditState state) throws ChangeCancelledException {
            state.deleteTrack();
        }

        @Override
        public int getAutoActivateDelay() {
            return 10;
        }

        @Override
        public int getAutoActivateInterval() {
            return 3;
        }
    };
    NodeManipulationMode SET_RAIL_BLOCK = PlayerEditState::setRailBlock;
    NodeManipulationMode OBJECT = new NodeManipulationMode() {
        @Override
        public void update(PlayerEditState state) throws ChangeCancelledException {
            state.getObjects().createObject();
        }

        @Override
        public int getAutoActivateDelay() {
            return 10;
        }

        @Override
        public int getAutoActivateInterval() {
            return 3;
        }
    };
    NodeManipulationMode BUILDER = new NodeManipulationMode() {
        @Override
        public void update(PlayerEditState state) throws ChangeCancelledException {
            select(state).update(state);
        }

        @Override
        public NodeManipulationMode select(PlayerEditState state) {
            // Check if the player is looking exactly at a track node.
            // If so, activate position manipulation mode instead.
            // If not, do normal track creation (place a node down)
            TrackNode lookingAt = state.findLookingAt();
            if (lookingAt != null && state.isEditing(lookingAt)) {
                return POSITION_ORIENTATION;
            } else {
                return CREATE_TRACK;
            }
        }
    };

    /**
     * Called periodically while the Player is holding down right-click.
     *
     * @param state PlayerEditState
     * @throws ChangeCancelledException When the manipulation failed (permissions) and was cancelled.
     *                                  De-selects all selected nodes when thrown.
     */
    void update(PlayerEditState state) throws ChangeCancelledException;

    /**
     * Looks at the player edit state and selects what manipulation mode should be active.
     * This is called once when the player starts right-clicking. The returned object will then
     * receive all update calls until the player stops right-clicking.
     *
     * @param state PlayerEditState
     * @return NodeManipulationMode to use for this right-click session
     */
    default NodeManipulationMode select(PlayerEditState state) {
        return this;
    }

    /**
     * When the player holds down right-click, this is the delay (in ticks) before the action is automatically
     * repeated every {@link #getAutoActivateInterval()} ticks.
     *
     * @return Tick delay before auto-activation starts
     */
    default int getAutoActivateDelay() {
        return 0;
    }

    /**
     * When the player holds down right-click, this is the interval (in ticks) between auto-activations after the initial delay.
     *
     * @return Tick interval between auto-activations
     */
    default int getAutoActivateInterval() {
        return 1;
    }
}
