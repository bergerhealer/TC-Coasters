package com.bergerkiller.bukkit.coasters.world;

import java.util.Arrays;
import java.util.List;

import org.bukkit.World;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.animation.TrackAnimationWorld;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannelRegistry;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;

/**
 * Stores all coaster information of a single World
 */
public class CoasterWorldImpl implements CoasterWorld {
    private final TCCoasters _plugin;
    private final World _world;
    private final OfflineWorld _offlineWorld;
    private final TrackWorld _tracks;
    private final TrackParticleWorld _particles;
    private final TrackRailsWorld _rails;
    private final TrackAnimationWorld _animations;
    private final NamedPowerChannelRegistry _namedPowerRegistry;
    private final List<CoasterWorldComponent> _components;

    public CoasterWorldImpl(TCCoasters plugin, World world) {
        this._plugin = plugin;
        this._world = world;
        this._offlineWorld = OfflineWorld.of(world);
        this._tracks = new TrackWorld(this);
        this._particles = new TrackParticleWorld(this);
        this._rails = new TrackRailsWorld(this);
        this._animations = new TrackAnimationWorld(this);
        this._namedPowerRegistry = new NamedPowerChannelRegistry(this);
        this._components = Arrays.asList(_tracks, _particles, _animations, _rails, _namedPowerRegistry);
    }

    @Override
    public TCCoasters getPlugin() {
        return this._plugin;
    }

    @Override
    public World getBukkitWorld() {
        return this._world;
    }

    @Override
    public OfflineWorld getOfflineWorld() {
        return this._offlineWorld;
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

    @Override
    public TrackAnimationWorld getAnimations() {
        return this._animations;
    }

    @Override
    public NamedPowerChannelRegistry getNamedPowerChannels() {
        return this._namedPowerRegistry;
    }

    /**
     * Performs all the logic required to unload a World from memory
     */
    public void unload() {
        {
            NamedPowerChannelRegistry powerChannels = getNamedPowerChannels();
            powerChannels.saveAndAbortPulses();
        }
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
     * Performs all the logic required to load a World into memory.
     * Can be safely called during the onLoad() plugin stage.
     */
    public void load() {
        getTracks().load();
        getNamedPowerChannels().loadPulses();
    }

    /**
     * Performs all the logic required to start animating a World.
     * Must be called during or after the onEnable() plugin stage.
     */
    public void enable() {
        getNamedPowerChannels().enable();
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
    @Override
    public void updateAll() {
        _components.forEach(CoasterWorldComponent::updateAll);
    }
}
