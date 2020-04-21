package com.bergerkiller.bukkit.coasters.editor.object;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

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

    public static DuplicatedObject create(TrackConnection connection, double distance, TrackObject object) {
        DuplicatedObject dupe = new DuplicatedObject(connection, object.clone());
        dupe.object.setDistanceFlippedSilently(distance, false); //TODO: Compute flipped!
        dupe.connection.addObject(dupe.object);
        return dupe;
    }
}
