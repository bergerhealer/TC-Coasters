package com.bergerkiller.bukkit.coasters;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.bergerkiller.bukkit.common.events.PacketReceiveEvent;
import com.bergerkiller.bukkit.common.events.PacketSendEvent;
import com.bergerkiller.bukkit.common.protocol.PacketListener;
import com.bergerkiller.bukkit.common.protocol.PacketType;
import com.bergerkiller.bukkit.common.utils.PacketUtil;
import com.bergerkiller.bukkit.common.wrappers.UseAction;

public class TCCoastersListener implements Listener, PacketListener {
    private final TCCoasters plugin;

    public TCCoastersListener(TCCoasters plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        PacketUtil.addPacketListener(this.plugin, this, PacketType.IN_USE_ENTITY);
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable() {
        PacketUtil.removePacketListener(this);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Only check anything at all ever when holding the expected tool in the left hand
        if (!this.plugin.isHoldingEditTool(event.getPlayer())) {
            return;
        }

        if (event.getType() == PacketType.IN_USE_ENTITY) {
            UseAction action = event.getPacket().read(PacketType.IN_USE_ENTITY.useAction);
            if (action == UseAction.ATTACK) {
                event.setCancelled(true);
            } else if (action == UseAction.INTERACT || action == UseAction.INTERACT_AT) {
                event.setCancelled(onRightClick(event.getPlayer()));
            }
            event.setCancelled(true);
        }
    }

    public boolean onRightClick(Player player) {
        return this.plugin.getEditState(player).onRightClick();
    }

    public boolean onLeftClick(Player player) {
        return this.plugin.getEditState(player).onLeftClick();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!this.plugin.isHoldingEditTool(event.getPlayer())) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(onLeftClick(event.getPlayer()));
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(onRightClick(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        // This implicitly loads it, if it wasn't already
        this.plugin.getCoasterWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        this.plugin.unloadWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        // Do this otherwise funky things can happen!
        this.plugin.getEditState(event.getPlayer()).clearEditedNodes();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.logoutPlayer(event.getPlayer());
    }
}
