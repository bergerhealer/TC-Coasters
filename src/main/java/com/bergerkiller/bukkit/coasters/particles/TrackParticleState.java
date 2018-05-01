package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;

/**
 * The state of a displayed particle
 */
public enum TrackParticleState {
    DEFAULT, SELECTED;

    /**
     * Source providing track particle state information
     * for a particular player
     */
    public static interface Source {

        /**
         * Gets particle state information for a particular player
         * 
         * @param viewer
         * @return state
         */
        public TrackParticleState getState(Player viewer);
    }

    public static Source SOURCE_NONE = new Source() {
        @Override
        public TrackParticleState getState(Player viewer) {
            return DEFAULT;
        }
    };
}
