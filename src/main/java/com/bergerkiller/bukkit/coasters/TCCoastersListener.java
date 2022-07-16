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
import com.bergerkiller.bukkit.coasters.events.CoasterConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterNodeEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.wrappers.HumanHand;

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
        boolean isLeftClick = (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK);
        boolean isRightClick = (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK);

        // Right-or-left-clicking a node with a sign to place/break signs
        if (this.plugin.isHoldingEditSign(event.getPlayer())) {
            PlayerEditState state = this.plugin.getEditState(event.getPlayer());
            if (state.getMode() != PlayerEditMode.DISABLED) {
                TrackNode lookingAt = state.getWorld().getTracks().findNodeLookingAt(
                        event.getPlayer().getEyeLocation(), 1.0, 10.0);
                if (lookingAt != null) {
                    if (isLeftClick && state.getSigns().onSignLeftClick(lookingAt)) {
                        event.setCancelled(true);
                    }
                    if (isRightClick && state.getSigns().onSignRightClick(lookingAt, HumanHand.getItemInMainHand(event.getPlayer()))) {
                        event.setCancelled(true);
                    }
                    return;
                }
            }
        }

        // Right-or-left-clicking a node with a redstone torch to assign/remove power channels
        if (this.plugin.isHoldingEditTorch(event.getPlayer())) {
            PlayerEditState state = this.plugin.getEditState(event.getPlayer());
            if (state.getMode() != PlayerEditMode.DISABLED) {
                TrackNode lookingAt = state.getWorld().getTracks().findNodeLookingAt(
                        event.getPlayer().getEyeLocation(), 1.0, 10.0);
                if (lookingAt != null) {
                    if (isLeftClick && state.getSigns().onTorchLeftClick(lookingAt)) {
                        event.setCancelled(true);
                    }
                    if (isRightClick && state.getSigns().onTorchRightClick(lookingAt)) {
                        event.setCancelled(true);
                    }
                    return;
                }
            }
        }

        // Interacting while holding the editor map
        if (this.plugin.isHoldingEditTool(event.getPlayer())) {
            PlayerEditState state = this.plugin.getEditState(event.getPlayer());
            if (isLeftClick && state.onLeftClick()) {
                event.setCancelled(true);
            }
            if (isRightClick) {
                state.setTargetedBlock(event.getClickedBlock(), event.getBlockFace());
            }
            if (isRightClick && state.onRightClick()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSneakChange(PlayerToggleSneakEvent event) {
        if (!this.plugin.isHoldingEditTool(event.getPlayer())) {
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
