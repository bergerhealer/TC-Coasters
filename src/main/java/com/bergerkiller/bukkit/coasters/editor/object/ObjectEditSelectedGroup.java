package com.bergerkiller.bukkit.coasters.editor.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.bergerkiller.bukkit.coasters.objects.TrackObject;
import com.bergerkiller.bukkit.coasters.tracks.TrackConnection;

/**
 * A group of track objects close to where a player is looking.
 * If multiple objects are positioned at the exact same position,
 * this class groups them together. The player can then cycle through
 * the track objects by clicking multiple times on the same group.
 *
 * Initial selection is for all the objects at once.
 */
public final class ObjectEditSelectedGroup {
    private final TrackConnection connection;
    private final List<SelectedObject> selection;
    private final long creationTime;
    private boolean remembering = false;

    private ObjectEditSelectedGroup(TrackConnection connection, List<SelectedObject> selection) {
        this.connection = connection;
        this.selection = selection;
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Gets the connection on which the selected objects sit
     *
     * @return connection
     */
    public TrackConnection getConnection() {
        return this.connection;
    }

    /**
     * Gets for how many milliseconds this group has been selected
     *
     * @return Milliseconds that elapsed since creating this selected group
     */
    public long getSelectDuration() {
        return System.currentTimeMillis() - this.creationTime;
    }

    /**
     * Gets a mapping of all the track objects that should or should
     * not be selected
     *
     * @return Map of track objects, with as value whether it should be selected
     */
    public Map<TrackObject, Boolean> getSelection() {
        Map<TrackObject, Boolean> result = new LinkedHashMap<>(selection.size());
        for (SelectedObject select : selection) {
            result.put(select.object, select.selected);
        }
        return result;
    }

    /**
     * Gets the values of {@link #getSelection()} in order of when they are cycled.
     *
     * @return Selection states
     */
    public boolean[] getSelectionStates() {
        boolean[] result = new boolean[selection.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = selection.get(i).selected;
        }
        return result;
    }

    /**
     * Gets the first/single track object that is currently selected.
     * Used for updating the UI for the selected track object when
     * cycling through multiple of them.
     *
     * @return Selected TrackObject, or null if none are selected
     */
    public TrackObject getSingleSelection() {
        for (SelectedObject select : selection) {
            if (select.selected) {
                return select.object;
            }
        }
        return null;
    }

    /**
     * Gets whether this group contains the specified track object
     *
     * @param object Track Object
     * @return True if the object is contained
     */
    public boolean containsObject(TrackObject object) {
        for (SelectedObject select : selection) {
            if (select.object == object) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets whether any track objects are inside this group
     *
     * @return True if number of track objects is 0
     */
    public boolean isEmpty() {
        return selection.isEmpty();
    }

    /**
     * Gets the number of track objects that are selected
     *
     * @return number of selected objects
     */
    public int getNumberSelected() {
        int count = 0;
        for (SelectedObject object : selection) {
            if (object.selected) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks whether this group contains the same track objects as another
     *
     * @param group
     * @return True if it is the same group of track objects
     */
    public boolean isSameGroup(ObjectEditSelectedGroup group) {
        if (group == null || this.selection.size() != group.selection.size()) {
            return false;
        }
        for (int n = 0; n < this.selection.size(); n++) {
            if (this.selection.get(n).object != group.selection.get(n).object) {
                return false;
            }
        }
        return true;
    }

    /**
     * Forces all currently selected track objects to be remembered. This causes future
     * calls to {@link #nextSelection()} to keep these selections
     * unchanged while cycling through them.<br>
     * <br>
     * This is called when the player presses the sneak button.
     */
    public void remember() {
        this.remembering = true;
        for (SelectedObject select : selection) {
            select.remember = select.selected;
        }
    }

    /**
     * Explicitly forgets all track objects that may have previously been remembered.
     * This is called when the player stops sneaking again.
     */
    public void forget() {
        this.remembering = false;
        for (SelectedObject select : selection) {
            select.remember = false;
        }
    }

    /**
     * When clicking on the same group of track objects multiple times, this method
     * will cycle through all selection options for the group:
     * <ul>
     * <li>All objects are selected (initially, before nextSelection is called)
     * <li>A single object is selected, in sequence of distance on the path
     * <li>Repeat back to the beginning
     * </ul>
     * If {@link #remember()} was last called, then those selected track objects
     * will remain selected and all non-remembered objects are cycled through.
     */
    public void nextSelection() {
        // Weird?
        if (selection.isEmpty()) {
            return;
        }

        // If only one track object or less, all we handle is the remember logic
        // This allows for toggling a single selection on and off
        if (selection.size() == 1) {
            SelectedObject select = selection.get(0);
            select.selected = !select.remember || !select.selected;
            return;
        }

        // Calculate the number of objects selected that were not already remembered,
        // the number that can still be selected (non-remembered) and the total.
        int numThatCanBeSelected = 0;
        int numSelected = 0;
        int numRemembered = 0;
        int numSelectedTotal = 0;
        for (SelectedObject select : selection) {
            if (select.selected) {
                numSelectedTotal++;
            }
            if (select.remember) {
                numRemembered++;
            } else {
                numThatCanBeSelected++;
                if (select.selected) {
                    numSelected++;
                }
            }
        }

        // If number that can be selected is 0, then we've already remembered all of them
        // At that point all this will do is toggle the entire group on and off
        if (numThatCanBeSelected == 0) {
            boolean selectOrDeselectAll = (numSelectedTotal == 0);
            for (SelectedObject select : selection) {
                select.selected = selectOrDeselectAll;
            }
            return;
        }

        // If only one object can still be selected, toggle this one object on and off
        if (numThatCanBeSelected == 1) {
            for (SelectedObject select : selection) {
                if (!select.remember) {
                    select.selected = !select.selected;
                }
            }
            return;
        }

        // If all that can be selected are selected, cycle around (loop) and select only the first
        // Also do this is none are selected, and we are remembering previous selections
        if (numSelected == numThatCanBeSelected || (numSelected == 0 && numRemembered > 0)) {
            boolean selectNextObject = true;
            for (SelectedObject select : selection) {
                if (!select.remember) {
                    select.selected = selectNextObject;
                    selectNextObject = false;
                }
            }
            return;
        }

        // If exactly one is selected, cycle between the nodes until reaching the end
        // If end is reached, the default logic is done (select all of them)
        if (numSelected == 1) {
            boolean hasDoneNextSelection = false;
            boolean selectNext = false;
            for (SelectedObject select : selection) {
                if (!select.remember) {
                    if (hasDoneNextSelection) {
                        select.selected = false;
                    } else if (selectNext) {
                        select.selected = true;
                        hasDoneNextSelection = true;
                    } else if (select.selected) {
                        select.selected = false;
                        selectNext = true;
                    }
                }
            }
            if (hasDoneNextSelection) {
                return;
            }

            // If remembering, cycle through de-selecting everything
            if (this.remembering) {
                for (SelectedObject select : selection) {
                    if (!select.remember) {
                        select.selected = false;
                    }
                }
                return;
            }
        }

        // Select all we have not set to remember
        for (SelectedObject select : selection) {
            if (!select.remember) {
                select.selected = true;
            }
        }
    }

    /**
     * Finds all the track objects grouped together near to a point on a connection path.
     * If the point is 4.0 distance or less to an object, but not right on an object,
     * then it will select all the objects close to that object.<br>
     * <br>
     * By default all objects will be de-selected. Call {@link #nextSelection()} to start
     * selecting them.
     *
     * @param point Point near to which to find objects
     * @return group of track objects found
     */
    public static ObjectEditSelectedGroup findNear(TrackConnection.PointOnPath point) {
        final List<TrackObject> allObjects = point.connection.getObjects();
        if (allObjects.isEmpty()) {
            return new ObjectEditSelectedGroup(point.connection, Collections.emptyList());
        }

        // If nothing is very closeby to where the player clicked, allow for a distance of 4.0 away
        // It will pick the best alternative track object found, and from there, all that is close to it
        final double groupDistanceThreshold = 0.2; // Objects 0.2 distance apart are grouped together
        double altBestDistance = 4.0; // No further than 4 blocks away
        TrackObject altBestObject = null;
        List<SelectedObject> result = new ArrayList<>(Math.min(16, allObjects.size()));
        for (TrackObject object : allObjects) {
            double distance = Math.abs(object.getDistance() - point.distance);
            if (distance < groupDistanceThreshold) {
                result.add(new SelectedObject(object));
            } else if (result.isEmpty() && distance < altBestDistance) {
                altBestDistance = distance;
                altBestObject = object;
            }
        }

        // Select all objects near to the alternative best object if player didn't click near to one
        if (result.isEmpty() && altBestObject != null) {
            for (TrackObject object : allObjects) {
                double distance = Math.abs(object.getDistance() - altBestObject.getDistance());
                if (distance < groupDistanceThreshold) {
                    result.add(new SelectedObject(object));
                }
            }
        }

        return new ObjectEditSelectedGroup(point.connection, result);
    }

    private static final class SelectedObject {
        /**
         * Track object that is selected or not
         */
        public final TrackObject object;
        /**
         * Whether this object is currently selected
         */
        public boolean selected;
        /**
         * Whether this object was initially selected before a sneak-add
         * operation was done. This means it should stay selected.
         */
        public boolean remember;

        public SelectedObject(TrackObject object) {
            this.object = object;
            this.selected = false;
            this.remember = false;
        }
    }
}
