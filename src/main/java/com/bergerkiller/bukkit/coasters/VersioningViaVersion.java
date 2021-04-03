package com.bergerkiller.bukkit.coasters;
import org.bukkit.entity.Player;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.ViaAPI;

public class VersioningViaVersion implements Versioning {

    @SuppressWarnings("unchecked")
    ViaAPI<Player> api = (ViaAPI<Player>) Via.getAPI();

    @Override
    public boolean SERVER_IS_1_16_2(Player player) {
        return (api.getPlayerVersion(player) >= 751);
    }

    @Override
    public boolean SERVER_1_16_TO_1_16_1(Player player) {
        int version = api.getPlayerVersion(player);
        return (version >= 735 && version <= 736);
    }

    @Override
    public boolean SERVER_IS_1_8_TO_1_15_2(Player player) {
        int version = api.getPlayerVersion(player);
        return (version >= 47 && version <= 578);
    }
}