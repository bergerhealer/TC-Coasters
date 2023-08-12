package com.bergerkiller.bukkit.coasters;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.events.CoasterConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterNodeEvent;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;

/**
 * Handles node editing events using PlotSquared version 5 permissions.<br>
 * <br>
 * https://www.spigotmc.org/resources/plotsquared-v5.77506/
 */
public class PlotSquaredHandler_v5 implements Listener {
    private final TCCoasters plugin;
    private final Location location = new Location(null, 0, 0, 0);

    public PlotSquaredHandler_v5(TCCoasters plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCoasterNodeEvent(CoasterNodeEvent event) {
        event.setCancelledIfPositionInvalid(position -> checkAllowed(event.getPlayer(), position));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCoasterConnectionEvent(CoasterConnectionEvent event) {
        event.setCancelledIfPositionInvalid(position -> checkAllowed(event.getPlayer(), position));
    }

    // Checks whether the node at the position can be modified by the player
    private boolean checkAllowed(Player player, Vector position) {
        // If player has global USE permission, the player can use the plugin anywhere
        if (TCCoastersPermissions.USE.has(player)) {
            return true;
        }

        // Note: TCCCoastersListener already checks whether player has the plotsquared use permission
        //       At event priority LOW the event would already be cancelled if that permission was absent.

        // Re-use Location
        location.setWorld(player.getWorld().getName());
        location.setX(position.getBlockX());
        location.setY(position.getBlockY());
        location.setZ(position.getBlockZ());

        // Find plot area at Location
        // Check whether the player in question is an Owner inside own plots
        Plot plot = Plot.getPlot(location);
        if (plot == null || !plot.isAdded(player.getUniqueId())) {
            this.plugin.sendNoPermissionMessage(player, TCCoastersLocalization.PLOTSQUARED_NO_PERMISSION);
            return false;
        }

        return true;
    }
}
