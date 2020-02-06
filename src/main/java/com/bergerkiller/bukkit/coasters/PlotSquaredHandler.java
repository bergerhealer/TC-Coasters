package com.bergerkiller.bukkit.coasters;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.events.CoasterConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterNodeEvent;
import com.github.intellectualsites.plotsquared.plot.object.Location;
import com.github.intellectualsites.plotsquared.plot.object.Plot;

/**
 * Handles node editing events using PlotSquared permissions
 */
public class PlotSquaredHandler implements Listener {
    private final TCCoasters plugin;
    private final Location location = new Location(null, 0, 0, 0);

    public PlotSquaredHandler(TCCoasters plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCoasterNodeEvent(CoasterNodeEvent event) {
        event.setCancelled(!checkAllowed(event.getPlayer(), event.getNode().getPosition()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCoasterConnectionEvent(CoasterConnectionEvent event) {
        event.setCancelled(!checkAllowed(event.getPlayer(), event.getConnection().getNodeA().getPosition()) ||
                           !checkAllowed(event.getPlayer(), event.getConnection().getNodeB().getPosition()));
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
