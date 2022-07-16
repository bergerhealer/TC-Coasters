package com.bergerkiller.bukkit.coasters.signs.actions;

import org.bukkit.Effect;
import org.bukkit.Location;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersPermissions;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;

public class SignActionPower extends SignAction {
    private final TCCoasters plugin;

    public SignActionPower(TCCoasters plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType("tcc-power");
    }

    @Override
    public void execute(SignActionEvent info) {
        if (!info.isAction(SignActionType.REDSTONE_ON, SignActionType.REDSTONE_OFF)) {
            return;
        }

        NamedPowerChannel channel = plugin.getCoasterWorld(info.getWorld())
                .getNamedPowerChannels().findIfExists(info.getLine(2));
        if (channel == null) {
            failNoPowerChannel(info);
            return;
        }

        int delay = ParseUtil.parseInt(info.getLine(3), -1);
        if (delay > 0) {
            if (info.isAction(SignActionType.REDSTONE_ON)) {
                channel.pulsePowered(true, delay);
            }
        } else {
            channel.setPowered(info.isAction(SignActionType.REDSTONE_ON));
        }
    }

    // Plays a smoke and sound effect at the sign to indicate the power channel doesn't exist
    private void failNoPowerChannel(SignActionEvent event) {
        Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        loc.getWorld().playEffect(loc, Effect.SMOKE, 0);
        WorldUtil.playSound(loc, SoundEffect.EXTINGUISH, 1.0f, 2.0f);
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setPermission(TCCoastersPermissions.BUILD_POWER)
                .setName("power channel transmitter")
                .setDescription("update a power channel using an input redstone signal")
                .handle(event.getPlayer());
    }
}
