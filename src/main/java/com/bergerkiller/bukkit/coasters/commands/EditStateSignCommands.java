package com.bergerkiller.bukkit.coasters.commands;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.Flag;

@CommandMethod("tccoasters|tcc sign|signs")
class EditStateSignCommands {

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("add <lines>")
    @CommandDescription("Adds a new sign to the currently selected nodes")
    public void commandReset(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("lines") String[] lines
    ) {
        if (lines == null || lines.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please specify at least one line of text");
            return;
        }

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
    @CommandMethod("remove")
    @CommandDescription("Removes the last-added sign from all selected nodes")
    public void commandRemove(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            if (state.removeLastSign()) {
                sender.sendMessage(ChatColor.GREEN + "Last sign removed from the selected nodes");
            } else {
                sender.sendMessage(ChatColor.RED + "The selected nodes do not have signs");
            }
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The signs of the selected nodes could not be cleared");
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

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("scroll|next")
    @CommandDescription("Scrolls all signs one down, so a different sign can be edited")
    public void commandScroll(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.scrollSigns();
            sender.sendMessage(ChatColor.GREEN + "Signs scrolled one down");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "Failed to scroll the signs by one");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("power add <channel>")
    @CommandDescription("Assigns a power channel to the last-added sign of the selected nodes")
    public void commandAddPowerChannel(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument(value="channel", suggestions="power_channels") String channel_name,
            final @Flag(value="face",
                        parserName="sign_block_face",
                        description="Sets a face direction to power from. Defaults to ALL.") BlockFace face,
            final @Flag(value="powered",
                        description="If first creating the channel, defaults to it being powered") boolean powered
    ) {
        final AtomicInteger numNodesChanged = new AtomicInteger(0);
        try {
            state.updateLastSign(s -> {
                s.addPowerChannel(
                        channel_name,
                        powered,
                        (face == null) ? BlockFace.SELF : face);
                numNodesChanged.incrementAndGet();
            });
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_POWER_FAILED.message(sender);
            return;
        }

        if (numNodesChanged.get() == 0) {
            sender.sendMessage(ChatColor.RED + "The selected nodes don't have any signs!");
            return;
        }

        String faceStr = (face == null || face == BlockFace.SELF)
                ? "All sides" : ("The " + face.name().toLowerCase(Locale.ENGLISH) + " side");
        sender.sendMessage(ChatColor.YELLOW + "Power channel '" + ChatColor.WHITE + channel_name +
                ChatColor.YELLOW + "' assigned to " +
                ChatColor.BLUE + faceStr +
                ChatColor.YELLOW + " of the last sign of " + ChatColor.WHITE + numNodesChanged.get() +
                ChatColor.YELLOW + " nodes");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("power remove <channel>")
    @CommandDescription("Removes a power channel from the last-added sign of the selected nodes")
    public void commandRemovePowerChannel(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument(value="channel", suggestions="power_channels") String channel_name,
            final @Flag(value="face",
                        parserName="sign_block_face",
                        description="Removes a specific face instead of any with the channel name") BlockFace face
    ) {
        final AtomicInteger numNodesChanged = new AtomicInteger(0);
        try {
            state.updateLastSign(s -> {
                boolean removed;
                if (face == null) {
                    removed = s.removePowerChannels(channel_name);
                } else {
                    removed = s.removePowerChannel(NamedPowerChannel.of(channel_name, false, face));
                }
                if (removed) {
                    numNodesChanged.incrementAndGet();
                }
            });
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_POWER_FAILED.message(sender);
            return;
        }

        if (numNodesChanged.get() == 0) {
            sender.sendMessage(ChatColor.RED + "The selected nodes don't have any signs with power channels that match!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Power channel '" + ChatColor.WHITE + channel_name +
                ChatColor.YELLOW + "' removed from the last sign of " + ChatColor.WHITE + numNodesChanged.get() +
                ChatColor.YELLOW + " nodes");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("power clear")
    @CommandDescription("Clears all power channels from last-added sign of the selected nodes")
    public void commandClearPowerChannels(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.updateLastSign(s -> {
                s.clearPowerChannels();
            });
            sender.sendMessage(ChatColor.GREEN + "Last sign power channels cleared");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The power channels of the selected nodes' last signs could not be cleared");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("power rotate")
    @CommandDescription("Rotates all power channels from last-added sign of the selected nodes by 90 degrees")
    public void commandRotatePowerChannels(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            final AtomicBoolean changed = new AtomicBoolean(false);
            state.updateLastSign(s -> {
                TrackNodeSign newSign = s.clone();
                if (newSign.rotatePowerChannels()) {
                    changed.set(true);
                    return newSign;
                } else {
                    return s;
                }
            });
            if (changed.get()) {
                sender.sendMessage(ChatColor.GREEN + "Last sign power channels rotated 90 degrees");
            } else {
                sender.sendMessage(ChatColor.RED + "Last sign has no directional power channels");
            }
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The power channels of the selected nodes' last signs could not be rotated");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @CommandMethod("power rotate <channel>")
    @CommandDescription("Rotates a power channel from last-added sign of the selected nodes by 90 degrees")
    public void commandRotateNamedPowerChannels(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument(value="channel", suggestions="power_channels") String channel_name
    ) {
        try {
            final AtomicBoolean changed = new AtomicBoolean(false);
            state.updateLastSign(s -> {
                TrackNodeSign newSign = s.clone();
                if (newSign.rotatePowerChannel(channel_name)) {
                    changed.set(true);
                    return newSign;
                } else {
                    return s;
                }
            });
            if (changed.get()) {
                sender.sendMessage(ChatColor.GREEN + "Last sign power channel rotated 90 degrees");
            } else {
                sender.sendMessage(ChatColor.RED + "Last sign has no directional power channel by this name");
            }
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The power channel of the selected nodes' last signs could not be rotated");
        }
    }
}
