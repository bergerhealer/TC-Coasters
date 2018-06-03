package com.bergerkiller.bukkit.coasters.editor.history;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import com.bergerkiller.bukkit.coasters.world.CoasterWorldAccess;

/**
 * Single point in history which can be reverted or re-done.
 */
public abstract class HistoryChange extends HistoryChangeCollection {
    private final List<HistoryChange> children = new LinkedList<HistoryChange>();
    protected final CoasterWorldAccess world;

    public HistoryChange(CoasterWorldAccess world) {
        this.world = world;
    }

    /**
     * To be implemented to perform the changes
     * 
     * @param undo whether undoing instead of redoing
     */
    protected abstract void run(boolean undo);

    /**
     * Performs the history change
     */
    public final void redo() {
        this.run(false);
        for (HistoryChange child : this.children) {
            child.redo();
        }
    }

    /**
     * Undoes the history change
     */
    public final void undo() {
        ListIterator<HistoryChange> iter = this.children.listIterator(this.children.size());
        while (iter.hasPrevious()) {
            iter.previous().undo();
        }
        this.run(true);
    }

    /**
     * Adds a child change to be performed after this change is performed ({@link #redo()}).
     * When undoing, the child changes are performed in reverse order beforehand.
     * 
     * @param childChange to add
     * @return childChange added
     */
    @Override
    public final HistoryChange addChange(HistoryChange childChange) {
        this.children.add(childChange);
        return childChange;
    }
}
