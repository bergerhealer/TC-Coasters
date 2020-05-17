package com.bergerkiller.bukkit.coasters.editor.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.csv.TrackCSV;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.ChangeCancelledException;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCreateTrackObject;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeGroup;
import com.bergerkiller.bukkit.coasters.editor.object.util.ConnectionChain;
import com.bergerkiller.bukkit.coasters.editor.object.util.DuplicatedObject;
import com.bergerkiller.bukkit.coasters.editor.object.util.DuplicationSourceList;
import com.bergerkiller.bukkit.coasters.editor.object.util.TrackObjectDiscoverer;
import com.bergerkiller.bukkit.coasters.events.CoasterBeforeChangeTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterSelectTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeFallingBlock;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeItemStack;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSearchPath;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.resources.CommonSounds;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;

public class ObjectEditState {
    private final PlayerEditState editState;
    private final Map<TrackObject, ObjectEditTrackObject> editedTrackObjects = new LinkedHashMap<TrackObject, ObjectEditTrackObject>();
    private final List<DuplicatedObject> duplicatedObjects = new ArrayList<DuplicatedObject>();
    private final List<DragListener> dragListeners = new ArrayList<DragListener>();
    private TrackObject lastEditedTrackObject = null;
    private long lastEditTrackObjectTime = System.currentTimeMillis();
    private double dragListenersDistanceToObjects = 0.0;
    private boolean isDraggingObjects = false;
    private boolean isDuplicating = false;
    private boolean blink = false;
    private TrackObjectType<?> selectedType;

    public ObjectEditState(PlayerEditState editState) {
        this.editState = editState;
        this.selectedType = TrackObjectTypeFallingBlock.createDefault();
    }

    /**
     * Gets the selected track object type information
     * 
     * @return selected track object type
     */
    public TrackObjectType<?> getSelectedType() {
        return this.selectedType;
    }

    /**
     * Sets the selected track object type information to a new value.
     * Refreshes all selected track objects to use this new type.
     * New objects placed will have this same type.
     * 
     * @param type
     */
    public void setSelectedType(TrackObjectType<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type can not be null");
        }
        this.selectedType = type;
        this.editState.markChanged();

        Iterator<ObjectEditTrackObject> iter = this.editedTrackObjects.values().iterator();
        while (iter.hasNext()) {
            ObjectEditTrackObject editObject = iter.next();
            if (CommonUtil.callEvent(new CoasterBeforeChangeTrackObjectEvent(getPlayer(), editObject.connection, editObject.object)).isCancelled()) {
                iter.remove();
                editObject.object.onStateUpdated(editObject.connection, this.editState);
                this.editState.markChanged();

                // May have caused a particle visibility change
                getWorld().getParticles().scheduleViewerUpdate(this.getPlayer());
                continue;
            }
            editObject.object.setType(editObject.connection, type);
        }
    }

    /**
     * Alters the currently selected track object type using a manipulator function
     * 
     * @param manipulator Manipulator function that will change the selected type as a result
     * @return transformed type, original type if unchanged
     */
    public TrackObjectType<?> transformSelectedType(Function<TrackObjectType<?>, TrackObjectType<?>> manipulator) {
        TrackObjectType<?> original = this.getSelectedType();
        TrackObjectType<?> result = manipulator.apply(original);
        if (!result.equals(original)) {
            this.setSelectedType(result);
            return result;
        } else {
            return original;
        }
    }

    /**
     * Alters the currently selected track object type using a manipulator function, but only
     * if the track object type is of the type specified.
     * 
     * @param type The type to manipulate
     * @param manipulator Manipulator function that will change the selected type as a result
     * @return transformed type, original type if unchanged, null if type is incompatible
     */
    public <T extends TrackObjectType<?>> T transformSelectedType(Class<T> type, Function<T, T> manipulator) {
        if (type.isAssignableFrom(this.getSelectedType().getClass())) {
            return CommonUtil.unsafeCast(transformSelectedType(CommonUtil.unsafeCast(manipulator)));
        } else {
            return null;
        }
    }

    /**
     * Whether selected track objects are currently glowing (blinking logic)
     * 
     * @return True if blinking
     */
    public boolean isBlink() {
        return this.blink;
    }

    public void load(ConfigurationNode config) {
        this.selectedType = parseType(config);
    }

    public void save(ConfigurationNode config) {
        // Make use of the CSV format to save the selected track object type information
        TrackCSV.TrackObjectTypeEntry<?> entry = TrackCSV.createTrackObjectTypeEntry("SELECTED", this.selectedType);
        if (entry != null) {
            StringArrayBuffer buffer = new StringArrayBuffer();
            entry.write(buffer);
            config.set("selectedTrackObject", Arrays.asList(buffer.toArray()));
        } else {
            config.remove("selectedTrackObject");
        }
    }

    public CoasterWorld getWorld() {
        return this.editState.getWorld();
    }

    public Player getPlayer() {
        return this.editState.getPlayer();
    }

    public Set<TrackObject> getEditedObjects() {
        return this.editedTrackObjects.keySet();
    }

    public boolean hasEditedObjects() {
        return !this.editedTrackObjects.isEmpty();
    }

    public void clearEditedTrackObjects() {
        if (!this.editedTrackObjects.isEmpty()) {
            ArrayList<ObjectEditTrackObject> oldObjects = new ArrayList<ObjectEditTrackObject>(this.editedTrackObjects.values());
            this.editedTrackObjects.clear();

            //this.editedAnimationNamesChanged |= !this.editedNodesByAnimationName.isEmpty();
            //this.editedNodesByAnimationName.clear();
            //this.lastEdited = null;

            this.editState.markChanged();
            for (ObjectEditTrackObject oldObject : oldObjects) {
                oldObject.object.onStateUpdated(oldObject.connection, this.editState);
            }

            // May have caused a particle visibility change
            getWorld().getParticles().scheduleViewerUpdate(this.getPlayer());
        }
    }

    public void addDragListener(DragListener listener) {
        this.dragListeners.add(listener);
    }

    public void removeDragListener(DragListener listener) {
        this.dragListeners.remove(listener);
    }

    public void onModeChanged() {
        for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
            editObject.object.onStateUpdated(editObject.connection, this.editState);
        }
    }

    public void update() {
        // Turn objects glowing on and off at an interval of 16 ticks
        // Blinking turns off while an object is dragged to highlight the width marker
        boolean new_blink = !this.editState.isHoldingRightClick() && (CommonUtil.getServerTicks() & 15) >= 8;
        if (this.blink != new_blink) {
            this.blink = new_blink;
            for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
                editObject.object.onStateUpdated(editObject.connection, this.editState);
            }
        }
    }

    public boolean onLeftClick() {
        // Find point on path clicked
        TrackConnection.PointOnPath point = this.getWorld().getTracks().findPointOnPath(this.getPlayer().getEyeLocation(), 3.0);

        // While holding right-click, left-click switches to duplicating mode
        if (this.editState.isHoldingRightClick()) {
            // Switch to duplicate mode
            if (point != null) {
                if (this.isDuplicating) {
                    // Cancel duplicating and switch back to dragging mode
                    this.undoDuplicatedObjects();
                    this.isDuplicating = false;
                    this.startDrag(point, false);
                } else if (this.editedTrackObjects.size() == 1) {
                    // If only one object is selected, duplicate it and resume dragging the original object
                    ObjectEditTrackObject editObject = this.editedTrackObjects.values().iterator().next();
                    editObject.connection.addObject(editObject.object.clone());

                    // Play a sound cue so the player knows an object was placed
                    PlayerUtil.playSound(getPlayer(), CommonSounds.CLICK_WOOD, 0.5f, 1.0f);
                } else {
                    // Finish the drag action and switch to duplicate mode
                    try {
                        this.onEditingFinished();
                        this.startDrag(point, true);
                    } catch (ChangeCancelledException e) {
                        this.clearEditedTrackObjects();
                    }
                }
            }
            return true;
        }

        // Single or multiple object selection mode
        boolean isMultiSelect = this.editState.isSneaking();

        if (point == null) {
            if (!isMultiSelect) {
                clearEditedTrackObjects();
            }
            return true;
        }

        // Check for objects on this connection that are very close to the point clicked
        TrackObject bestObject = null;
        double bestDistance = 4.0; // within 4.0 distance to select it (TODO: larger wiggle room when further away?)
        for (TrackObject object : point.connection.getObjects()) {
            double distance = Math.abs(object.getDistance() - point.distance);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestObject = object;
            }
        }
        if (bestObject == null) {
            if (!isMultiSelect) {
                clearEditedTrackObjects();
            }
            return true;
        }

        long lastEditTime = getLastEditTime(bestObject);
        if (lastEditTime > 300) {
            // Toggle edit state
            if (isEditing(bestObject) && (isMultiSelect || this.editedTrackObjects.size() == 1)) {
                // Disable just this object when it's our only selection,
                // or when multi-select is active
                setEditingTrackObject(point.connection, bestObject, false);
            } else {
                // Deselect all previous objects when multiselect is off, and select the object
                if (!isMultiSelect) {
                    clearEditedTrackObjects();
                }
                selectTrackObject(point.connection, bestObject);
            }
        } else {
            // Mass-selection mode
            if (this.editState.isSneaking()) {
                // Select all objects between the clicked object and the nearest other selected track object
                this.floodSelectNearest(point.connection, bestObject);
            } else {
                // Flood-fill select all nodes connected from bestObject
                this.floodSelectObjects(point.connection, bestObject);
            }
        }
        return true;
    }

    public void onEditingFinished() throws ChangeCancelledException {
        this.isDraggingObjects = false;
        if (this.isDuplicating) {
            // Duplicating finished
            this.isDuplicating = false;

            // Finalize duplication with a history update
            if (!this.duplicatedObjects.isEmpty()) {
                HistoryChange changes = this.editState.getHistory().addChangeGroup();
                for (DuplicatedObject dupe : this.duplicatedObjects) {
                    changes.addChange(new HistoryChangeCreateTrackObject(dupe.connection, dupe.object));
                }
                this.duplicatedObjects.clear();
            }

            // Reset drag info
            for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
                editObject.moveEnd();
            }
        } else {
            // Dragging/moving objects finished
            boolean wasCancelled = false;
            HistoryChange changes = null;
            for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
                if (Double.isNaN(editObject.dragDistance)) {
                    continue;
                }
                if (changes == null) {
                    changes = new HistoryChangeGroup();
                }
                try {
                    changes.addChangeAfterMovingTrackObject(this.getPlayer(), editObject.connection, editObject.object,
                            editObject.beforeDragConnection, editObject.beforeDragDistance, editObject.beforeDragFlipped);
                } catch (ChangeCancelledException ex) {
                    wasCancelled = true;
                }
                editObject.moveEnd();
            }
            if (changes != null) {
                this.editState.getHistory().addChange(changes);
            }
            if (wasCancelled) {
                throw new ChangeCancelledException();
            }
        }
    }

    /**
     * Tries to select a track object for editing. If not already editing,
     * an event is fired to check whether selecting the track object is permitted
     * 
     * @param connection The connection the object is on
     * @param object The object being selected
     * @return True if the object was selected or was already selected, False if this was disallowed
     */
    public boolean selectTrackObject(TrackConnection connection, TrackObject object) {
        if (object == null) {
            throw new IllegalArgumentException("Track object can not be null");
        }
        if (this.editedTrackObjects.containsKey(object)) {
            return true;
        }

        if (CommonUtil.callEvent(new CoasterSelectTrackObjectEvent(this.getPlayer(), connection, object)).isCancelled()) {
            return false;
        }

        this.setEditingTrackObject(connection, object, true);
        return true;
    }

    public void setEditingTrackObject(TrackConnection connection, TrackObject object, boolean editing) {
        if (object == null) {
            throw new IllegalArgumentException("Track Object can not be null");
        }
        boolean changed;
        if (editing) {
            if (this.editedTrackObjects.containsKey(object)) {
                changed = false;
            } else {
                changed = true;
                this.editedTrackObjects.put(object, new ObjectEditTrackObject(connection, object));
                this.selectedType = object.getType();
            }
        } else {
            changed = (this.editedTrackObjects.remove(object) != null);
        }
        if (changed) {
            // Can be caused by the node being removed, handle that here
            object.onStateUpdated(connection, this.editState);

            /*
            for (TrackNodeAnimationState state : node.getAnimationStates()) {
                Set<TrackNode> values = this.editedNodesByAnimationName.get(state.name);
                if (editing && values.add(node)) {
                    this.editedAnimationNamesChanged |= (values.size() == 1);
                } else if (!editing && values.remove(node)) {
                    this.editedAnimationNamesChanged |= values.isEmpty();
                }
            }
            */

            this.lastEditedTrackObject = object;
            this.lastEditTrackObjectTime = System.currentTimeMillis();
            this.editState.markChanged();

            // May have caused a particle visibility change
            getWorld().getParticles().scheduleViewerUpdate(this.getPlayer());
        }
    }

    public long getLastEditTime(TrackObject object) {
        if (this.lastEditedTrackObject != object) {
            return Long.MAX_VALUE;
        } else {
            return System.currentTimeMillis() - this.lastEditTrackObjectTime;
        }
    }

    public boolean isEditing(TrackObject object) {
        return this.editedTrackObjects.containsKey(object);
    }

    /**
     * Deselects all track objects that we cannot actually edit, because the connection
     * they are on is on a coaster that is locked.
     * 
     * @return True if track objects were deselected
     */
    public boolean deselectLockedObjects() {
        boolean hadLockedTrackObjects = false;
        Iterator<ObjectEditTrackObject> iter = this.editedTrackObjects.values().iterator();
        while (iter.hasNext()) {
            ObjectEditTrackObject editObject = iter.next();
            if (editObject.connection.isLocked()) {
                iter.remove();
                hadLockedTrackObjects = true;
                editObject.object.onStateUpdated(editObject.connection, this.editState);
                this.lastEditedTrackObject = editObject.object;
            }
        }
        if (hadLockedTrackObjects) {
            this.lastEditTrackObjectTime = System.currentTimeMillis();
            this.editState.markChanged();
            TCCoastersLocalization.LOCKED.message(this.getPlayer());

            // May have caused a particle visibility change
            getWorld().getParticles().scheduleViewerUpdate(this.getPlayer());
        }
        return hadLockedTrackObjects;
    }

    /**
     * Deletes all selected track objects
     * 
     * @throws ChangeCancelledException
     */
    public void deleteObjects() throws ChangeCancelledException {
        if (this.editedTrackObjects.isEmpty()) {
            return;
        }

        List<ObjectEditTrackObject> objects = new ArrayList<ObjectEditTrackObject>(this.editedTrackObjects.values());
        this.editedTrackObjects.clear();
        
        boolean wereChangesCancelled = false;
        for (ObjectEditTrackObject object : objects) {
            try {
                this.editState.getHistory().addChangeBeforeDeleteTrackObject(this.getPlayer(), object.connection, object.object);
                object.connection.removeObject(object.object);
                object.connection.removeObjectFromAnimationStates(this.editState.getSelectedAnimation(), object.object);
            } catch (ChangeCancelledException ex) {
                wereChangesCancelled = true;
                object.object.onStateUpdated(object.connection, this.editState);
            }
        }
        if (wereChangesCancelled) {
            throw new ChangeCancelledException();
        }
    }

    /**
     * Creates a new track objects where the player is looking, or drags existing track objects around on the track
     * 
     * @throws ChangeCancelledException
     */
    public void createObject() throws ChangeCancelledException {
        // Find point looked at. More strict rules when a drag listener also wants to be dragged.
        double fov = this.dragListeners.isEmpty() ? 3.0 : 0.5;
        TrackConnection.PointOnPath point = this.getWorld().getTracks().findPointOnPath(this.editState.getInput().get(), fov);
        if (point == null) {
            if (!this.isDraggingObjects) {
                updateDragListeners();
            }
            return;
        }
        if (point.connection.isLocked()) {
            TCCoastersLocalization.LOCKED.message(this.getPlayer());
            return;
        }

        // Player looks into a certain direction, which changes the orientation of the objects
        Vector rightDirection = this.editState.getInput().get().getRotation().forwardVector();

        // Create new objects when none are selected
        if (this.editedTrackObjects.isEmpty()) {
            // Compare orientation with the direction the player is looking
            // If inverted, then flip the object
            boolean flipped = (point.orientation.rightVector().dot(rightDirection) < 0.0);
            TrackObject object = new TrackObject(this.getSelectedType(), point.distance, flipped);
            this.editState.getHistory().addChangeBeforeCreateTrackObject(this.getPlayer(), point.connection, object);

            point.connection.addObject(object);
            point.connection.addObjectToAnimationStates(this.editState.getSelectedAnimation(), object);
            return;
        }

        // Deselect objects we cannot edit before proceeding
        this.deselectLockedObjects();

        if (this.editState.getHeldDownTicks() == 0) {
            this.startDrag(point, false);
            if (!this.isDraggingObjects) {
                updateDragListeners();
            }
        } else if (!this.isDraggingObjects) {
            // Started right-click somewhere where the selected track objects cannot be reached
            // We do nothing except update the drag listeners
            updateDragListeners();
        } else if (this.isDuplicating) {
            // Successive clicks while drag: duplicate the selected track objects
            this.duplicateObjects(point);
        } else {
            // Successive clicks: move the objects to the point, making use of the relative dragDistance to do so
            this.undoDuplicatedObjects();
            HistoryChange changes = this.editState.getHistory().addChangeGroup();
            boolean success = true;
            success &= moveObjects(point, changes, false, rightDirection);
            success &= moveObjects(point, changes, true, rightDirection);
            if (!success) {
                throw new ChangeCancelledException();
            }
        }
    }

    private void updateDragListeners() {
        if (this.editState.getHeldDownTicks() == 0) {
            this.dragListenersDistanceToObjects = this.computeDistanceToObjects();
        }
        if (!this.dragListeners.isEmpty()) {
            Quaternion rotationDiff = this.editState.getInput().delta().getRotation();
            double delta = -rotationDiff.getPitch() / 90.0 + rotationDiff.getYaw() / 90.0;
            delta *= this.dragListenersDistanceToObjects;
            for (DragListener listener : this.dragListeners) {
                listener.onDrag(delta);
            }
        }
    }

    /**
     * Computes the nearest distance from the player to any of the selected track objects
     * 
     * @return minimum distance
     */
    private double computeDistanceToObjects() {
        Vector loc = getPlayer().getLocation().toVector();
        double minDistSq = 160.0; // 10 chunks as max
        for (ObjectEditTrackObject object : this.editedTrackObjects.values()) {
            double theta = object.connection.findPointThetaAtDistance(object.object.getDistance());
            Vector pos = object.connection.getPosition(theta);
            minDistSq = Math.min(minDistSq, pos.distanceSquared(loc));
        }
        return minDistSq;
    }

    /**
     * Initializes a new drag operation
     * 
     * @param point The point the player looks at
     * @param duplicating Whether duplicating mode is active or not
     */
    private void startDrag(TrackConnection.PointOnPath point, boolean duplicating) {
        // First click: calculate the positions of the objects relative to the clicked point
        // If objects aren't accessible from the point, then their dragDistance is set to NaN

        // When sneaking during initial right-click, enable duplicating mode
        this.isDuplicating = duplicating;
        this.isDraggingObjects = false;

        // Reset all objects to NaN
        for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
            editObject.dragDistance = Double.NaN;
        }

        // First do the objects on the clicked connection itself
        Map<TrackObject, ObjectEditTrackObject> pending = new HashMap<TrackObject, ObjectEditTrackObject>(this.editedTrackObjects);
        for (TrackObject object : point.connection.getObjects()) {
            ObjectEditTrackObject editObject = pending.remove(object);
            if (editObject != null) {
                editObject.dragDistance = object.getDistance() - point.distance;
                editObject.dragDirection = (editObject.dragDistance >= 0.0);
                editObject.alignmentFlipped = object.isFlipped();
                if (!editObject.dragDirection) {
                    editObject.dragDistance = -editObject.dragDistance;
                }
            }
        }

        // If there's more, perform discovery in the two directions from the point's connection
        // Do this stepwise, so in a loop we find the nearest distance ideally
        if (!pending.isEmpty()) {
            Set<TrackConnection> visited = new HashSet<TrackConnection>();
            TrackObjectDiscoverer left  = new TrackObjectDiscoverer(pending, visited, point.connection, false, point.distance);
            TrackObjectDiscoverer right = new TrackObjectDiscoverer(pending, visited, point.connection, true,  point.distance);
            while (left.next() || right.next());
        }

        // For all objects we've found, fire before change event and cancel the drag when cancelled
        for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
            if (Double.isNaN(editObject.dragDistance)) {
                continue;
            }
            if (!this.isDuplicating && CommonUtil.callEvent(new CoasterBeforeChangeTrackObjectEvent(this.getPlayer(), editObject.connection, editObject.object)).isCancelled()) {
                // Cancelled
                editObject.dragDistance = Double.NaN;
                continue;
            }

            // Save state prior to drag for history and after change event later
            this.isDraggingObjects = true;
            editObject.beforeDragConnection = editObject.connection;
            editObject.beforeDragDistance = editObject.object.getDistance();
            editObject.beforeDragFlipped = editObject.object.isFlipped();

            // Player looks into a certain direction, which changes the orientation of the objects
            Vector rightDirection = this.editState.getInput().get().getRotation().forwardVector();

            // Looking at the object from left-to-right or right-to-left?
            // This is important when moving the object later
            TrackConnection.PointOnPath beforePoint = editObject.connection.findPointAtDistance(editObject.beforeDragDistance);
            editObject.beforeDragLookingAtFlipped = (beforePoint.orientation.rightVector().dot(rightDirection) < 0.0);
        }
    }

    /**
     * Selects all the track objects of all connections accessible from the start connection,
     * that are between the startObject and the closest other selected track object.
     * The selected track objects are added and the previous selection is kept.
     * 
     * @param startConnection
     * @param startObject
     */
    public void floodSelectNearest(TrackConnection startConnection, TrackObject startObject) {
        // If there is a selected track object with the same connection, then we can optimize this
        // This is technically incorrect, because we could be at the end/start of the connection, with
        // another selected object on a connection next to it. For now, this is good enough, really.
        // Normally people only select a single object prior to flood-selecting anyway.
        {
            TrackObject closestObjectOnSameConnection = null;
            double closestDistance = Double.MAX_VALUE;
            for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
                if (editObject.connection == startConnection && editObject.object != startObject) {
                    double distance = Math.abs(startObject.getDistance() - editObject.object.getDistance());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestObjectOnSameConnection = editObject.object;
                    }
                }
            }
            if (closestObjectOnSameConnection != null) {
                // Add all objects in between the distance range
                double d_from = Math.min(startObject.getDistance(), closestObjectOnSameConnection.getDistance());
                double d_to   = Math.max(startObject.getDistance(), closestObjectOnSameConnection.getDistance());
                for (TrackObject object : startConnection.getObjects()) {
                    if (object.getDistance() >= d_from && object.getDistance() <= d_to) {
                        this.selectTrackObject(startConnection, object);
                    }
                }
                return; // done.
            }
        }

        // Make use of the node flood selecting, using the nodes from the objects we have selected
        // For start node, we pick the node closest to the start object
        List<ObjectEditTrackObject> editedTrackObjects = new ArrayList<ObjectEditTrackObject>(this.editedTrackObjects.values());
        HashSet<TrackNode> nodesOfTrackObjects = new HashSet<TrackNode>();
        for (ObjectEditTrackObject editObject : editedTrackObjects) {
            if (editObject.object != startObject) {
                nodesOfTrackObjects.add(editObject.connection.getNodeA());
                nodesOfTrackObjects.add(editObject.connection.getNodeB());
            }
        }
        boolean searchRight = (startObject.getDistance() >= 0.5 * startConnection.getFullDistance());
        TrackNode searchStart = searchRight ? startConnection.getNodeB() : startConnection.getNodeA();
        TrackNodeSearchPath bestPath = TrackNodeSearchPath.findShortest(searchStart, nodesOfTrackObjects);

        // Now do stuff with the found path, if found
        if (bestPath != null) {
            // If best path contains the other node of the start connection, then fill select to the right
            // Otherwise, select all objects to the left of the start object
            boolean searchWentRight = searchRight == (bestPath.path.contains(startConnection.getOtherNode(searchStart)));
            this.selectObjectsBeyondDistance(startConnection, searchWentRight, startObject.getDistance());
            bestPath.pathConnections.remove(startConnection);

            // The current node is one that has a connection with one of the track objects we had selected
            // We need to find the connection of the node which is closest to it
            TrackConnection bestConnection = null;
            double bestObjectDistance = 0.0;
            boolean bestObjectDirection = false;
            double bestObjectDistanceToEnd = Double.MAX_VALUE;
            for (ObjectEditTrackObject editObject : editedTrackObjects) {
                if (editObject.object == startObject) {
                    continue;
                }

                boolean direction = (editObject.connection.getNodeA() == bestPath.current);
                if (!direction && editObject.connection.getNodeB() != bestPath.current) {
                    continue;
                }

                double distance = editObject.object.getDistance();
                if (!direction) {
                    distance = editObject.connection.getFullDistance() - distance;
                }
                if (distance < bestObjectDistanceToEnd) {
                    bestObjectDistanceToEnd = distance;
                    bestConnection = editObject.connection;
                    bestObjectDistance = editObject.object.getDistance();
                    bestObjectDirection = direction;
                }
            }
            if (bestConnection != null) {
                bestPath.pathConnections.remove(bestConnection);
                this.selectObjectsBeyondDistance(bestConnection, bestObjectDirection, bestObjectDistance);
            }

            // Add all objects on connections in-between
            for (TrackConnection connection : bestPath.pathConnections) {
                for (TrackObject object : connection.getObjects()) {
                    this.selectTrackObject(connection, object);
                }
            }
        }
    }

    private void selectObjectsBeyondDistance(TrackConnection connection, boolean direction, double distance) {
        for (TrackObject object : connection.getObjects()) {
            if (direction ? (object.getDistance() <= distance) :
                            (object.getDistance() >= distance)
            ) {
                this.selectTrackObject(connection, object);
            }
        }
    }

    /**
     * Selects all the track objects of all connections accessible from a connection, recursively.
     * This will select all the objects of an entire coaster or piece of track, for example.
     * Any previous selection is cleared.
     * 
     * @param startConnection
     * @param startObject
     */
    public void floodSelectObjects(TrackConnection startConnection, TrackObject startObject) {
        this.clearEditedTrackObjects();

        List<TrackConnection> pending = new ArrayList<TrackConnection>();
        pending.add(startConnection);
        HashSet<TrackConnection> processed = new HashSet<TrackConnection>(pending);
        while (!pending.isEmpty()) {
            int size = pending.size();
            for (int i = 0; i < size; i++) {
                TrackConnection connection = pending.get(i);
                for (TrackObject object : connection.getObjects()) {
                    this.selectTrackObject(connection, object);
                }
                for (TrackConnection neighbour : connection.getNodeA().getConnections()) {
                    if (processed.add(neighbour)) {
                        pending.add(neighbour);
                    }
                }
                for (TrackConnection neighbour : connection.getNodeB().getConnections()) {
                    if (processed.add(neighbour)) {
                        pending.add(neighbour);
                    }
                }
            }
            pending.subList(0, size).clear();
        }
    }

    /**
     * Takes existing selected objects and duplicates them until the difference
     * in distance is filled up.
     * 
     * @param point
     */
    public void duplicateObjects(TrackConnection.PointOnPath point) {
        // Collect all selected objects with an initial drag distance in a list and sort by it's position
        // Can't duplicate if we only have 1 or no objects selected, or the objects are too close together (lag!)
        DuplicationSourceList objects = new DuplicationSourceList(this.editedTrackObjects.values());
        if (!objects.isValidSelection()) {
            this.undoDuplicatedObjects();
            return;
        }

        // Check whether any of the objects share a connection with the point
        boolean isPointOnObjectConnection = false;
        for (ObjectEditTrackObject editObject : objects.list()) {
            if (editObject.connection == point.connection) {
                isPointOnObjectConnection = true;
                break;
            }
        }

        // Find the closest path from where we are looking to the nearest selected track object
        TrackNodeSearchPath bestPath;
        if (isPointOnObjectConnection) {
            bestPath = null;
        } else {
            HashSet<TrackNode> nodesOfTrackObjects = new HashSet<TrackNode>();
            for (ObjectEditTrackObject editObject : objects.list()) {
                nodesOfTrackObjects.add(editObject.connection.getNodeA());
                nodesOfTrackObjects.add(editObject.connection.getNodeB());
            }
            TrackNode searchStart = (point.distance >= 0.5 * point.connection.getFullDistance()) ?
                    point.connection.getNodeB() : point.connection.getNodeA();
            bestPath = TrackNodeSearchPath.findShortest(searchStart, nodesOfTrackObjects);
            if (bestPath == null) {
                undoDuplicatedObjects();
                return;
            }

            // Make sure the first connection in the search path is of our own point
            // This could be missing if our search start was accurate
            if (bestPath.pathConnections.isEmpty() || bestPath.pathConnections.get(0) != point.connection) {
                bestPath.pathConnections.add(0, point.connection);
            }
        }

        // Start from the closest point on the connection
        boolean objectsWereAdded = false;
        int duplicatedObjectIndex = -1;
        double currentDistance;
        TrackConnection currentConnection;
        {
            ObjectEditTrackObject closestObjectOnSameConnection = null;
            double edgeDistance = 0.0;
            if (isPointOnObjectConnection) {
                // On same connection, find closest to the point clicked
                double closestDistance = Double.MAX_VALUE;
                edgeDistance = point.distance;
                for (ObjectEditTrackObject editObject : objects.list()) {
                    if (editObject.connection == point.connection) {
                        double distance = Math.abs(point.distance - editObject.object.getDistance());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestObjectOnSameConnection = editObject;
                        }
                    }
                }
            } else {
                // Different connection, find closest that is connected to the current (last) node
                double closestDistance = Double.MAX_VALUE;
                for (ObjectEditTrackObject editObject : objects.list()) {
                    if (editObject.connection.getNodeA() == bestPath.current) {
                        double distance = editObject.object.getDistance();
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestObjectOnSameConnection = editObject;
                            edgeDistance = 0.0;
                        }
                    } else if (editObject.connection.getNodeB() == bestPath.current) {
                        double distance = editObject.connection.getFullDistance() - editObject.object.getDistance();
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestObjectOnSameConnection = editObject;
                            edgeDistance = editObject.connection.getFullDistance();
                        }
                    } else {
                        continue;
                    }
                }
            }

            // This is impossible, but just in case we handle it
            // If the closest object is not at the edge of the list, then we can't duplicate
            // In that case the player is looking in the middle of the objects, which does nothing
            if (closestObjectOnSameConnection == null || !objects.start(closestObjectOnSameConnection)) {
                this.undoDuplicatedObjects();
                return;
            }

            // Start situation (start from the selected track objects)
            currentDistance = closestObjectOnSameConnection.object.getDistance();
            currentConnection = closestObjectOnSameConnection.connection;
            boolean order = (currentDistance < edgeDistance);

            // Index of connection in bestPath after the currentConnection (reverse order)
            int nextConnectionIndex = -1;
            if (bestPath != null) {
                nextConnectionIndex = bestPath.pathConnections.size()-1;
                if (bestPath.pathConnections.get(nextConnectionIndex) == currentConnection) {
                    nextConnectionIndex--;
                }
            }

            objectAddLoop:
            while (true) {
                double gap = objects.getDistance();
                ObjectEditTrackObject editObject = objects.getObject();
                objects.next();
                duplicatedObjectIndex++;

                // Add towards point
                if (order) {
                    currentDistance += gap;
                } else {
                    currentDistance -= gap;
                }

                // While exceeding, go to next connection, unless no longer possible
                while (order ? (currentDistance >= edgeDistance) : (currentDistance <= edgeDistance)) {
                    if (bestPath == null || nextConnectionIndex < 0) {
                        break objectAddLoop;
                    }

                    // Remove remainder of current connection from distance
                    if (order) {
                        currentDistance -= edgeDistance;
                    } else {
                        currentDistance = (edgeDistance - currentDistance);
                    }

                    TrackConnection nextConnection = bestPath.pathConnections.get(nextConnectionIndex--);
                    order = currentConnection.isConnected(nextConnection.getNodeA());
                    currentConnection = nextConnection;
                    if (currentConnection == point.connection) {
                        edgeDistance = point.distance;
                    } else if (order) {
                        edgeDistance = currentConnection.getFullDistance();
                    } else {
                        edgeDistance = 0.0;
                    }
                    if (!order) {
                        currentDistance = currentConnection.getFullDistance() - currentDistance;
                    }
                }

                // Check if distance matches what is set in our already duplicated objects
                // If not, remove all of them past this point and create a new one
                if (duplicatedObjectIndex < this.duplicatedObjects.size()) {
                    DuplicatedObject dupe = this.duplicatedObjects.get(duplicatedObjectIndex);
                    if (dupe.connection == currentConnection && dupe.object.getDistance() == currentDistance) {
                        continue;
                    }
                    this.undoDuplicatedObjects(duplicatedObjectIndex);
                }

                // Compute flipped 
                boolean flipped = (editObject.alignmentFlipped != order) != objects.isIndexIncreasing();

                // Create a duplicated track object
                DuplicatedObject dupe = DuplicatedObject.create(currentConnection, currentDistance, editObject.object, flipped);

                // Check permissions that we can create this object, but do not yet update history
                // History is updated in one go when we finish editing/duplicating
                if (!dupe.testCanCreate(this.getPlayer())) {
                    break; // abort
                }

                // Create the object
                dupe.add(this.editState.getSelectedAnimation());
                this.duplicatedObjects.add(dupe);
                objectsWereAdded = true;
            }
        }

        // Play a sound cue so the player knows objects were added
        if (objectsWereAdded) {
            PlayerUtil.playSound(getPlayer(), CommonSounds.CLICK_WOOD, 0.5f, 1.0f);
        }

        // Remove excess objects
        this.undoDuplicatedObjects(duplicatedObjectIndex);
    }

    /**
     * Removes all objects that were previously duplicated onto the tracks
     */
    public void undoDuplicatedObjects() {
        if (!this.duplicatedObjects.isEmpty()) {
            for (DuplicatedObject dupe : this.duplicatedObjects) {
                dupe.remove();
            }
            this.duplicatedObjects.clear();

            // Play a sound cue so the player knows objects were removed
            PlayerUtil.playSound(getPlayer(), CommonSounds.ITEM_BREAK, 0.5f, 1.0f);
        }
    }

    private void undoDuplicatedObjects(int startIndex) {
        if (startIndex < this.duplicatedObjects.size()) {
            for (int i = startIndex; i < this.duplicatedObjects.size(); i++) {
                this.duplicatedObjects.get(i).remove();
            }
            this.duplicatedObjects.subList(startIndex, this.duplicatedObjects.size()).clear();

            // Play a sound cue so the player knows objects were removed
            PlayerUtil.playSound(getPlayer(), CommonSounds.ITEM_BREAK, 0.5f, 1.0f);
        }
    }

    /// Makes use of the CSV format to load the selected track object type information
    private TrackObjectType<?> parseType(ConfigurationNode config) {
        if (config.contains("selectedTrackObject")) {
            List<String> values = config.getList("selectedTrackObject", String.class);
            if (values != null && !values.isEmpty()) {
                StringArrayBuffer buffer = new StringArrayBuffer();
                buffer.load(values);
                try {
                    TrackCSV.CSVEntry entry = TrackCSV.decode(buffer);
                    if (entry instanceof TrackCSV.TrackObjectTypeEntry) {
                        TrackCSV.TrackObjectTypeEntry<?> typeEntry = (TrackCSV.TrackObjectTypeEntry<?>) entry;
                        if (typeEntry.objectType != null) {
                            return typeEntry.objectType;
                        }
                    }
                } catch (SyntaxException e) {
                    this.editState.getPlugin().getLogger().log(Level.WARNING, "Failed to load track object type for " +
                            this.getPlayer().getName(), e);
                }
            }
        }
        return TrackObjectTypeItemStack.createDefault();
    }

    /// Moves selected track objects. Direction defines whether to walk to nodeA (false) or nodeB (true).
    private boolean moveObjects(TrackConnection.PointOnPath point, HistoryChange changes, boolean initialDirection, Vector rightDirection) {
        // Create a sorted list of objects to move, with drag distance increasing
        // Only add objects with the same direction
        SortedSet<ObjectEditTrackObject> objects = new TreeSet<ObjectEditTrackObject>(
            (a, b) -> Double.compare(a.dragDistance, b.dragDistance)
        );
        for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
            if (editObject.dragDirection == initialDirection && !Double.isNaN(editObject.dragDistance)) {
                objects.add(editObject);
            }
        }
        if (objects.isEmpty()) {
            return true; // none in this category
        }

        // Optimized: compute up-front
        Vector rightDirectionFlipped = rightDirection.clone().multiply(-1.0);

        // Distance offset based on the point position on the clicked connection
        // This makes the maths easier, as we can just look from the start of the connection
        // This value is initially always a negative number (or 0)
        double distanceOffset = -(initialDirection ? point.distance : (point.connection.getFullDistance() - point.distance));

        // Proceed to walk down the connections relative to the point
        boolean allSuccessful = true;
        ConnectionChain connection = new ConnectionChain(point.connection, initialDirection);
        for (ObjectEditTrackObject object : objects) {
            while (true) {
                // Check if the object can fit within the remaining distance on the current connection
                double objectDistance = object.dragDistance - distanceOffset;
                if (objectDistance < connection.getFullDistance()) {
                    if (!connection.direction) {
                        objectDistance = connection.getFullDistance() - objectDistance;
                    }
                    object.connection.moveObject(object.object, connection.connection, objectDistance,
                            object.beforeDragLookingAtFlipped ? rightDirectionFlipped : rightDirection);
                    object.connection = connection.connection;
                    break; // done!
                }

                distanceOffset += connection.getFullDistance();
                if (!connection.next()) {
                    return allSuccessful; // end reached
                }
            }
        }
        return allSuccessful;
    }
}

