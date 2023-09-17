package com.bergerkiller.bukkit.coasters.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.util.CSVFormatDetectorStream;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.bases.CheckedRunnable;
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
    private TrackCSV.CSVReaderState state = null;

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
     * Gets an action that can be run later on to finish building all the connections
     * stored in pending LINK entries. This method can safely be called before
     * actual creating has been done, and when called, will create the links
     * that were loaded before an error occurred if any.
     *
     * @return Coaster finalize action
     */
    public TrackCoaster.CoasterLoadFinalizeAction getFinalizeAction() {
        return () -> {
            try {
                createPendingLinksImpl();
            } catch (Throwable t) {
                if (state != null) {
                    state.coaster.getPlugin().getLogger().log(Level.SEVERE,
                            "An error occurred finalizing coaster " + state.coaster.getName(), t);
                }
            }
        };
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
     * @throws TrackCoaster.CoasterLoadException
     * @throws ChangeCancelledException
     */
    public void create(TrackCoaster coaster, Player player) throws TrackCoaster.CoasterLoadException, ChangeCancelledException {
        this.state = new TrackCSV.CSVReaderState(coaster, player);
        wrapErrors(this::createImpl);
    }

    /**
     * Reads the full CSV file and creates the coaster described in it. Does not perform any
     * permission checking, so this should only be used when loading in coasters from a secure
     * source such as disk.
     *
     * @param coaster Coaster to fill with nodes and connections
     * @throws TrackCoaster.CoasterLoadException
     */
    public void create(TrackCoaster coaster) throws TrackCoaster.CoasterLoadException {
        try {
            this.state = new TrackCSV.CSVReaderState(coaster);
            wrapErrors(this::createImpl);
        } catch (ChangeCancelledException ex) {
            // This never happens in practise but handle it anyway
            throw new TrackCoaster.CoasterLoadException("Unexpected Change Cancelled Exception", ex);
        }
    }

    /**
     * Loads in the coaster's main ROOT and NODE link entries, but does not yet load in
     * all the LINK entries. When loading all coasters in a world this first step
     * makes sure all nodes available. You should call {@link #getFinalizeAction()}
     * to get a task to run after all coasters are loaded in to finish initializing
     * the junction connections.
     *
     * @param coaster Coaster to create
     * @throws TrackCoaster.CoasterLoadException
     */
    public void createBaseOnly(TrackCoaster coaster) throws TrackCoaster.CoasterLoadException {
        try {
            this.state = new TrackCSV.CSVReaderState(coaster);
            wrapErrors(this::createBaseImpl);
        } catch (ChangeCancelledException ex) {
            // This never happens in practise but handle it anyway
            throw new TrackCoaster.CoasterLoadException("Unexpected Change Cancelled Exception", ex);
        }
    }

    private void wrapErrors(CheckedRunnable action) throws TrackCoaster.CoasterLoadException, ChangeCancelledException {
        try {
            action.run();
        } catch (ChangeCancelledException ex) {
            throw ex;
        } catch (SyntaxException ex) {
            throw new TrackCoaster.CoasterLoadException("Syntax error while loading coaster " + state.coaster.getName() + " " + ex.getMessage());
        } catch (IOException ex) {
            throw new TrackCoaster.CoasterLoadException("An I/O Error occurred while loading coaster " + state.coaster.getName(), ex);
        } catch (Throwable t) {
            state.coaster.getPlugin().getLogger().log(Level.SEVERE, "An unexpected error occurred while loading coaster " + state.coaster.getName(), t);
            throw new TrackCoaster.CoasterLoadException("An unexpected error occurred while loading coaster " + state.coaster.getName(), t);
        }
    }

    private void createImpl() throws IOException, SyntaxException, ChangeCancelledException {
        boolean hasChangeCancelledException = false;
        try {
            createBaseImpl();
        } catch (ChangeCancelledException ex) {
            hasChangeCancelledException = true;
        }
        try {
            createPendingLinksImpl();
        } catch (ChangeCancelledException ex) {
            hasChangeCancelledException = true;
        }
        if (hasChangeCancelledException) {
            throw new ChangeCancelledException();
        }
    }

    private void createBaseImpl() throws IOException, SyntaxException, ChangeCancelledException {
        TrackCSV.CSVReaderState state = this.state;

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

        // If we had trouble at all, throw to indicate this
        if (hasChangeCancelledException) {
            throw new ChangeCancelledException();
        }
    }

    private void createPendingLinksImpl() throws ChangeCancelledException {
        TrackCSV.CSVReaderState state = this.state;

        // Maybe loading failed
        if (state == null) {
            return;
        }

        // Create all pending connections
        boolean hasChangeCancelledException = false;
        for (TrackConnectionState link : state.pendingLinks) {
            try {
                state.processConnection(link);
            } catch (ChangeCancelledException ex) {
                hasChangeCancelledException = true;
            }
        }
        if (hasChangeCancelledException) {
            throw new ChangeCancelledException();
        }
    }
}
