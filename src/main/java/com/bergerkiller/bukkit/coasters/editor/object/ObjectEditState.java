package com.bergerkiller.bukkit.coasters.editor.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
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
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeSearchPath;
import com.bergerkiller.bukkit.coasters.world.CoasterWorld;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

public class ObjectEditState {
    private final PlayerEditState editState;
    private final Map<TrackObject, ObjectEditTrackObject> editedTrackObjects = new LinkedHashMap<TrackObject, ObjectEditTrackObject>();
    private final List<DuplicatedObject> duplicatedObjects = new ArrayList<DuplicatedObject>();
    private TrackObject lastEditedTrackObject = null;
    private long lastEditTrackObjectTime = System.currentTimeMillis();
    private boolean isDuplicating = false;
    private ItemStack selectedItem;

    public ObjectEditState(PlayerEditState editState) {
        this.editState = editState;
        this.selectedItem = new ItemStack(MaterialUtil.getFirst("RAIL", "LEGACY_RAILS"));
    }

    public ItemStack getSelectedItem() {
        return this.selectedItem;
    }

    public void setSelectedItem(ItemStack item) {
        this.selectedItem = item;
    }

    public void load(ConfigurationNode config) {
        ItemStack item = config.get("item", ItemStack.class);
        if (item != null) {
            this.selectedItem = item;
        }
    }

    public void save(ConfigurationNode config) {
        if (this.selectedItem == null) {
            config.remove("item");
        } else {
            config.set("item", this.selectedItem);
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
            ArrayList<TrackObject> oldObjects = new ArrayList<TrackObject>(this.getEditedObjects());
            this.editedTrackObjects.clear();

            //this.editedAnimationNamesChanged |= !this.editedNodesByAnimationName.isEmpty();
            //this.editedNodesByAnimationName.clear();
            //this.lastEdited = null;

            this.editState.markChanged();
            for (TrackObject oldObject : oldObjects) {
                oldObject.onStateUpdated(this.getPlayer());
            }
        }
    }

    public boolean onLeftClick() {
        // Single object selection mode
        if (!this.editState.isSneaking()) {
            clearEditedTrackObjects();
        }

        // Find point on path clicked
        TrackConnection.PointOnPath point = this.getWorld().getTracks().findPointOnPath(this.getPlayer().getEyeLocation());
        if (point == null) {
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
            return true;
        }

        long lastEditTime = getLastEditTime(bestObject);
        if (lastEditTime > 300) {
            // Toggle edit state
            if (isEditing(bestObject)) {
                setEditingTrackObject(point.connection, bestObject, false);
            } else {
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
            }
        } else {
            changed = (this.editedTrackObjects.remove(object) != null);
        }
        if (changed) {
            // Can be caused by the node being removed, handle that here
            object.onStateUpdated(this.getPlayer());

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
                editObject.object.onStateUpdated(this.getPlayer());
                this.lastEditedTrackObject = editObject.object;
            }
        }
        if (hadLockedTrackObjects) {
            this.lastEditTrackObjectTime = System.currentTimeMillis();
            this.editState.markChanged();
            TCCoastersLocalization.LOCKED.message(this.getPlayer());
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
                object.object.onStateUpdated(this.getPlayer());
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
        // Find point looked at
        TrackConnection.PointOnPath point = this.getWorld().getTracks().findPointOnPath(this.editState.getInput().get());
        if (point == null) {
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
            TrackObject object = new TrackObject(point.distance, this.getSelectedItem(), flipped);
            this.editState.getHistory().addChangeBeforeCreateTrackObject(this.getPlayer(), point.connection, object);

            point.connection.addObject(object);
            point.connection.addObjectToAnimationStates(this.editState.getSelectedAnimation(), object);
            return;
        }

        // Deselect objects we cannot edit before proceeding
        this.deselectLockedObjects();

        if (this.editState.getHeldDownTicks() == 0) {
            // First click: calculate the positions of the objects relative to the clicked point
            // If objects aren't accessible from the point, then their dragDistance is set to NaN

            // When sneaking during initial right-click, enable duplicating mode
            this.isDuplicating = this.editState.isSneaking();

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
                editObject.beforeDragConnection = editObject.connection;
                editObject.beforeDragDistance = editObject.object.getDistance();
                editObject.beforeDragFlipped = editObject.object.isFlipped();

                // Looking at the object from left-to-right or right-to-left?
                // This is important when moving the object later
                TrackConnection.PointOnPath beforePoint = editObject.connection.findPointAtDistance(editObject.beforeDragDistance);
                editObject.beforeDragLookingAtFlipped = (beforePoint.orientation.rightVector().dot(rightDirection) < 0.0);
            }
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
            }
        }

        // Remove excess objects
        this.undoDuplicatedObjects(duplicatedObjectIndex);
    }

    /**
     * Removes all objects that were previously duplicated onto the tracks
     */
    public void undoDuplicatedObjects() {
        for (DuplicatedObject dupe : this.duplicatedObjects) {
            dupe.remove();
        }
        this.duplicatedObjects.clear();
    }

    private void undoDuplicatedObjects(int startIndex) {
        for (int i = startIndex; i < this.duplicatedObjects.size(); i++) {
            this.duplicatedObjects.get(i).remove();
        }
        this.duplicatedObjects.subList(startIndex, this.duplicatedObjects.size()).clear();
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

