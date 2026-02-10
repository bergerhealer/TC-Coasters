package com.bergerkiller.bukkit.coasters.commands;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.editor.manipulation.NodeManipulator;
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
        HistoryChangeCollection history = state.getHistory().addChangeGroup();
        if (state.performManipulation(history, NodeManipulator::equalizeNodeSpacing)) {
            sender.sendMessage(ChatColor.GREEN + "The selected nodes have been evenly spaced");
        } else {
            sender.sendMessage(ChatColor.RED + "The selected nodes could not be evenly spaced");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("finer")
    @CommandDescription("Inserts additional nodes between the selected nodes, making the shape finer")
    public void commandShapeFiner(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        HistoryChangeCollection history = state.getHistory().addChangeGroup();
        if (state.performManipulation(history, NodeManipulator::makeFiner)) {
            sender.sendMessage(ChatColor.GREEN + "Inserted additional node(s) between the selected nodes");
            if (!state.performManipulation(history, NodeManipulator::equalizeNodeSpacing)) {
                sender.sendMessage(ChatColor.RED + "The selected nodes could not be evenly spaced");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Could not insert additional nodes between the selected nodes");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("courser")
    @CommandDescription("Removes nodes between the selected nodes, making the shape courser")
    public void commandShapeCourser(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        HistoryChangeCollection history = state.getHistory().addChangeGroup();
        if (state.performManipulation(history, NodeManipulator::makeCourser)) {
            sender.sendMessage(ChatColor.GREEN + "Removed node(s) between the selected nodes");
            if (!state.performManipulation(history, NodeManipulator::equalizeNodeSpacing)) {
                sender.sendMessage(ChatColor.RED + "The selected nodes could not be evenly spaced");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Could not remove node(s) between the selected nodes");
        }
    }
}
