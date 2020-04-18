package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Event fired after a player finishes changing the properties/position of a track object,
 * or when the player logs off while in the middle of it.
 */
public class CoasterAfterChangeTrackObjectEvent extends CoasterTrackObjectEvent {
    private final TrackConnection _oldConnection;
    private final TrackObject _oldObject;

    public CoasterAfterChangeTrackObjectEvent(Player who, TrackConnection connection, TrackObject object, TrackConnection old_connection, TrackObject old_object) {
        super(who, connection, object);
        this._oldConnection = old_connection;
        this._oldObject = old_object;
    }

    /**
     * Gets the previous connection between two points the track object was on before moving
     * 
     * @return old connection
     */
    public TrackConnection getPreviousConnection() {
        return this._oldConnection;
    }

    /**
     * Gets the previous state of the object, prior to making the changes.
     * This is a clone of the original track object that is not bound to any connections.
     * 
     * @return previous object
     */
    public TrackObject getPreviousObject() {
        return this._oldObject;
    }

    /**
     * Gets whether the track object will be moved between connections
     * 
     * @return getPreviousConnection() != getConnection()
     */
    public boolean isChangingConnections() {
        return this.getPreviousConnection() != this.getConnection();
    }
}
