package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Event fired when a track object is removed from a connection between two nodes
 */
public class CoasterDeleteTrackObjectEvent extends CoasterTrackObjectEvent {

    /**
     * Initializer for CoasterDeleteTrackObjectEvent
     * 
     * @param who         The player that removed the track object
     * @param connection  The connection between two nodes from which the track object was removed
     * @param object      The track object that was removed
     */
    public CoasterDeleteTrackObjectEvent(Player who, TrackConnection connection, TrackObject object) {
        super(who, connection, object);
    }
}
