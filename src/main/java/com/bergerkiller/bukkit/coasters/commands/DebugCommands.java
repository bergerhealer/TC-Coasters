package com.bergerkiller.bukkit.coasters.commands;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import com.bergerkiller.bukkit.coasters.TCCoastersPermissions;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackWorld;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.internal.CommonPlugin;
import com.bergerkiller.bukkit.common.internal.permissions.PermissionHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.rails.TrackRailsSectionsAtRail;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

import org.bukkit.util.Vector;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;

@Command("tccoasters|tcc debug")
class DebugCommands {
    @CommandRequiresTCCPermission
    @Command("rebuild")
    @CommandDescription("Rebuilds the track data, might sometimes fix things")
    public void commandBuild(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Rebuilding tracks...");
        plugin.rebuildAll();
        sender.sendMessage("Rebuilding done!");
    }

    @CommandRequiresTCCPermission
    @Command("path")
    @CommandDescription("Logs the path segments of the selected nodes to system log")
    public void commandDebugPath(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        sender.sendMessage("Logging paths of all selected nodes");
        for (TrackNode node : state.getEditedNodes()) {
            plugin.log(Level.INFO, "Path for: " + node.getPosition());
            for (RailPath.Point point : node.buildPath().getPoints()) {
                plugin.log(Level.INFO, "  - " + point);
            }
        }
    }

    @CommandRequiresTCCPermission
    @Command("rail")
    @CommandDescription("Shows built rail information tied to certain rail block coordinates")
    public void commandRail(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        PlayerEditState.LookingAtRailInfo rail = state.findLookingAtRailBlock();
        if (rail != null) {
            commandRail(state, sender, rail.rail.x, rail.rail.y, rail.rail.z);
        } else {
            sender.sendMessage(ChatColor.RED + "Not looking at any node rail blocks");
        }
    }

    @CommandRequiresTCCPermission
    @Command("rail <x> <y> <z>")
    @CommandDescription("Shows built rail information tied to certain rail block coordinates")
    public void commandRail(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("x") int x,
            final @Argument("y") int y,
            final @Argument("z") int z
    ) {
        TrackRailsSectionsAtRail atRail = state.getWorld().getRails().findAtRailsInformation(x, y, z);
        if (atRail != null) {
            sender.sendMessage(ChatColor.YELLOW + "Rail Information at " + x + " / " + y + " / " + z);
            sender.sendMessage(atRail.debugString().split("\n"));
        } else {
            sender.sendMessage(ChatColor.RED + "No rail information stored for rail at " +
                x + " / " + y + " / " + z);
        }
    }

    @CommandRequiresTCCPermission
    @Command("visibletoeveryone <visible>")
    @CommandDescription("Makes track visible to all players, also those not editing track")
    public void commandVisibleToEveryone(
            final PlayerEditState state,
            final CommandSender sender,
            final @Argument("visible") boolean visible
    ) {
        if (!TCCoastersPermissions.VISIBLE_TO_EVERYONE.has(sender)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to change this option");
        } else {
            state.getWorld().getParticles().setVisibleToEveryone(visible);
            if (visible) {
                sender.sendMessage(ChatColor.GREEN + "Coasters are now visible to all players");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Coasters are no longer visible to all players");
            }
        }
    }

    @Command("player_edit_state")
    @CommandDescription("Displays TCC player edit state and capabilities")
    public void commandDisplayPlayerState(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        PermissionHandler handler = CommonPlugin.getInstance().getPermissionHandler();
        sender.sendMessage(ChatColor.YELLOW + "Permission Handler: " +
                ((handler == null) ? ChatColor.RED + "None" : ChatColor.WHITE + handler.getClass().getName()));
        sender.sendMessage(ChatColor.YELLOW + "Permission [train.coasters.use]: " +
                (TCCoastersPermissions.USE.has(sender) ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Permission [train.coasters.plotsquared.use]: " +
                (TCCoastersPermissions.PLOTSQUARED_USE.has(sender) ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Selected Mode: " + state.getMode().getName());
    }

    @CommandRequiresTCCPermission
    @Command("metrics")
    @CommandDescription("Makes plugin load time metrics available to diagnose slow loading problems")
    public void commandGetMetrics(
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        StringBuilder str = new StringBuilder();

        List<CoasterWorld> worldsByTime = new ArrayList<>(plugin.getCoasterWorlds());
        worldsByTime.sort(Comparator.comparing(world -> world.getTracks().getLoadMetrics().totalTime(), Comparator.reverseOrder()));

        str.append("Plugin load-time metrics by world\n\n");

        for (CoasterWorld world : worldsByTime) {
            if (world.getTracks().getCoasters().isEmpty()) {
                continue;
            }

            TrackWorld.LoadMetrics metrics = world.getTracks().getLoadMetrics();
            str.append(world.getBukkitWorld().getName()).append(":\n");
            str.append("  Total load time: " ).append(formatTime(metrics.totalTime())).append("\n");
            str.append("    Load time: ").append(formatTime(metrics.loadTimeSeconds)).append("\n");
            str.append("    Refresh time: ").append(formatTime(metrics.updateTimeSeconds)).append("\n");
            str.append("    Rebuild time: ").append(formatTime(metrics.rebuildTimeSeconds)).append("\n");

            str.append("  Coasters:\n");
            List<TrackCoaster> coastersByTime = new ArrayList<>(world.getTracks().getCoasters());
            coastersByTime.sort(Comparator.comparing(coaster -> coaster.getLoadMetrics().totalTime(), Comparator.reverseOrder()));
            for (TrackCoaster coaster : coastersByTime) {
                // Compute an average/centroid position where the coaster is roughly located at
                List<TrackNode> nodes = coaster.getNodes();
                Set<TrackConnection> uniqueConnections = new HashSet<>();
                Vector avgPos = new Vector();
                if (!nodes.isEmpty()) {
                    nodes.forEach(node -> {
                        avgPos.add(node.getPosition());
                        uniqueConnections.addAll(node.getConnections());
                    });
                    avgPos.multiply(1.0 / nodes.size());
                }

                // Compute metrics about how many track objects there are
                int trackObjectCount = 0;
                for (TrackConnection connection : uniqueConnections) {
                    trackObjectCount += connection.getObjects().size();
                }

                TrackCoaster.CoasterLoadMetrics coasterLoadMetrics = coaster.getLoadMetrics();

                str.append("    - ").append(coaster.getName()).append(" [")
                        .append(avgPos.getBlockX()).append(", ")
                        .append(avgPos.getBlockY()).append(", ")
                        .append(avgPos.getBlockZ()).append("] took ")
                        .append(formatTime(coasterLoadMetrics.totalTime())).append("\n");
                str.append("      Contains ").append(nodes.size()).append(" nodes, ")
                        .append(uniqueConnections.size()).append(" connections, ")
                        .append(trackObjectCount).append(" track objects\n");
                str.append("      Load took ").append(formatTime(coasterLoadMetrics.loadTimeSeconds))
                        .append(", finalize took ").append(formatTime(coasterLoadMetrics.finalizeTimeSeconds)).append("\n");
            }
            str.append("\n");
        }

        plugin.getHastebin().upload(str.toString()).thenAccept(t -> {
            if (t.success()) {
                sender.sendMessage(ChatColor.GREEN + "Metrics exported: " + ChatColor.WHITE + ChatColor.UNDERLINE + t.url());
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to export metrics: " + t.error());
            }
        });
    }

    private static final DecimalFormat timeFormat = new DecimalFormat("0.################");
    private static String formatTime(double seconds) {
        if (seconds >= 0.1) {
            return timeFormat.format(seconds) + "s";
        } else {
            return timeFormat.format(seconds * 1000.0) + "ms";
        }
    }
}
