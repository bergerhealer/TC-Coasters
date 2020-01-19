package com.bergerkiller.bukkit.coasters;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.events.CoasterConnectionEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterNodeEvent;
import com.github.intellectualsites.plotsquared.api.PlotAPI;

/**
 * Handles node editing events using PlotSquared permissions
 */
public class PlotSquaredHandler implements Listener {
    private final PlotAPI api = new PlotAPI();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCoasterNodeEvent(CoasterNodeEvent event) {
        event.setCancelled(!checkAllowed(event.getPlayer(), event.getNode().getPosition()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCoasterConnectionEvent(CoasterConnectionEvent event) {
        event.setCancelled(!checkAllowed(event.getPlayer(), event.getConnection().getNodeA().getPosition()) ||
                           !checkAllowed(event.getPlayer(), event.getConnection().getNodeB().getPosition()));
    }

    // Checks whether the node at the position can be modified by the player
    private boolean checkAllowed(Player player, Vector position) {
        return true;
    }
}
