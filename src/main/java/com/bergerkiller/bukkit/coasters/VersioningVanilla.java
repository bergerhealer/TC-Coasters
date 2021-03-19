package com.bergerkiller.bukkit.coasters;
import com.bergerkiller.bukkit.common.internal.CommonBootstrap;
import org.bukkit.entity.Player;

public class VersioningVanilla implements Versioning {

    @Override
    public boolean SERVER_IS_1_16_2(Player player) {
        return CommonBootstrap.evaluateMCVersion(">=", "1.16.2");
    }

    @Override
    public boolean SERVER_1_16_TO_1_16_1(Player player) {
        return CommonBootstrap.evaluateMCVersion("<=", "1.16.1") && CommonBootstrap.evaluateMCVersion(">=", "1.16");
    }

    @Override
    public boolean SERVER_IS_1_8_TO_1_15_2(Player player) {
        return CommonBootstrap.evaluateMCVersion("<=", "1.15.2");
    }
}
