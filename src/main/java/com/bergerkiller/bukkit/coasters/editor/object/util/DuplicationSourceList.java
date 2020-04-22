package com.bergerkiller.bukkit.coasters.editor.object.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.bergerkiller.bukkit.coasters.editor.object.ObjectEditTrackObject;

/**
 * Stores track objects to be duplicated in the order in which they should be placed.
 * Automatically computes the right duplication order and distances between the objects.
 */
public class DuplicationSourceList {
    private final List<ObjectEditTrackObject> sourceObjects;
    private int index;
    private boolean indexIncreasing;

    public DuplicationSourceList(Collection<ObjectEditTrackObject> inSourceObjects) {
        this.sourceObjects = new ArrayList<ObjectEditTrackObject>(inSourceObjects.size());
        for (ObjectEditTrackObject editObject : inSourceObjects) {
            if (!Double.isNaN(editObject.dragDistance)) {
                this.sourceObjects.add(editObject);
            }
        }
        this.sourceObjects.sort((a, b) -> Double.compare(a.getDistancePosition(), b.getDistancePosition()));
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
        if ((pmax - pmin) < 1e-3) {
            return false; // too close together
        }

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
