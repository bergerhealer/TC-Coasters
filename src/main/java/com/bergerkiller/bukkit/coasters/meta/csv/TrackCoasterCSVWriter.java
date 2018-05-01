package com.bergerkiller.bukkit.coasters.meta.csv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bergerkiller.bukkit.coasters.meta.TrackCoaster;
import com.bergerkiller.bukkit.coasters.meta.TrackConnection;
import com.bergerkiller.bukkit.coasters.meta.TrackNode;
import com.opencsv.CSVWriter;

/**
 * Helper class for turning a coaster into a CSV file
 */
public class TrackCoasterCSVWriter {
    private final TrackCoaster coaster;
    private final CSVWriter writer;
    private final Set<TrackNode> writtenNodes = new HashSet<TrackNode>();
    private final Set<TrackConnection> writtenConnections = new HashSet<TrackConnection>();

    public TrackCoasterCSVWriter(TrackCoaster coaster, CSVWriter writer) {
        this.coaster = coaster;
        this.writer = writer;
    }

    /**
     * Writes a new chain of CSV entries starting iteration from a start node.
     * 
     * @param startNode
     * @param rootsOnly whether to only write out nodes with one or less neighbours
     */
    public void writeFrom(TrackNode startNode, boolean rootsOnly) throws IOException {
        // Check if start node already traversed. If so, don't do anything!
        if (this.writtenNodes.contains(startNode)) {
            return;
        }

        TrackNode previous = null;
        TrackCoasterCSVEntry entry = new TrackCoasterCSVEntry();
        while (true) {
            // Retrieve all connections not yet written out for this node
            List<TrackConnection> connections = new ArrayList<TrackConnection>(startNode.getConnections());
            for (int i = connections.size() - 1; i >= 0; i--) {
                if (this.writtenConnections.contains(connections.get(i))) {
                    connections.remove(i);
                }
            }

            // Disable rootsOnly flag when encountering a node with 1 or less connections
            if (rootsOnly) {
                if (connections.size() >= 2) {
                    // Deny.
                    break;
                } else {
                    rootsOnly = false;
                }
            }

            // If previous == null, this is a ROOT csv entry.
            // Otherwise, this is a NODE csv entry.
            this.writtenNodes.add(startNode);
            if (previous == null) {
                entry.setType(TrackCoasterCSVEntry.Type.ROOT);
            } else {
                entry.setType(TrackCoasterCSVEntry.Type.NODE);
            }
            entry.setPosition(startNode.getPosition());
            entry.setOrientation(startNode.getOrientation());
            entry.writeTo(this.writer);

            // Write a LINK entry for all connections to nodes that are not this coaster,
            // or link to nodes we have already written and risk being forgotten about.
            // These are nodes impossible to back-iterate from, so they must be done now!
            for (int i = connections.size() - 1; i >= 0; i--) {
                TrackConnection conn = connections.get(i);
                TrackNode node = conn.getOtherNode(startNode);
                if (node.getCoaster() != this.coaster || this.writtenNodes.contains(node)) {
                    this.writtenConnections.add(conn); // LINK connects the nodes
                    entry.setType(TrackCoasterCSVEntry.Type.LINK);
                    entry.setPosition(node.getPosition());
                    entry.setOrientation(node.getOrientation());
                    entry.writeTo(this.writer);
                    connections.remove(i);
                }
            }

            // If no connections exist, we can stop it here and now.
            if (connections.isEmpty()) {
                break;
            }

            // Simply pick the first connection all the time, and attempt chaining calls to it
            // TODO: We could be smarter here and choose the connection leading to the longest chain
            TrackConnection nextConn = connections.get(0);
            this.writtenConnections.add(nextConn);
            previous = startNode;
            startNode = nextConn.getOtherNode(previous);            
        }
    }
}
