package com.bergerkiller.bukkit.coasters;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditTool;
import com.bergerkiller.bukkit.coasters.events.CoasterConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterNodeEvent;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;

public class TCCoastersListener implements Listener {
    private final TCCoasters plugin;

    public TCCoastersListener(TCCoasters plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable() {
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        CoasterWorld world = this.plugin.getCoasterWorld(event.getPlayer().getWorld());
        world.getParticles().hideAllFor(event.getPlayer());
    }

    /* Note: Do not ignore cancelled events! Player clicks from afar are cancelled by default. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final boolean isLeftClick = (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK);
        final boolean isRightClick = (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK);
        final PlayerEditTool tool = plugin.getHeldTool(event.getPlayer());
        if (tool != PlayerEditTool.NONE) {
            PlayerEditState state = this.plugin.getEditState(event.getPlayer());
            if (state.getMode() != PlayerEditMode.DISABLED) {
                // This one is tricky to handle generically
                if (tool == PlayerEditTool.MAP && isRightClick) {
                    state.setTargetedBlock(event.getClickedBlock(), event.getBlockFace());
                }

                // Handle tool
                if (tool.handleClick(state, isLeftClick, isRightClick)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSneakChange(PlayerToggleSneakEvent event) {
        if (this.plugin.getHeldTool(event.getPlayer()) != PlayerEditTool.MAP) {
            return;
        }

        PlayerEditState state = this.plugin.getEditState(event.getPlayer());
        state.onSneakingChanged(event.isSneaking());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCoasterNodeEvent(CoasterNodeEvent event) {
        if (!this.plugin.hasUsePermission(event.getPlayer())) {
            this.plugin.sendNoPermissionMessage(event.getPlayer(), TCCoastersLocalization.NO_PERMISSION);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCoasterConnectionEvent(CoasterConnectionEvent event) {
        if (!this.plugin.hasUsePermission(event.getPlayer())) {
            this.plugin.sendNoPermissionMessage(event.getPlayer(), TCCoastersLocalization.NO_PERMISSION);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent event) {
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
