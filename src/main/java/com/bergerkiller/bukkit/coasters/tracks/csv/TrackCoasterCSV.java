package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeFallingBlock;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeItemStack;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeLeash;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeState;
import com.bergerkiller.bukkit.coasters.util.PlayerOrigin;
import com.bergerkiller.bukkit.coasters.util.PlayerOriginHolder;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.opencsv.CSVReader;

/**
 * Helper class for storing possible data stored on each line of the CSV file
 */
public class TrackCoasterCSV {
    private static final List<Map.Entry<CSVEntry, Supplier<CSVEntry>>> _entryTypes = new ArrayList<>();
    private static final Map<Class<?>, Supplier<TrackObjectTypeEntry<?>>> _trackObjectTypes = new HashMap<>();
    private static final List<TrackObjectType<?>> _trackObjectTypeDefaults = new ArrayList<>();

    public static void registerEntry(Supplier<CSVEntry> entrySupplier) {
        CSVEntry entry = entrySupplier.get();
        _entryTypes.add(new AbstractMap.SimpleEntry<CSVEntry, Supplier<CSVEntry>>(entry, entrySupplier));
        if (entry instanceof TrackObjectTypeEntry) {
            TrackObjectType<?> type = ((TrackObjectTypeEntry<?>) entry).getDefaultType();
            _trackObjectTypes.put(type.getClass(), CommonUtil.unsafeCast(entrySupplier));
            _trackObjectTypeDefaults.add(type);
        }
    }

    static {
        registerEntry(NodeEntry::new);
        registerEntry(RootNodeEntry::new);
        registerEntry(LinkNodeEntry::new);
        registerEntry(AnimationStateNodeEntry::new);
        registerEntry(AnimationStateLinkNodeEntry::new);
        registerEntry(PlayerOrigin::new);
        registerEntry(LockCoasterEntry::new);
        registerEntry(ObjectEntry::new);
        registerEntry(NoLimits2Entry::new);

        // Object types
        registerEntry(TrackObjectTypeItemStack.CSVEntry::new);
        registerEntry(TrackObjectTypeFallingBlock.CSVEntry::new);
        registerEntry(TrackObjectTypeLeash.CSVEntry::new);
    }

    /**
     * Gets a list of track object types which are default selectable
     * 
     * @return unmodifiable list of default track object types
     */
    public static List<TrackObjectType<?>> getDefaultTrackObjectTypes() {
        return Collections.unmodifiableList(_trackObjectTypeDefaults);
    }

    /**
     * Creates the appropriate entry for writing a track object type.
     * Returns null if the type is not registered.
     * The entry is initialized with the name and type.
     * 
     * @param name The name to store in the entry
     * @param type The track object type to store in the entry
     * @return entry for this type, if not registered, is null
     */
    public static <T extends TrackObjectType<?>> TrackObjectTypeEntry<T> createTrackObjectTypeEntry(String name, T type) {
        if (type == null) {
            return null;
        }
        Supplier<TrackObjectTypeEntry<?>> supplier = _trackObjectTypes.get(type.getClass());
        if (supplier == null) {
            return null;
        }
        TrackObjectTypeEntry<T> entry = CommonUtil.unsafeCast(supplier.get());
        entry.name = name;
        entry.objectType = type;
        return entry;
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
     * @throws SyntaxException if decoding the entry fails
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
     * State information used while reading CSV data
     */
    public static final class CSVReaderState {
        public List<TrackConnectionState> pendingLinks = new ArrayList<TrackConnectionState>();
        public List<TrackConnectionState> prevNode_pendingLinks = new ArrayList<TrackConnectionState>();
        public Map<String, TrackObjectType<?>> trackObjectTypesByName = new HashMap<>();
        public List<TrackObject> pendingTrackObjects = new ArrayList<TrackObject>();
        public boolean prevNode_hasDefaultAnimationLinks = true;
        public TrackNode prevNode = null;
        public Matrix4x4 transform = null;
        public CoasterWorld world;
        public TrackCoaster coaster;

        public void addConnectionToAnimationStates(TrackConnectionState connection) {
            for (TrackNodeAnimationState state : this.prevNode.getAnimationStates()) {
                this.prevNode.addAnimationStateConnection(state.name, connection);
            }
        }

        public Vector transformVector(Vector position) {
            if (this.transform != null) {
                position = position.clone();
                this.transform.transformPoint(position);
            }
            return position;
        }

        public TrackNodeState transformState(TrackNodeState nodeState) {
            return (this.transform == null) ? nodeState : nodeState.transform(this.transform);
        }

        /**
         * Adds a node using node state information. If a transformation was set,
         * it is applied to the state prior to creating it.
         * 
         * @param nodeState
         * @param linkToPrevious
         */
        public void addNode(TrackNodeState nodeState, boolean linkToPrevious) {
            // Reset all connections being added to the previous node
            this.prevNode_pendingLinks.clear();

            // Create the new node
            TrackNode node = this.coaster.createNewNode(transformState(nodeState));

            // Connect node to previous node
            if (linkToPrevious && this.prevNode != null) {
                TrackConnection connection = this.world.getTracks().connect(this.prevNode, node);

                // Add all track objects
                connection.addAllObjects(this.pendingTrackObjects);

                // Add connection to all animation states
                TrackConnectionState link = TrackConnectionState.create(this.prevNode, node, this.pendingTrackObjects);
                if (this.prevNode_hasDefaultAnimationLinks && this.prevNode.hasAnimationStates()) {
                    this.addConnectionToAnimationStates(link);
                }
                this.prevNode_pendingLinks.add(link);
            }

            // Refresh prevNode
            this.prevNode = node;
            this.prevNode_hasDefaultAnimationLinks = true;
            this.pendingTrackObjects.clear();
        }
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

        /**
         * Process this entry while reading the file sequentially.
         * The reader csv state should be updated by this entry.
         * 
         * @param state
         */
        public abstract void processReader(CSVReaderState state);
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

        /**
         * Gets the type name of this base node entry
         * 
         * @return type name
         */
        public abstract String getType();

        /**
         * Converts this entry to a TrackNodeState object
         * 
         * @return nodeState
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

    /**
     * Starts a new chain of nodes, not connecting to the previous node.
     * State is reset.
     */
    public static final class RootNodeEntry extends BaseNodeEntry {
        @Override
        public String getType() {
            return "ROOT";
        }

        @Override
        public void processReader(CSVReaderState state) {
            state.addNode(this.toState(), false);
        }
    }

    /**
     * Creates a new node in the chain, connecting to the previously read node.
     * State is reset.
     */
    public static final class NodeEntry extends BaseNodeEntry {
        @Override
        public String getType() {
            return "NODE";
        }

        @Override
        public void processReader(CSVReaderState state) {
            state.addNode(this.toState(), true);
        }
    }

    /**
     * ANIM entries are animation states that refer to the previously written node.
     * When no ANIMLINK entries are specified, defaults to the connections already made
     * between the nodes. Otherwise the ANIMLINK defines the connections for this state.
     */
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

        @Override
        public void processReader(CSVReaderState state) {
            // Can't update if no previous node is known
            if (state.prevNode == null) {
                return;
            }

            TrackConnectionState[] connections = TrackConnectionState.EMPTY;
            if (state.prevNode_hasDefaultAnimationLinks) {
                connections = new TrackConnectionState[state.prevNode_pendingLinks.size()];
                for (int i = 0; i < connections.length; i++) {
                    connections[i] = state.prevNode_pendingLinks.get(i).cloneObjects();
                }
            }
            state.prevNode.setAnimationState(this.name, state.transformState(this.toState()), connections);
        }
    }

    /**
     * ANIMLINK entries specify the connections that exist at a particular animation node state.
     * Once this type of entry is found, connections between nodes (LINK and NODE) are no longer
     * stored in the animation states.
     */
    public static final class AnimationStateLinkNodeEntry extends CSVEntry implements PlayerOriginHolder {
        public Vector pos;

        @Override
        public PlayerOrigin getOrigin() {
            return PlayerOrigin.getForNode(this.pos);
        }

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals("ANIMLINK");
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.skipNext(1); // Type
            this.pos = buffer.nextVector();
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("ANIMLINK");
            buffer.putVector(this.pos);
        }

        @Override
        public void processReader(CSVReaderState state) {
            // Can't update if no previous node is known
            if (state.prevNode == null) {
                return;
            }

            // So far we have added default links. This is now invalid!
            // Wipe all previously stored connections in all nodes
            if (state.prevNode_hasDefaultAnimationLinks) {
                state.prevNode_hasDefaultAnimationLinks = false;
                List<TrackNodeAnimationState> states = new ArrayList<>(state.prevNode.getAnimationStates());
                for (TrackNodeAnimationState oldState : states) {
                    if (oldState.connections.length != 0) {
                        state.prevNode.setAnimationState(oldState.name, oldState.state, TrackConnectionState.EMPTY);
                    }
                }
            }

            // Add an extra connection to the last added animation state
            List<TrackNodeAnimationState> states = state.prevNode.getAnimationStates();
            if (!states.isEmpty()) {
                Vector pos = state.transformVector(this.pos);
                TrackNodeAnimationState lastAddedState = states.get(states.size()-1);
                TrackConnectionState new_link = TrackConnectionState.create(state.prevNode, TrackNodeReference.at(pos), state.pendingTrackObjects);
                state.prevNode.addAnimationStateConnection(lastAddedState.name, new_link);
                state.pendingTrackObjects.clear();
            }
        }
    }

    /**
     * LINK entries add connections to be made between nodes to the pendingLinks list.
     * There connections are created after reading the entire file, in the same order.
     */
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

        @Override
        public void processReader(CSVReaderState state) {
            Vector pos = state.transformVector(this.pos);
            TrackConnectionState link = TrackConnectionState.create(state.prevNode, TrackNodeReference.at(pos), state.pendingTrackObjects);
            state.pendingLinks.add(link);
            state.prevNode_pendingLinks.add(link);
            if (state.prevNode_hasDefaultAnimationLinks && state.prevNode.hasAnimationStates()) {
                state.addConnectionToAnimationStates(link);
            }

            state.pendingTrackObjects.clear();
        }
    }

    /**
     * Locks the coaster when it exists
     */
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

        @Override
        public void processReader(CSVReaderState state) {
            state.coaster.setLocked(true);
        }
    }

    /**
     * Declares the details of a track object type implementation
     */
    public static abstract class TrackObjectTypeEntry<T extends TrackObjectType<?>> extends CSVEntry {
        public String name;
        public T objectType;
        protected double width;

        /**
         * Gets the type name identifying this track object type entry.
         * This is written out, and used to detect this entry.
         * 
         * @return type name
         */
        public abstract String getType();

        /**
         * Gets the default value for the type created by this entry. When saving, this
         * identifies the entry to use when writing a type to csv. It also sets the
         * default type selected when switched in the editor.
         *
         * @return track object type class
         */
        public abstract T getDefaultType();

        /**
         * Reads further details of a track object
         * 
         * @param buffer The buffer to read from
         * @return Fully parsed object type
         * @throws SyntaxException
         */
        public abstract T readDetails(StringArrayBuffer buffer) throws SyntaxException;

        /**
         * Writes further details of the track object
         * 
         * @param buffer The buffer to write to
         * @param objectType The object type to write
         */
        public abstract void writeDetails(StringArrayBuffer buffer, T objectType);

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals(getType());
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.next();
            this.name = buffer.next();

            // Width, with support for legacy behavior when ItemStack info was stored
            if (ParseUtil.isNumeric(buffer.peek())) {
                this.width = buffer.nextDouble();
            } else {
                this.width = 0.0;
            }

            this.objectType = this.readDetails(buffer);
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put(getType());
            buffer.put(this.name);
            buffer.putDouble(this.objectType.getWidth());
            writeDetails(buffer, this.objectType);
        }

        @Override
        public void processReader(CSVReaderState state) {
            state.trackObjectTypesByName.put(this.name, this.objectType);
        }
    }

    /**
     * Adds an object to a previously created connection
     */
    public static final class ObjectEntry extends CSVEntry {
        public double distance;
        public String itemName;
        public boolean flipped;

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals("OBJECT");
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.next();
            this.distance = buffer.nextDouble();
            this.itemName = buffer.next();
            this.flipped = buffer.next().equalsIgnoreCase("flipped");
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("OBJECT");
            buffer.putDouble(this.distance);
            buffer.put(this.itemName);
            if (this.flipped) {
                buffer.put("FLIPPED");
            }
        }

        @Override
        public void processReader(CSVReaderState state) {
            TrackObjectType<?> type = state.trackObjectTypesByName.get(this.itemName);
            if (type != null) {
                state.pendingTrackObjects.add(new TrackObject(type, this.distance, this.flipped));
            }
        }
    }

    /**
     * Creates a new node in the chain, connecting to the previously read node.
     * This entry matches the NoLimits2 CSV format.
     * State is reset.
     * 
     * For future reference: the nl2 park file format
     * https://github.com/geforcefan/libnolimits
     */
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

        @Override
        public void processReader(CSVReaderState state) {
            state.addNode(this.toState(), true);
        }
    }
}
