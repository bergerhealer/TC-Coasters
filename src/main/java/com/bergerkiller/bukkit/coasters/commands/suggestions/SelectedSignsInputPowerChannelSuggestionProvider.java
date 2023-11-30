package com.bergerkiller.bukkit.coasters.commands.suggestions;

import cloud.commandframework.context.CommandContext;
import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Suggests the names of power channels used by signs assigned to nodes a player has selected
 */
public final class SelectedSignsInputPowerChannelSuggestionProvider implements BiFunction<CommandContext<CommandSender>, String, List<String>> {

    @Override
    public List<String> apply(CommandContext<CommandSender> context, String input) {
        final PlayerEditState state = context.inject(PlayerEditState.class).get();
        return state.getEditedNodes().stream()
                .flatMap(n -> Stream.of(n.getSigns()))
                .flatMap(n -> Stream.of(n.getInputPowerChannels()))
                .map(NamedPowerChannel::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
