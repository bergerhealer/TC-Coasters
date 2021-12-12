package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.commands.arguments.TrackPositionAxis;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * Commands that edit the position of selected track nodes
 */
@CommandMethod("tccoasters|tcc position|pos")
class EditStatePositionCommands {

    @CommandRequiresTCCPermission
    @CommandMethod("<axis>")
    @CommandDescription("Shows short help and gets the average position of the selected nodes")
    public void commandGetPosition(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis
    ) {
        // Compute average value for the selected axis and show them
        if (state.hasEditedNodes()) {
            double averageValue = 0.0;
            for (TrackNode node : state.getEditedNodes()) {
                averageValue += axis.get(node.getPosition());
            }
            averageValue /= state.getEditedNodes().size();

            sender.sendMessage(ChatColor.YELLOW + "Current position " + axis.getCoord() +
                    ": " + ChatColor.WHITE + averageValue);
            sender.sendMessage("");
        }

        // Help
        sender.sendMessage(ChatColor.RED + "/tcc set " + axis.getName() + " <new_value>");
        sender.sendMessage(ChatColor.RED + "/tcc set " + axis.getName() + " (add/align) <value>");
        sender.sendMessage(ChatColor.RED + "/tcc set " + axis.getName() + " average");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("<axis> <value>")
    @CommandDescription("Sets an axis of the position of the selected nodes to a specified value")
    public void commandSetPosition(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis,
            final @Argument("value") double value
    ) {
        try {
            state.transformPosition(pos -> axis.set(pos, value));
            sender.sendMessage(ChatColor.GREEN + "The " + axis.getCoord() +
                    " of all the selected nodes has been set to " + value + "!");
        } catch (ChangeCancelledException ex) {
            sender.sendMessage(ChatColor.RED + "The position of one or more nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("<axis> set <value>")
    @CommandDescription("Adds a value to an axis of the position of all selected nodes")
    public void commandSetPositionAlias(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis,
            final @Argument("value") double value
    ) {
        commandSetPositionAlias(state, sender, axis, value);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("<axis> add <value>")
    @CommandDescription("Adds a value to an axis of the position of all selected nodes")
    public void commandSetAddPosition(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis,
            final @Argument("value") double value
    ) {
        try {
            state.transformPosition(pos -> axis.add(pos, value));
            sender.sendMessage(ChatColor.GREEN + "Added "+ value + " to the " + axis.getCoord() +
                    " of all selected nodes!");
        } catch (ChangeCancelledException ex) {
            sender.sendMessage(ChatColor.RED + "The position of one or more nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("<axis> subtract <value>")
    @CommandDescription("Subtracts a value from an axis of the position of all selected nodes")
    public void commandSetSubtractPosition(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis,
            final @Argument("value") double value
    ) {
        try {
            state.transformPosition(pos -> axis.add(pos, -value));
            sender.sendMessage(ChatColor.GREEN + "Subtracted "+ value + " from the " + axis.getCoord() +
                    " of all selected nodes!");
        } catch (ChangeCancelledException ex) {
            sender.sendMessage(ChatColor.RED + "The position of one or more nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("<axis> align <block_relative_value>")
    @CommandDescription("Aligns all nodes relative to their block coordinates for an axis")
    public void commandSetAlignPosition(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis,
            final @Argument("block_relative_value") double value
    ) {
        try {
            state.transformPosition(pos -> axis.align(pos, value));
            sender.sendMessage(ChatColor.GREEN + "The " + axis.getCoord() +
                    " of all the selected nodes has been set to " + value +
                    " relative to the node block coordinates!");
        } catch (ChangeCancelledException ex) {
            sender.sendMessage(ChatColor.RED + "The position of one or more nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("<axis> average")
    @CommandDescription("Computes the average position of all selected nodes, and sets an axis to that")
    public void commandSetAveragePosition(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis
    ) {
        // Compute average value for the selected axis
        final double averageValue;
        {
            double tmp = 0.0;
            for (TrackNode node : state.getEditedNodes()) {
                tmp += axis.get(node.getPosition());
            }
            averageValue = tmp / state.getEditedNodes().size();
        }

        try {
            state.transformPosition(pos -> axis.set(pos, averageValue));
            sender.sendMessage(ChatColor.GREEN + "The " + axis.getCoord() +
                    " of all the selected nodes has been set to the average " + averageValue + "!");
        } catch (ChangeCancelledException ex) {
            sender.sendMessage(ChatColor.RED + "The position of one or more nodes could not be changed");
        }
    }
}
