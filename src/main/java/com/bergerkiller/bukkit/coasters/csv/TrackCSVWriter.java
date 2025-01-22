package com.bergerkiller.bukkit.coasters.csv;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.bergerkiller.bukkit.coasters.csv.TrackCSV.SignEntry;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectHolder;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.TrailingNewLineTrimmingWriter;
import com.bergerkiller.mountiplex.reflection.util.UniqueHash;
import com.opencsv.CSVWriter;

/**
 * Writes a list of TrackNodes to a CSVWriter, automatically traversing the chains of connections
 * and writing those as well.
 */
public class TrackCSVWriter implements AutoCloseable {
    private final ThrowingCSVWriter writer;
    private final StringArrayBuffer buffer = new StringArrayBuffer();
    private final Set<TrackNode> pendingNodes = new HashSet<TrackNode>();
    private final Set<TrackNode> writtenNodes = new HashSet<TrackNode>();
    private final Set<TrackConnection> writtenConnections = new HashSet<TrackConnection>();
    private final List<TrackConnection> currConnections = new ArrayList<TrackConnection>();
    private final Map<TrackObjectType<?>, String> writtenTrackObjectTypes = new HashMap<TrackObjectType<?>, String>();
    private final UniqueHash writtenItemNameHash = new UniqueHash();
    private final AtomicInteger adjustCounter = new AtomicInteger(0);
    private boolean writeLinksToForeignNodes = true;
    private boolean writeSignKeys = false;

    public TrackCSVWriter(OutputStream outputStream) {
        this(outputStream, ',');
    }

    public TrackCSVWriter(OutputStream outputStream, char separator) {
        java.io.Writer writer = new TrailingNewLineTrimmingWriter(new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        char quotechar = '"';
        char escapechar = '\\';
        String lineEnd = "\r\n";
        this.writer = new ThrowingCSVWriter(writer, separator, quotechar, escapechar, lineEnd);
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

    /**
     * Sets whether to write out the UUID keys of all signs used in the coaster.
     * By default, only writes them out when they have animation states where
     * it's important to preserve sign identities.
     * Should be set when saving coasters persistently to disk.
     *
     * @param write Whether to write all sign keys
     */
    public void setWriteSignKeys(boolean write) {
        this.writeSignKeys = write;
    }

    @Override
    public void close() throws IOException {
        this.writer.close();
    }

    /**
     * Writes all nodes specified to the CSV Output Stream in NoLimits2's CSV format.
     * Junctions cannot be represented in this format. For this reason, the longest chain
     * is selected in the source. Writes positions absolute.
     *
     * @param nodes to write
     * @throws IOException
     */
    public void writeAllNoLimits2(Collection<TrackNode> nodes) throws IOException {
        writeAllNoLimits2(nodes, null);
    }

    /**
     * Writes all nodes specified to the CSV Output Stream in NoLimits2's CSV format.
     * Junctions cannot be represented in this format. For this reason, the longest chain
     * is selected in the source.
     * 
     * @param nodes to write
     * @param origin Player origin relative to which to transform the positions. Null for absolute.
     * @throws IOException
     */
    public void writeAllNoLimits2(Collection<TrackNode> nodes, PlayerOrigin origin) throws IOException {
        // Write header
        this.writer.writeNextThrow(new String[] {"No.","PosX","PosY","PosZ","FrontX","FrontY","FrontZ","LeftX","LeftY","LeftZ","UpX","UpY","UpZ"}, true);

        // Set number format to use for all numbers written out
        // NoLimits2 uses 6-decimal precision, which are always printed out (even if .000000)
        this.buffer.setNumberFormat("0.000000");

        // Find the longest chain of nodes we can write out
        // This 'cuts off' junctions that lead to short ends
        // We cannot represent such junctions in this format anyway
        NodeChain chain = findLongestChain(nodes);
        if (chain == null) {
            return;
        }
        TrackNode node = chain.startNode;

        // Helper for transforming positions into entries for writing
        TrackCSV.NoLimits2Entry.Transformer nl2Transformer = new TrackCSV.NoLimits2Entry.Transformer(origin);

        // If there are no links we write only one node, and there's no real way to figure out the orientation
        if (chain.links.isEmpty()) {
            this.write(nl2Transformer.next(node.getPosition(), node.getDirection(), node.getOrientation()));
        } else {
            // Go from node to node using the chain links until we reach the end
            TrackNode current = node;
            for (TrackConnection link : chain.links) {
                TrackNode next = link.getOtherNode(current);
                this.write(nl2Transformer.next(current.getPosition(), current.getDirectionTo(next), current.getOrientation()));
                current = next;
            }

            // The last node's direction must be done using getDirectionFrom() instead
            {
                TrackNode previous = chain.links.get(chain.links.size() - 1).getOtherNode(current);
                this.write(nl2Transformer.next(current.getPosition(), current.getDirectionFrom(previous), current.getOrientation()));
            }
        }
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
            writeFrom(node, TrackCSVWriter.Mode.JUNCTIONS_ONLY);
        }

        // Go by all nodes and first save the chain from all nodes with one or less neighbours.
        // These are the end nodes of a chain of nodes, and are almost always a valid start of a new chain.
        for (TrackNode node : nodes) {
            writeFrom(node, TrackCSVWriter.Mode.ROOTS_ONLY);
        }

        // Clean up any remaining unwritten nodes, such as nodes in the middle of a chain
        for (TrackNode node : nodes) {
            writeFrom(node, TrackCSVWriter.Mode.NORMAL);
        }
    }

    /**
     * Writes a new CSV Entry
     * 
     * @param entry to write
     */
    public void write(TrackCSV.CSVEntry entry) throws IOException {
        this.buffer.clear();
        entry.write(this.buffer);
        this.writer.writeNextThrow(this.buffer.toArray(), entry.applyQuotes());
    }

    /**
     * Writes a new chain of CSV entries starting iteration from a start node.
     * 
     * @param startNode Node to start a new chain from
     * @param mode Mode of operation when writing out the nodes
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
            TrackCSV.BaseNodeEntry node_entry;
            if (previous == null) {
                node_entry = new TrackCSV.RootNodeEntry();
            } else {
                node_entry = new TrackCSV.NodeEntry();
            }
            node_entry.setFromNode(startNode);
            this.write(node_entry);
            this.writeAllSigns(startNode.getSigns(), writeSignKeys || startNode.hasAnimationStates());

            // If any exist, add animation node state entries
            boolean saveConnections = startNode.doAnimationStatesChangeConnections();
            for (TrackNodeAnimationState animState : startNode.getAnimationStates()) {
                TrackCSV.AnimationStateNodeEntry anim_entry = new TrackCSV.AnimationStateNodeEntry();
                anim_entry.name = animState.name;
                anim_entry.setFromState(animState.state);
                this.write(anim_entry);
                this.writeAllSigns(animState.state.signs, true);
                if (saveConnections) {
                    for (TrackConnectionState ref : animState.connections) {
                        this.writeAllObjects(ref);

                        TrackCSV.AnimationStateLinkNodeEntry anim_link_entry = new TrackCSV.AnimationStateLinkNodeEntry();
                        if (ref.node_a.isReference(startNode)) {
                            anim_link_entry.pos = ref.node_b.getPosition();
                            anim_link_entry.objectsFlipped = false;
                        } else {
                            anim_link_entry.pos = ref.node_a.getPosition();
                            anim_link_entry.objectsFlipped = true;
                        }
                        this.write(anim_link_entry);
                    }
                }
            }

            // In junctions only mode, we write out all connections in order
            // After that, we stop.
            if (mode == Mode.JUNCTIONS_ONLY) {
                // Write out all the connections - in order
                for (TrackConnection conn : this.currConnections) {
                    // If startNode is not nodeA of the connection, and objects are added,
                    // the ends must be swapped to correct for this. Otherwise the distance written
                    // is incorrect.
                    if (conn.hasObjects() && conn.getNodeA() != startNode) {
                        conn.swapEnds();
                    }

                    this.writeAllObjects(conn);
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
                    // If startNode is not nodeA of the connection, and objects are added,
                    // the ends must be swapped to correct for this. Otherwise the distance written
                    // is incorrect.
                    if (conn.hasObjects() && conn.getNodeA() != startNode) {
                        conn.swapEnds();
                    }

                    this.writtenConnections.add(conn); // LINK connects the nodes
                    this.writeAllObjects(conn);
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

            // If startNode is not nodeA of the connection, and objects are added,
            // the ends must be swapped to correct for this. Otherwise the distance written
            // is incorrect.
            if (nextConn.hasObjects() && nextConn.getNodeA() != startNode) {
                nextConn.swapEnds();
            }

            this.writtenConnections.add(nextConn);
            this.writeAllObjects(nextConn);
            previous = startNode;
            startNode = nextConn.getOtherNode(previous);            
        }
    }

    private void writeLink(TrackNode node) throws IOException {
        TrackCSV.LinkNodeEntry link_entry = new TrackCSV.LinkNodeEntry();
        link_entry.pos = node.getPosition();
        this.write(link_entry);
    }

    private void writeAllObjects(TrackObjectHolder trackObjectHolder) throws IOException {
        for (TrackObject object : trackObjectHolder.getObjects()) {
            TrackCSV.ObjectEntry object_entry = new TrackCSV.ObjectEntry();
            object_entry.distance = object.getDistance();
            object_entry.flipped = object.isFlipped();
            object_entry.name = writeTrackObjectType(object.getType());
            this.write(object_entry);
        }
    }

    private void writeAllSigns(TrackNodeSign[] signs, boolean writeKeys) throws IOException {
        for (TrackNodeSign sign : signs) {
            SignEntry entry = new SignEntry();
            entry.sign = sign;
            entry.writeKeys = writeKeys;
            this.write(entry);
        }
    }

    private <T extends TrackObjectType<?>> String writeTrackObjectType(T type) throws IOException {
        if (type == null) {
            return "";
        }
        String name = this.writtenTrackObjectTypes.get(type);
        if (name == null) {
            if (type.getTransform() != null) {
                // If a transform was set, make sure to save the transform-less one first
                // Then write an 'adjust' entry with just the transformation matrix info
                TrackCSV.AdjustTrackObjectTypeEntry entry = new TrackCSV.AdjustTrackObjectTypeEntry();
                entry.objectName = writeTrackObjectType(type.setTransform(null));
                entry.name = name = entry.objectName + "_adj" + adjustCounter.incrementAndGet();
                entry.transform = type.getTransform();
                this.write(entry);
            } else {
                // Generate a name not already used
                name = this.writtenItemNameHash.nextHex() + "_" + type.generateName();

                // Write the type out
                TrackCSV.CSVEntry entry = TrackCSV.createTrackObjectTypeEntry(name, type);
                if (entry != null) {
                    this.write(entry);
                }
            }
            this.writtenTrackObjectTypes.put(type, name);
        }
        return name;
    }

    public static enum Mode {
        JUNCTIONS_ONLY,
        ROOTS_ONLY,
        NORMAL
    }

    private static NodeChain findLongestChain(Collection<TrackNode> nodes) {
        NodeChain longestChain = null;
        for (TrackNode possible : nodes) {
            if (longestChain == null) {
                longestChain = new NodeChain(possible, nodes);
                longestChain = findLongestChain(longestChain);
            } else if (possible.getConnections().size() != 2 || !longestChain.remaining.contains(possible)) {
                NodeChain possibleChain = new NodeChain(possible, nodes);
                possibleChain = findLongestChain(possibleChain);
                if (possibleChain.links.size() > longestChain.links.size()) {
                    longestChain = possibleChain;
                }
            }
        }
        return longestChain;
    }

    private static NodeChain findLongestChain(NodeChain start) {
        ArrayList<TrackConnection> nextNodeOptions = new ArrayList<TrackConnection>();

        // Walk as many nodes as we can
        while (true) {
            nextNodeOptions.clear();
            for (TrackConnection connection : start.lastNode.getConnections()) {
                TrackNode nextNode = connection.getOtherNode(start.lastNode);
                if (start.remaining.contains(nextNode)) {
                    nextNodeOptions.add(connection);
                }
            }
            if (nextNodeOptions.size() == 1) {
                start.add(nextNodeOptions.get(0));
            } else {
                break;
            }
        }

        // Compress
        start.links.trimToSize();

        // Walk again for all remaining options
        NodeChain result = start;
        for (TrackConnection nextNodeConnection : nextNodeOptions) {
            NodeChain nextChain = new NodeChain(start);
            nextChain.add(nextNodeConnection);
            nextChain = findLongestChain(nextChain);
            if (nextChain.links.size() > result.links.size()) {
                result = nextChain;
            }
        }

        // Return result with longest chain
        return result;
    }

    private static class NodeChain {
        public final TrackNode startNode;
        public TrackNode lastNode;
        public final HashSet<TrackNode> remaining;
        public final ArrayList<TrackConnection> links;

        public NodeChain(TrackNode startNode, Collection<TrackNode> nodes) {
            this.startNode = startNode;
            this.lastNode = startNode;
            this.remaining = new HashSet<TrackNode>(nodes);
            this.remaining.remove(startNode);
            this.links = new ArrayList<TrackConnection>();
        }

        public NodeChain(NodeChain source) {
            this.startNode = source.startNode;
            this.lastNode = source.lastNode;
            this.remaining = new HashSet<TrackNode>(source.remaining);
            this.links = new ArrayList<TrackConnection>(source.links);
        }

        public void add(TrackConnection connection) {
            this.lastNode = connection.getOtherNode(this.lastNode);
            this.remaining.remove(this.lastNode);
            this.links.add(connection);
        }
    }

    // Extension that adds a writeNext that throws the IOException, rather than swallowing it silently
    private static class ThrowingCSVWriter extends CSVWriter {

        public ThrowingCSVWriter(Writer writer, char separator, char quotechar, char escapechar, String lineEnd) {
            super(writer, separator, quotechar, escapechar, lineEnd);
        }

        public void writeNextThrow(String[] nextLine, boolean applyQuotesToAll) throws IOException {
            super.writeNext(nextLine, applyQuotesToAll, new StringBuilder(INITIAL_STRING_SIZE));
        }
    }
}
