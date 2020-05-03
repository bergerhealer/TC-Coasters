package com.bergerkiller.bukkit.coasters.editor.history;

/**
 * Group of history change, for use of performing multiple changes at once.
 * Adds itself to the collection when first adding a change, so that
 * no changes are added when this one stays empty.
 */
public final class HistoryChangeLazyGroup extends HistoryChange {
    private final HistoryChangeCollection parent;

    public HistoryChangeLazyGroup(HistoryChangeCollection parent) {
        super(null);
        this.parent = parent;
    }

    @Override
    public HistoryChange addChange(HistoryChange childChange) {
        if (!this.hasChanges()) {
            this.parent.addChange(this);
        }
        return super.addChange(childChange);
    }

    @Override
    protected final void run(boolean undo) {
    }
}
