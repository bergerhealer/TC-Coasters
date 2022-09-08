package com.bergerkiller.bukkit.coasters.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.JsonSerializer;
import com.bergerkiller.bukkit.common.config.JsonSerializer.JsonSyntaxException;
import com.bergerkiller.bukkit.common.wrappers.BlockData;

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
    private static final NumberFormat DEFAULT_NUMBER_FORMAT = new DecimalFormat("0.0#####", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    private final JsonSerializer jsonSerializer = new JsonSerializer();
    private String[] buffer = new String[10];
    private int size = 0;
    private int index = 0;
    private NumberFormat numberFormat = DEFAULT_NUMBER_FORMAT;

    /**
     * Loads this buffer with the data specified.
     * Size will equal the data's length and read index will be reset to 0.
     * 
     * @param data to load
     */
    public void load(String[] data) {
        growBuffer(data.length);
        System.arraycopy(data, 0, this.buffer, 0, data.length);
        this.size = data.length;
        this.index = 0;
    }

    /**
     * Loads this buffer with the data specified.
     * Size will equal the data's length and read index will be reset to 0.
     * 
     * @param data to load
     */
    public void load(List<String> data) {
        growBuffer(data.size());
        this.size = data.size();
        this.index = 0;
        for (int i = 0; i < this.size; i++) {
            this.buffer[i] = data.get(i);
        }
    }

    private void growBuffer(int size) {
        if (size > this.buffer.length) {
            int new_size = this.buffer.length;
            while (size > new_size) {
                new_size *= 2;
            }
            this.buffer = new String[new_size];
        }
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
     * Sets the number format to use when writing out decimal numbers.
     * A dot is used for decimal delimiter. (English Locale)
     *
     * @param formatPattern
     */
    public void setNumberFormat(String formatPattern) {
        setNumberFormat(new DecimalFormat(formatPattern, DecimalFormatSymbols.getInstance(Locale.ENGLISH)));
    }

    /**
     * Sets the number format to use when writing out decimal numbers
     *
     * @param format
     */
    public void setNumberFormat(NumberFormat format) {
        this.numberFormat = format;
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
     * Sets the next UUID value
     *
     * @param uuid
     */
    public void putUUID(UUID uuid) {
        put(uuid.toString());
    }

    /**
     * Sets the next ItemStack value
     * 
     * @param item
     */
    public void putItemStack(ItemStack item) {
        put(jsonSerializer.itemStackToJson(item).replace(" ", "\\u0020"));
    }

    /**
     * Sets the next BlockData value
     * 
     * @param material
     */
    public void putBlockData(BlockData material) {
        put(material.serializeToString());
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
        put(this.numberFormat.format(value));
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
     * Gets the next UUID value, which is a stringified UUID token
     *
     * @return next UUID value
     * @throws SyntaxException If the field value isn't a decodable valid UUID
     */
    public UUID nextUUID() throws SyntaxException {
        String str = next();
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException ex) {
            throw createSyntaxException("Invalid UUID: " + str);
        }
    }

    /**
     * Gets the next ItemStack value, which consists of jsonified itemstack properties
     * 
     * @return next ItemStack value
     * @throws SyntaxException if the field value isn't valid JSON or doesn't refer to an ItemStack
     */
    public ItemStack nextItemStack() throws SyntaxException {
        try {
            return this.jsonSerializer.fromJsonToItemStack(next());
        } catch (JsonSyntaxException e) {
            throw createSyntaxException("Invalid ItemStack Json(" + e.getMessage() + ")");
        }
    }

    /**
     * Gets the next BlockData value, which consists of the material and possibly some state info
     * 
     * @return next BlockData value
     * @throws SyntaxException
     */
    public BlockData nextBlockData() throws SyntaxException {
        String serialized = next();
        BlockData blockData = BlockData.fromString(serialized);
        if (blockData == null) {
            throw createSyntaxException("Unknown block data: " + serialized);
        }
        return blockData;
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
            throw createSyntaxException("Empty value, number expected");
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            // TCC Bug - wrote out 1,555.23 as a number sometimes
            // Fixed by trimming out the ,. Gets fixed as it re-saves.
            if (value.contains(".") && value.contains(",")) {
                try {
                    return Double.parseDouble(value.replace(",", ""));
                } catch (NumberFormatException ex2) { /* ignore */ }
            }

            throw createSyntaxException("Value is not a number: " + value);
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
            throw createSyntaxException("Empty value, number expected");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw createSyntaxException("Value is not a number: " + value);
        }
    }

    /**
     * Creates a syntax exception for the last column read out
     * 
     * @param message Message for the exception
     * @return syntax exception
     */
    public SyntaxException createSyntaxException(String message) {
        return new SyntaxException(-1, this.index+1, message);
    }

    /**
     * Gets the next String value
     * 
     * @return next String value
     */
    @Override
    public String next() {
        String result = get(this.index);
        this.index++;
        return result;
    }

    /**
     * Checks whether a next String value is available.
     * If false, all upcoming values are empty strings / unavailable.
     * 
     * @return True if a next String value is available
     */
    @Override
    public boolean hasNext() {
        return this.index < this.size;
    }

    /**
     * Checks whether the next value is available, and not an empty String
     *
     * @return True if a next non-empty value is available
     * @see #has(int)
     */
    public boolean isNextNotEmpty() {
        return has(index);
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
