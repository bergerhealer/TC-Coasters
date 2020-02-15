package com.bergerkiller.bukkit.coasters.world;

import org.bukkit.World;

import com.bergerkiller.bukkit.coasters.TCCoasters;

/**
 * Any component that has access to the {@link CoasterWorld}
 */
public interface CoasterWorldComponent {
    /**
     * Gets the Coaster World instance, the root of all the different components
     * 
     * @return Coaster World
     */
    CoasterWorld getWorld();

    /**
     * Gets the Bukkit World
     * 
     * @return World
     */
    default World getBukkitWorld() { return getWorld().getBukkitWorld(); }

    /**
     * Gets the TC Coasters plugin instance
     * 
     * @return TC Coasters plugin
     */
    default TCCoasters getPlugin() { return getWorld().getPlugin(); }
}
