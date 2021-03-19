package com.bergerkiller.bukkit.coasters;
        import com.bergerkiller.bukkit.common.internal.CommonBootstrap;
        import org.bukkit.entity.Player;

public interface Versioning {

    boolean SERVER_IS_1_16_2(Player player);
    boolean SERVER_1_16_TO_1_16_1(Player player);
    boolean SERVER_IS_1_8_TO_1_15_2(Player player);
}
