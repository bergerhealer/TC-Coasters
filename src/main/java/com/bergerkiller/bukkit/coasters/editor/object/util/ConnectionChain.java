package com.bergerkiller.bukkit.coasters.editor.object.util;

import java.util.List;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;

/**
 * Represents a chain of node connections iterated from a start connection
 * into a given direction. The direction taken on the current connection
 * is automatically updated.
 */
public class ConnectionChain {
    /**
     * Current connection
     */
    public TrackConnection connection;
    /**
     * Current direction on the connection, where true
     * means node A->B and false means node B->A.
     */
    public boolean direction;

    public ConnectionChain(TrackConnection connection, boolean direction) {
        this.connection = connection;
        this.direction = direction;
    }

    public double getFullDistance() {
        return this.connection.getFullDistance();
    }

    public boolean next() {
        if (this.connection == null) {
            return false;
        }

        // Pick next connection. We don't really support junctions that well.
        TrackNode next = this.direction ? this.connection.getNodeB() : this.connection.getNodeA();
        List<TrackConnection> nextConnections = next.getConnections();
        if (nextConnections.size() <= 1) {
            this.connection = null;
            return false;
        } else {
            this.connection = (nextConnections.get(0) == this.connection) ? nextConnections.get(1) : nextConnections.get(0);
            this.direction = (this.connection.getNodeA() == next);
            return true;
        }
    }
}
