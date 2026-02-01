package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.tc.TrainCarts;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

@Command("tccoasters|tcc")
class GlobalCommands {

    @Command("")
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
    @Command("version")
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

    @Command("startuplog")
    @CommandDescription("Views everything logged during startup of TC-Coasters")
    @Permission("bkcommonlib.command.startuplog")
    private void commandShowStartupLog(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        plugin.onStartupLogCommand(sender, "startuplog", new String[0]);
    }

    @CommandRequiresTCCPermission
    @Command("reload|load")
    @CommandDescription("Reloads all (new) coasters from the csv files on disk")
    public void commandReload(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Loading all tracks from disk now");
        plugin.reload();
    }

    @CommandRequiresTCCPermission
    @Command("save")
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
    @Command("smoothness")
    @CommandDescription("Gets the track smoothness value")
    public void commandGetSmoothness(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Smoothness is currently set to " + plugin.getSmoothness());
    }

    @CommandRequiresTCCPermission
    @Command("smoothness <value>")
    @CommandDescription("Sets the track smoothness value")
    public void commandSetSmoothness(
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument("value") double value
    ) {
        plugin.setSmoothness(value);
        sender.sendMessage("Set smoothness to " + plugin.getSmoothness() + ", rebuilding tracks");
        plugin.rebuildAll();
    }

    @CommandRequiresTCCPermission
    @Command("glow")
    @CommandDescription("Gets whether glowing selections are enabled")
    public void commandGetGlowingSelections(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Glowing selections are currently " + (plugin.getGlowingSelections() ? "enabled" : "disabled"));
    }

    @CommandRequiresTCCPermission
    @Command("glow <enabled>")
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
                    if (!node.isRemoved()) {
                        node.onStateUpdated(player);
                    }
                }
            }
        }
    }
}
