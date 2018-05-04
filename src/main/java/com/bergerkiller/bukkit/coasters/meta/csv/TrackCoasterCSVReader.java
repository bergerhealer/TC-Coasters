package com.bergerkiller.bukkit.coasters.meta.csv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.meta.TrackCoaster;
import com.bergerkiller.bukkit.coasters.meta.TrackNode;
import com.opencsv.CSVReader;

/**
 * Helper class for building a coaster from a csv file
 */
public class TrackCoasterCSVReader {
    private final TrackCoaster coaster;
    private final CSVReader reader;
    private final List<PendingLink> pendingLinks = new ArrayList<PendingLink>();

    public TrackCoasterCSVReader(TrackCoaster coaster, CSVReader reader) {
        this.coaster = coaster;
        this.reader = reader;
    }

    public void read() throws IOException {
        this.pendingLinks.clear();
        TrackNode prevNode = null;
        TrackCoasterCSVEntry entry = new TrackCoasterCSVEntry();
        while (entry.readFrom(reader)) {
            TrackCoasterCSVEntry.Type type = entry.getType();
            Vector position = entry.getPosition();
            if (position == null) {
                continue; // failure to read position, entry is useless now...
            }
            if (type == TrackCoasterCSVEntry.Type.ROOT || type == TrackCoasterCSVEntry.Type.NODE) {
                // Adding new nodes, where NODE connects to the previous node loaded
                Vector orientation = entry.getOrientation();
                TrackNode node = this.coaster.createNewNode(position, orientation);
                if (prevNode != null && type == TrackCoasterCSVEntry.Type.NODE) {
                    this.coaster.getTracks().connect(prevNode, node);
                }
                prevNode = node;
            } else if (type == TrackCoasterCSVEntry.Type.LINK && prevNode != null) {
                // Create a connection between the previous node and the node of this entry
                this.pendingLinks.add(new PendingLink(prevNode, position));
            }
        }
    }

    public void createPendingLinks() {
        for (PendingLink link : this.pendingLinks) {
            TrackNode target = this.coaster.getTracks().findNodeExact(link.targetNodePos);
            if (target != null) {
                this.coaster.getTracks().connect(link.node, target);
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
