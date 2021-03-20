package com.bergerkiller.bukkit.coasters;

import org.bukkit.entity.Player;

public interface Versioning {

    /**
     * Checks if the version is 1.16.2
     * @param player Player to check (if using ViaVersion, ignores the variable if vanilla)
     * @return If the version is 1.16.2 or above
     */
    boolean SERVER_IS_1_16_2(Player player);

    /**
     * Checks if the version is 1.16 to 1.16.1
     * @param player Player to check version, ignored if vanilla
     * @return If the version is 1.16 to 1.16.1
     */
    boolean SERVER_1_16_TO_1_16_1(Player player);

    /**
     * Checks if the version is 1.8 to 1.15.2
     * @param player The Player to check
     * @return If the version is 1.16 to 1.16.1
     */
    boolean SERVER_IS_1_8_TO_1_15_2(Player player);
}