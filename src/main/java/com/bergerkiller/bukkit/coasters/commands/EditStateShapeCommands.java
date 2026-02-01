package com.bergerkiller.bukkit.coasters.commands;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeDragManipulator;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;

/**
 * Commands that operate on editing the shape of coaster track. This is done using
 * the track manipulator tool.
 */
@Command("tccoasters|tcc shape")
class EditStateShapeCommands {

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("even")
    @CommandDescription("Balances the selected nodes evenly along the shape")
    public void commandShapeEven(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        if (state.performManipulation(NodeDragManipulator::equalizeNodeSpacing)) {
            sender.sendMessage(ChatColor.GREEN + "The selected nodes have been evenly spaced");
        } else {
            sender.sendMessage(ChatColor.RED + "The selected nodes could not be evenly spaced");
        }
    }
}
