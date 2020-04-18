package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Event fired when a track object is placed on a connection between two nodes
 */
public class CoasterCreateTrackObjectEvent extends CoasterTrackObjectEvent {

    /**
     * Initializer for CoasterCreateTrackObjectEvent
     * 
     * @param who         The player that created the track object
     * @param connection  The connection on which the track object was placed
     * @param object      The placed track object
     */
    public CoasterCreateTrackObjectEvent(Player who, TrackConnection connection, TrackObject object) {
        super(who, connection, object);
    }
}
