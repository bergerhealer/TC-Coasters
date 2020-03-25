package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.LockCoasterEntry;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.PendingLink;
import com.bergerkiller.bukkit.coasters.util.CSVFormatDetectorStream;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

/**
 * Helper class for building a coaster from a csv file
 */
public class TrackCoasterCSVReader implements AutoCloseable {
    private final CSVReader reader;
    private final StringArrayBuffer buffer;
    private PlayerOrigin origin = null;

    public TrackCoasterCSVReader(InputStream inputStream) throws IOException {
        CSVFormatDetectorStream detectorInput = new CSVFormatDetectorStream(inputStream);
        detectorInput.detect();

        CSVParser csv_parser = (new CSVParserBuilder())
                .withSeparator(detectorInput.getSeparator())
                .withQuoteChar('"')
                .withEscapeChar('\\')
                .withIgnoreQuotations(detectorInput.getIgnoreQuotes())
                .withIgnoreLeadingWhiteSpace(true)
                .build();
        this.reader = (new CSVReaderBuilder(new InputStreamReader(detectorInput, StandardCharsets.UTF_8)))
                .withCSVParser(csv_parser)
                .build();

        this.buffer = new StringArrayBuffer();
    }

    @Override
    public void close() throws IOException {
        this.reader.close();
    }

    /**
     * Sets the origin relative to which to place all the nodes.
     * If the input csv data contains an origin, this will be used to place the coaster
     * relative to the player. If this is not the case, the first node's position and a
     * default orientation is used instead.
     * 
     * @param origin to set to
     */
    public void setOrigin(PlayerOrigin origin) {
        this.origin = origin;
    }

    /**
     * Reads the next entry in the CSV file, decoding it into a CSVEntry.
     * If the end of the file was reached, null is returned.
     * 
     * @return entry
     * @throws IOException
     * @throws SyntaxException
     */
    public TrackCoasterCSV.CSVEntry readNextEntry() throws IOException, SyntaxException {
        return TrackCoasterCSV.readNext(this.reader, this.buffer);
    }

    /**
     * Reads the full CSV file and creates the coaster described in it
     * 
     * @param coaster to fill with nodes and connections
     * @throws IOException
     * @throws SyntaxException
     */
    public void create(TrackCoaster coaster) throws IOException, SyntaxException {
        TrackCoasterCSV.CSVReaderState state = new TrackCoasterCSV.CSVReaderState();
        state.origin = this.origin;
        state.coaster = coaster;
        state.world = coaster.getWorld();

        // By default not locked
        state.coaster.setLocked(false);

        // Read all the entries we can from the CSV reader
        TrackCoasterCSV.CSVEntry entry;
        while ((entry = readNextEntry()) != null) {
            // Read the origin of the coaster from the csv
            // The first line that refers to an origin is used
            if (this.origin != null && state.transform == null && entry instanceof PlayerOriginHolder) {
                state.transform = ((PlayerOriginHolder) entry).getOrigin().getTransformTo(this.origin);
            }

            // LINK entries are added to the pendingLinks list and added later, in the same order
            if (entry instanceof TrackCoasterCSV.LinkNodeEntry) {
                Vector pos = ((TrackCoasterCSV.LinkNodeEntry) entry).pos;
                if (state.transform != null) {
                    pos = pos.clone();
                    state.transform.transformPoint(pos);
                }
                PendingLink link = new PendingLink(state.prevNode, pos);
                state.pendingLinks.add(link);
                state.prevNode_pendingLinks.add(pos);
                if (state.prevNode_hasDefaultAnimationLinks && state.prevNode.hasAnimationStates()) {
                    addConnectionToAnimationStates(state.prevNode, new TrackNodeReference(state.world.getTracks(), pos));
                }
                continue;
            }

            // ANIM entries are animation states that refer to the previously written node
            // When no ANIMLINK entries are specified, defaults to the connections already made
            if (entry instanceof TrackCoasterCSV.AnimationStateNodeEntry) {
                TrackCoasterCSV.AnimationStateNodeEntry animEntry = (TrackCoasterCSV.AnimationStateNodeEntry) entry;
                if (state.prevNode != null) {
                    TrackNodeReference[] connections = new TrackNodeReference[state.prevNode_pendingLinks.size()];
                    for (int i = 0; i < connections.length; i++) {
                        connections[i] = new TrackNodeReference(state.world.getTracks(), state.prevNode_pendingLinks.get(i));
                    }
                    state.prevNode.setAnimationState(animEntry.name, animEntry.createState(), connections);
                }
                continue;
            }

            // ANIMLINK entries specify the connections that exist at a particular animation node state
            if (entry instanceof TrackCoasterCSV.AnimationStateLinkNodeEntry) {
                TrackCoasterCSV.AnimationStateLinkNodeEntry animLinkEntry = (TrackCoasterCSV.AnimationStateLinkNodeEntry) entry;
                if (state.prevNode != null) {
                    // So far we have added default links. This is now invalid!
                    // Wipe all previously stored connections in all nodes
                    if (state.prevNode_hasDefaultAnimationLinks) {
                        state.prevNode_hasDefaultAnimationLinks = false;
                        List<TrackNodeAnimationState> states = new ArrayList<>(state.prevNode.getAnimationStates());
                        for (TrackNodeAnimationState oldState : states) {
                            if (oldState.connections.length != 0) {
                                state.prevNode.setAnimationState(oldState.name, oldState.state, new TrackNodeReference[0]);
                            }
                        }
                    }

                    // Add an extra connection to the last added animation state
                    List<TrackNodeAnimationState> states = state.prevNode.getAnimationStates();
                    if (!states.isEmpty()) {
                        TrackNodeAnimationState lastAddedState = states.get(states.size()-1);
                        TrackNodeReference new_link = new TrackNodeReference(state.world.getTracks(), animLinkEntry.pos);
                        TrackNodeReference[] new_connections = LogicUtil.appendArray(lastAddedState.connections, new_link);
                        state.prevNode.setAnimationState(lastAddedState.name, lastAddedState.state, new_connections);
                    }
                }
            }

            // ROOT entry starts a new chain, and is not connected to the previous entry
            if (entry instanceof TrackCoasterCSV.RootNodeEntry) {
                TrackCoasterCSV.BaseNodeEntry nodeEntry = (TrackCoasterCSV.BaseNodeEntry) entry;
                TrackNodeState nodeState = nodeEntry.toState();
                if (state.transform != null) {
                    nodeState = nodeState.transform(state.transform);
                }

                // Reset
                state.prevNode_pendingLinks.clear();

                // Adding new nodes, where NODE connects to the previous node loaded
                TrackNode node = state.coaster.createNewNode(nodeState);
                state.prevNode = node;
                state.prevNode_hasDefaultAnimationLinks = true;
                continue;
            }

            // NODE entry adds a new node to the chain, connected to the previous
            if (entry instanceof TrackCoasterCSV.NodeEntry) {
                TrackCoasterCSV.BaseNodeEntry nodeEntry = (TrackCoasterCSV.BaseNodeEntry) entry;
                TrackNodeState nodeState = nodeEntry.toState();
                if (state.transform != null) {
                    nodeState = nodeState.transform(state.transform);
                }

                // Reset
                state.prevNode_pendingLinks.clear();

                // Adding new nodes, where NODE connects to the previous node loaded
                TrackNode node = state.coaster.createNewNode(nodeState);
                if (state.prevNode != null) {
                    state.world.getTracks().connect(state.prevNode, node);
                    if (state.prevNode_hasDefaultAnimationLinks && state.prevNode.hasAnimationStates()) {
                        addConnectionToAnimationStates(state.prevNode, new TrackNodeReference(node));
                    }
                    state.prevNode_pendingLinks.add(state.prevNode.getPosition());
                }
                state.prevNode = node;
                state.prevNode_hasDefaultAnimationLinks = true;
                continue;
            }

            // Special support for NoLimits2 CSV format
            // TODO: Does the first node connect to the last node?
            if (entry instanceof TrackCoasterCSV.NoLimits2Entry) {
                TrackCoasterCSV.NoLimits2Entry nle = (TrackCoasterCSV.NoLimits2Entry) entry;
                TrackNodeState nodeState = nle.toState();
                if (state.transform != null) {
                    nodeState = nodeState.transform(state.transform);
                }

                // Reset
                state.prevNode_pendingLinks.clear();

                TrackNode node = state.coaster.createNewNode(nodeState);
                if (state.prevNode != null) {
                    state.world.getTracks().connect(state.prevNode, node);
                    if (state.prevNode_hasDefaultAnimationLinks && state.prevNode.hasAnimationStates()) {
                        addConnectionToAnimationStates(state.prevNode, new TrackNodeReference(node));
                    }
                    state.prevNode_pendingLinks.add(state.prevNode.getPosition());
                }
                state.prevNode = node;
                state.prevNode_hasDefaultAnimationLinks = true;
                continue;
            }

            // Locks the coaster
            if (entry instanceof LockCoasterEntry) {
                state.coaster.setLocked(true);
                continue;
            }
        }

        // Go by all created nodes and initialize animation state connections up-front
        state.coaster.refreshConnections();

        // Create all pending connections
        for (PendingLink link : state.pendingLinks) {
            TrackNode target = state.coaster.findNodeExact(link.targetNodePos);
            if (target == null) {
                target = state.world.getTracks().findNodeExact(link.targetNodePos);
            }
            if (target != null) {
                TrackConnection conn = state.world.getTracks().connect(link.node, target);
                link.node.pushBackJunction(conn); // Ensures preserved order of connections
                continue;
            }

            // Ignore: we may connect to it in the future.
            //this.coaster.getPlugin().getLogger().warning("Failed to create link from " +
            //    this.coaster.getName() + " " + link.node.getPosition() + " to " + link.targetNodePos);
        }
    }

    private void addConnectionToAnimationStates(TrackNode node, TrackNodeReference reference) {
        for (TrackNodeAnimationState state : new ArrayList<>(node.getAnimationStates())) {
            TrackNodeReference[] new_connections = LogicUtil.appendArray(state.connections, reference);
            node.setAnimationState(state.name, state.state, new_connections);
        }
    }
}
