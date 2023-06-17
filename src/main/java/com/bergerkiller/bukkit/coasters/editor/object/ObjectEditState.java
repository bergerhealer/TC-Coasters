package com.bergerkiller.bukkit.coasters.editor.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import java.util.stream.Collectors;

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
import com.bergerkiller.bukkit.coasters.events.CoasterAfterChangeTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterBeforeChangeTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.events.CoasterSelectTrackObjectEvent;
import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectType;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeFallingBlock;
import com.bergerkiller.bukkit.coasters.objects.TrackObjectTypeArmorStandItem;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSearchPath;
import com.bergerkiller.bukkit.coasters.util.StringArrayBuffer;
import com.bergerkiller.bukkit.coasters.util.SyntaxException;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.PlayerUtil;

public class ObjectEditState {
    private final PlayerEditState editState;
    private final Map<TrackObject, ObjectEditTrackObject> editedTrackObjects = new LinkedHashMap<TrackObject, ObjectEditTrackObject>();
    private final List<DuplicatedObject> duplicatedObjects = new ArrayList<DuplicatedObject>();
    private final List<DragListener> dragListeners = new ArrayList<DragListener>();
    private ObjectEditSelectedGroup lastEditedGroup = null;
    private double dragListenersDistanceToObjects = 0.0;
    private boolean isDragControlEnabled = true;
    private boolean isDraggingObjects = false;
    private boolean isPreDuplicating = false; // If true, player just left-clicked, and single or mass duplicating needs to be decided
    private boolean isDuplicating = false; // If true, creates a trail of duplicates where the player looks
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
     * Reads a property of the selected track object type, if the track object type is of the given
     * type specified. Also works if the type specified here is a subclass.
     * 
     * @param <T> Type of track object
     * @param <V> Value type to return
     * @param type Type Class of the track object
     * @param property The property of the track object to read
     * @param defaultValue Default value to return if the selected type is not this type
     * @return property value
     */
    public <T, V> V getSelectedTypeProperty(Class<T> type, Function<T, V> property, V defaultValue) {
        if (type.isAssignableFrom(this.getSelectedType().getClass())) {
            return property.apply(CommonUtil.unsafeCast(this.getSelectedType()));
        } else {
            return defaultValue;
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
        this.isDragControlEnabled = config.get("dragControl", true);
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
        config.set("dragControl", this.isDragControlEnabled);
    }

    /**
     * Sets whether right-click drag map menu input control is enabled
     * 
     * @param enabled
     */
    public void setDragControlEnabled(boolean enabled) {
        this.isDragControlEnabled = enabled;
        this.editState.markChanged();
    }

    /**
     * Gets whether right-click drag map menu input control is enabled
     * 
     * @return True if enabled
     */
    public boolean isDragControlEnabled() {
        return this.isDragControlEnabled;
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
        this.lastEditedGroup = null;
        if (!this.editedTrackObjects.isEmpty()) {
            ArrayList<ObjectEditTrackObject> oldObjects = new ArrayList<ObjectEditTrackObject>(this.editedTrackObjects.values());
            this.editedTrackObjects.clear();

            //this.editedAnimationNamesChanged |= !this.editedNodesByAnimationName.isEmpty();
            //this.editedNodesByAnimationName.clear();
            //this.lastEdited = null;

            this.editState.markChanged();
            for (ObjectEditTrackObject oldObject : oldObjects) {
                if (!oldObject.isRemoved()) {
                    oldObject.object.onStateUpdated(oldObject.connection, this.editState);
                }
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

    public void onSneakingChanged(boolean sneaking) {
        if (this.lastEditedGroup != null) {
            if (sneaking) {
                this.lastEditedGroup.remember();
            } else {
                this.lastEditedGroup.forget();
            }
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
                if (this.isPreDuplicating) {
                    // Player left-clicked once before, didn't move cursor, then clicked again
                    // We assume the player wanted to cancel duplicating altogether, so switch back
                    this.isPreDuplicating = false;
                } else if (this.isDuplicating) {
                    // Cancel duplicating and switch back to dragging mode
                    this.undoDuplicatedObjects();
                    this.startDrag(point, false);
                } else if (this.editedTrackObjects.size() == 1) {
                    // If only one object is selected, duplicate it and resume dragging the original object
                    // No need for the isPreDuplicating stage in that case
                    duplicateObjectsOnce();
                } else {
                    // Set a flag that we are about to start duplicating mode
                    // Once the player moves the cursor away, actual duplicating starts
                    this.isPreDuplicating = true;
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
        ObjectEditSelectedGroup group = ObjectEditSelectedGroup.findNear(point);
        if (group.isEmpty()) {
            if (!isMultiSelect) {
                clearEditedTrackObjects();
            }
            return true;
        }

        if (!group.isSameGroup(this.lastEditedGroup)) {
            // New group selected, if not multi-selecting clear the previous selection
            if (!isMultiSelect) {
                clearEditedTrackObjects();
            }

            // Initially none are selected, so we just state that we're in 'remember mode'
            // This is important so that when cycling through it goes through the no-selection phase
            if (isMultiSelect) {
                group.remember();
            }

            // Assign and update
            this.lastEditedGroup = group;
            this.lastEditedGroup.nextSelection(); // Selects all
            this.setEditingUsingGroup(group);
        } else if (this.lastEditedGroup.getSelectDuration() > 300) {
            // Not double-clicking, update the selection
            if (!isMultiSelect) {
                // In case we desync-d, make sure to forget when not sneaking
                this.lastEditedGroup.forget();

                // De-select all track objects not part of this group
                List<ObjectEditTrackObject> objectsToDeselect = this.editedTrackObjects.values().stream()
                        .filter(o -> o.connection != group.getConnection() || !group.containsObject(o.object))
                        .collect(Collectors.toList());
                for (ObjectEditTrackObject selectedObject : objectsToDeselect) {
                    this.deselectTrackObject(selectedObject.connection, selectedObject.object);
                }
            }

            // Cycle
            this.lastEditedGroup.nextSelection();
            this.setEditingUsingGroup(this.lastEditedGroup);
        } else {
            // Double-click mass-selection mode
            if (this.editState.isSneaking()) {
                // Select all objects between the clicked object and the nearest other selected track object
                this.floodSelectNearest(group.getConnection(), group.getSelection().keySet());
            } else {
                // Flood-fill select all nodes connected from bestObject
                this.floodSelectObjects(group.getConnection(), group.getSelection().keySet().iterator().next());
            }
        }

        return true;
    }

    /**
     * Called when the Player released the right-click button while the Track Object menu is active
     *
     * @throws ChangeCancelledException
     */
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
                    dupe.connection.saveObjectsToAnimationStates(this.editState.getSelectedAnimation());
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
                    // Make and verify the changes
                    changes.addChangeAfterMovingTrackObject(this.getPlayer(), editObject.connection, editObject.object,
                            editObject.beforeDragConnection, editObject.beforeDragObject);

                    // Refresh animation states too!
                    editObject.connection.saveObjectsToAnimationStates(this.editState.getSelectedAnimation());
                    if (editObject.beforeDragConnection != editObject.connection) {
                        editObject.beforeDragConnection.saveObjectsToAnimationStates(this.editState.getSelectedAnimation());
                    }
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

        // Player pressed left-click once while holding right click in the past
        // Player then released right-click again. This is single-duplicating mode,
        // so duplicate the selected track objects and done.
        if (this.isPreDuplicating) {
            this.isPreDuplicating = false;
            this.duplicateObjectsOnce();
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
            throw new IllegalArgumentException("Track object cannot be null");
        }
        if (this.editedTrackObjects.containsKey(object)) {
            return true;
        }

        if (CommonUtil.callEvent(new CoasterSelectTrackObjectEvent(this.getPlayer(), connection, object)).isCancelled()) {
            return false;
        }

        // Select it
        this.editedTrackObjects.put(object, new ObjectEditTrackObject(connection, object));
        this.selectedType = object.getType();

        // Can be caused by the object being removed, handle that here
        if (object.isAdded()) {
            object.onStateUpdated(connection, this.editState);
            this.editState.markChanged();
        }

        // May have caused a particle visibility change
        getWorld().getParticles().scheduleViewerUpdate(this.getPlayer());

        return true;
    }

    /**
     * Deselects a track object for editing, if it was previously selected.
     * Invalidates the current selection.
     *
     * @param connection
     * @param object
     * @return True if the object was indeed selected, and is now deselected
     */
    public boolean deselectTrackObject(TrackConnection connection, TrackObject object) {
        if (object == null) {
            throw new IllegalArgumentException("Track object cannot be null");
        }

        // Invalidate group as it now contains an object that no longer exists
        if (this.lastEditedGroup != null && this.lastEditedGroup.containsObject(object)) {
            this.lastEditedGroup = null;
        }

        // Remove selection
        if (this.editedTrackObjects.remove(object) == null) {
            return false;
        }

        // May have caused a particle visibility change
        getWorld().getParticles().scheduleViewerUpdate(this.getPlayer());
        return true;
    }

    /**
     * Selects and de-selects track objects based on a track object selected group.
     *
     * @param group
     */
    public void setEditingUsingGroup(ObjectEditSelectedGroup group) {
        // Validation
        if (group == null) {
            throw new IllegalArgumentException("Track Object Group cannot be null");
        } else if (group.isEmpty()) {
            return;
        }

        // Update selected objects state
        boolean changed = false;
        Map<TrackObject, Boolean> selection = group.getSelection();
        for (Map.Entry<TrackObject, Boolean> entry : selection.entrySet()) {
            TrackObject object = entry.getKey();
            if (!entry.getValue()) {
                // Deselect
                changed |= (this.editedTrackObjects.remove(object) != null);
            } else if (!this.editedTrackObjects.containsKey(object)) {
                // Select
                // Fire select event to see if the player can actually select this one
                CoasterSelectTrackObjectEvent event = new CoasterSelectTrackObjectEvent(
                        this.getPlayer(), group.getConnection(), object);
                if (!CommonUtil.callEvent(event).isCancelled()) {
                    changed = true;
                    this.editedTrackObjects.put(object, new ObjectEditTrackObject(group.getConnection(), object));
                    this.selectedType = object.getType();
                }
            }
        }

        // If changed, do a bunch of stuff
        if (changed) {
            for (TrackObject object : selection.keySet()) {
                // Can be caused by the object being removed, handle that here
                if (!object.isAdded()) {
                    continue;
                }

                object.onStateUpdated(group.getConnection(), this.editState);
                this.editState.markChanged();
            }

            // May have caused a particle visibility change
            getWorld().getParticles().scheduleViewerUpdate(this.getPlayer());
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
            }
        }
        if (hadLockedTrackObjects) {
            this.lastEditedGroup = null;
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
            if (object.isRemoved()) {
                continue;
            }
            try {
                this.editState.getHistory().addChangeBeforeDeleteTrackObject(this.getPlayer(), object.connection, object.object);
                object.connection.removeObject(object.object);
                object.connection.saveObjectsToAnimationStates(this.editState.getSelectedAnimation());
            } catch (ChangeCancelledException ex) {
                wereChangesCancelled = true;

                // Refresh, but the event could've removed the object anyway, so check!
                if (!object.isRemoved()) {
                    object.object.onStateUpdated(object.connection, this.editState);
                }
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
            point.connection.saveObjectsToAnimationStates(this.editState.getSelectedAnimation());
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
        } else if (this.isPreDuplicating) {
            // Player held right-click and left-clicked once. We are now testing to see
            // if the player is starting to drag the cursor around, indicating the player wants
            // to start duplicating. If not, we do nothing, and on release the selected objects
            // are duplicated only once.
            this.undoDuplicatedObjects();
            if (isMovingObjects(point, false) || isMovingObjects(point, true)) {
                // Finish the drag action and switch to duplicate mode
                this.isPreDuplicating = false; // Cancel
                try {
                    this.onEditingFinished();
                    this.startDrag(point, true);
                } catch (ChangeCancelledException e) {
                    this.clearEditedTrackObjects();
                }
            }
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

    /**
     * Flips the orientation of the selected objects 180 degrees
     * 
     * @throws ChangeCancelledException
     */
    public void flipObject() throws ChangeCancelledException {
        List<ObjectEditTrackObject> objects = new ArrayList<ObjectEditTrackObject>(this.editedTrackObjects.values());
        for (ObjectEditTrackObject editObject : objects) {
            // Object may have been removed from the connection by now
            if (editObject.isRemoved()) {
                this.editedTrackObjects.remove(editObject.object);
                continue;
            }

            if (CommonUtil.callEvent(new CoasterBeforeChangeTrackObjectEvent(getPlayer(),
                                                                             editObject.connection,
                                                                             editObject.object)).isCancelled())
            {
                throw new ChangeCancelledException();
            }

            // Object may have been removed during the CoasterBeforeChangeTrackObjectEvent
            if (editObject.isRemoved()) {
                this.editedTrackObjects.remove(editObject.object);
                continue;
            }

            TrackObject old_object = editObject.object.clone();
            editObject.object.setDistanceFlipped(editObject.connection,
                                                 editObject.object.getDistance(),
                                                 !editObject.object.isFlipped());

            if (CommonUtil.callEvent(new CoasterAfterChangeTrackObjectEvent(getPlayer(),
                                                                            editObject.connection,
                                                                            editObject.object,
                                                                            editObject.connection,
                                                                            old_object)).isCancelled())
            {
                // Object may have been removed during the CoasterBeforeChangeTrackObjectEvent
                if (!editObject.isRemoved()) {
                    editObject.object.setDistanceFlipped(editObject.connection,
                                                         old_object.getDistance(),
                                                         old_object.isFlipped());
                }
                throw new ChangeCancelledException();
            }

            editObject.connection.saveObjectsToAnimationStates(this.editState.getSelectedAnimation());
        }
    }

    private void updateDragListeners() {
        if (!this.isDragControlEnabled()) {
            return;
        }
        if (this.editState.getHeldDownTicks() == 0) {
            this.dragListenersDistanceToObjects = this.computeDistanceToObjects();
        }
        if (!this.dragListeners.isEmpty()) {
            Quaternion rotationDiff = this.editState.getInput().delta().getRotation();
            double delta = -rotationDiff.getPitch() / 90.0 + rotationDiff.getYaw() / 90.0;
            delta *= this.dragListenersDistanceToObjects;
            delta *= 5.0;
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
        double minDistSq = 160.0 * 160.0; // 10 chunks as max
        for (ObjectEditTrackObject object : this.editedTrackObjects.values()) {
            double theta = object.connection.findPointThetaAtDistance(object.object.getDistance());
            Vector pos = object.connection.getPosition(theta);
            minDistSq = Math.min(minDistSq, pos.distanceSquared(loc));
        }
        return Math.sqrt(minDistSq);
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
        this.isPreDuplicating = false;
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
            editObject.beforeDragObject = editObject.object.clone();

            // Player looks into a certain direction, which changes the orientation of the objects
            Vector rightDirection = this.editState.getInput().get().getRotation().forwardVector();

            // Looking at the object from left-to-right or right-to-left?
            // This is important when moving the object later
            TrackConnection.PointOnPath beforePoint = editObject.connection.findPointAtDistance(editObject.beforeDragObject.getDistance());
            editObject.beforeDragLookingAtFlipped = editObject.object.isFlipped() != (beforePoint.orientation.rightVector().dot(rightDirection) < 0.0);
        }
    }

    /**
     * Selects all the track objects of all connections accessible from the start connection,
     * that are between any of the startObjects cluster and the closest other selected track object.
     * The selected track objects are added and the previous selection is kept.
     * 
     * @param startConnection
     * @param startObjects
     */
    public void floodSelectNearest(TrackConnection startConnection, Collection<TrackObject> startObjects) {
        // Protect me!
        if (startObjects.isEmpty()) {
            return;
        }

        // If there is a selected track object with the same connection, then we can optimize this
        // This is technically incorrect, because we could be at the end/start of the connection, with
        // another selected object on a connection next to it. For now, this is good enough, really.
        // Normally people only select a single object prior to flood-selecting anyway.
        {
            TrackObject closestObjectOnSameConnection = null;
            TrackObject closestObjectOnSameConnectionStart = null;
            double closestDistance = Double.MAX_VALUE;
            for (ObjectEditTrackObject editObject : this.editedTrackObjects.values()) {
                if (editObject.connection == startConnection && !startObjects.contains(editObject.object)) {
                    for (TrackObject startObject : startObjects) {
                        double distance = Math.abs(startObject.getDistance() - editObject.object.getDistance());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestObjectOnSameConnection = editObject.object;
                            closestObjectOnSameConnectionStart = startObject;
                        }
                    }
                }
            }
            if (closestObjectOnSameConnection != null) {
                // Add all objects in between the distance range
                double d_from = Math.min(closestObjectOnSameConnectionStart.getDistance(), closestObjectOnSameConnection.getDistance());
                double d_to   = Math.max(closestObjectOnSameConnectionStart.getDistance(), closestObjectOnSameConnection.getDistance());
                for (TrackObject object : startConnection.getObjects()) {
                    if (object.getDistance() >= d_from && object.getDistance() <= d_to) {
                        this.selectTrackObject(startConnection, object);
                    }
                }
                return; // done.
            }
        }

        // Make use of the node flood selecting, using the nodes from the objects we have selected
        // For start node, we pick the node closest to the starting objects
        List<ObjectEditTrackObject> editedTrackObjects = new ArrayList<ObjectEditTrackObject>(this.editedTrackObjects.values());
        HashSet<TrackNode> nodesOfTrackObjects = new HashSet<TrackNode>();
        for (ObjectEditTrackObject editObject : editedTrackObjects) {
            if (!startObjects.contains(editObject.object)) {
                nodesOfTrackObjects.add(editObject.connection.getNodeA());
                nodesOfTrackObjects.add(editObject.connection.getNodeB());
            }
        }
        double searchStartDistance = startObjects.iterator().next().getDistance(); //TODO: Is this OK?
        boolean searchRight = (searchStartDistance >= 0.5 * startConnection.getFullDistance());
        TrackNode searchStart = searchRight ? startConnection.getNodeB() : startConnection.getNodeA();
        TrackNodeSearchPath bestPath = TrackNodeSearchPath.findShortest(searchStart, nodesOfTrackObjects);

        // Now do stuff with the found path, if found
        if (bestPath != null) {
            // If best path contains the other node of the start connection, then fill select to the right
            // Otherwise, select all objects to the left of the start object
            boolean searchWentRight = searchRight == (bestPath.path.contains(startConnection.getOtherNode(searchStart)));
            this.selectObjectsBeyondDistance(startConnection, searchWentRight, searchStartDistance);
            bestPath.pathConnections.remove(startConnection);

            // The current node is one that has a connection with one of the track objects we had selected
            // We need to find the connection of the node which is closest to it
            TrackConnection bestConnection = null;
            double bestObjectDistance = 0.0;
            boolean bestObjectDirection = false;
            double bestObjectDistanceToEnd = Double.MAX_VALUE;
            for (ObjectEditTrackObject editObject : editedTrackObjects) {
                if (startObjects.contains(editObject.object)) {
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
     * Creates a duplicate of all track objects currently selected.
     * Leaves the original track objects selected, so they can be moved into position.
     */
    public void duplicateObjectsOnce() {
        // Duplicate all edited objects
        ArrayList<ObjectEditTrackObject> objectsToDupe = new ArrayList<>(this.editedTrackObjects.values());
        for (ObjectEditTrackObject editObject : objectsToDupe) {
            editObject.connection.addObject(editObject.object.clone());
        }

        // Play a sound cue so the player knows an object was placed
        PlayerUtil.playSound(getPlayer(), SoundEffect.CLICK_WOOD, 0.1f, 1.0f);
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
            PlayerUtil.playSound(getPlayer(), SoundEffect.CLICK_WOOD, 0.1f, 1.0f);
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
            PlayerUtil.playSound(getPlayer(), SoundEffect.ITEM_BREAK, 0.1f, 1.0f);
        }
    }

    private void undoDuplicatedObjects(int startIndex) {
        if (startIndex < this.duplicatedObjects.size()) {
            for (int i = startIndex; i < this.duplicatedObjects.size(); i++) {
                this.duplicatedObjects.get(i).remove();
            }
            this.duplicatedObjects.subList(startIndex, this.duplicatedObjects.size()).clear();

            // Play a sound cue so the player knows objects were removed
            PlayerUtil.playSound(getPlayer(), SoundEffect.ITEM_BREAK, 0.1f, 1.0f);
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
        return TrackObjectTypeArmorStandItem.createDefault();
    }

    private SortedSet<ObjectEditTrackObject> computeDraggedObjects(boolean initialDirection) {
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
        return objects;
    }

    /// Tests to see if the player changes the cursor enough to indicate the player is moving the objects around
    /// Used to decide whether or not to switch to duplicating mode
    private boolean isMovingObjects(TrackConnection.PointOnPath point, boolean initialDirection) {
        SortedSet<ObjectEditTrackObject> objects = computeDraggedObjects(initialDirection);
        if (objects.isEmpty()) {
            return false; // none in this category
        }

        // Distance offset based on the point position on the clicked connection
        // This makes the maths easier, as we can just look from the start of the connection
        // This value is initially always a negative number (or 0)
        double distanceOffset = -(initialDirection ? point.distance : (point.connection.getFullDistance() - point.distance));

        // Proceed to walk down the connections relative to the point
        ConnectionChain connection = new ConnectionChain(point.connection, initialDirection);
        for (ObjectEditTrackObject object : objects) {
            while (true) {
                // Check if the object can fit within the remaining distance on the current connection
                double objectDistance = object.dragDistance - distanceOffset;
                if (objectDistance < connection.getFullDistance()) {
                    if (!connection.direction) {
                        objectDistance = connection.getFullDistance() - objectDistance;
                    }
                    if (object.connection != connection.connection) {
                        return true; // Different connection, so yeah, we're moving it
                    }
                    if (Math.abs(object.object.getDistance() - objectDistance) > 0.1) {
                        return true; // Moved significantly enough to matter
                    }
                    break; // done!
                }

                distanceOffset += connection.getFullDistance();
                if (!connection.next()) {
                    return false; // end reached
                }
            }
        }
        return false;
    }

    /// Moves selected track objects. Direction defines whether to walk to nodeA (false) or nodeB (true).
    private boolean moveObjects(TrackConnection.PointOnPath point, HistoryChange changes, boolean initialDirection, Vector rightDirection) {
        SortedSet<ObjectEditTrackObject> objects = computeDraggedObjects(initialDirection);
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

