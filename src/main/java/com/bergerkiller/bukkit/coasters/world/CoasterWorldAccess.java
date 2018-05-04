package com.bergerkiller.bukkit.coasters.world;

import org.bukkit.World;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;

/**
 * Everything from TC Coasters that is accessible for a single World
 */
public interface CoasterWorldAccess {
    /**
     * Gets the TC Coasters plugin instance
     * 
     * @return TC Coasters plugin
     */
    TCCoasters getPlugin();

    /**
     * Gets the Bukkit World
     * 
     * @return World
     */
    World getWorld();

    /**
     * Gets all the stored coaster tracks information
     * 
     * @return coaster tracks
     */
    TrackWorld getTracks();

    /**
     * Gets all the stored particle information
     * 
     * @return particles
     */
    TrackParticleWorld getParticles();

    /**
     * Gets all the stored rails logic information
     * 
     * @return rails
     */
    TrackRailsWorld getRails();

    /**
     * Component of a Coaster World. Simplifies access to world-specific operations.
     */
    public static class Component implements CoasterWorldAccess {
        private final CoasterWorldAccess _world;

        public Component(CoasterWorldAccess world) {
            this._world = world;
        }

        @Override
        public TCCoasters getPlugin() {
            return this._world.getPlugin();
        }

        @Override
        public World getWorld() {
            return this._world.getWorld();
        }

        @Override
        public TrackWorld getTracks() {
            return this._world.getTracks();
        }

        @Override
        public TrackParticleWorld getParticles() {
            return this._world.getParticles();
        }

        @Override
        public TrackRailsWorld getRails() {
            return this._world.getRails();
        }
    }
}
