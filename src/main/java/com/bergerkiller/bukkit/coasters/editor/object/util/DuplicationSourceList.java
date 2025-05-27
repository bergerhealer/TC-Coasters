package com.bergerkiller.bukkit.coasters.editor.object.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.bergerkiller.bukkit.coasters.editor.object.ObjectEditTrackObject;

/**
 * Stores track objects to be duplicated in the order in which they should be placed.
 * Automatically computes the right duplication order and distances between the objects.
 */
public class DuplicationSourceList {
    public static final double MIN_REPEAT_SPACING = 1e-2;
    private final List<ObjectEditTrackObject> sourceObjects;
    private int index;
    private boolean indexIncreasing;

    public DuplicationSourceList(Collection<ObjectEditTrackObject> inSourceObjects) {
        this.sourceObjects = new ArrayList<>(inSourceObjects.size());
        for (ObjectEditTrackObject editObject : inSourceObjects) {
            if (!Double.isNaN(editObject.dragDistance)) {
                this.sourceObjects.add(editObject);
            }
        }
        this.sourceObjects.sort(Comparator.comparingDouble(ObjectEditTrackObject::getDistancePosition));
    }

    public List<ObjectEditTrackObject> list() {
        return this.sourceObjects;
    }

    public boolean isIndexIncreasing() {
        return this.indexIncreasing;
    }

    public boolean isValidSelection() {
        if (this.sourceObjects.size() < 2) {
            return false;
        }

        double pmin = this.sourceObjects.get(0).getDistancePosition();
        double pmax = this.sourceObjects.get(this.sourceObjects.size()-1).getDistancePosition();
        if ((pmax - pmin) < MIN_REPEAT_SPACING) {
            return false; // too close together
        }

        return true;
    }

    public static boolean isMultipleSameTrackObject(Collection<ObjectEditTrackObject> objects) {
        Iterator<ObjectEditTrackObject> iter = objects.iterator();
        if (!iter.hasNext()) {
            return false;
        }

        ObjectEditTrackObject first = iter.next();
        if (!iter.hasNext()) {
            return false;
        }

        do {
            ObjectEditTrackObject other = iter.next();
            if (
                    first.connection != other.connection
                    || Math.abs(first.object.getDistance() - other.object.getDistance()) >= MIN_REPEAT_SPACING
                    || !first.object.getType().equals(other.object.getType())
            ) {
                return false;
            }
        } while (iter.hasNext());

        return true;
    }

    public boolean start(ObjectEditTrackObject editObject) {
        if (editObject == this.sourceObjects.get(0)) {
            this.index = this.sourceObjects.size()-2;
            this.indexIncreasing = false;
            return true;
        } else if (editObject == this.sourceObjects.get(this.sourceObjects.size()-1)) {
            this.index = 1;
            this.indexIncreasing = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Call after placing an object to begin with the next one
     */
    public void next() {
        if (this.indexIncreasing) {
            if (++this.index >= this.sourceObjects.size()) {
                this.index = 1;
            }
        } else {
            if (--this.index < 0) {
                this.index = this.sourceObjects.size()-2;
            }
        }
    }

    /**
     * Gets the next object to place down
     * 
     * @return next object
     */
    public ObjectEditTrackObject getObject() {
        return this.sourceObjects.get(this.index);
    }

    /**
     * Gets the distance step towards placing the next object
     * 
     * @return next distance
     */
    public double getDistance() {
        ObjectEditTrackObject a, b;
        if (this.indexIncreasing) {
            a = this.sourceObjects.get(this.index - 1);
            b = this.sourceObjects.get(this.index);
        } else {
            a = this.sourceObjects.get(this.index);
            b = this.sourceObjects.get(this.index + 1);
        }
        return b.getDistancePosition() - a.getDistancePosition();
    }
}
