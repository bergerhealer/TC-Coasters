package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.TCCoastersDisplay;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.tc.TrainCarts;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;

@CommandMethod("tccoasters|tcc")
class GlobalCommands {

    @CommandMethod("")
    @CommandDescription("Shows help")
    public void commandRootHelp(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("This command is for TC-Coasters, a TrainCarts add-on");
        if (plugin.hasUsePermission(sender)) {
            sender.sendMessage("/tcc help - Show help information");
            sender.sendMessage("/tcc give - Give player the editor map");
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use TC-Coasters. Ask an Admin.");
        }
    }

    @CommandRequiresTCCPermission
    @CommandMethod("version")
    @CommandDescription("Shows installed version of TCCoasters, TrainCarts and BKCommonLib")
    private void commandShowVersion(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage(ChatColor.GREEN + plugin.getName() + ": v" + plugin.getDebugVersion());
        sender.sendMessage(ChatColor.GREEN + "TrainCarts: v" + TrainCarts.plugin.getDebugVersion());
        sender.sendMessage(ChatColor.GREEN + "BKCommonLib: v" + CommonPlugin.getInstance().getDebugVersion());
        sender.sendMessage(ChatColor.GREEN + "Server: " + Bukkit.getServer().getVersion());
    }

    @CommandMethod("startuplog")
    @CommandDescription("Views everything logged during startup of TC-Coasters")
    @CommandPermission("bkcommonlib.command.startuplog")
    private void commandShowStartupLog(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        plugin.onStartupLogCommand(sender, "startuplog", new String[0]);
    }

    @CommandRequiresTCCPermission
    @CommandMethod("reload|load")
    @CommandDescription("Reloads all (new) coasters from the csv files on disk")
    public void commandReload(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Loading all tracks from disk now");

        // First, log out all players to guarantee their state is saved and then reset
        for (Player player : plugin.getPlayersWithEditStates()) {
            plugin.logoutPlayer(player);
        }

        // Unload all coasters, saving coasters that have open changes first
        // The load command should only be used to load new coasters / reload existing ones
        for (World world : Bukkit.getWorlds()) {
            plugin.unloadWorld(world);
        }

        // Reload all coasters, getCoasterWorld() will automatically load them
        for (World world : Bukkit.getWorlds()) {
            plugin.getCoasterWorld(world);
        }

        // For all players holding the editor map, reload it
        for (TCCoastersDisplay display : MapDisplay.getAllDisplays(TCCoastersDisplay.class)) {
            display.restartDisplay();
        }
    }

    @CommandRequiresTCCPermission
    @CommandMethod("save")
    @CommandDescription("Forces a save to disk of all coasters")
    public void commandSave(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Saving all tracks to disk now");
        for (CoasterWorld coasterWorld : plugin.getCoasterWorlds()) {
            coasterWorld.getTracks().saveForced();
        }
    }

    @CommandRequiresTCCPermission
    @CommandMethod("smoothness")
    @CommandDescription("Gets the track smoothness value")
    public void commandGetSmoothness(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Smoothness is currently set to " + plugin.getSmoothness());
    }

    @CommandRequiresTCCPermission
    @CommandMethod("smoothness <value>")
    @CommandDescription("Sets the track smoothness value")
    public void commandSetSmoothness(
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument("value") double value
    ) {
        plugin.setSmoothness(value);
        sender.sendMessage("Set smoothness to " + plugin.getSmoothness() + ", rebuilding tracks");
        plugin.buildAll();
    }

    @CommandRequiresTCCPermission
    @CommandMethod("glow")
    @CommandDescription("Gets whether glowing selections are enabled")
    public void commandGetGlowingSelections(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Glowing selections are currently " + (plugin.getGlowingSelections() ? "enabled" : "disabled"));
    }

    @CommandRequiresTCCPermission
    @CommandMethod("glow <enabled>")
    @CommandDescription("Sets whether glowing selections are enabled")
    public void commandSetGlowingSelections(
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument("enabled") boolean enabled
    ) {
        plugin.setGlowingSelections(enabled);

        sender.sendMessage((enabled ? "Enabled" : "Disabled") + " glowing selections");
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerEditState editState = plugin.getEditState(player);
            if (editState != null) {
                for (TrackNode node : editState.getEditedNodes()) {
                    node.onStateUpdated(player);
                }
            }
        }
    }
}
