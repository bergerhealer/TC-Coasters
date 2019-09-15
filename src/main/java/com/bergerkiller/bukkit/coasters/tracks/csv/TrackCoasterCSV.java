package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.opencsv.CSVReader;

/**
 * Helper class for storing possible data stored on each line of the CSV file
 */
public class TrackCoasterCSV {
    private static final List<Map.Entry<CSVEntry, Supplier<CSVEntry>>> _entryTypes = new ArrayList<>();

    public static void registerEntry(Supplier<CSVEntry> entrySupplier) {
        _entryTypes.add(new AbstractMap.SimpleEntry<CSVEntry, Supplier<CSVEntry>>(entrySupplier.get(), entrySupplier));
    }

    static {
        registerEntry(NodeEntry::new);
        registerEntry(RootNodeEntry::new);
        registerEntry(LinkNodeEntry::new);
        registerEntry(NoLimits2Entry::new);
    }

    /**
     * Reads the next CSVEntry from a CSVReader. The buffer is used to store data temporarily.
     * 
     * @param reader to read from
     * @param buffer to fill with data
     * @return decoded entry, null if the end of the CSV was reached
     * @throws EntrySyntaxException if there is a syntax error in the CSV
     * @throws IOException if reading fails due to an I/O error
     */
    public static CSVEntry readNext(CSVReader reader, StringArrayBuffer buffer) throws EntrySyntaxException, IOException {
        String[] lines;
        while ((lines = reader.readNext()) != null) {
            buffer.load(lines);
            CSVEntry entry;
            try {
                entry = decode(buffer);
            } catch (EntrySyntaxException ex) {
                throw new EntrySyntaxException("At L" + reader.getLinesRead() + ": " + ex.getMessage());
            }
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Decodes an entry from a StringArrayBuffer storing the CSV line.
     * 
     * @param buffer
     * @return decoded entry, null if no entry was identified inside the buffer
     * @throws EntrySyntaxException if decoding the entry fails
     */
    public static CSVEntry decode(StringArrayBuffer buffer) throws EntrySyntaxException {
        for (Map.Entry<CSVEntry, Supplier<CSVEntry>> registeredEntry : _entryTypes) {
            if (registeredEntry.getKey().detect(buffer)) {
                CSVEntry entry = registeredEntry.getValue().get();
                entry.read(buffer);
                return entry;
            }
        }
        return null;
    }

    /**
     * Base type for a CSV entry
     */
    public static abstract class CSVEntry {
        /**
         * Checks a buffer to see if it matches the formatting of this kind of entry.
         * This method should not modify the internal state of this entry.
         * 
         * @param buffer to read from
         * @return True if this entry type matches the contents of the buffer
         */
        public abstract boolean detect(StringArrayBuffer buffer);

        /**
         * Reads the data in the buffer into this entry
         * 
         * @param buffer to read from
         * @return True if read without syntax errors, False if the data is wrong
         * @throws EntrySyntaxException when the buffer stores data the entry cannot read
         */
        public abstract void read(StringArrayBuffer buffer) throws EntrySyntaxException;

        /**
         * Writes this entry to the buffer
         * 
         * @param buffer
         */
        public abstract void write(StringArrayBuffer buffer);

        @Override
        public String toString() {
            StringArrayBuffer buffer = new StringArrayBuffer();
            this.write(buffer);
            return buffer.toString();
        }
    }

    /**
     * Base class for an entry that refers to a node of the track.
     * All the preserved state of a node are stored in here.
     */
    public static abstract class BaseNodeEntry extends CSVEntry {
        public Vector pos;
        public Vector up;
        public IntVector3 rail;

        public void setFromNode(TrackNode node) {
            this.pos = node.getPosition();
            this.up = node.getOrientation();
            this.rail = node.getRailBlock(false);
        }

        /**
         * Gets the type name of this base node entry
         * 
         * @return type name
         */
        public abstract String getType();

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals(this.getType());
        }

        @Override
        public void read(StringArrayBuffer buffer) throws EntrySyntaxException {
            try {
                buffer.skipNext(1); // Type
                this.pos = new Vector( Double.parseDouble(buffer.next()),
                                       Double.parseDouble(buffer.next()),
                                       Double.parseDouble(buffer.next()) );
                this.up = new Vector( Double.parseDouble(buffer.next()),
                                      Double.parseDouble(buffer.next()),
                                      Double.parseDouble(buffer.next()) );
                if (buffer.hasNext()) {
                    this.rail = new IntVector3( Integer.parseInt(buffer.next()),
                                                Integer.parseInt(buffer.next()),
                                                Integer.parseInt(buffer.next()) );
                } else {
                    buffer.skipNext(3);
                    this.rail = null;
                }
            } catch (NumberFormatException ex) {
                throw new EntrySyntaxException(getClass().getSimpleName() + " failed to parse number");
            }
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put(getType());
            buffer.put(Double.toString(this.pos.getX()));
            buffer.put(Double.toString(this.pos.getY()));
            buffer.put(Double.toString(this.pos.getZ()));
            buffer.put(Double.toString(this.up.getX()));
            buffer.put(Double.toString(this.up.getY()));
            buffer.put(Double.toString(this.up.getZ()));
            if (this.rail != null) {
                buffer.put(Integer.toString(this.rail.x));
                buffer.put(Integer.toString(this.rail.y));
                buffer.put(Integer.toString(this.rail.z));
            } else {
                buffer.skipNext(3);
            }
        }
    }

    public static final class NodeEntry extends BaseNodeEntry {
        @Override
        public String getType() {
            return "NODE";
        }
    }

    public static final class LinkNodeEntry extends BaseNodeEntry {
        @Override
        public String getType() {
            return "LINK";
        }
    }

    public static final class RootNodeEntry extends BaseNodeEntry {
        @Override
        public String getType() {
            return "ROOT";
        }
    }

    // For future reference: the nl2 park file format
    // https://github.com/geforcefan/libnolimits
    public static class NoLimits2Entry extends CSVEntry {
        public int no;
        public Vector pos;
        public Vector front;
        public Vector left;
        public Vector up;

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            if (buffer.size() < 13) {
                return false;
            }
            String firstColumnValue = buffer.get(0);
            if (firstColumnValue.isEmpty()) {
                return false;
            }
            for (int n = 0; n < firstColumnValue.length(); n++) {
                if (!Character.isDigit(firstColumnValue.charAt(n))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void read(StringArrayBuffer buffer) throws EntrySyntaxException {
            try {
                this.no = Integer.parseInt(buffer.next());
                this.pos = new Vector( Double.parseDouble(buffer.next()),
                                       Double.parseDouble(buffer.next()),
                                       Double.parseDouble(buffer.next()) );
                this.front = new Vector( Double.parseDouble(buffer.next()),
                                         Double.parseDouble(buffer.next()),
                                         Double.parseDouble(buffer.next()) );
                this.left = new Vector( Double.parseDouble(buffer.next()),
                                        Double.parseDouble(buffer.next()),
                                        Double.parseDouble(buffer.next()) );
                this.up = new Vector( Double.parseDouble(buffer.next()),
                                      Double.parseDouble(buffer.next()),
                                      Double.parseDouble(buffer.next()) );
            } catch (NumberFormatException ex) {
                throw new EntrySyntaxException(getClass().getSimpleName() + " failed to parse number");
            }
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put(Integer.toString(this.no));
            buffer.put(Double.toString(this.pos.getX()));
            buffer.put(Double.toString(this.pos.getY()));
            buffer.put(Double.toString(this.pos.getZ()));
            buffer.put(Double.toString(this.front.getX()));
            buffer.put(Double.toString(this.front.getY()));
            buffer.put(Double.toString(this.front.getZ()));
            buffer.put(Double.toString(this.left.getX()));
            buffer.put(Double.toString(this.left.getY()));
            buffer.put(Double.toString(this.left.getZ()));
            buffer.put(Double.toString(this.up.getX()));
            buffer.put(Double.toString(this.up.getY()));
            buffer.put(Double.toString(this.up.getZ()));
        }
    }

    /**
     * Exception thrown when an entry could not be parsed from a line in the CSV file
     */
    public static class EntrySyntaxException extends Exception {
        private static final long serialVersionUID = 1277434257635748172L;

        public EntrySyntaxException(String message) {
            super(message);
        }
    }
}
