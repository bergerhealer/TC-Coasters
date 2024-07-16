package com.bergerkiller.bukkit.coasters.commands;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.bergerkiller.bukkit.coasters.commands.parsers.QuotedLinesParser;
import org.bukkit.ChatColor;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
import com.bergerkiller.bukkit.common.block.SignEditDialog;

import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;

@Command("tccoasters|tcc sign|signs")
class EditStateSignCommands {

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("add")
    @CommandDescription("Shows a sign dialog and puts the inputed text as a sign to the currently selected nodes")
    public void commandAddWithDialog(
            final PlayerEditState state,
            final Player sender
    ) {
        new SignEditDialog() {
            @Override
            public void onClosed(Player player, String[] lines) {
                handleCommandAddWithLines(state, player, QuotedLinesParser.create().addLines(lines));
            }
        }.open(sender);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("add <lines>")
    @CommandDescription("Adds a new sign to the currently selected nodes")
    public void commandAddWithLines(
            final PlayerEditState state,
            final Player sender,
            final @Greedy @Argument("lines") String linesText
    ) {
        handleCommandAddWithLines(state, sender, QuotedLinesParser.create().parse(linesText));
    }

    public void handleCommandAddWithLines(
            final PlayerEditState state,
            final Player sender,
            final QuotedLinesParser lines
    ) {
        if (!lines.hasLines()) {
            commandAddWithDialog(state, sender);
            return;
        }

        TrackNodeSign sign = new TrackNodeSign(lines.getLinesArray());
        try {
            state.getSigns().addSign(sign);
            TCCoastersLocalization.SIGN_ADD_MANY_SUCCESS.message(sender);
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_ADD_MANY_FAILED.message(sender);
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("edit")
    @CommandDescription("Shows a sign dialog and replaces the sign text with the inputed text for all signs of the currently selected nodes")
    public void commandEditWithDialog(
            final PlayerEditState state,
            final Player sender
    ) {
        final TrackNodeSign toEdit = findSignToEdit(state);
        if (toEdit != null) {
            new SignEditDialog() {
                @Override
                public void onClosed(Player player, String[] lines) {
                    commandEditWithLines(state, player, toEdit, lines);
                }
            }.open(sender, toEdit.getLines());
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("edit <lines>")
    @CommandDescription("Adds a new sign to the currently selected nodes")
    public void commandEditWithLines(
            final PlayerEditState state,
            final Player sender,
            final @Greedy @Argument("lines") String linesText
    ) {
        QuotedLinesParser parser = QuotedLinesParser.create().parse(linesText);
        if (!parser.hasLines()) {
            commandEditWithDialog(state, sender);
            return;
        }

        TrackNodeSign toEdit = findSignToEdit(state);
        if (toEdit != null) {
            commandEditWithLines(state, sender, toEdit, parser.getLinesArray());
        }
    }

    private void commandEditWithLines(
            final PlayerEditState state,
            final Player sender,
            final TrackNodeSign toReplace,
            final String[] lines
    ) {
        TrackNodeSign sign = new TrackNodeSign(lines);
        if (toReplace.hasSameLines(sign)) {
            TCCoastersLocalization.SIGN_EDIT_MANY_NOTCHANGED.message(sender);
            return;
        }
        try {
            if (state.getSigns().replaceSign(toReplace, sign)) {
                TCCoastersLocalization.SIGN_ADD_MANY_SUCCESS.message(sender);
            } else {
                TCCoastersLocalization.SIGN_EDIT_MANY_NOTEXIST.message(sender);
            }
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_ADD_MANY_FAILED.message(sender);
        }
    }

    private static TrackNodeSign findSignToEdit(PlayerEditState state) {
        TrackNode lastEdited = state.getLastEditedNode();
        TrackNodeSign[] signs;
        if (lastEdited != null && (signs = lastEdited.getSigns()).length > 0) {
            return signs[signs.length - 1];
        }

        for (TrackNode node : state.getEditedNodes()) {
            if ((signs = node.getSigns()).length > 0) {
                return signs[signs.length - 1];
            }
        }

        TCCoastersLocalization.SIGN_MISSING.message(state.getPlayer());
        return null;
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("remove")
    @CommandDescription("Removes the last-added sign from all selected nodes")
    public void commandRemove(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            if (state.getSigns().removeLastSign()) {
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
    @Command("clear")
    @CommandDescription("Clears all signs set for the currently selected nodes")
    public void commandClear(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.getSigns().clearSigns();
            sender.sendMessage(ChatColor.GREEN + "Signs cleared");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The signs of the selected nodes could not be cleared");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("scroll|next")
    @CommandDescription("Scrolls all signs one down, so a different sign can be edited")
    public void commandScroll(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.getSigns().scrollSigns();
            sender.sendMessage(ChatColor.GREEN + "Signs scrolled one down");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "Failed to scroll the signs by one");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("power add <channel>")
    @CommandDescription("Assigns a power channel to the last-added sign of the selected nodes")
    public void commandAddInputPowerChannel(
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
            state.getSigns().updateLastSign(s -> {
                s.addInputPowerChannel(
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
            TCCoastersLocalization.SIGN_MISSING.message(sender);
            return;
        }

        String faceStr = (face == null || face == BlockFace.SELF)
                ? "All sides" : ("The " + face.name().toLowerCase(Locale.ENGLISH) + " side");
        TCCoastersLocalization.SIGN_POWER_ASSIGNED.message(sender, channel_name, faceStr, Integer.toString(numNodesChanged.get()));
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("power remove <channel>")
    @CommandDescription("Removes a power channel from the last-added sign of the selected nodes")
    public void commandRemoveInputPowerChannel(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument(value="channel", suggestions="selected_input_power_channels") String channel_name,
            final @Flag(value="face",
                        parserName="sign_block_face",
                        description="Removes a specific face instead of any with the channel name") BlockFace face
    ) {
        final AtomicInteger numNodesChanged = new AtomicInteger(0);
        try {
            state.getSigns().updateLastSign(s -> {
                boolean removed;
                if (face == null) {
                    removed = s.removeInputPowerChannels(channel_name);
                } else {
                    removed = s.removeInputPowerChannel(NamedPowerChannel.of(channel_name, false, face));
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
    @Command("power clear")
    @CommandDescription("Clears all power channels from last-added sign of the selected nodes")
    public void commandClearInputPowerChannels(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.getSigns().updateLastSign(s -> {
                s.clearInputPowerChannels();
            });
            sender.sendMessage(ChatColor.GREEN + "Last sign power channels cleared");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The power channels of the selected nodes' last signs could not be cleared");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("power rotate")
    @CommandDescription("Rotates all power channels from last-added sign of the selected nodes by 90 degrees")
    public void commandRotateInputPowerChannels(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            final AtomicBoolean changed = new AtomicBoolean(false);
            state.getSigns().updateLastSign(s -> {
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
    @Command("power rotate <channel>")
    @CommandDescription("Rotates a power channel from last-added sign of the selected nodes by 90 degrees")
    public void commandRotateInputNamedPowerChannels(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument(value="channel", suggestions="selected_input_power_channels") String channel_name
    ) {
        try {
            final AtomicBoolean changed = new AtomicBoolean(false);
            state.getSigns().updateLastSign(s -> {
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

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("output add <channel>")
    @CommandDescription("Assigns a power output channel to the last-added sign of the selected nodes")
    public void commandAddOutputPowerChannel(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument(value="channel", suggestions="power_channels") String channel_name,
            final @Flag(value="powered",
                        description="If first creating the channel, defaults to it being powered") boolean powered
    ) {
        if (!NamedPowerChannel.checkPermission(sender, channel_name)) {
            return;
        }

        final AtomicInteger numNodesChanged = new AtomicInteger(0);
        try {
            state.getSigns().updateLastSign(s -> {
                s.addOutputPowerChannel(
                        channel_name,
                        powered);
                numNodesChanged.incrementAndGet();
            });
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_POWER_FAILED.message(sender);
            return;
        }

        if (numNodesChanged.get() == 0) {
            TCCoastersLocalization.SIGN_MISSING.message(sender);
            return;
        }

        TCCoastersLocalization.SIGN_POWER_ASSIGNED.message(sender, channel_name, "OUTPUT", Integer.toString(numNodesChanged.get()));
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("output remove <channel>")
    @CommandDescription("Removes a power output channel from the last-added sign of the selected nodes")
    public void commandRemoveOutputPowerChannel(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument(value="channel", suggestions="selected_output_power_channels") String channel_name
    ) {
        final AtomicInteger numNodesChanged = new AtomicInteger(0);
        try {
            state.getSigns().updateLastSign(s -> {
                boolean removed = s.removeOutputPowerChannel(channel_name);
                if (removed) {
                    numNodesChanged.incrementAndGet();
                }
            });
        } catch (ChangeCancelledException e) {
            TCCoastersLocalization.SIGN_POWER_FAILED.message(sender);
            return;
        }

        if (numNodesChanged.get() == 0) {
            sender.sendMessage(ChatColor.RED + "The selected nodes don't have any signs with power output channels that match!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Output power channel '" + ChatColor.WHITE + channel_name +
                ChatColor.YELLOW + "' removed from the last sign of " + ChatColor.WHITE + numNodesChanged.get() +
                ChatColor.YELLOW + " nodes");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("output clear")
    @CommandDescription("Clears all power output channels from last-added sign of the selected nodes")
    public void commandClearOutputPowerChannels(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        try {
            state.getSigns().updateLastSign(s -> {
                s.clearOutputPowerChannels();
            });
            sender.sendMessage(ChatColor.GREEN + "Last sign output power channels cleared");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "The output power channels of the selected nodes' last signs could not be cleared");
        }
    }
}
