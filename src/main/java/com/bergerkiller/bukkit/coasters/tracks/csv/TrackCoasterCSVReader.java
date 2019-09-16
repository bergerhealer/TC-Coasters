package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.opencsv.CSVReader;

/**
 * Helper class for building a coaster from a csv file
 */
public class TrackCoasterCSVReader {
    private final TrackCoaster coaster;
    private final CSVReader reader;
    private final StringArrayBuffer buffer;
    private final List<PendingLink> pendingLinks;
    private PlayerOrigin origin = null;

    public TrackCoasterCSVReader(TrackCoaster coaster, CSVReader reader) {
        this.coaster = coaster;
        this.reader = reader;
        this.buffer = new StringArrayBuffer();
        this.pendingLinks = new ArrayList<PendingLink>();
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

    public void read() throws IOException, SyntaxException {
        this.pendingLinks.clear();
        TrackNode prevNode = null;
        Matrix4x4 transform = null;

        // Read all the entries we can from the CSV reader
        TrackCoasterCSV.CSVEntry entry;
        while ((entry = TrackCoasterCSV.readNext(this.reader, this.buffer)) != null) {
            // Read the origin of the coaster from the csv
            // The first line that refers to an origin is used
            if (this.origin != null && transform == null && entry instanceof PlayerOriginHolder) {
                transform = ((PlayerOriginHolder) entry).getOrigin().getTransformTo(this.origin);
            }

            if (entry instanceof TrackCoasterCSV.BaseNodeEntry) {
                TrackCoasterCSV.BaseNodeEntry nodeEntry = (TrackCoasterCSV.BaseNodeEntry) entry;
                TrackNodeState state = nodeEntry.toState();
                if (transform != null) {
                    state = state.transform(transform);
                }

                if (nodeEntry instanceof TrackCoasterCSV.LinkNodeEntry) {
                    // Create a connection between the previous node and the node of this entry
                    this.pendingLinks.add(new PendingLink(prevNode, state.position));
                } else {
                    // Adding new nodes, where NODE connects to the previous node loaded
                    TrackNode node = this.coaster.createNewNode(state);
                    if (prevNode != null && !(nodeEntry instanceof TrackCoasterCSV.RootNodeEntry)) {
                        this.coaster.getTracks().connect(prevNode, node);
                    }
                    prevNode = node;
                }
            } else if (entry instanceof TrackCoasterCSV.NoLimits2Entry) {
                TrackCoasterCSV.NoLimits2Entry nle = (TrackCoasterCSV.NoLimits2Entry) entry;
                TrackNodeState state = nle.toState();
                if (transform != null) {
                    state = state.transform(transform);
                }

                TrackNode node = this.coaster.createNewNode(state);
                if (prevNode != null) {
                    this.coaster.getTracks().connect(prevNode, node);
                }
                prevNode = node;
            }
        }

        // Create all pending connections
        for (PendingLink link : this.pendingLinks) {
            TrackNode target = this.coaster.getTracks().findNodeExact(link.targetNodePos);
            if (target != null) {
                TrackConnection conn = this.coaster.getTracks().connect(link.node, target);
                link.node.pushBackJunction(conn); // Ensures preserved order of connections
                continue;
            }

            // Ignore: we may connect to it in the future.
            //this.coaster.getPlugin().getLogger().warning("Failed to create link from " +
            //    this.coaster.getName() + " " + link.node.getPosition() + " to " + link.targetNodePos);
        }
    }

    private static class PendingLink {
        public final TrackNode node;
        public final Vector targetNodePos;

        public PendingLink(TrackNode node, Vector targetNodePos) {
            this.node = node;
            this.targetNodePos = targetNodePos;
        }
    }
}
