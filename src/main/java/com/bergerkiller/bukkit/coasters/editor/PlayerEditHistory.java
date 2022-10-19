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

    @Override
    public void removeChange(HistoryChange change) {
        this.history.remove(change);
    }

    @Override
    public boolean hasChanges() {
        return !this.history.isEmpty();
    }

    /**
     * Gets the last change added to this history
     * 
     * @return last change, null if no changes are known
     */
    public HistoryChange getLastChange() {
        return this.history.peekLast();
    }

    /**
     * Undoes the last change.
     *
     * @return The change that was un-done, or null if none
     */
    public HistoryChange undo() {
        return undo(Long.MIN_VALUE);
    }

    /**
     * Undoes the last change.
     *
     * @param minTimestamp Minimum timestamp, only changes beyond this point are undone
     * @return The change that was un-done, or null if none
     */
    public HistoryChange undo(long minTimestamp) {
        while (!this.history.isEmpty()) {
            HistoryChange change = this.history.removeLast();
            if (change.isNOOP()) {
                continue;
            }
            if (change.getTimestamp() < minTimestamp) {
                this.history.addLast(change);
                return null;
            }
            try {
                change.undo();
            } catch (TrackLockedException ex) {
                this.player.sendMessage(ChatColor.RED + "Some changes could not be rolled back because the coaster is locked!");
            }
            this.future.add(change);
            return change;
        }
        return null;
    }

    /**
     * Gets the number of times {@link #undo()} can be called successfully before no more changes
     * can be undone.
     *
     * @return undo count
     */
    public int undoCountRemaining() {
        int count = 0;
        int limit = 0;
        for (HistoryChange change : this.history) {
            if (!change.isNOOP()) {
                count++;
            }
            if (++limit == 1000) {
                // Abort early. Don't want to iterate thousands of entries here, waste of time.
                return count + this.history.size() - limit;
            }
        }
        return count;
    }

    /**
     * Redoes the last change that has been un-done.
     * 
     * @return The change that was re-done, or null if none
     */
    public HistoryChange redo() {
        while (!this.future.isEmpty()) {
            HistoryChange change = this.future.removeLast();
            if (change.isNOOP()) {
                continue;
            }
            try {
                change.redo();
            } catch (TrackLockedException ex) {
                this.player.sendMessage(ChatColor.RED + "Some changes could not be applied because the coaster is locked!");
            }
            this.history.add(change);
            return change;
        }
        return null;
    }

    /**
     * Gets the number of times {@link #redo()} can be called successfully before no more changes
     * can be redone.
     *
     * @return redo count
     */
    public int redoCountRemaining() {
        return this.future.size();
    }
}
