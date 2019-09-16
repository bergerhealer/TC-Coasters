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
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.util.CSVSeparatorDetectorStream;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

/**
 * Helper class for building a coaster from a csv file
 */
public class TrackCoasterCSVReader implements AutoCloseable {
    private final CSVReader reader;
    private final TrackCoaster coaster;
    private final StringArrayBuffer buffer;
    private PlayerOrigin origin = null;

    public TrackCoasterCSVReader(InputStream inputStream, TrackCoaster coaster) throws IOException {
        CSVSeparatorDetectorStream detectorInput = new CSVSeparatorDetectorStream(inputStream);
        CSVParser csv_parser = (new CSVParserBuilder())
                .withSeparator(detectorInput.getSeparator())
                .withQuoteChar('"')
                .withEscapeChar('\\')
                .withIgnoreQuotations(false)
                .withIgnoreLeadingWhiteSpace(true)
                .build();
        this.reader = (new CSVReaderBuilder(new InputStreamReader(detectorInput, StandardCharsets.UTF_8)))
                .withCSVParser(csv_parser)
                .build();

        this.coaster = coaster;
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

    public void read() throws IOException, SyntaxException {
        List<PendingLink> pendingLinks = new ArrayList<PendingLink>();
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

            // LINK entries are added to the pendingLinks list and added later, in the same order
            if (entry instanceof TrackCoasterCSV.LinkNodeEntry) {
                Vector pos = ((TrackCoasterCSV.LinkNodeEntry) entry).pos;
                if (transform != null) {
                    pos = pos.clone();
                    transform.transformPoint(pos);
                }
                pendingLinks.add(new PendingLink(prevNode, pos));
                continue;
            }

            // ROOT and NODE entries are created, where NODE connects to the previous node (chain)
            if (entry instanceof TrackCoasterCSV.BaseNodeEntry) {
                TrackCoasterCSV.BaseNodeEntry nodeEntry = (TrackCoasterCSV.BaseNodeEntry) entry;
                TrackNodeState state = nodeEntry.toState();
                if (transform != null) {
                    state = state.transform(transform);
                }

                // Adding new nodes, where NODE connects to the previous node loaded
                TrackNode node = this.coaster.createNewNode(state);
                if (prevNode != null && !(nodeEntry instanceof TrackCoasterCSV.RootNodeEntry)) {
                    this.coaster.getTracks().connect(prevNode, node);
                }
                prevNode = node;
                continue;
            }

            // Special support for NoLimits2 CSV format
            // TODO: Does the first node connect to the last node?
            if (entry instanceof TrackCoasterCSV.NoLimits2Entry) {
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
                continue;
            }
        }

        // Create all pending connections
        for (PendingLink link : pendingLinks) {
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
