package com.bergerkiller.bukkit.coasters.util;

import java.util.Arrays;
import java.util.Iterator;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * An array of String values. When reading a value that does not exist
 * in the array, an empty String is returned. The array is automatically
 * resized when writing at an index outside of it.<br>
 * <br>
 * In addition read/write
 * streaming functions can be used to read and write multiple values, and
 * automatically increment the current position.
 */
public class StringArrayBuffer implements Iterator<String> {
    private String[] buffer = new String[10];
    private int size = 0;
    private int index = 0;

    /**
     * Loads this buffer with the data specified.
     * Size will equal the data's length and read index will be reset to 0.
     * 
     * @param data to load
     */
    public void load(String[] data) {
        if (data.length > this.buffer.length) {
            int new_size = this.buffer.length;
            while (data.length > new_size) {
                new_size *= 2;
            }
            this.buffer = new String[new_size];
        }
        System.arraycopy(data, 0, this.buffer, 0, data.length);
        this.size = data.length;
        this.index = 0;
    }

    /**
     * Clears the buffer
     */
    public void clear() {
        this.size = 0;
        this.index = 0;
    }

    /**
     * Resets the index iterated at using {@link #next()} and
     * {@link #put(String)}.
     */
    public void reset() {
        this.index = 0;
    }

    /**
     * Gets the size of the buffer. This is the exclusive maximum
     * index where a String is stored.
     * 
     * @return buffer size
     */
    public int size() {
        return this.size;
    }

    /**
     * Skips the next number of String values
     * 
     * @param numberOfValues to skip
     */
    public void skipNext(int numberOfValues) {
        this.index += numberOfValues;
    }

    /**
     * Sets the next IntVector3 value
     * 
     * @param value to set
     */
    public void putIntVector3(IntVector3 v) {
        putInt(v.x);
        putInt(v.y);
        putInt(v.z);
    }

    /**
     * Sets the next Vector value
     * 
     * @param value to set
     */
    public void putVector(Vector v) {
        putDouble(v.getX());
        putDouble(v.getY());
        putDouble(v.getZ());
    }

    /**
     * Sets the next Double value
     * 
     * @param value to set
     */
    public void putDouble(double value) {
        put(Double.toString(value));
    }

    /**
     * Sets the next Integer value
     * 
     * @param value to set
     */
    public void putInt(int value) {
        put(Integer.toString(value));
    }

    /**
     * Sets the next String value
     * 
     * @param value to set
     */
    public void put(String value) {
        set(this.index++, value);
    }

    /**
     * Gets the next String value without advancing
     * 
     * @return next String value
     */
    public String peek() {
        return get(this.index);
    }

    /**
     * Gets the next IntVector3 value, which consists of three integer values (x/y/z)
     * 
     * @return next IntVector3 value
     * @throws SyntaxException if the three next values are not Integer
     */
    public IntVector3 nextIntVector3() throws SyntaxException {
        return new IntVector3(nextInt(), nextInt(), nextInt());
    }

    /**
     * Gets the next Vector value, which consists of three double values (x/y/z)
     * 
     * @return next Vector value
     * @throws SyntaxException if the three next values are not Double
     */
    public Vector nextVector() throws SyntaxException {
        return new Vector(nextDouble(), nextDouble(), nextDouble());
    }

    /**
     * Gets the next Double value
     * 
     * @return next Double value
     * @throws SyntaxException if the value is not an Double
     */
    public double nextDouble() throws SyntaxException {
        String value = next();
        if (value.isEmpty()) {
            throw new SyntaxException(-1, this.index+1, "Empty value, number expected");
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new SyntaxException(-1, this.index+1, "Value is not a number");
        }
    }

    /**
     * Gets the next Integer value
     * 
     * @return next Integer value
     * @throws SyntaxException if the value is not an Integer
     */
    public int nextInt() throws SyntaxException {
        String value = next();
        if (value.isEmpty()) {
            throw new SyntaxException(-1, this.index+1, "Empty value, number expected");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new SyntaxException(-1, this.index+1, "Value is not a number");
        }
    }

    /**
     * Gets the next String value
     * 
     * @return next String value
     */
    @Override
    public String next() {
        return get(this.index++);
    }

    /**
     * Checks whether a next String value is available
     * 
     * @return True if a next String value is available
     */
    @Override
    public boolean hasNext() {
        return has(this.index);
    }

    /**
     * Gets whether a non-empty String value is contained at the index
     * 
     * @param index
     * @return True if contained
     */
    public boolean has(int index) {
        return !get(index).isEmpty();
    }

    /**
     * Gets the String value at the index. Returns an empty String if none exists.
     * 
     * @param index
     * @return String at the index
     */
    public String get(int index) {
        if (index >= this.size) {
            return "";
        } else {
            return this.buffer[index];
        }
    }

    /**
     * Sets the String value at the index. If the index is beyond the size of the array,
     * the array is resized, with missing values in-between initialized to an empty String.
     * 
     * @param index to set at
     * @param value to set to
     */
    public void set(int index, String value) {
        while (index >= this.size) {
            if (this.size == this.buffer.length) {
                this.buffer = Arrays.copyOf(this.buffer, this.buffer.length * 2);
            }
            this.buffer[this.size++] = "";
        }
        this.buffer[index] = value;
    }

    /**
     * Converts the contents of this buffer to an array.
     * 
     * @return array
     */
    public String[] toArray() {
        return Arrays.copyOf(this.buffer, this.size);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        for (int n = 0; n < this.size; n++) {
            if (n > 0) {
                builder.append(", ");
            }
            builder.append('\"').append(this.buffer[n]).append('\"');
        }
        builder.append('}');
        return builder.toString();
    }
}
