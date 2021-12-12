package com.bergerkiller.bukkit.coasters.commands;

import java.util.Locale;

import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.CommandManager;
import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.InitializationMethod;
import cloud.commandframework.meta.CommandMeta;

/**
 * Commands that change the orientation of selected track nodes
 */
@CommandMethod("tccoasters|tcc orientation|ori")
class EditStateOrientationCommands {

    @InitializationMethod
    private void init(CommandManager<CommandSender> manager) {
        // Register all block faces as literals
        for (BlockFace face : LogicUtil.appendArray(FaceUtil.RADIAL, BlockFace.UP, BlockFace.DOWN)) {
            final Vector up_vector = FaceUtil.faceToVector(face).normalize();

            manager.command(manager.commandBuilder("tccoasters", "tcc")
                .literal("orientation", "ori")
                .literal(face.name().toLowerCase(Locale.ENGLISH),
                        ArgumentDescription.of("Block face to align the node towards"))
                .handler(context -> {
                    commandSetOrientationUpVector(
                            context.inject(PlayerEditState.class).get(), context.getSender(),
                            up_vector.getX(), up_vector.getY(), up_vector.getZ());
                })
                .meta(CommandMeta.DESCRIPTION, "Sets the orientation of the selected nodes to a Block Face direction"));
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("")
    @CommandDescription("Shows the current orientation of the selected track nodes")
    public void commandShowOrientation(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        showOrientation(state, sender, false);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("set <up_x> <up_y> <up_z>")
    @CommandDescription("Sets the orientation of the selected nodes by specifying the up-vector")
    public void commandSetOrientationUpVector(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("up_x") double upX,
            final @Argument("up_y") double upY,
            final @Argument("up_z") double upZ
    ) {
        try {
            state.setOrientation(new Vector(upX, upY, upZ));

            showOrientation(state, sender, true);
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The orientation of the selected nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("roll <roll>")
    @CommandDescription("Sets the orientation of the selected nodes by specifying a roll angle around the track")
    public void commandSetOrientationRollAngle(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("roll") double roll
    ) {
        try {
            // Angle offset applies to the 'up' orientation
            // Compute average forward direction vector of selected nodes
            Vector forward = new Vector();
            for (TrackNode node : state.getEditedNodes()) {
                forward.add(node.getDirection());
            }
            Quaternion q = Quaternion.fromLookDirection(forward, new Vector(0, 1, 0));
            q.rotateZ(roll);
            state.setOrientation(q.upVector());

            showOrientation(state, sender, true);
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The orientation of the selected nodes could not be changed");
        }
    }

    private void showOrientation(PlayerEditState state, CommandSender sender, boolean hasChanged) {
        // Display feedback to user
        Vector ori = state.getLastEditedNode().getOrientation();
        String ori_str = "dx=" + Double.toString(MathUtil.round(ori.getX(), 4)) + " / " +
                         "dy=" + Double.toString(MathUtil.round(ori.getY(), 4)) + " / " +
                         "dz=" + Double.toString(MathUtil.round(ori.getZ(), 4));
        if (hasChanged) {
            sender.sendMessage(ChatColor.GREEN + "Track orientation set to " + ori_str);
        } else {
            sender.sendMessage(ChatColor.GREEN + "Current track orientation is " + ori_str);
        }
    }
}
