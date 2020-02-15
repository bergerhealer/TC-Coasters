package com.bergerkiller.bukkit.coasters.world;

import org.bukkit.World;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.animation.TrackAnimationWorld;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;

/**
 * Everything from TC Coasters that is accessible for a single World
 */
public interface CoasterWorld extends CoasterWorldComponent {
    /**
     * Gets the Coaster World instance, the root of all the different components
     * 
     * @return Coaster World
     */
    default CoasterWorld getWorld() { return this; }

    @Override
    TCCoasters getPlugin();

    @Override
    World getBukkitWorld();

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
     * Gets access to the animation states for nodes on a world
     * 
     * @return animations
     */
    TrackAnimationWorld getAnimations();
}
