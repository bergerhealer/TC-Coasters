package com.bergerkiller.bukkit.coasters.commands;

import java.util.stream.Collectors;

import com.bergerkiller.bukkit.tc.attachments.animation.AnimationEasing;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresSelectedNodes;
import com.bergerkiller.bukkit.coasters.commands.annotations.CommandRequiresTCCPermission;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.tracks.TrackNode;
import com.bergerkiller.bukkit.coasters.tracks.TrackNodeAnimationState;

import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;

/**
 * Commands that alter or play track animations
 */
@Command("tccoasters|tcc animation")
class EditStateAnimationCommands {

    @CommandRequiresTCCPermission
    @Command("")
    @CommandDescription("Shows short command help and what animations are available and selected")
    public void commandShowAnimationDetails(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        String selected = state.getSelectedAnimation();
        if (selected == null) {
            sender.sendMessage(ChatColor.YELLOW + "No animation is selected.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Selected animation: " + selected);
        }

        String available = state.getEditedNodes().stream()
            .flatMap(n -> n.getAnimationStates().stream())
            .map(s -> s.name)
            .distinct()
            .collect(Collectors.joining(", "));
        if (available.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "The selected nodes contain no animations!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Available animations: " + available);
        }

        sender.sendMessage("");
        sender.sendMessage("Usage:");
        sender.sendMessage("/tcc animation [add/remove/select] [name]");
        sender.sendMessage("/tcc animation play [name] (duration)");
        sender.sendMessage("/tcc animation clear");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("add <name>")
    @CommandDescription("Adds an animation state of the current positions to all selected nodes")
    public void commandAddAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        for (TrackNode node : state.getEditedNodes()) {
            node.saveAnimationState(animName);
        }
        sender.sendMessage("Animation '" + animName + "' added to " + state.getEditedNodes().size() + " nodes!");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("remove <name>")
    @CommandDescription("Removes an animation state from all selected nodes")
    public void commandRemoveAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        int removedCount = 0;
        for (TrackNode node : state.getEditedNodes()) {
            if (node.removeAnimationState(animName)) {
                removedCount++;
            }
        }
        if (removedCount == 0) {
            sender.sendMessage(ChatColor.RED + "Animation '" + animName + "' was not added to the selected nodes!");
        } else {
            sender.sendMessage("Animation '" + animName + "' removed for " + removedCount + " nodes!");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("clear")
    @CommandDescription("Clears all animation state from all selected nodes")
    public void commandRemoveAnimation(
            final PlayerEditState state,
            final CommandSender sender
    ) {
        for (TrackNode node : state.getEditedNodes()) {
            for (TrackNodeAnimationState anim_state : node.getAnimationStates()) {
                node.removeAnimationState(anim_state.name);
            }
        }
        sender.sendMessage("Animations cleared for " + state.getEditedNodes().size() + " nodes!");
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("select <name>")
    @CommandDescription("Selects an animation state for editing")
    public void commandSelectAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        state.setSelectedAnimation(animName);
        sender.sendMessage(ChatColor.GREEN + "Selected track animation '" + animName + "'!");
        if (state.getSelectedAnimationNodes().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "None of your selected nodes contain this animation!");
        }
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("play <name>")
    @CommandDescription("Activates an animation state instantly")
    public void commandPlayAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName
    ) {
        commandPlayAnimation(state, sender, animName, 0.0, null);
    }

    @CommandRequiresTCCPermission
    @CommandRequiresSelectedNodes
    @Command("play <name> <duration>")
    @CommandDescription("Activates an animation state over a duration in seconds")
    public void commandPlayAnimation(
            final PlayerEditState state,
            final CommandSender sender,
            final @Quoted @Argument(value="name", suggestions="animation_names") String animName,
            final @Argument("duration") double duration,
            final @Flag(value="easing", suggestions="easing_types") String easingText
    ) {
        // AnimationEasing.LINEAR if easingText is null
        // null if easingText is invalid
        // Handles error messaging to sender
        AnimationEasing easing = parseEasing(sender, easingText);
        if (easing == null) { // easingText not null, but invalid
            return;
        }

        int playingCount = 0;
        for (TrackNode node : state.getEditedNodes()) {
            if (node.playAnimation(animName, duration, easing)) {
                playingCount++;
            }
        }
        if (playingCount == 0) {
            sender.sendMessage(ChatColor.RED + "Animation '" + animName + "' was not added to the selected nodes!");
        } else {
            sender.sendMessage("Animation '" + animName + "' is now playing for " + playingCount + " nodes!");
        }
    }

    private static AnimationEasing parseEasing(
            CommandSender sender,
            String input
    ) {
        if (input == null || input.isEmpty()) {
            return AnimationEasing.LINEAR;
        }

        // Presets
        try {
            AnimationEasing.EasingType type = AnimationEasing.EasingType.valueOf(input.toUpperCase());
            if (type == AnimationEasing.EasingType.CUSTOM) {
                sender.sendMessage(ChatColor.RED +
                        "CUSTOM is not a valid easing type here.");
                return null;
            }
            return type.getEasing();
        } catch (IllegalArgumentException ignored) {}

        // Custom bezier
        String[] parts = input.split(",");
        if (parts.length != 4) {
            sender.sendMessage(ChatColor.RED +
                    "Expected either an easing preset or x1,y1,x2,y2");
            return null;
        }

        try {
            double x1 = Double.parseDouble(parts[0]);
            double y1 = Double.parseDouble(parts[1]);
            double x2 = Double.parseDouble(parts[2]);
            double y2 = Double.parseDouble(parts[3]);

            validateRange(x1, "x1");
            validateRange(y1, "y1");
            validateRange(x2, "x2");
            validateRange(y2, "y2");

            return new AnimationEasing(x1, y1, x2, y2);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return null;
        }
    }

    private static void validateRange(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and 1");
        }
    }
}
