package com.bergerkiller.bukkit.coasters.commands;

import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSectionsAtRail;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

@CommandMethod("tccoasters|tcc debug")
class DebugCommands {
    @CommandRequiresTCCPermission
    @CommandMethod("rebuild")
    @CommandDescription("Rebuilds the track data, might sometimes fix things")
    public void commandBuild(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Rebuilding tracks");
        plugin.buildAll();
    }

    @CommandRequiresTCCPermission
    @CommandMethod("path")
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
    @CommandMethod("rail")
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
    @CommandMethod("rail <x> <y> <z>")
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
}
