package com.bergerkiller.bukkit.coasters.csv;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.events.CoasterBeforeUpdateAnimationStateEvent;
import com.bergerkiller.bukkit.coasters.objects.display.TrackObjectTypeDisplayBlock;
import com.bergerkiller.bukkit.coasters.objects.display.TrackObjectTypeDisplayItemStack;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeFallingBlock;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeArmorStandItem;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeLeash;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import com.bergerkiller.bukkit.coasters.tracks.TrackCoaster;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnectionState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeReference;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSign;
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
public class TrackCSV {
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

    public static void unregisterEntry(Supplier<CSVEntry> entrySupplier) {
        CSVEntry entry = entrySupplier.get();
        {
            Iterator<Map.Entry<CSVEntry, Supplier<CSVEntry>>> iter = _entryTypes.iterator();
            while (iter.hasNext()) {
                if (iter.next().getKey().getClass() == entry.getClass()) {
                    iter.remove();
                }
            }
        }
        if (entry instanceof TrackObjectTypeEntry) {
            TrackObjectType<?> type = ((TrackObjectTypeEntry<?>) entry).getDefaultType();
            _trackObjectTypes.remove(type.getClass());
            _trackObjectTypeDefaults.remove(type);
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
        registerEntry(AdjustTrackObjectTypeEntry::new);
        registerEntry(ObjectEntry::new);
        registerEntry(SignEntry::new);
        registerEntry(NoLimits2Entry::new);

        // Object types
        registerEntry(TrackObjectTypeArmorStandItem.CSVEntry::new);
        registerEntry(TrackObjectTypeFallingBlock.CSVEntry::new);
        if (CommonCapabilities.HAS_DISPLAY_ENTITY) {
            registerEntry(TrackObjectTypeDisplayItemStack.CSVEntry::new);
            registerEntry(TrackObjectTypeDisplayBlock.CSVEntry::new);
        }
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
     * @throws SyntaxException if there is a syntax error in the CSV
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
        public TrackNode firstNode = null;
        public TrackNode prevNode = null;
        public String prevNodeAnimName = null;
        public Matrix4x4 transform = null;
        public CoasterWorld world;
        public TrackCoaster coaster;
        public Player player = null; // If non-null, should check for perms with this Player
        public HistoryChange changes = null; // If non-null, should track changes for a Player
        public boolean preserveSignKeys = false; // If true, will preserve the KEY entries of signs read from CSV
        public TrackConnection.AddObjectPredicate addTrackObjectPredicate = (connection, object) -> true;
        public TrackNode.AddSignPredicate addNodeSignPredicate = null; // Supports null for no filter!
        public TrackNode.UpdateAnimationStatePredicate updateAnimationStatePredicate = null; // Supports null for no filter!

        public CSVReaderState(TrackCoaster coaster, Player player) {
            this(coaster);

            // If player is specified, track that player's historic changes and permission handling
            if (player != null) {
                this.handleUsingPlayer(player);
            }
        }

        public CSVReaderState(TrackCoaster coaster) {
            this.coaster = coaster;
            this.world = coaster.getWorld();
        }

        public void handleUsingPlayer(Player player) {
            this.player = player;
            this.changes = this.coaster.getPlugin().getEditState(player).getHistory().addChangeGroup();
            this.addTrackObjectPredicate = (connection, object) -> {
                try {
                    changes.addChangeBeforeCreateTrackObject(player, connection, object);
                    return true;
                } catch (ChangeCancelledException ex) {
                    return false;
                }
            };
            this.addNodeSignPredicate = (node, sign) -> sign.fireBuildEvent(player, false);
            this.updateAnimationStatePredicate = (node, old_state, new_state) -> {
                CoasterBeforeUpdateAnimationStateEvent event = new CoasterBeforeUpdateAnimationStateEvent(
                        player, node, old_state, new_state);
                CommonUtil.callEvent(event);
                return !event.isCancelled();
            };
        }

        public void addConnectionToAnimationStates(TrackConnectionState connection) {
            for (TrackNodeAnimationState state : this.prevNode.getAnimationStates()) {
                this.prevNode.addAnimationStateConnection(state.name, connection, updateAnimationStatePredicate);
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

        private void afterAddingConnection(TrackConnection connection) throws ChangeCancelledException {
            // Permission and history handling
            if (changes != null) {
                changes.addChangeAfterConnect(player, connection);
            }
        }

        public void processConnection(TrackConnectionState link) throws ChangeCancelledException {
            TrackConnection connection = coaster.getWorld().getTracks().connect(link, false);
            if (connection == null) {
                return;
            }

            afterAddingConnection(connection);

            // Only add objects if the connection didn't already exist with other objects
            // This prevents duplicate objects when a link between coasters is created
            if (!connection.hasObjects()) {
                connection.addAllObjects(link, this.addTrackObjectPredicate);
            }

            // Ensure junction switching order is preserved
            // Note: the input link node A is the one that contains the junction, and whose junction
            //       selection order must be preserved. However, if the connection already existed,
            //       node A and node B might be swapped, so that must be accounted for.
            if (link.node_a.isReference(connection.getNodeA())) {
                connection.getNodeA().pushBackJunction(connection);
            } else {
                connection.getNodeB().pushBackJunction(connection);
            }
        }

        /**
         * Adds a node using node state information. If a transformation was set,
         * it is applied to the state prior to creating it.
         * 
         * @param nodeState
         * @param linkToPrevious
         */
        public void addNode(TrackNodeState nodeState, boolean linkToPrevious) throws ChangeCancelledException {
            // Reset all connections being added to the previous node
            this.prevNode_pendingLinks.clear();

            // Create the new node + optional perm checking
            TrackNode node = this.coaster.createNewNode(transformState(nodeState));
            if (changes != null) {
                changes.addChangeCreateNode(player, node);
            }

            // Connect node to previous node
            if (linkToPrevious && this.prevNode != null) {
                TrackConnection connection = this.world.getTracks().connect(this.prevNode, node);
                afterAddingConnection(connection);

                // Add all track objects
                connection.addAllObjects(this.pendingTrackObjects, this.addTrackObjectPredicate);

                // Add connection to all animation states
                TrackConnectionState link = TrackConnectionState.create(this.prevNode, node, this.pendingTrackObjects);
                if (this.prevNode_hasDefaultAnimationLinks && this.prevNode.hasAnimationStates()) {
                    this.addConnectionToAnimationStates(link);
                }
                this.prevNode_pendingLinks.add(link);
            }

            // Refresh prevNode
            if (this.firstNode == null) {
                this.firstNode = node;
            }
            this.prevNode = node;
            this.prevNodeAnimName = null;
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
         * @throws SyntaxException when the buffer stores data the entry cannot read
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
         * @param state State of the reader
         */
        public abstract void processReader(CSVReaderState state) throws ChangeCancelledException;

        /**
         * Called when this is the last entry read from a reader. Some types
         * of nodes can perform special logic here, like connecting the first
         * and last entries together.
         *
         * @param state State of the reader
         */
        public void processReaderEnd(CSVReaderState state) throws ChangeCancelledException {
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
            if (buffer.isNextNotEmpty()) {
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
        public void processReader(CSVReaderState state) throws ChangeCancelledException {
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
        public void processReader(CSVReaderState state) throws ChangeCancelledException {
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
            state.prevNodeAnimName = this.name;

            state.prevNode.updateAnimationState(
                    TrackNodeAnimationState.create(name,
                                                   state.transformState(this.toState()),
                                                   connections),
                    state.updateAnimationStatePredicate);
        }
    }

    /**
     * ANIMLINK entries specify the connections that exist at a particular animation node state.
     * Once this type of entry is found, connections between nodes (LINK and NODE) are no longer
     * stored in the animation states.
     */
    public static final class AnimationStateLinkNodeEntry extends CSVEntry implements PlayerOriginHolder {
        public Vector pos;
        public boolean objectsFlipped; // whether start/end positions are flipped, important for track object positions

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
            if (buffer.isNextNotEmpty()) {
                objectsFlipped = buffer.next().equals("FLIPPED");
            }
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("ANIMLINK");
            buffer.putVector(this.pos);
            if (objectsFlipped) {
                buffer.put("FLIPPED");
            }
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
                TrackNodeReference linkNodeA = state.prevNode;
                TrackNodeReference linkNodeB = TrackNodeReference.at(pos);
                if (this.objectsFlipped) {
                    TrackNodeReference tmp = linkNodeA;
                    linkNodeA = linkNodeB;
                    linkNodeB = tmp;
                }
                TrackNodeAnimationState lastAddedState = states.get(states.size()-1);
                TrackConnectionState new_link = TrackConnectionState.create(linkNodeA, linkNodeB, state.pendingTrackObjects);
                state.pendingTrackObjects.clear();
                state.prevNode.addAnimationStateConnection(lastAddedState.name, new_link, state.updateAnimationStatePredicate);
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
     * Adjusts the position / spatial information of a previously read track object type.
     */
    public static final class AdjustTrackObjectTypeEntry extends CSVEntry {
        public String objectName;
        public String name;
        public Matrix4x4 transform;

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals("ADJUST");
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.next();
            this.objectName = buffer.next();
            this.name = buffer.next();
            this.transform = new Matrix4x4();
            this.transform.translate(buffer.nextDouble(), buffer.nextDouble(), buffer.nextDouble());
            this.transform.rotateYawPitchRoll(buffer.nextDouble(), buffer.nextDouble(), buffer.nextDouble());
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("ADJUST");
            buffer.put(this.objectName);
            buffer.put(this.name);
            buffer.putVector(this.transform.toVector());
            buffer.putVector(this.transform.getYawPitchRoll());
        }

        @Override
        public void processReader(CSVReaderState state) {
            TrackObjectType<?> type = state.trackObjectTypesByName.get(this.objectName);
            if (type != null) {
                state.trackObjectTypesByName.put(this.name, type.setTransform(this.transform));
            }
        }
    }

    /**
     * Adds an object to a previously created connection
     */
    public static final class ObjectEntry extends CSVEntry {
        public double distance;
        public String name;
        public boolean flipped;

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals("OBJECT");
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.next();
            this.distance = buffer.nextDouble();
            this.name = buffer.next();
            this.flipped = buffer.next().equalsIgnoreCase("flipped");
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("OBJECT");
            buffer.putDouble(this.distance);
            buffer.put(this.name);
            if (this.flipped) {
                buffer.put("FLIPPED");
            }
        }

        @Override
        public void processReader(CSVReaderState state) {
            TrackObjectType<?> type = state.trackObjectTypesByName.get(this.name);
            if (type != null) {
                state.pendingTrackObjects.add(new TrackObject(type, this.distance, this.flipped));
            }
        }
    }

    /**
     * Adds a fake sign to a previously created node or node animation state
     */
    public static final class SignEntry extends CSVEntry {
        public TrackNodeSign sign;
        public boolean writeKeys = false;

        @Override
        public boolean detect(StringArrayBuffer buffer) {
            return buffer.get(0).equals("SIGN");
        }

        @Override
        public void read(StringArrayBuffer buffer) throws SyntaxException {
            buffer.next();

            sign = new TrackNodeSign();
            writeKeys = false;

            // Options might be expanded in the future
            while (buffer.hasNext()) {
                String option = buffer.next();
                if (option.equals("LINES")) {
                    break;
                } else if (option.startsWith("POWER_ON_")) {
                    sign.addInputPowerChannel(buffer.next(), true, parseFace(buffer, option.substring(9)));
                } else if (option.startsWith("POWER_OFF_")) {
                    sign.addInputPowerChannel(buffer.next(), false, parseFace(buffer, option.substring(10)));
                } else if (option.equals("OUTPUT_ON")) {
                    sign.addOutputPowerChannel(buffer.next(), true);
                } else if (option.equals("OUTPUT_OFF")) {
                    sign.addOutputPowerChannel(buffer.next(), false);
                } else if (option.equals("KEY")) {
                    sign.setKey(buffer.nextUUID());
                    writeKeys = true;
                } else {
                    throw buffer.createSyntaxException("Unknown sign option: " + option);
                }
            }

            // Process all lines
            List<String> lines = new ArrayList<>(8);
            while (buffer.hasNext()) {
                lines.add(buffer.next());
            }
            sign.setLines(lines.toArray(new String[lines.size()]));
        }

        private BlockFace parseFace(StringArrayBuffer buffer, String text) throws SyntaxException {
            for (BlockFace face : BlockFace.values()) {
                if (face.name().equals(text)) {
                    return face;
                }
            }
            throw buffer.createSyntaxException("Not a valid BlockFace: " + text);
        }

        @Override
        public void write(StringArrayBuffer buffer) {
            buffer.put("SIGN");
            if (writeKeys) {
                buffer.put("KEY");
                buffer.putUUID(sign.getKey());
            }
            for (NamedPowerChannel channel : sign.getInputPowerChannels()) {
                if (channel.isPowered()) {
                    buffer.put("POWER_ON_" + channel.getFace().name());
                } else {
                    buffer.put("POWER_OFF_" + channel.getFace().name());
                }
                buffer.put(channel.getName());
            }
            for (NamedPowerChannel channel : sign.getOutputPowerChannels()) {
                if (channel.isPowered()) {
                    buffer.put("OUTPUT_ON");
                } else {
                    buffer.put("OUTPUT_OFF");
                }
                buffer.put(channel.getName());
            }
            buffer.put("LINES");
            for (String line : sign.getLines()) {
                buffer.put(line);
            }
        }

        @Override
        public void processReader(CSVReaderState state) throws ChangeCancelledException {
            if (state.prevNodeAnimName != null) {
                // Add to this specific animation state, optionally handles perms/filter
                state.prevNode.addAnimationStateSign(state.prevNodeAnimName, sign, state.addNodeSignPredicate);
                return;
            } else {
                // Add to the node itself
                state.prevNode.addSign(sign, state.addNodeSignPredicate);
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
            this.left = orientation.rightVector();
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
        public void processReader(CSVReaderState state) throws ChangeCancelledException {
            state.addNode(this.toState(), true);
        }

        @Override
        public void processReaderEnd(CSVReaderState state) throws ChangeCancelledException {
            // Connect first node to the previous node to create a loop, as all NoLimits2
            // coasters are loops. Only do this if the distance between the nodes is
            // acceptably low, perhaps it is not a loop? If that is at all possible.
            if (state.firstNode != null &&
                state.firstNode != state.prevNode &&
                state.firstNode.getPosition().distance(state.prevNode.getPosition()) < 8.0
            ) {
                TrackConnection connection = state.world.getTracks().connect(state.prevNode, state.firstNode);
                if (state.changes != null) {
                    state.changes.addChangeAfterConnect(state.player, connection);
                }
            }
        }
    }
}
