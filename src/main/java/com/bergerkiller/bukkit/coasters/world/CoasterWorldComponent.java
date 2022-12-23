package com.bergerkiller.bukkit.coasters.world;

import org.bukkit.World;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.common.offline.OfflineWorld;

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
     * Gets the Offline World (UUID details)
     */
    default OfflineWorld getOfflineWorld() { return getWorld().getOfflineWorld(); }

    /**
     * Gets the TC Coasters plugin instance
     * 
     * @return TC Coasters plugin
     */
    default TCCoasters getPlugin() { return getWorld().getPlugin(); }

    /**
     * Called every tick to perform background maintenance
     */
    default void updateAll() {}
}
