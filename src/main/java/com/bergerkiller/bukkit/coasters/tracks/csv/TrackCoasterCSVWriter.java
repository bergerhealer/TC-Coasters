package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.opencsv.CSVWriter;

/**
 * Writes a list of TrackNodes to a CSVWriter, automatically traversing the chains of connections
 * and writing those as well.
 */
public class TrackCoasterCSVWriter implements AutoCloseable {
    private final CSVWriter writer;
    private final StringArrayBuffer buffer = new StringArrayBuffer();
    private final Set<TrackNode> pendingNodes = new HashSet<TrackNode>();
    private final Set<TrackNode> writtenNodes = new HashSet<TrackNode>();
    private final Set<TrackConnection> writtenConnections = new HashSet<TrackConnection>();
    private final List<TrackConnection> currConnections = new ArrayList<TrackConnection>();
    private boolean writeLinksToForeignNodes = true;

    public TrackCoasterCSVWriter(OutputStream outputStream) {
        this.writer = new CSVWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    /**
     * Sets whether links from nodes being written to nodes that are not being
     * written are included.
     * 
     * @param write option
     */
    public void setWriteLinksToForeignNodes(boolean write) {
        this.writeLinksToForeignNodes = write;
    }

    @Override
    public void close() throws IOException {
        this.writer.close();
    }

    /**
     * Writes all nodes specified, and their connections, to the CSV Output Stream.
     * Nodes that have already been written are not written a second time.
     * 
     * @param nodes to write
     * @throws IOException
     */
    public void writeAll(Collection<TrackNode> nodes) throws IOException {
        // Add all nodes to write to the pending nodes set
        // Track the new nodes in the writtenNodes set for future calls
        this.pendingNodes.addAll(nodes);
        this.pendingNodes.removeAll(this.writtenNodes);
        this.writtenNodes.addAll(this.pendingNodes);

        // Go by all junctions and write their state out first
        // This preserves switching direction: the first 2 connections are selected
        for (TrackNode node : nodes) {
            writeFrom(node, TrackCoasterCSVWriter.Mode.JUNCTIONS_ONLY);
        }

        // Go by all nodes and first save the chain from all nodes with one or less neighbours.
        // These are the end nodes of a chain of nodes, and are almost always a valid start of a new chain.
        for (TrackNode node : nodes) {
            writeFrom(node, TrackCoasterCSVWriter.Mode.ROOTS_ONLY);
        }

        // Clean up any remaining unwritten nodes, such as nodes in the middle of a chain
        for (TrackNode node : nodes) {
            writeFrom(node, TrackCoasterCSVWriter.Mode.NORMAL);
        }
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
        TrackNode previous = null;
        while (true) {
            this.currConnections.clear();
            if (mode == Mode.JUNCTIONS_ONLY) {
                // Junctions only mode: if 2 or less connections, not a junction, so skip that node
                // We do not skip connections that have already been written, because the order of the
                // connections is important. This stores the state of how the junction is switched.
                if (startNode.getConnections().size() <= 2) {
                    break;
                }
                if (this.writeLinksToForeignNodes) {
                    this.currConnections.addAll(startNode.getConnections());
                } else {
                    for (TrackConnection connection : startNode.getConnections()) {
                        if (this.writtenNodes.contains(connection.getOtherNode(startNode))) {
                            this.currConnections.add(connection);
                        }
                    }
                    if (this.currConnections.size() <= 2) {
                        break;
                    }
                }
            } else {
                // Find connections we have not yet written out
                for (TrackConnection connection : startNode.getConnections()) {
                    if (!this.writeLinksToForeignNodes && !this.writtenNodes.contains(connection.getOtherNode(startNode))) {
                        continue;
                    }
                    if (!this.writtenConnections.contains(connection)) {
                        this.currConnections.add(connection);
                    }
                }

                // Roots only mode: only start writing a chain of nodes with 1 or 0 remaining connections
                if (mode == Mode.ROOTS_ONLY) {
                    if (this.currConnections.size() >= 2) {
                        break;
                    }
                    mode = Mode.NORMAL;
                }
            }

            // We are going to write a new node, so remove the node from the pending nodes list
            // If it was already removed, then it has already been written, abort!
            if (!this.pendingNodes.remove(startNode)) {
                break;
            }

            // If previous == null, this is a ROOT csv entry.
            // Otherwise, this is a NODE csv entry.
            // The NODE csv entry connects the previously written node to this new one
            TrackCoasterCSV.BaseNodeEntry node_entry;
            if (previous == null) {
                node_entry = new TrackCoasterCSV.RootNodeEntry();
            } else {
                node_entry = new TrackCoasterCSV.NodeEntry();
            }
            node_entry.setFromNode(startNode);
            this.write(node_entry);

            // In junctions only mode, we write out all connections in order
            // After that, we stop.
            if (mode == Mode.JUNCTIONS_ONLY) {
                // Write out all the connections - in order
                for (TrackConnection conn : this.currConnections) {
                    this.writeLink(conn.getOtherNode(startNode));
                }

                // Mark all connections as written
                this.writtenConnections.addAll(this.currConnections);

                // Stop
                break;
            }

            // Write a LINK entry for all connections to nodes that do not exist in the node list,
            // or link to nodes we have already written out and otherwise risk being forgotten about.
            for (int i = this.currConnections.size() - 1; i >= 0; i--) {
                TrackConnection conn = this.currConnections.get(i);
                TrackNode node = conn.getOtherNode(startNode);
                if (!this.pendingNodes.contains(node)) {
                    this.writtenConnections.add(conn); // LINK connects the nodes
                    this.writeLink(node);
                    this.currConnections.remove(i);
                }
            }

            // If no more connections exist, this is the end of the chain.
            if (this.currConnections.isEmpty()) {
                break;
            }

            // Simply pick the first connection all the time, and attempt chaining calls to it
            // TODO: We could be smarter here and choose the connection leading to the longest chain
            TrackConnection nextConn = this.currConnections.get(0);
            this.writtenConnections.add(nextConn);
            previous = startNode;
            startNode = nextConn.getOtherNode(previous);            
        }
    }

    private void writeLink(TrackNode node) throws IOException {
        TrackCoasterCSV.LinkNodeEntry link_entry = new TrackCoasterCSV.LinkNodeEntry();
        link_entry.pos = node.getPosition();
        this.write(link_entry);
    }

    public static enum Mode {
        JUNCTIONS_ONLY,
        ROOTS_ONLY,
        NORMAL
    }
}
