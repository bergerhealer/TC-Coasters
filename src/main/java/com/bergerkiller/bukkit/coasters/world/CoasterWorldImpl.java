package com.bergerkiller.bukkit.coasters.world;

import org.bukkit.World;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;

/**
 * Stores all coaster information of a single World
 */
public class CoasterWorldImpl implements CoasterWorldAccess {
    private final TCCoasters _plugin;
    private final World _world;
    private final TrackWorld _tracks;
    private final TrackParticleWorld _particles;
    private final TrackRailsWorld _rails;

    public CoasterWorldImpl(TCCoasters plugin, World world) {
        this._plugin = plugin;
        this._world = world;
        this._tracks = new TrackWorld(this);
        this._particles = new TrackParticleWorld(this);
        this._rails = new TrackRailsWorld(this);
    }

    @Override
    public TCCoasters getPlugin() {
        return this._plugin;
    }

    @Override
    public World getWorld() {
        return this._world;
    }

    @Override
    public TrackWorld getTracks() {
        return this._tracks;
    }

    @Override
    public TrackParticleWorld getParticles() {
        return this._particles;
    }

    @Override
    public TrackRailsWorld getRails() {
        return this._rails;
    }

    /**
     * Performs all the logic required to unload a World from memory
     */
    public void unload() {
        {
            TrackWorld tracks = getTracks();
            tracks.saveChanges();
            tracks.clear();
        }
        {
            TrackParticleWorld particles = getParticles();
            particles.removeAll();
        }
    }

    /**
     * Performs all the logic required to load a World into memory
     */
    public void load() {
        
    }

    /**
     * Performs a save of the underlying world components
     */
    public void saveChanges() {
        getTracks().saveChanges();
    }

    /**
     * Called every tick to update the underlying objects
     */
    public void updateAll() {
        getTracks().updateAll();
        getParticles().updateAll();
    }

}
