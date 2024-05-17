package com.bergerkiller.bukkit.coasters.commands.parsers;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.common.cloud.CloudLocalizedException;
import org.bukkit.command.CommandSender;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Util;

import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

public class TimeTicksParser implements ArgumentParser<CommandSender, Integer>, BlockingSuggestionProvider.Strings<CommandSender> {

    // Returns -1 on fail
    public static int parse(String text) {
        int result = Util.parseTimeTicks(text);
        if (result < 0) {
            result = ParseUtil.parseInt(text, -1);
        }
        return result;
    }

    @Override
    public ArgumentParseResult<Integer> parse(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        String peek = commandInput.peekString();
        int result = parse(peek);
        if (result < 0) {
            return ArgumentParseResult.failure(new CloudLocalizedException(commandContext,
                    TCCoastersLocalization.INVALID_TIME, peek));
        }

        commandInput.readString();
        return ArgumentParseResult.success(result);
    }

    @Override
    public Iterable<String> stringSuggestions(
            final CommandContext<CommandSender> commandContext,
            final CommandInput commandInput
    ) {
        String input = commandInput.lastRemainingToken();
        char last = input.isEmpty() ? '.' : input.charAt(input.length() - 1);
        if (last == '.' || last == ',') {
            return Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").stream()
                    .map(m -> input + m)
                    .collect(Collectors.toList());
        }

        if (Character.isDigit(last)) {
            return Arrays.asList(".", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                    "ms", "m", "s", "t").stream()
                    .map(m -> input + m)
                    .collect(Collectors.toList());
        }

        // m -> m/ms
        if (last == 'm' && input.length() > 1 && Character.isDigit(input.charAt(input.length() - 2))) {
            return Arrays.asList(input, input + "s");
        }

        return Collections.emptyList();
    }
}
