package com.bergerkiller.bukkit.coasters.util;

/**
 * Exception thrown when there is an error in the syntax of user supplied data.
 * Usually in the context of a text file or table with multiple lines and columns.
 */
public class SyntaxException extends Exception {
    private static final long serialVersionUID = 1277434257635748172L;
    private final String baseMessage;
    private final int line, column;

    public SyntaxException(int line, int column, String message) {
        super(generateMessage(line, column, message));
        this.baseMessage = message;
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return this.line;
    }

    public int getColumn() {
        return this.column;
    }

    public SyntaxException setLine(int line) {
        SyntaxException copy = new SyntaxException(line, this.column, this.baseMessage);
        copy.setStackTrace(this.getStackTrace());
        return copy;
    }

    public SyntaxException setColumn(int column) {
        SyntaxException copy = new SyntaxException(this.line, column, this.baseMessage);
        copy.setStackTrace(this.getStackTrace());
        return copy;
    }

    private static String generateMessage(int line, int column, String message) {
        if (line == -1 && column == -1) {
            return message;
        } else if (line == -1) {
            return "at L" + line + ": " + message;
        } else if (column == -1) {
            return "at C" + column + ": " + message;
        } else {
            return "at L" + line + " C" + column + ": " + message;
        }
    }
}
