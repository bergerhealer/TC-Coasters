package com.bergerkiller.bukkit.coasters.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CSVFormatDetectorStream extends InputStream {
    private final InputStream _input;
    private byte[] _prebuffer_bytes;
    private int _prebuffer_pos;
    private char _separator;
    private boolean _ignoreQuotes;

    public CSVFormatDetectorStream(InputStream input) {
        this._input = input;
        this._prebuffer_bytes = null;
        this._prebuffer_pos = 0;
        this._separator = ',';
        this._ignoreQuotes = false;
    }

    /**
     * Reads the base input stream to detect the CSV format that is used
     * 
     * @throws IOException
     */
    public void detect() throws IOException {
        ByteArrayOutputStream prebuffer = new ByteArrayOutputStream();

        int c;
        int num_quotes = 0;
        int num_tabs = 0;
        int num_spaces = 0;
        int num_commas = 0;
        int num_tabs_within_quotes = 0;
        int num_spaces_within_quotes = 0;
        int num_commas_within_quotes = 0;
        boolean inEscape = false;
        boolean inQuote = false;
        boolean foundContents = false;
        boolean firstCharacterIsQuote = false;
        boolean lastCharacterIsQuote = false;
        while ((c = this._input.read()) != -1) {
            prebuffer.write(c);
            char ch = (char) c;
            if (ch == '\n' || ch == '\r') {
                if (foundContents) {
                    break;
                } else {
                    continue;
                }
            }

            if (!foundContents) {
                // Analyze first character with actual contents, ignore whitespace
                if (ch == ' ' || ch == '\t') {
                    continue;
                } else if (ch == '\"') {
                    firstCharacterIsQuote = true;
                }
                foundContents = true;
            } else {
                // Track last character
                lastCharacterIsQuote = (ch == '\"');
            }

            if (ch == '\\') {
                inEscape = !inEscape;
                continue;
            } else {
                inEscape = false;
            }
            if (ch == '\"' && !inEscape) {
                inQuote = !inQuote;
                num_quotes++;
                continue;
            }
            if (inQuote) {
                // This is data inside the quotes
                if (ch == '\t') {
                    num_tabs_within_quotes++;
                } else if (ch == ',') {
                    num_commas_within_quotes++;
                } else if (ch == ' ') {
                    num_spaces_within_quotes++;
                }
            } else {
                // This is data outside the quotes
                if (ch == '\t') {
                    num_tabs++;
                } else if (ch == ',') {
                    num_commas++;
                } else if (ch == ' ') {
                    num_spaces++;
                }
            }
        }

        if (prebuffer.size() > 0) {
            this._prebuffer_bytes = prebuffer.toByteArray();
            this._prebuffer_pos = 0;
        }

        // If the entire line starts and ends with a quote, and there are no quotes elsewhere,
        // toggle 'ignore quotes' on and use the 'within quotes' metrics instead
        this._ignoreQuotes = (firstCharacterIsQuote && lastCharacterIsQuote && num_quotes == 2);
        if (this._ignoreQuotes) {
            num_tabs = num_tabs_within_quotes;
            num_spaces = num_spaces_within_quotes;
            num_commas = num_commas_within_quotes;
        }

        if (num_tabs > num_spaces && num_tabs > num_commas) {
            this._separator = '\t';
        } else if (num_spaces > num_commas) {
            this._separator = ' ';
        } else {
            this._separator = ',';
        }
    }

    /**
     * Gets the separator character that was detected after calling {@link #detect()}
     * 
     * @return separator character
     */
    public char getSeparator() {
        return this._separator;
    }

    /**
     * Gets whether quotes in the input must be ignored. This is the case if every line
     * starts and ends with a quote character.
     * 
     * @return
     */
    public boolean getIgnoreQuotes() {
        return this._ignoreQuotes;
    }

    @Override
    public int read() throws IOException {
        if (this._prebuffer_bytes == null) {
            return this._input.read();
        } else {
            byte result = this._prebuffer_bytes[this._prebuffer_pos++];
            if (this._prebuffer_pos >= this._prebuffer_bytes.length) {
                this._prebuffer_bytes = null;
            }
            return result;
        }
    }

    @Override
    public int read(byte b[]) throws IOException {
        if (this._prebuffer_bytes == null) {
            return this._input.read(b);
        } else {
            return this.read(b, 0, b.length);
        }
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (this._prebuffer_bytes == null) {
            return this._input.read(b, off, len);
        } else {
            return super.read(b, off, len);
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (this._prebuffer_bytes == null) {
            return this._input.skip(n);
        } else {
            return super.skip(n);
        }
    }

    @Override
    public int available() throws IOException {
        if (this._prebuffer_bytes == null) {
            return this._input.available();
        } else {
            return this._prebuffer_bytes.length - this._prebuffer_pos + this._input.available();
        }
    }

    @Override
    public void close() throws IOException {
        this._prebuffer_bytes = null;
        this._input.close();
    }
}
