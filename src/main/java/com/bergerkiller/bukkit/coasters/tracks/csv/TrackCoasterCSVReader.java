package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.opencsv.CSVReader;

/**
 * Helper class for building a coaster from a csv file
 */
public class TrackCoasterCSVReader {
    private final TrackCoaster coaster;
    private final CSVReader reader;
    private final StringArrayBuffer buffer;
    private final List<PendingLink> pendingLinks;

    public TrackCoasterCSVReader(TrackCoaster coaster, CSVReader reader) {
        this.coaster = coaster;
        this.reader = reader;
        this.buffer = new StringArrayBuffer();
        this.pendingLinks = new ArrayList<PendingLink>();
    }

    public void read() throws IOException, TrackCoasterCSV.EntrySyntaxException {
        this.pendingLinks.clear();
        TrackNode prevNode = null;
        TrackCoasterCSV.CSVEntry entry;
        while ((entry = TrackCoasterCSV.readNext(this.reader, this.buffer)) != null) {
            if (entry instanceof TrackCoasterCSV.BaseNodeEntry) {
                TrackCoasterCSV.BaseNodeEntry nodeEntry = (TrackCoasterCSV.BaseNodeEntry) entry;
                if (nodeEntry instanceof TrackCoasterCSV.LinkNodeEntry) {
                    // Create a connection between the previous node and the node of this entry
                    this.pendingLinks.add(new PendingLink(prevNode, nodeEntry.pos));
                } else {
                    // Adding new nodes, where NODE connects to the previous node loaded
                    TrackNode node = this.coaster.createNewNode(nodeEntry.pos, nodeEntry.up);
                    node.setRailBlock(nodeEntry.rail);
                    if (prevNode != null && !(nodeEntry instanceof TrackCoasterCSV.RootNodeEntry)) {
                        this.coaster.getTracks().connect(prevNode, node);
                    }
                    prevNode = node;
                }
            } else if (entry instanceof TrackCoasterCSV.NoLimits2Entry) {
                TrackCoasterCSV.NoLimits2Entry nle = (TrackCoasterCSV.NoLimits2Entry) entry;
                TrackNode node = this.coaster.createNewNode(nle.pos, nle.up);
                if (prevNode != null) {
                    this.coaster.getTracks().connect(prevNode, node);
                }
                prevNode = node;
            }
        }
    }

    public void createPendingLinks() {
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
