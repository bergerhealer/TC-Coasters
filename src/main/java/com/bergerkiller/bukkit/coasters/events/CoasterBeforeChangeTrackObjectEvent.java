package com.bergerkiller.bukkit.coasters.events;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Event fired before a player makes changes to an existing track object
 */
public class CoasterBeforeChangeTrackObjectEvent extends CoasterTrackObjectEvent {

    public CoasterBeforeChangeTrackObjectEvent(Player who, TrackConnection connection, TrackObject object) {
        super(who, connection, object);
    }
}
