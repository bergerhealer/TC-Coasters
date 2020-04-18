package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Event fired when a track object is selected by a player
 */
public class CoasterSelectTrackObjectEvent extends CoasterTrackObjectEvent {

    /**
     * Initializer for CoasterSelectTrackObjectEvent
     * 
     * @param who         The player that selected a track object
     * @param connection  The connection between two nodes the track object is on
     * @param object      The object being selected
     */
    public CoasterSelectTrackObjectEvent(Player who, TrackConnection connection, TrackObject object) {
        super(who, connection, object);
    }
}
