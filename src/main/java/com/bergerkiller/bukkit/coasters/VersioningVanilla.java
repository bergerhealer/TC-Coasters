package com.bergerkiller.bukkit.coasters;
import com.bergerkiller.bukkit.common.internal.CommonBootstrap;
import org.bukkit.entity.Player;

public class VersioningVanilla implements Versioning {

    boolean is1_16_2, is_1_16_to_1_16_1, is1_8_to_1_15_2;

    public VersioningVanilla() {
        is1_16_2 = CommonBootstrap.evaluateMCVersion(">=", "1.16.2");
        is_1_16_to_1_16_1 = CommonBootstrap.evaluateMCVersion("<=", "1.16.1") && CommonBootstrap.evaluateMCVersion(">=", "1.16");
        is1_8_to_1_15_2 =  CommonBootstrap.evaluateMCVersion("<=", "1.15.2");
    }

    @Override
    public boolean SERVER_IS_1_16_2(Player player) {
        return is1_16_2;
    }

    @Override
    public boolean SERVER_1_16_TO_1_16_1(Player player) {
        return is_1_16_to_1_16_1;
    }

    @Override
    public boolean SERVER_IS_1_8_TO_1_15_2(Player player) {
        return is1_8_to_1_15_2;
    }
}
