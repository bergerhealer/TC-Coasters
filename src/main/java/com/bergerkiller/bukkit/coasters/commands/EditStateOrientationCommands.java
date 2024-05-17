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

import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.description.Description;

/**
 * Commands that change the orientation of selected track nodes
 */
@Command("tccoasters|tcc orientation|ori")
class EditStateOrientationCommands {

    public EditStateOrientationCommands(CommandManager<CommandSender> manager) {
        // Register all block faces as literals
        for (BlockFace face : LogicUtil.appendArray(FaceUtil.RADIAL, BlockFace.UP, BlockFace.DOWN)) {
            final Vector up_vector = FaceUtil.faceToVector(face).normalize();

            manager.command(manager.commandBuilder("tccoasters", "tcc")
                .literal("orientation", "ori")
                .literal(face.name().toLowerCase(Locale.ENGLISH),
                        Description.of("Block face to align the node towards"))
                .handler(context -> {
                    commandSetOrientationUpVector(
                            context.inject(PlayerEditState.class).get(), context.sender(),
                            up_vector.getX(), up_vector.getY(), up_vector.getZ());
                })
                .commandDescription(Description.of("Sets the orientation of the selected nodes to a Block Face direction"))
            );
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("")
    @CommandDescription("Shows the current orientation of the selected track nodes")
    public void commandShowOrientation(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        showOrientation(state, sender, false);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("set <up_x> <up_y> <up_z>")
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
    @Command("roll <roll>")
    @CommandDescription("Sets the orientation of the selected nodes by specifying a roll angle around the track")
    public void commandSetOrientationRollAngle(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("roll") double roll,
            final @Flag("add") boolean add
    ) {
        try {
            // Compute average forward direction of all selected nodes
            // We use this to flip the direction of nodes while processing, so that
            // they all rotate in the same direction properly.
            final Vector forwardAvg = new Vector();
            if (state.hasEditedNodes()) {
                for (TrackNode node : state.getEditedNodes()) {
                    forwardAvg.add(node.getDirection());
                }
                forwardAvg.normalize();
            }

            state.calcOrientation(node -> {
                Vector forward = node.getDirection();
                if (forward.dot(forwardAvg) < 0.0) {
                    forward = forward.clone().multiply(-1.0);
                }
                Quaternion q;
                if (add) {
                    q = Quaternion.fromLookDirection(forward, node.getOrientation());
                } else {
                    q = Quaternion.fromLookDirection(forward, new Vector(0, 1, 0));
                }
                q.rotateZ(roll);
                return q.upVector();
            });

            showOrientation(state, sender, true);
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The orientation of the selected nodes could not be changed");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("flip|invert")
    @CommandDescription("Flips the orientation of the selected nodes 180 degrees")
    public void commandFlipOrientation(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.calcOrientation(node -> node.getOrientation().clone().multiply(-1.0));

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
