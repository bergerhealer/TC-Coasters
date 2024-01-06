package com.bergerkiller.bukkit.coasters;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.events.CoasterConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterNodeEvent;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;

import java.lang.reflect.Method;

/**
 * Handles node editing events using PlotSquared version 6 permissions.<br>
 * <br>
 * https://www.spigotmc.org/resources/plotsquared-v6.77506/
 */
public class PlotSquaredHandler_v6 implements Listener {
    private final TCCoasters plugin;
    private final FastMethod<Location> locationAt;

    public PlotSquaredHandler_v6(TCCoasters plugin) {
        this.plugin = plugin;

        try {
            Method locationAt = Location.class.getDeclaredMethod("at", String.class, int.class, int.class, int.class);
            this.locationAt = new FastMethod<>(locationAt);
            this.locationAt.forceInitialization();
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to find plotsquared Location.at() method", t);
        }
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

        // Create Location
        Location location = locationAt.invoke(null, player.getWorld().getName(),
                position.getBlockX(), position.getBlockY(), position.getBlockZ());

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
