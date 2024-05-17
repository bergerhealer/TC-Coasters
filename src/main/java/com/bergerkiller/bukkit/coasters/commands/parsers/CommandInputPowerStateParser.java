package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.Arrays;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.arguments.CommandInputPowerState;
import com.bergerkiller.bukkit.common.utils.ParseUtil;

import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

public class CommandInputPowerStateParser implements ArgumentParser<CommandSender, CommandInputPowerState>, BlockingSuggestionProvider.Strings<CommandSender> {

    @Override
    public ArgumentParseResult<CommandInputPowerState> parse(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        String name = commandInput.readString();
        if (name.equalsIgnoreCase("toggle") || name.equalsIgnoreCase("invert") || name.equalsIgnoreCase("opposite")) {
            return ArgumentParseResult.success(CommandInputPowerState.TOGGLE);
        }
        if (ParseUtil.isBool(name)) {
            return ArgumentParseResult.success(ParseUtil.parseBool(name) ? CommandInputPowerState.ON : CommandInputPowerState.OFF);
        }

        // Not found
        return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                TCCoastersLocalization.INVALID_POWER_STATE, name));
    }

    @Override
    public Iterable<String> stringSuggestions(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        return Arrays.asList("on", "off", "toggle");
    }
}
