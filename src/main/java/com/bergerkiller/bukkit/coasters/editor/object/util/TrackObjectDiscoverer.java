package com.bergerkiller.bukkit.coasters.editor.object.util;

import java.util.Map;
import java.util.Set;

import com.bergerkiller.bukkit.coasters.editor.object.ObjectEditTrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * Goes down the track through all connections from a starting point and direction,
 * looking for track objects on the track that have been requested to be found by the player.
 */
public class TrackObjectDiscoverer {
    public final Map<TrackObject, ObjectEditTrackObject> pending;
    public final Set<TrackConnection> visited;
    public final ConnectionChain connection;
    public boolean initialDirection;
    public double distance;

    public TrackObjectDiscoverer(Map<TrackObject, ObjectEditTrackObject> pending, Set<TrackConnection> visited, TrackConnection connection, boolean direction, double pointDistance) {
        this.pending = pending;
        this.visited = visited;
        this.connection = new ConnectionChain(connection, direction);
        this.initialDirection = direction;
        this.distance = direction ? (connection.getFullDistance() - pointDistance) : pointDistance;
    }

    public boolean next() {
        // Check no more pending objects
        if (this.pending.isEmpty()) {
            return false;
        }

        // Next connection
        if (!this.connection.next()) {
            return false;
        }

        // If already visited, abort
        if (!this.visited.add(this.connection.connection)) {
            this.connection.connection = null;
            return false;
        }

        // Check all objects on connection
        for (TrackObject object : this.connection.connection.getObjects()) {
            ObjectEditTrackObject editObject = this.pending.remove(object);
            if (editObject != null) {
                double objectDistance = object.getDistance();
                if (!this.connection.direction) {
                    objectDistance = connection.getFullDistance() - objectDistance;
                }
                editObject.dragDirection = this.initialDirection;
                editObject.dragDistance = this.distance + objectDistance;
                editObject.alignmentFlipped = (object.isFlipped() != this.connection.direction) != this.initialDirection;
            }
        }
        this.distance += this.connection.getFullDistance();
        return true;
    }
}
