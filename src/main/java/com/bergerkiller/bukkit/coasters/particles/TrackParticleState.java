package com.bergerkiller.bukkit.coasters.particles;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;

/**
 * The state of a displayed particle
 */
public enum TrackParticleState {
    HIDDEN, DEFAULT, SELECTED;

    /**
     * Source providing track particle state information
     * for a particular player
     */
    public static interface Source {

        /**
         * Gets particle state information for a particular player
         * 
         * @param viewer The viewer viewing the particle, including the viewer's state information
         * @return state of the particle for this viewer
         */
        public TrackParticleState getState(PlayerEditState viewer);
    }

    public static Source SOURCE_NONE = new Source() {
        @Override
        public TrackParticleState getState(PlayerEditState viewer) {
            return DEFAULT;
        }
    };
}
