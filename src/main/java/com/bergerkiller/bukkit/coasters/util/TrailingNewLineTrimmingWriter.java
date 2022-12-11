package com.bergerkiller.bukkit.coasters.util;

import java.io.IOException;
import java.io.Writer;

/**
 * Writer that wraps another writer, but prevents a trailing newline
 * sequence being generated at the end. Newlines are only written
 * out when additional non-newline characters follow.
 */
public final class TrailingNewLineTrimmingWriter extends Writer {
    private static final char[] CRLF = new char[] { '\r', '\n' };
    private final Writer base;
    private boolean hasNewline = false; // whether there is a \n
    private boolean hasControlChar = false; // whether or not \r precedes it

    public TrailingNewLineTrimmingWriter(Writer base) {
        this.base = base;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        int pendingStart = -1;
        int end = off + len;
        while (true) {
            boolean endOfBuffer = (off >= end);
            char c = endOfBuffer ? '\0' : cbuf[off];
            if (endOfBuffer || c == '\r' || c == '\n') {
                // Write non-newline contents that are still pending
                if (pendingStart != -1) {
                    base.write(cbuf, pendingStart, off - pendingStart);
                    pendingStart = -1;
                }

                if (endOfBuffer) {
                    break;
                }

                // If double-newline is detected, write the stuff we got so far
                if ((c == '\r' && hasControlChar) || (c == '\n' && hasNewline)) {
                    flushNewlines();
                }

                // Update state
                if (c == '\r') {
                    hasControlChar = true;
                } else if (c == '\n') {
                    hasNewline = true;
                }
            } else {
                // Non-newline contents. Flush newlines and start a new pending section to write
                flushNewlines();
                if (pendingStart == -1) {
                    pendingStart = off;
                }
            }

            ++off;
        }
    }

    private void flushNewlines() throws IOException {
        if (hasControlChar && hasNewline) {
            base.write(CRLF);
        } else if (hasControlChar) {
            base.write('\r');
        } else if (hasNewline) {
            base.write('\n');
        }
        hasControlChar = false;
        hasNewline = false;
    }

    @Override
    public void flush() throws IOException {
        base.flush();
    }

    @Override
    public void close() throws IOException {
        base.close();
    }
}
