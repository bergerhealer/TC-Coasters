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

        FormatDetector bestDetector = null;
        FormatDetector detector = new FormatDetector();
        for (int c; (c = this._input.read()) != -1;) {
            prebuffer.write(c);
            char ch = (char) c;
            if (ch == '\n' || ch == '\r') {
                if (detector.canDetectFormat()) {
                    bestDetector = detector; // Use it
                    break;
                } else {
                    if (detector.hasContents() && bestDetector == null) {
                        bestDetector = detector; // Use first line as an alternative
                    }
                    detector = new FormatDetector(); // Reset completely
                    continue;
                }
            }

            detector.visit(ch);
        }

        // Replay what we've read
        if (prebuffer.size() > 0) {
            this._prebuffer_bytes = prebuffer.toByteArray();
            this._prebuffer_pos = 0;
        }

        // If no format could be detected, use a sane default
        if (bestDetector == null) {
            this._ignoreQuotes = false;
            this._separator = ',';
        } else {
            this._ignoreQuotes = bestDetector.isQuoted();
            this._separator = bestDetector.detectSeparator();
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

    /**
     * Attempts to detect the CSV format of a single line of text, by visiting it
     * one character at a time.
     */
    private static class FormatDetector {
        private int num_quotes = 0;
        private int num_tabs = 0;
        private int num_spaces = 0;
        private int num_commas = 0;
        private int num_tabs_within_quotes = 0;
        private int num_spaces_within_quotes = 0;
        private int num_commas_within_quotes = 0;
        private boolean inEscape = false;
        private boolean inQuote = false;
        private boolean foundContents = false;
        private boolean firstCharacterIsQuote = false;
        private boolean lastCharacterIsQuote = false;

        /**
         * Gets whether the line is surrounded on both sides by quotes, with no quotes
         * inbetween.
         *
         * @return True if quoted
         */
        public boolean isQuoted() {
            // If the entire line starts and ends with a quote, and there are no quotes elsewhere,
            // toggle 'ignore quotes' on and use the 'within quotes' metrics instead
            return (firstCharacterIsQuote && lastCharacterIsQuote && num_quotes == 2);
        }

        /**
         * Gets whether non-whitespace contents were parsed at all
         *
         * @return True if there were valid line contents
         */
        public boolean hasContents() {
            return foundContents;
        }

        /**
         * Gets whether enough information was gathered to make a decision about what
         * format is used. This requires at least one delimiter to have been detected.
         *
         * @return True if format can be detected
         */
        public boolean canDetectFormat() {
            if (!foundContents) {
                return false;
            } else if (isQuoted()) {
                return num_tabs_within_quotes > 0 || num_spaces_within_quotes > 0 || num_commas_within_quotes > 0;
            } else {
                return num_tabs > 0 || num_spaces > 0 || num_commas > 0;
            }
        }

        /**
         * Detects what separator character is likely used
         *
         * @return separator character
         */
        public char detectSeparator() {
            int _num_tabs, _num_spaces, _num_commas;
            if (isQuoted()) {
                _num_tabs = num_tabs_within_quotes;
                _num_spaces = num_spaces_within_quotes;
                _num_commas = num_commas_within_quotes;
            } else {
                _num_tabs = num_tabs;
                _num_spaces = num_spaces;
                _num_commas = num_commas;
            }

            if (_num_tabs > _num_spaces && _num_tabs > _num_commas) {
                return '\t';
            } else if (_num_spaces > _num_commas) {
                return ' ';
            } else {
                return ',';
            }
        }

        /**
         * Visits the next character, updating the detector state
         *
         * @param ch
         */
        public void visit(char ch) {
            if (!foundContents) {
                // Analyze first character with actual contents, ignore whitespace
                if (ch == ' ' || ch == '\t') {
                    return;
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
                return;
            } else {
                inEscape = false;
            }
            if (ch == '\"' && !inEscape) {
                inQuote = !inQuote;
                num_quotes++;
                return;
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
    }
}
