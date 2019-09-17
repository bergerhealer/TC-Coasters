package com.bergerkiller.bukkit.coasters.editor;

import java.util.LinkedList;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChangeCollection;
import com.bergerkiller.bukkit.coasters.tracks.TrackLockedException;

/**
 * Tracks the changes performed over time, allowing for 'undo' functionality.
 */
public class PlayerEditHistory extends HistoryChangeCollection {
    private final Player player;
    private final LinkedList<HistoryChange> history = new LinkedList<HistoryChange>();
    private final LinkedList<HistoryChange> future = new LinkedList<HistoryChange>();

    public PlayerEditHistory(Player player) {
        this.player = player;
    }

    /**
     * Adds a new change performed by the user. Future changes are discarded,
     * making any further redo impossible.
     * 
     * @param change to add
     * @return change added
     */
    @Override
    public HistoryChange addChange(HistoryChange change) {
        this.future.clear();
        this.history.add(change);
        return change;
    }

    /**
     * Undoes the last change.
     * 
     * @return True if the undo was performed
     */
    public boolean undo() {
        if (this.history.isEmpty()) {
            return false;
        }
        HistoryChange change = this.history.removeLast();
        try {
            change.undo();
        } catch (TrackLockedException ex) {
            this.player.sendMessage(ChatColor.RED + "Some changes could not be rolled back because the coaster is locked!");
        }
        this.future.add(change);
        return true;
    }

    /**
     * Redoes the last change that has been un-done.
     * 
     * @return True if the redo was performed
     */
    public boolean redo() {
        if (this.future.isEmpty()) {
            return false;
        }
        HistoryChange change = this.future.removeLast();
        try {
            change.redo();
        } catch (TrackLockedException ex) {
            this.player.sendMessage(ChatColor.RED + "Some changes could not be applied because the coaster is locked!");
        }
        this.history.add(change);
        return true;
    }
}
