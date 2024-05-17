package com.bergerkiller.bukkit.coasters.commands;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoasters;
import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.editor.history.HistoryChange;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;

@Command("tccoasters|tcc")
class EditStateHistoryCommands {

    @CommandRequiresTCCPermission
    @Command("undo")
    @CommandDescription("Undoes the last action")
    public void commandUndo(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        HistoryChange change;
        if ((change = state.getHistory().undo()) != null) {
            String ago = formatAgo(change.getSecondsAgo());
            TCCoastersLocalization.UNDO_SINGLE.message(sender, ago);
            showHistorySummary(state, sender);
        } else {
            TCCoastersLocalization.UNDO_FAILED.message(sender);
        }
    }

    @CommandRequiresTCCPermission
    @Command("redo")
    @CommandDescription("Redoes the last action that was undone using undo")
    public void commandRedo(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        HistoryChange change;
        if ((change = state.getHistory().redo()) != null) {
            String ago = formatAgo(change.getSecondsAgo());
            TCCoastersLocalization.REDO_SINGLE.message(sender, ago);
            showHistorySummary(state, sender);
        } else {
            TCCoastersLocalization.REDO_FAILED.message(sender);
        }
    }

    @CommandRequiresTCCPermission
    @Command("undo since <seconds_ago>")
    @CommandDescription("Undoes the last group of actions since a short time ago")
    public void commandUndoSince(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument("seconds_ago") int seconds
    ) {
        final long since = System.currentTimeMillis() - ((long) seconds * 1000L);
        commandUndoManyAndSince(state, sender, plugin, Integer.MAX_VALUE, since);
    }

    @CommandRequiresTCCPermission
    @Command("undo <count>")
    @CommandDescription("Undoes a number of actions last performed")
    public void commandUndoMany(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument(value="count", suggestions="history_count") int count
    ) {
        commandUndoManyAndSince(state, sender, plugin, count, Long.MIN_VALUE);
    }

    private void commandUndoManyAndSince(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final int count,
            final long since
    ) {
        HistoryChange lastChange = null;
        int numChanges = 0;
        for (HistoryChange ch; numChanges < count && (ch = state.getHistory().undo(since)) != null; numChanges++) {
            lastChange = ch;
        }

        if (lastChange != null) {
            String ago = formatAgo(lastChange.getSecondsAgo());
            TCCoastersLocalization.UNDO_MANY.message(sender, Integer.toString(numChanges), ago);
            showHistorySummary(state, sender);
        } else {
            TCCoastersLocalization.UNDO_FAILED.message(sender);
        }
    }

    @CommandRequiresTCCPermission
    @Command("redo all")
    @CommandDescription("Redoes all changes that were last undone")
    public void commandRedoAll(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin
    ) {
        commandRedoMany(state, sender, plugin, Integer.MAX_VALUE);
    }

    @CommandRequiresTCCPermission
    @Command("redo <count>")
    @CommandDescription("Redoes a number of actions previously undone")
    public void commandRedoMany(
            final PlayerEditState state,
            final CommandSender sender,
            final TCCoasters plugin,
            final @Argument(value="count", suggestions="history_count") int count
    ) {
        HistoryChange lastChange = null;
        int numChanges = 0;
        for (HistoryChange ch; numChanges < count && (ch = state.getHistory().redo()) != null; numChanges++) {
            lastChange = ch;
        }

        if (lastChange != null) {
            String ago = formatAgo(lastChange.getSecondsAgo());
            TCCoastersLocalization.REDO_MANY.message(sender, Integer.toString(numChanges), ago);
            showHistorySummary(state, sender);
        } else {
            TCCoastersLocalization.REDO_FAILED.message(sender);
        }
    }

    private void showHistorySummary(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        HistoryChange last = state.getHistory().getLastChange();
        String undosTimeAgo = last == null ? "X" : formatAgo(last.getSecondsAgo());
        String undosRemaining = Integer.toString(state.getHistory().undoCountRemaining());
        String redosRemaining = Integer.toString(state.getHistory().redoCountRemaining());
        TCCoastersLocalization.HISTORY_REMAINING.message(sender, undosRemaining, undosTimeAgo, redosRemaining);
    }

    //TODO: Make this configurable maybe?
    private String formatAgo(int secondsAgo) {
        if (secondsAgo < 60) {
            return secondsAgo + "s";
        } else if (secondsAgo < 3600) {
            return (secondsAgo / 60) + "m";
        } else {
            int hours = secondsAgo / 3600;
            int minutes = (secondsAgo - (hours * 3600)) / 60;
            return hours + "h" + minutes + "m";
        }
    }
}
