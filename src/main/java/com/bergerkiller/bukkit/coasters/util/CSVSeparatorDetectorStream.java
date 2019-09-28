package com.bergerkiller.bukkit.coasters.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CSVSeparatorDetectorStream extends InputStream {
    private final InputStream _input;
    private byte[] _prebuffer_bytes;
    private int _prebuffer_pos;

    public CSVSeparatorDetectorStream(InputStream input) {
        this._input = input;
        this._prebuffer_bytes = null;
        this._prebuffer_pos = 0;
    }

    /**
     * Reads the base input stream to detect the separator character that is used.
     * 
     * @return separator character
     * @throws IOException
     */
    public char getSeparator() throws IOException {
        ByteArrayOutputStream prebuffer = new ByteArrayOutputStream();

        int c;
        int num_tabs = 0;
        int num_spaces = 0;
        int num_commas = 0;
        boolean inEscape = false;
        boolean inQuote = false;
        boolean foundContents = false;
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
            if (ch == '\\') {
                inEscape = !inEscape;
                continue;
            } else {
                inEscape = false;
            }
            if (ch == '\"' && !inEscape) {
                inQuote = !inQuote;
                continue;
            }
            if (inQuote) {
                continue;
            }

            // This is data outside the quotes
            if (ch == '\t') {
                num_tabs++;
            } else if (ch == ',') {
                num_commas++;
            } else if (ch == ' ') {
                num_spaces++;
            }
        }

        if (prebuffer.size() > 0) {
            this._prebuffer_bytes = prebuffer.toByteArray();
            this._prebuffer_pos = 0;
        }
        if (num_tabs > num_spaces && num_tabs > num_commas) {
            return '\t';
        } else if (num_spaces > num_commas) {
            return ' ';
        } else {
            return ',';
        }
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
