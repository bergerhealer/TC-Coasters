package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.opencsv.CSVWriter;

/**
 * Helper class for turning a coaster into a CSV file
 */
public class TrackCoasterCSVWriter {
    private final TrackCoaster coaster;
    private final CSVWriter writer;
    private final StringArrayBuffer buffer = new StringArrayBuffer();
    private final Set<TrackNode> writtenNodes = new HashSet<TrackNode>();
    private final Set<TrackConnection> writtenConnections = new HashSet<TrackConnection>();

    public TrackCoasterCSVWriter(TrackCoaster coaster, CSVWriter writer) {
        this.coaster = coaster;
        this.writer = writer;
    }

    /**
     * Writes a new CSV Entry
     * 
     * @param entry to write
     */
    public void write(TrackCoasterCSV.CSVEntry entry) {
        this.buffer.clear();
        entry.write(this.buffer);
        this.writer.writeNext(this.buffer.toArray());
    }

    /**
     * Writes a new chain of CSV entries starting iteration from a start node.
     * 
     * @param startNode
     * @param rootsOnly whether to only write out nodes with one or less neighbours
     */
    public void writeFrom(TrackNode startNode, Mode mode) throws IOException {
        // Junctions only mode: verify there indeed are more than 2 connections, making it a junction
        if (mode == Mode.JUNCTIONS_ONLY) {
            List<TrackConnection> connections = startNode.getConnections();
            if (connections.size() <= 2) {
                return;
            }

            // Add and mark written. If already added, do nothing.
            if (!this.writtenNodes.add(startNode)) {
                return;
            }

            // Write out the ROOT node
            TrackCoasterCSV.RootNodeEntry root_entry = new TrackCoasterCSV.RootNodeEntry();
            root_entry.setFromNode(startNode);
            this.write(root_entry);

            // Write out all the links - in order
            for (TrackConnection conn : connections) {
                this.writeLink(conn.getOtherNode(startNode));
            }

            // Mark all connections as written
            this.writtenConnections.addAll(connections);

            // Stop
            return;
        }

        // Check if start node already traversed. If so, don't do anything!
        if (this.writtenNodes.contains(startNode)) {
            return;
        }

        boolean waitForRoot = (mode == Mode.ROOTS_ONLY);
        TrackNode previous = null;
        while (true) {
            // Retrieve all connections not yet written out for this node
            List<TrackConnection> connections = new ArrayList<TrackConnection>(startNode.getConnections());
            for (int i = connections.size() - 1; i >= 0; i--) {
                if (this.writtenConnections.contains(connections.get(i))) {
                    connections.remove(i);
                }
            }

            // Disable rootsOnly flag when encountering a node with 1 or less connections
            if (waitForRoot) {
                if (connections.size() >= 2) {
                    // Deny.
                    break;
                } else {
                    waitForRoot = false;
                }
            }

            // If previous == null, this is a ROOT csv entry.
            // Otherwise, this is a NODE csv entry.
            this.writtenNodes.add(startNode);
            TrackCoasterCSV.BaseNodeEntry node_entry;
            if (previous == null) {
                node_entry = new TrackCoasterCSV.RootNodeEntry();
            } else {
                node_entry = new TrackCoasterCSV.NodeEntry();
            }
            node_entry.setFromNode(startNode);
            this.write(node_entry);

            // Write a LINK entry for all connections to nodes that are not this coaster,
            // or link to nodes we have already written and risk being forgotten about.
            // These are nodes impossible to back-iterate from, so they must be done now!
            for (int i = connections.size() - 1; i >= 0; i--) {
                TrackConnection conn = connections.get(i);
                TrackNode node = conn.getOtherNode(startNode);
                if (node.getCoaster() != this.coaster || this.writtenNodes.contains(node)) {
                    this.writtenConnections.add(conn); // LINK connects the nodes
                    this.writeLink(node);
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

    private void writeLink(TrackNode node) throws IOException {
        TrackCoasterCSV.LinkNodeEntry link_entry = new TrackCoasterCSV.LinkNodeEntry();
        link_entry.pos = node.getPosition();
        link_entry.up = node.getOrientation();
        link_entry.rail = null;
        this.write(link_entry);
    }

    public static enum Mode {
        JUNCTIONS_ONLY,
        ROOTS_ONLY,
        NORMAL
    }
}
