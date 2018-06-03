package com.bergerkiller.bukkit.coasters.editor.history;

/**
 * Group of history change, for use of performing multiple changes at once
 */
public class HistoryChangeGroup extends HistoryChange {

    public HistoryChangeGroup() {
        super(null);
    }

    @Override
    protected final void run(boolean undo) {
    }

}
