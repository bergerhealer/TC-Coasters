package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.commands.arguments.TrackPositionAxis;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.common.bases.IntVector3;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;

/**
 * All commands for modifying the rail block
 */
@CommandMethod("tccoasters|tcc rail")
class EditStateRailCommands {

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("")
    @CommandDescription("Shows the current rail block coordinates of the last-selected node")
    public void commandGetPosition(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        showRailBlock(state, sender, false);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("reset")
    @CommandDescription("Resets the rail block to where the nodes are positioned")
    public void commandReset(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.resetRailsBlocks();
            sender.sendMessage(ChatColor.YELLOW + "Rail block position reset");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The rail blocks of the selected nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("set at <x> <y> <z>")
    @CommandDescription("Sets the rail block to the specified coordinates")
    public void commandSetTo(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("x") int x,
            final @Argument("y") int y,
            final @Argument("z") int z
    ) {
        try {
            state.setRailBlock(new IntVector3(x, y, z));
            sender.sendMessage(ChatColor.YELLOW + "Rail block set to " + ChatColor.WHITE + x + "/" + y + "/" + z);
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The rail blocks of the selected nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("set <axis> <value>")
    @CommandDescription("Sets a single rail block coordinate of the selected nodes")
    public void commandSetAxisTo(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis,
            final @Argument("value") int value
    ) {
        try {
            state.transformRailBlock(rail -> axis.set(rail, value));
            showRailBlock(state, sender, true);
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The rail blocks of the selected nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("move")
    @CommandDescription("Moves the rail block one block along the direction the player looks")
    public void commandMoveOne(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        commandMove(state, sender, TrackPositionAxis.eye(state.getPlayer()), 1);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("move <axis>")
    @CommandDescription("Moves the rail block along an axis a single block")
    public void commandMoveOne(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis
    ) {
        commandMove(state, sender, axis, 1);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("move <axis> <amount>")
    @CommandDescription("Moves the rail block along an axis a number of blocks")
    public void commandMove(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("axis") TrackPositionAxis axis,
            final @Argument("amount") int amount
    ) {
        try {
            state.transformRailBlock(rail -> axis.add(rail, amount));
            sender.sendMessage(ChatColor.YELLOW + "Rail block moved by " + amount + " blocks along the " + axis.getCoord());
            showRailBlock(state, sender, true);
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The rail blocks of the selected nodes could not be changed");
        }
    }

    private void showRailBlock(PlayerEditState state, CommandSender sender, boolean hasChanged) {
        IntVector3 rail = state.getLastEditedNode().getRailBlock(true);
        String rail_str = "x=" + rail.x + " / " +
                          "y=" + rail.y + " / " +
                          "z=" + rail.z;
        if (hasChanged) {
            sender.sendMessage(ChatColor.GREEN + "Track rail block set to " + rail_str);
        } else {
            sender.sendMessage(ChatColor.GREEN + "Current track rail block is " + rail_str);
        }
    }
}
