package com.bergerkiller.bukkit.coasters.commands;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersPermissions;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.csv.TrackCSVWriter;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditMode;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.events.CoasterCopyEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterImportEvent;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster.CoasterLoadException;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.common.io.AsyncTextWriter;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;

import org.incendo.cloud.annotation.specifier.FlagYielding;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;

@Command("tccoasters|tcc")
class EditStateCommands {

    @CommandRequiresTCCPermission
    @Command("create")
    @CommandDescription("Creates new track nodes, objects or connects selected nodes together")
    public void commandCreate(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        try {
            state.createTrack();
            sender.sendMessage("Created a new track node at your position");
        } catch (ChangeCancelledException e) {
            sender.sendMessage(ChatColor.RED + "A new track node could not be created here");
        }
    }

    @CommandRequiresTCCPermission
    @Command("delete")
    @CommandDescription("Disconnects selected nodes and deletes unconnected nodes and track objects")
    public void commandDelete(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        if (state.getMode() == PlayerEditMode.OBJECT) {
            // Object
            state.getObjects().deselectLockedObjects();
            if (!state.getObjects().hasEditedObjects()) {
                sender.sendMessage("No track objects selected, nothing has been deleted!");
            } else {
                try {
                    int numDeleted = state.getObjects().getEditedObjects().size();
                    state.getObjects().deleteObjects();
                    sender.sendMessage("Deleted " + numDeleted + " track objects!");
                } catch (ChangeCancelledException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to delete some of the track objects!");
                }
            }
        } else {
            // Tracks
            state.deselectLockedNodes();
            if (!state.hasEditedNodes()) {
                sender.sendMessage("No track nodes selected, nothing has been deleted!");
            } else {
                try {
                    int numDeleted = state.getEditedNodes().size();
                    state.deleteTrack();
                    sender.sendMessage("Deleted " + numDeleted + " track nodes!");
                } catch (ChangeCancelledException e) {
                    sender.sendMessage(ChatColor.RED + "Failed to delete some of the track nodes!");
                }
            }
        }
    }

    @CommandRequiresTCCPermission
    @Command("deselect")
    @CommandDescription("Deselects all nodes currently selected")
    public void commandDeselect(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        if (state.hasEditedNodes()) {
            state.clearEditedNodes();
            sender.sendMessage("Deselected all previously selected nodes");
        } else {
            sender.sendMessage("No nodes were selected");
        }
    }

    @CommandRequiresTCCPermission
    @Command("copy")
    @CommandDescription("Copies all currently selected nodes to the clipboard")
    public void commandCopy(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        state.getClipboard().copy();
        if (state.getClipboard().isFilled()) {
            sender.sendMessage(state.getClipboard().getNodeCount() + " track nodes copied to the clipboard!");
        } else {
            sender.sendMessage("No tracks selected, clipboard cleared!");
        }
    }

    @CommandRequiresTCCPermission
    @Command("cut")
    @CommandDescription("Copies all currently selected nodes to the clipboard and deletes them")
    public void commandCut(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        state.getClipboard().copy();
        if (state.getClipboard().isFilled()) {
            try {
                state.deleteTrack();
                sender.sendMessage(state.getClipboard().getNodeCount() + " track nodes cut from the world and saved to the clipboard!");
            } catch (ChangeCancelledException e) {
                sender.sendMessage(ChatColor.RED + "Some track nodes could not be cut from the world!");
            }
        } else {
            sender.sendMessage("No tracks selected, clipboard cleared!");
        }
    }

    @CommandRequiresTCCPermission
    @Command("paste")
    @CommandDescription("Pastes the current contents of the clipboard at the current position")
    public void commandPaste(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        if (state.getClipboard().isFilled()) {
            try {
                state.getClipboard().paste();
                sender.sendMessage(state.getClipboard().getNodeCount() + " track nodes pasted from the clipboard at your position!");
            } catch (ChangeCancelledException e) {
                sender.sendMessage(ChatColor.RED + "The track nodes could not be pasted here");
            }
        } else {
            sender.sendMessage("Clipboard is empty, nothing has been pasted!");
        }
    }

    @CommandRequiresTCCPermission
    @Command("lock")
    @CommandDescription("Locks the selected coaster so that nobody can change it anymore, until it is unlocked")
    public void commandLock(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        if (!TCCoastersPermissions.LOCK.has(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to lock coasters");
        } else {
            for (TrackCoaster coaster : state.getEditedCoasters()) {
                coaster.setLocked(true);
            }
            sender.sendMessage(ChatColor.YELLOW + "Selected coasters have been " + ChatColor.RED + "LOCKED");
        }
    }

    @CommandRequiresTCCPermission
    @Command("unlock")
    @CommandDescription("Unlocks the selected coaster so that it can be changed again after locking")
    public void commandUnlock(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        if (!TCCoastersPermissions.LOCK.has(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to unlock coasters");
        } else {
            for (TrackCoaster coaster : state.getEditedCoasters()) {
                coaster.setLocked(false);
            }
            sender.sendMessage(ChatColor.YELLOW + "Selected coasters have been " + ChatColor.GREEN + "UNLOCKED");
        }
    }

    @CommandRequiresTCCPermission
    @Command("rename")
    @CommandDescription("Shows help about renaming, and what coasters exist")
    public void commandRenameHelp(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        List<String> coasterNames = state.getEditedNodes().stream()
                .map(TrackNode::getCoaster)
                .distinct()
                .map(TrackCoaster::getName)
                .collect(Collectors.toList());
        sender.sendMessage(ChatColor.YELLOW + "Selected coaster names: " + ChatColor.WHITE +
                StringUtil.combineNames(coasterNames));
        sender.sendMessage(ChatColor.RED + "Usage: /tcc rename <coaster_name> <new_coaster_name>");
        sender.sendMessage(ChatColor.RED + "Usage for one: /tcc rename <new_coaster_name>");
    }

    @CommandRequiresTCCPermission
    @Command("rename <new_coaster_name>")
    @CommandDescription("Renames the selected coaster to a new name, so that the csv file of it can be identified")
    public void commandRename(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Quoted @Argument("new_coaster_name") String newCoasterName
    ) {
        List<String> coasterNames = state.getEditedNodes().stream()
                .map(TrackNode::getCoaster)
                .distinct()
                .map(TrackCoaster::getName)
                .collect(Collectors.toList());
        if (coasterNames.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "You have no coaster selected!");
            return;
        } else if (coasterNames.size() > 1) {
            sender.sendMessage(ChatColor.RED + "You have more than one coaster selected!");
            return;
        }

        commandRename(state, sender, plugin, newCoasterName, coasterNames.get(0));
    }

    @CommandRequiresTCCPermission
    @Command("rename <new_coaster_name> <old_coaster_name>")
    @CommandDescription("Renames a coaster by an old name to a new name, so that the csv file of it can be identified")
    public void commandRename(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Quoted @Argument("new_coaster_name") String newCoasterName,
            final @Quoted @Argument("old_coaster_name") String oldCoasterName
    ) {
        if (oldCoasterName.equalsIgnoreCase(newCoasterName)) {
            sender.sendMessage(ChatColor.RED + "Old and new coaster name is the same!");
            return;
        }
        if (state.getWorld().getTracks().getCoasterByName(newCoasterName) != null) {
            sender.sendMessage(ChatColor.RED + "Coaster with name " + newCoasterName + " already exists!");
            return;
        }
        TrackCoaster coaster = state.getWorld().getTracks().getCoasterByName(oldCoasterName);
        if (coaster == null) {
            sender.sendMessage(ChatColor.RED + "Coaster with name " + oldCoasterName + " does not exist!");
            return;
        }
        coaster.renameTo(newCoasterName);
        sender.sendMessage(ChatColor.GREEN + "Coaster '"  + ChatColor.YELLOW + oldCoasterName +
                ChatColor.GREEN + "' renamed to '" + ChatColor.YELLOW + newCoasterName +
                ChatColor.GREEN + "'!");
    }

    @CommandRequiresTCCPermission
    @Command("straight")
    @CommandDescription("Makes connections between selected nodes straight")
    public void commandStraight(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        try {
            if (state.hasCurvedConnectionTrackNodes()) {
                state.makeConnectionsStraight();
                sender.sendMessage(ChatColor.GREEN + "Connections of the selected nodes are now straight");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No nodes are selected, or all nodes already use straight connections!");
            }
        } catch (ChangeCancelledException ex) {
            sender.sendMessage(ChatColor.RED + "Failed to make the connections straight");
        }
    }

    @CommandRequiresTCCPermission
    @Command("curved")
    @CommandDescription("Makes connections between selected nodes curved")
    public void commandCurved(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        try {
            if (state.hasStraightConnectionTrackNodes()) {
                state.makeConnectionsCurved();
                sender.sendMessage(ChatColor.GREEN + "Connections of the selected nodes are now curved");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No nodes are selected, or all nodes already use curved connections!");
            }
        } catch (ChangeCancelledException ex) {
            sender.sendMessage(ChatColor.RED + "Failed to make the connections curved");
        }
    }

    @CommandRequiresTCCPermission
    @Command("import <url_or_file>")
    @CommandDescription("Imports a coaster by (down)loading a csv file")
    public void commandImport(
            final Player player,
            final TCCoasters plugin,
            final @FlagYielding @Greedy @Argument("url_or_file") String urlOrFile,
            final @Flag("absolute") boolean absolute
    ) {
        TCCoastersPermissions.IMPORT.handle(player);
        plugin.importFileOrURL(urlOrFile).thenAccept(download -> {
            if (!download.success()) {
                player.sendMessage(ChatColor.RED + "Failed to import coaster: " + download.error());
                return;
            }

            PlayerEditState dlEditState = plugin.getEditState(player);
            TrackCoaster coaster = dlEditState.getWorld().getTracks().createNewEmpty(plugin.generateNewCoasterName());
            try {
                try {
                    coaster.loadFromStream(download.contentInputStream(), player,
                            absolute ? null : PlayerOrigin.getForPlayer(player));
                } catch (ChangeCancelledException ex) {
                    player.sendMessage(ChatColor.RED + "Not all coaster track nodes could be imported!");

                    // Skip the 'failed to decode' message, makes no sense
                    if (coaster.getNodes().isEmpty()) {
                        coaster.remove();
                        return;
                    }
                }
                if (coaster.getNodes().isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Failed to decode any coaster track nodes!");
                    coaster.remove();
                    return;
                }
            } catch (CoasterLoadException ex) {
                player.sendMessage(ChatColor.RED + ex.getMessage());
                if (coaster.getNodes().isEmpty()) {
                    coaster.remove();
                    return;
                }
            }

            // Check power state permissions. Would rather do it during load but whatever...
            try {
                for (TrackNode node : coaster.getNodes()) {
                    node.checkPowerPermissions(player);
                }
            } catch (ChangeCancelledException ex) {
                coaster.remove();
                return;
            }

            // Handle event
            if (CommonUtil.callEvent(new CoasterImportEvent(player, coaster)).isCancelled()) {
                player.sendMessage(ChatColor.RED + "Coaster could not be imported here!");
                coaster.remove();
                return;
            }
            if (coaster.getNodes().isEmpty()) {
                player.sendMessage(ChatColor.RED + "None of the nodes could be imported here!");
                coaster.remove();
                return;
            }

            // Create a history change for the entire coaster details
            // This allows the player to undo this
            dlEditState.getHistory().addChangeAfterCreatingCoaster(coaster);

            player.sendMessage(ChatColor.GREEN + "Coaster with " + coaster.getNodes().size() + " nodes imported!");

            // Show where this coaster is located
            Vector centerPos = coaster.getNodes().stream()
                .map(TrackNode::getPosition)
                .reduce((a,b) -> a.clone().add(b))
                .get().multiply(1.0 / coaster.getNodes().size());
            player.sendMessage(ChatColor.GREEN + "Position is roughly:" +
                " x=" + Double.toString(MathUtil.round(centerPos.getX(), 4)) +
                " y=" + Double.toString(MathUtil.round(centerPos.getY(), 4)) +
                " z=" + Double.toString(MathUtil.round(centerPos.getZ(), 4)));
            ChatText text = ChatText.fromClickableRunCommand(ChatColor.UNDERLINE + "> Take me there! <",
                    "/tp " + player.getName() + " " + centerPos.getX() + " " +
                    centerPos.getY() + " " + centerPos.getZ());
            text.sendTo(player);
        });
    }

    @CommandRequiresTCCPermission
    @Command("export")
    @CommandDescription("Exports a coaster to a haste bin server or local file")
    public void commandExport(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Flag(value="file", description="Whether to export to a file in the plugin export folder") boolean exportToFile,
            final @Flag(value="nl2", description="Whether to export in the NoLimits2 fileformat") boolean nolimits2Format,
            final @Flag(value="origin", description="Whether to write the NoLimits2 positions relative to the player") boolean nolimits2Origin
    ) {
        TCCoastersPermissions.EXPORT.handle(sender);
        if (!state.hasEditedNodes()) {
            sender.sendMessage(ChatColor.RED + "No track nodes selected, nothing has been exported!");
            return;
        }

        HashSet<TrackNode> exportedNodes = new HashSet<TrackNode>(state.getEditedNodes());
        CoasterCopyEvent event = CommonUtil.callEvent(new CoasterCopyEvent(state.getPlayer(), exportedNodes, true));
        if (event.isCancelled() || exportedNodes.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "These nodes could not be exported!");
        }

        String content;
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try (TrackCSVWriter writer = new TrackCSVWriter(stream, nolimits2Format ? '\t' : ',')) {
                if (nolimits2Format) {
                    // NoLimits2 format
                    writer.writeAllNoLimits2(exportedNodes, nolimits2Origin ? PlayerOrigin.getForPlayer(state.getPlayer()) : null);
                } else {
                    // Normal TCC format
                    writer.setWriteLinksToForeignNodes(false);
                    writer.write(PlayerOrigin.getForPlayer(state.getPlayer()));
                    writer.writeAll(exportedNodes);
                }
            }
            content = stream.toString("UTF-8");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to export coaster", t);
            sender.sendMessage(ChatColor.RED + "Failed to export: " + t.getMessage());
            return;
        }

        if (exportToFile) {
            // Asynchronously write text content to file
            final File resultFile = new File(plugin.getExportFolder(), "coaster_" + System.currentTimeMillis() + ".csv");
            AsyncTextWriter.write(resultFile, content).handleAsync((result, t) -> {
                if (t != null) {
                    resultFile.delete();
                    sender.sendMessage(ChatColor.RED + "Failed to export to file: " + t.getMessage());
                } else {
                    String relPath = plugin.getDataFolder().getParentFile().toPath().relativize(resultFile.toPath()).toString();
                    sender.sendMessage(ChatColor.GREEN + "Tracks exported to file:");
                    sender.sendMessage(ChatColor.WHITE + relPath);
                }
                return result;
            }, CommonUtil.getPluginExecutor(plugin));
        } else {
            // Asynchronously upload to a hastebin server
            plugin.getHastebin().upload(content).thenAccept(t -> {
                if (t.success()) {
                    sender.sendMessage(ChatColor.GREEN + "Tracks exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to export: " + t.error());
                }
            });
        }
    }

    @CommandRequiresTCCPermission
    @Command("info")
    @CommandDescription("Shows meta information about the selected nodes")
    public void commandInfo(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        List<String> coasterNames = state.getEditedNodes().stream()
                .map(TrackNode::getCoaster)
                .distinct()
                .map(TrackCoaster::getName)
                .collect(Collectors.toList());
        double totalDistance = state.getEditedNodes().stream()
                .flatMap(c -> c.getConnections().stream())
                .filter(c -> state.isEditing(c.getNodeA()) && state.isEditing(c.getNodeB()))
                .distinct()
                .mapToDouble(TrackConnection::getFullDistance) 
                .sum();
        sender.sendMessage(ChatColor.YELLOW + "Total nodes: " + ChatColor.WHITE + state.getEditedNodes().size());
        sender.sendMessage(ChatColor.YELLOW + "Total track distance: " + ChatColor.WHITE + totalDistance);
        sender.sendMessage(ChatColor.YELLOW + "Coaster names: " + ChatColor.WHITE + StringUtil.combineNames(coasterNames));
    }

    @CommandRequiresTCCPermission
    @Command("option dragcontrol <enabled>")
    @CommandDescription("Sets whether position sliders can be controlled by right-click dragging ingame")
    public void commandOptionDragControl(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument("enabled") boolean enabled
    ) {
        state.getObjects().setDragControlEnabled(enabled);
        if (state.getObjects().isDragControlEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Right-click drag menu control is now " + ChatColor.GREEN + "ENABLED");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Right-click drag menu control is now " + ChatColor.RED + "DISABLED");
        }
    }

    @org.incendo.cloud.annotations.Permission("train.coasters.customviewrange")
    @Command("option particleviewrange")
    @CommandDescription("Gets the particle view range set for yourself")
    public void commandOptionViewRangeGet(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        if (state.isParticleViewRangeOverridden()) {
            sender.sendMessage(ChatColor.YELLOW + "Particle view range is set to " +
                    ChatColor.WHITE + state.getParticleViewRange());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Particle view range is set to the default " +
                    ChatColor.WHITE + state.getParticleViewRange());
        }
        ChatText.fromMessage(ChatColor.YELLOW + "Use ")
                .append(ChatText.fromMessage(ChatColor.WHITE + "/tcc option particleviewrange <value>")
                        .setClickableSuggestedCommand("/tcc option particleviewrange "))
                .append(ChatColor.YELLOW + " to change it")
                .sendTo(sender);
    }

    @org.incendo.cloud.annotations.Permission("train.coasters.customviewrange")
    @Command("option particleviewrange <range>")
    @CommandDescription("Sets the particle view range for yourself")
    public void commandOptionViewRangeSet(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument("range") int range
    ) {
        if (range < 0) {
            sender.sendMessage(ChatColor.RED + "Particle view range must be greater than or equal to 0");
            return;
        }

        int safeRange = range;
        if (safeRange > plugin.getMaximumParticleViewRange()) {
            safeRange = plugin.getMaximumParticleViewRange();
            sender.sendMessage(ChatColor.RED + "Range of " + range + " is too high and has been limited " +
                    "(maximum: " + plugin.getMaximumParticleViewRange() + ")");
        }
        int oldRange = state.getParticleViewRange();
        state.setParticleViewRangeOverride(safeRange);
        sender.sendMessage(ChatColor.YELLOW + "Particle view range set to " + ChatColor.WHITE + safeRange +
                           ChatColor.YELLOW + " (was " + ChatColor.WHITE + oldRange + ChatColor.YELLOW + ")");
    }

    @org.incendo.cloud.annotations.Permission("train.coasters.customviewrange")
    @Command("option particleviewrange reset")
    @CommandDescription("Resets your particle view range to the default")
    public void commandOptionViewRangeReset(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        int oldRange = state.getParticleViewRange();
        state.setParticleViewRangeOverride(null);
        sender.sendMessage(ChatColor.YELLOW + "Particle view range reset to default " + ChatColor.WHITE + plugin.getParticleViewRange() +
                           ChatColor.YELLOW + " (was " + ChatColor.WHITE + oldRange + ChatColor.YELLOW + ")");
    }
}
