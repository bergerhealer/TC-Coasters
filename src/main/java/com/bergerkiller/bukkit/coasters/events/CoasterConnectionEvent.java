package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

/**
 * Base class for an event involving a connection between two nodes
 */
public abstract class CoasterConnectionEvent extends CoasterEvent {
    private static final HandlerList handlers = new HandlerList();
    private final TrackConnection _connection;

    protected CoasterConnectionEvent(Player who, TrackConnection connection) {
        super(who);
        _connection = connection;
    }

    @Override
    public World getWorld() {
        return _connection.getNodeA().getBukkitWorld();
    }

    /**
     * Gets the track connection involved in this event
     * 
     * @return track connection
     */
    public TrackConnection getConnection() {
        return _connection;
    }

    @Override
    public boolean testPositions(Predicate<Vector> positionFilter) {
        return positionFilter.test(_connection.getNodeA().getPosition()) &&
               positionFilter.test(_connection.getNodeB().getPosition());
    }

    @Override
    public final HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
