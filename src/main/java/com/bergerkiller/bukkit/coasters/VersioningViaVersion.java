package com.bergerkiller.bukkit.coasters;
import org.bukkit.entity.Player;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.ViaAPI;

public class VersioningViaVersion implements Versioning {

    ViaAPI api = Via.getAPI();

    @Override
    public boolean SERVER_IS_1_16_2(Player player) {
        return (api.getPlayerVersion(player) >= 751);
    }

    @Override
    public boolean SERVER_1_16_TO_1_16_1(Player player) {
        return (api.getPlayerVersion(player) >= 735 && api.getPlayerVersion(player) <= 736);
    }

    @Override
    public boolean SERVER_IS_1_8_TO_1_15_2(Player player) {
        return (api.getPlayerVersion(player) >= 47 && api.getPlayerVersion(player) <= 578);
    }
}