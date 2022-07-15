package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.coasters.commands.arguments.CommandInputPowerState;
import com.bergerkiller.bukkit.common.utils.ParseUtil;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;

public class CommandInputPowerStateParser implements ArgumentParser<CommandSender, CommandInputPowerState> {

    @Override
    public ArgumentParseResult<CommandInputPowerState> parse(
            final CommandContext<CommandSender> commandContext,
            final Queue<String> inputQueue
    ) {
        if (inputQueue.isEmpty()) {
            return ArgumentParseResult.failure(new NoInputProvidedException(
                    this.getClass(),
                    commandContext
            ));
        }

        String name = inputQueue.peek();
        if (name.equalsIgnoreCase("toggle") || name.equalsIgnoreCase("invert") || name.equalsIgnoreCase("opposite")) {
            inputQueue.poll();
            return ArgumentParseResult.success(CommandInputPowerState.TOGGLE);
        }
        if (ParseUtil.isBool(name)) {
            inputQueue.poll();
            return ArgumentParseResult.success(ParseUtil.parseBool(name) ? CommandInputPowerState.ON : CommandInputPowerState.OFF);
        }

        // Not found
        return ArgumentParseResult.failure(new LocalizedParserException(commandContext,
                TCCoastersLocalization.INVALID_POWER_STATE, name));
    }

    @Override
    public List<String> suggestions(
            final CommandContext<CommandSender> commandContext,
            final String input
    ) {
        return Arrays.asList("on", "off", "toggle");
    }
}
