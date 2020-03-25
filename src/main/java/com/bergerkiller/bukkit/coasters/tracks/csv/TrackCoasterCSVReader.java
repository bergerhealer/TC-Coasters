package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.PendingLink;
import com.bergerkiller.bukkit.coasters.util.CSVFormatDetectorStream;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
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

            // Let the entry refresh the reader state
            entry.processReader(state);
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

}
