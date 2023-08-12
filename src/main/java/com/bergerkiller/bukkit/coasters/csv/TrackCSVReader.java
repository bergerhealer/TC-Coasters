package com.bergerkiller.bukkit.coasters.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.util.CSVFormatDetectorStream;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.bukkit.entity.Player;

/**
 * Helper class for building a coaster from a csv file
 */
public class TrackCSVReader implements AutoCloseable {
    private final CSVReader reader;
    private final StringArrayBuffer buffer;
    private PlayerOrigin origin = null;

    public TrackCSVReader(InputStream inputStream) throws IOException {
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
    public TrackCSV.CSVEntry readNextEntry() throws IOException, SyntaxException {
        return TrackCSV.readNext(this.reader, this.buffer);
    }

    /**
     * Reads the full CSV file and creates the coaster described in it. Fires all the
     * appropriate events for building this coaster as the Player specified, checking
     * for build permissions of the nodes, connections and node signs.
     *
     * @param coaster Coaster to fill with nodes and connections
     * @param player Player that is creating (importing) the coaster
     * @throws IOException
     * @throws SyntaxException
     * @throws ChangeCancelledException
     */
    public void create(TrackCoaster coaster, Player player) throws IOException, SyntaxException, ChangeCancelledException {
        createImpl(coaster, player);
    }

    /**
     * Reads the full CSV file and creates the coaster described in it. Does not perform any
     * permission checking, so this should only be used when loading in coasters from a secure
     * source such as disk.
     *
     * @param coaster Coaster to fill with nodes and connections
     * @throws IOException
     * @throws SyntaxException
     */
    public void create(TrackCoaster coaster) throws IOException, SyntaxException, ChangeCancelledException {
        try {
            createImpl(coaster, null);
        } catch (ChangeCancelledException ex) {
            // This never happend in practise but handle it anyway
            throw new RuntimeException("Unexpected Change Cancelled Exception", ex);
        }
    }

    private void createImpl(TrackCoaster coaster, Player player) throws IOException, SyntaxException, ChangeCancelledException {
        TrackCSV.CSVReaderState state = new TrackCSV.CSVReaderState();
        state.coaster = coaster;
        state.world = coaster.getWorld();

        // If player is specified, track that player's historic changes and permission handling
        if (player != null) {
            state.handleUsingPlayer(player);
        }

        // Not locked by default
        state.coaster.setLocked(false);

        // Read all the entries we can from the CSV reader
        boolean hasChangeCancelledException = false;
        TrackCSV.CSVEntry lastEntry = null;
        TrackCSV.CSVEntry entry;
        while ((entry = readNextEntry()) != null) {
            // Read the origin of the coaster from the csv
            // The first line that refers to an origin is used
            if (this.origin != null && state.transform == null && entry instanceof PlayerOriginHolder) {
                state.transform = ((PlayerOriginHolder) entry).getOrigin().getTransformTo(this.origin);
            }

            // Let the entry refresh the reader state
            // If a permission issue occurs, abort, but do continue with creating the connections
            // as it would be very ugly to just have a bunch of disconnected nodes imported in
            try {
                entry.processReader(state);
            } catch (ChangeCancelledException ex) {
                hasChangeCancelledException = true;
                lastEntry = null; // Don't run this logic
                break;
            }
            lastEntry = entry;
        }

        // Closing logic, like connecting first node with last node
        if (lastEntry != null) {
            try {
                lastEntry.processReaderEnd(state);
            } catch (ChangeCancelledException ex) {
                hasChangeCancelledException = true;
            }
        }

        // Go by all created nodes and initialize animation state connections up-front
        state.coaster.refreshConnections();

        // Create all pending connections
        for (TrackConnectionState link : state.pendingLinks) {
            try {
                state.processConnection(link);
            } catch (ChangeCancelledException ex) {
                hasChangeCancelledException = true;
            }
        }

        // If we had trouble at all, throw to indicate this
        if (hasChangeCancelledException) {
            throw new ChangeCancelledException();
        }
    }

}
