package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

/**
 * Base class for all coaster events that involve a single track node
 */
public abstract class CoasterNodeEvent extends CoasterEvent {
    private static final HandlerList handlers = new HandlerList();
    private final TrackNode _node;

    protected CoasterNodeEvent(Player who, TrackNode node) {
        super(who);
        _node = node;
    }

    /**
     * Gets the track node that is involved in this event
     * 
     * @return node
     */
    public TrackNode getNode() {
        return _node;
    }

    /**
     * Gets the coaster of the node that is involved in this event
     * 
     * @return coaster
     */
    public TrackCoaster getCoaster() {
        return _node.getCoaster();
    }

    @Override
    public boolean testPositions(Predicate<Vector> positionFilter) {
        return positionFilter.test(getNode().getPosition());
    }

    @Override
    public World getWorld() {
        return _node.getBukkitWorld();
    }

    @Override
    public final HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
