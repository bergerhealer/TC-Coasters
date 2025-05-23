package com.bergerkiller.bukkit.coasters.particles;

import org.bukkit.entity.Player;

/**
 * The lifecycle (spawning and de-spawning) of a single TrackParticle.
 * Normally this is the default lifecycle of the particle itself, but can
 * be separately implemented for different LOD's (levels of detail).
 */
public interface TrackParticleLifecycle {
    /**
     * Spawns in the particle for a viewer
     *
     * @param viewer Player viewer
     */
    void makeVisibleFor(Player viewer);

    /**
     * De-spawns the particle for a viewer
     *
     * @param viewer Player viewer
     */
    void makeHiddenFor(Player viewer);

    /**
     * Gets whether this lifecycle is still valid for the arguments specified.
     * If invalid, a new lifecycle will be requested from the particle and the
     * old one will be de-spawned.
     *
     * @param state Current state that controls the lifecycle
     * @return True if this lifecycle is still valid
     */
    default boolean isLifecycleValid(State state) {
        return true;
    }

    /**
     * Switches from the current (this) lifecycle to a new one. By default de-spawns the
     * old particle and spawns the new one, but can be implemented to do something
     * smarter like only updating the metadata of the already-spawned particle.
     *
     * @param newLifecycle The new lifecycle being switched to (new LOD)
     * @param viewer Player viewer for who the lifecycle changed
     */
    default void switchToLifecycle(TrackParticleLifecycle newLifecycle, Player viewer) {
        this.makeHiddenFor(viewer);
        newLifecycle.makeVisibleFor(viewer);
    }

    /**
     * Information fed to a track particle to help it decide what particle to
     * display to a player. This state invalidates old lifecycles and is used
     * to decide on new lifecycles.<br>
     * <br>
     * Primarily, it is used for deciding LOD (level of detail).
     */
    interface State {
        /**
         * Gets the viewer to which the particle is displayed
         *
         * @return Viewer
         */
        Player getViewer();

        /**
         * Gets the view distance in blocks between the player viewer and this particle
         *
         * @return View distance (manhattan distance) in blocks
         */
        int getViewDistance();
    }
}
