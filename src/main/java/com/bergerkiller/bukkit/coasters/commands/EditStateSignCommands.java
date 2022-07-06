package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

@CommandMethod("tccoasters|tcc signs")
class EditStateSignCommands {

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("add <line1> <line2> <line3> <line4>")
    @CommandDescription("Adds a new sign to the currently selected nodes")
    public void commandReset(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("line1") String line1,
            final @Argument("line2") String line2,
            final @Argument("line3") String line3,
            final @Argument("line4") String line4
    ) {
        String[] lines = new String[] { line1, line2, line3, line4 };
        TrackNodeSign sign = new TrackNodeSign(lines);
        try {
            state.addSign(sign);
            sender.sendMessage(ChatColor.GREEN + "Sign added to the selected node(s)");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The sign could not be added to the selected nodes");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("clear")
    @CommandDescription("Clears all signs set for the currently selected nodes")
    public void commandClear(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.clearSigns();
            sender.sendMessage(ChatColor.GREEN + "Signs cleared");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The signs of the selected nodes could not be cleared");
        }
    }
}
