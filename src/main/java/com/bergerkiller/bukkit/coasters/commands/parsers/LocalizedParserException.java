package com.bergerkiller.bukkit.coasters.commands.parsers;

import com.bergerkiller.bukkit.coasters.TCCoastersLocalization;

import cloud.commandframework.context.CommandContext;

/**
 * Exception thrown from parsers when provided input is invalid.
 * TODO: Replace with BKCL CloudLocalizedException when 1.18.1-v2 or later is a hard-dep.
 */
public class LocalizedParserException extends IllegalArgumentException {
    private static final long serialVersionUID = -5284037051953535599L;
    private final CommandContext<?> context;
    private final TCCoastersLocalization message;
    private final String[] input;

    public LocalizedParserException(
            final CommandContext<?> context,
            final TCCoastersLocalization message,
            final String... input
    ) {
        this.context = context;
        this.message = message;
        this.input = input;
    }

    @Override
    public final String getMessage() {
        return this.message.get(this.input);
    }

    /**
     * Get the command context
     *
     * @return Command context
     */
    public final CommandContext<?> getContext() {
        return this.context;
    }
}
