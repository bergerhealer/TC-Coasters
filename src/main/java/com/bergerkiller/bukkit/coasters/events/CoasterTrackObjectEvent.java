package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Base class for an event involving a track object on a connection between two nodes
 */
public abstract class CoasterTrackObjectEvent extends CoasterConnectionEvent {
    private final TrackObject _object;

    protected CoasterTrackObjectEvent(Player who, TrackConnection connection, TrackObject object) {
        super(who, connection);
        this._object = object;
    }

    /**
     * Gets the track object that was changed
     * 
     * @return track object
     */
    public TrackObject getObject() {
        return this._object;
    }
}
