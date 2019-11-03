package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Quaternion;
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
        registerEntry(AnimationStateNodeEntry::new);
        registerEntry(PlayerOrigin::new);
        registerEntry(LockCoasterEntry::new);
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
    public static CSVEntry readNext(CSVReader reader, StringArrayBuffer buffer) throws SyntaxException, IOException {
        String[] lines;
        while ((lines = reader.readNext()) != null) {
            buffer.load(lines);
            CSVEntry entry;
            try {
                entry = decode(buffer);
            } catch (SyntaxException ex) {
                throw ex.setLine((int) reader.getLinesRead());
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
    public static CSVEntry decode(StringArrayBuffer buffer) throws SyntaxException {
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
         * Gets whether quotes must be applied when writing out this entry
         * 
         * @return True if quotes must be applied
         */
        public boolean applyQuotes() { return true; }

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
         * @throws EntrySyntaxException when the buffer stores data the entry cannot read
         */
        public abstract void read(StringArrayBuffer buffer) throws SyntaxException;

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
    public static abstract class BaseNodeEntry extends CSVEntry implements PlayerOriginHolder {
        public Vector pos;
        public Vector up;
        public IntVector3 rail;

        public void setFromNode(TrackNode node) {
            this.pos = node.getPosition();
            this.up = node.getOrientation();
            this.rail = node.getRailBlock(false);
        }

        public void setFromState(TrackNodeState state) {
            this.pos = state.position;
            this.up = state.orientation;
            this.rail = state.railBlock;
        }

        public TrackNodeState createState() {
            return TrackNodeState.create(this.pos, this.up, this.rail);
        }

        /**
         * Gets the type name of this base node entry
         * 
         * @return type name
         */
        public abstract String getType();

        /**
         * Converts this entry to a TrackNodeState object
         * 
         * @return state
         */
        public TrackNodeState toState() {
            return TrackNodeState.create(this.pos, this.up, this.rail);
        }

        @Override
        public PlayerOrigin getOrigin() {
            return PlayerOrigin.getForNode(this.pos);
        }

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals(this.getType());
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.skipNext(1); // Type
            this.pos = buffer.nextVector();
            this.up = buffer.nextVector();
            if (buffer.hasNext()) {
                this.rail = buffer.nextIntVector3();
            } else {
                buffer.skipNext(3);
                this.rail = null;
            }
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put(getType());
            buffer.putVector(this.pos);
            buffer.putVector(this.up);
            if (this.rail != null) {
                buffer.putIntVector3(this.rail);
            } else {
                buffer.skipNext(3);
            }
        }
    }

    public static final class RootNodeEntry extends BaseNodeEntry {
        @Override
        public String getType() {
            return "ROOT";
        }
    }

    public static final class NodeEntry extends BaseNodeEntry {
        @Override
        public String getType() {
            return "NODE";
        }
    }

    public static final class AnimationStateNodeEntry extends BaseNodeEntry {
        public String name;

        @Override
        public String getType() {
            return "ANIM";
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            super.read(buffer);
            this.name = buffer.next();
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            super.write(buffer);
            buffer.put(this.name);
        }
    }

    public static final class LinkNodeEntry extends CSVEntry implements PlayerOriginHolder {
        public Vector pos;

        @Override
        public PlayerOrigin getOrigin() {
            return PlayerOrigin.getForNode(this.pos);
        }

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals("LINK");
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.skipNext(1); // Type
            this.pos = buffer.nextVector();
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("LINK");
            buffer.putVector(this.pos);
        }
    }

    public static final class LockCoasterEntry extends CSVEntry {

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals("LOCK");
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.next();
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("LOCK");
        }
    }

    // For future reference: the nl2 park file format
    // https://github.com/geforcefan/libnolimits
    public static class NoLimits2Entry extends CSVEntry implements PlayerOriginHolder {
        public int no;
        public Vector pos;
        public Vector front;
        public Vector left;
        public Vector up;

        /**
         * Sets the orientation of this node, which updates the front/left/up vectors
         * 
         * @param direction
         * @param up
         */
        public void setOrientation(Vector direction, Vector up) {
            this.setOrientation(Quaternion.fromLookDirection(direction, up));
        }

        /**
         * Sets the orientation of this node, which updates the front/left/up vectors
         * 
         * @param orientation
         */
        public void setOrientation(Quaternion orientation) {
            this.front = orientation.forwardVector();
            this.left = orientation.rightVector().multiply(-1.0);
            this.up = orientation.upVector();
        }

        /**
         * Converts this entry to a TrackNodeState object
         * 
         * @return state
         */
        public TrackNodeState toState() {
            return TrackNodeState.create(this.pos, this.up, null);
        }

        @Override
        public PlayerOrigin getOrigin() {
            return PlayerOrigin.getForNode(this.pos);
        }

        @Override
        public boolean applyQuotes() {
            return false;
        }

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
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            this.no = buffer.nextInt();
            this.pos = buffer.nextVector();
            this.front = buffer.nextVector();
            this.left = buffer.nextVector();
            this.up = buffer.nextVector();
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.putInt(this.no);
            buffer.putVector(this.pos);
            buffer.putVector(this.front);
            buffer.putVector(this.left);
            buffer.putVector(this.up);
        }
    }
}
