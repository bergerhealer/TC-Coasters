package com.bergerkiller.bukkit.coasters.commands;

import java.util.logging.Level;

import com.bergerkiller.bukkit.coasters.TCCoastersPermissions;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSectionsAtRail;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;

@Command("tccoasters|tcc debug")
class DebugCommands {
    @CommandRequiresTCCPermission
    @Command("rebuild")
    @CommandDescription("Rebuilds the track data, might sometimes fix things")
    public void commandBuild(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Rebuilding tracks...");
        plugin.rebuildAll();
        sender.sendMessage("Rebuilding done!");
    }

    @CommandRequiresTCCPermission
    @Command("path")
    @CommandDescription("Logs the path segments of the selected nodes to system log")
    public void commandDebugPath(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Logging paths of all selected nodes");
        for (TrackNode node : state.getEditedNodes()) {
            plugin.log(Level.INFO, "Path for: " + node.getPosition());
            for (RailPath.Point point : node.buildPath().getPoints()) {
                plugin.log(Level.INFO, "  - " + point);
            }
        }
    }

    @CommandRequiresTCCPermission
    @Command("rail")
    @CommandDescription("Shows built rail information tied to certain rail block coordinates")
    public void commandRail(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        PlayerEditState.LookingAtRailInfo rail = state.findLookingAtRailBlock();
        if (rail != null) {
            commandRail(state, sender, rail.rail.x, rail.rail.y, rail.rail.z);
        } else {
            sender.sendMessage(ChatColor.RED + "Not looking at any node rail blocks");
        }
    }

    @CommandRequiresTCCPermission
    @Command("rail <x> <y> <z>")
    @CommandDescription("Shows built rail information tied to certain rail block coordinates")
    public void commandRail(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("x") int x,
            final @Argument("y") int y,
            final @Argument("z") int z
    ) {
        TrackRailsSectionsAtRail atRail = state.getWorld().getRails().findAtRailsInformation(x, y, z);
        if (atRail != null) {
            sender.sendMessage(ChatColor.YELLOW + "Rail Information at " + x + " / " + y + " / " + z);
            sender.sendMessage(atRail.debugString().split("\n"));
        } else {
            sender.sendMessage(ChatColor.RED + "No rail information stored for rail at " +
                x + " / " + y + " / " + z);
        }
    }

    @CommandRequiresTCCPermission
    @Command("visibletoeveryone <visible>")
    @CommandDescription("Makes track visible to all players, also those not editing track")
    public void commandVisibleToEveryone(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("visible") boolean visible
    ) {
        if (!TCCoastersPermissions.VISIBLE_TO_EVERYONE.has(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to change this option");
        } else {
            state.getWorld().getParticles().setVisibleToEveryone(visible);
            if (visible) {
                sender.sendMessage(ChatColor.GREEN + "Coasters are now visible to all players");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Coasters are no longer visible to all players");
            }
        }
    }
}
