package com.bergerkiller.bukkit.coasters.world;

import java.io.File;

import org.bukkit.World;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.animation.TrackAnimationWorld;
import com.bergerkiller.bukkit.coasters.particles.TrackParticleWorld;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsWorld;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannelRegistry;
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
     * Gets the folder in which coasters and other world-specific data is saved for this world.
     * This function also ensures that the folder itself exists.
     * 
     * @return world config folder
     */
    default File getConfigFolder() {
        World w = this.getBukkitWorld();
        File f = new File(this.getPlugin().getDataFolder(), w.getName() + "_" + w.getUID());
        f.mkdirs();
        return f;
    }

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

    /**
     * Gets information about the named powered-states of fake signs added
     * to track nodes.
     *
     * @return sign named power registry
     */
    NamedPowerChannelRegistry getNamedPowerChannels();
}
