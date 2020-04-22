package com.bergerkiller.bukkit.coasters.editor.object.util;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.events.CoasterCreateTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.common.utils.CommonUtil;

/**
 * Object that was created while duplicating by a player
 */
public class DuplicatedObject {
    public final TrackConnection connection;
    public final TrackObject object;

    private DuplicatedObject(TrackConnection connection, TrackObject object) {
        this.connection = connection;
        this.object = object;
    }

    public void remove() {
        this.connection.removeObject(this.object);
    }

    public void add(String selectedAnimation) {
        this.connection.addObject(this.object);
        this.connection.addObjectToAnimationStates(selectedAnimation, this.object);
    }

    public boolean testCanCreate(Player who) {
        return !CommonUtil.callEvent(new CoasterCreateTrackObjectEvent(who, this.connection, this.object)).isCancelled();
    }

    public static DuplicatedObject create(TrackConnection connection, double distance, TrackObject object, boolean flipped) {
        DuplicatedObject dupe = new DuplicatedObject(connection, object.clone());
        dupe.object.setDistanceFlippedSilently(distance, flipped);
        return dupe;
    }
}
