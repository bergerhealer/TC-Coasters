package com.bergerkiller.bukkit.coasters.editor.history;

/**
 * Group of history change, for use of performing multiple changes at once
 */
public final class HistoryChangeGroup extends HistoryChange {

    public HistoryChangeGroup() {
        super(null);
    }

    @Override
    public boolean isNOOP() {
        return !this.hasChanges();
    }

    @Override
    protected final void run(boolean undo) {
    }

}
