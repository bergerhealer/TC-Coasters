package com.bergerkiller.bukkit.coasters.commands.suggestions;

import com.bergerkiller.bukkit.coasters.editor.PlayerEditState;
import com.bergerkiller.bukkit.coasters.signs.power.NamedPowerChannel;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Suggests the names of power channels used by signs assigned to nodes a player has selected
 */
public final class SelectedSignsOutputPowerChannelSuggestionProvider implements BlockingSuggestionProvider.Strings<CommandSender> {

    @Override
    public Iterable<String> stringSuggestions(
            final CommandContext<CommandSender> commandContext,
            final CommandInput input
    ) {
        final PlayerEditState state = commandContext.inject(PlayerEditState.class).get();
        return state.getEditedNodes().stream()
                .flatMap(n -> Stream.of(n.getSigns()))
                .flatMap(n -> Stream.of(n.getOutputPowerChannels()))
                .map(NamedPowerChannel::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
