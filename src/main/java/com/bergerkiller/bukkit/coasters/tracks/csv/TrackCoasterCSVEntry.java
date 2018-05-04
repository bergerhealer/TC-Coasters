package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.Arrays;

import org.bukkit.util.Vector;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

/**
 * Stores the contents of a single CSV entry being read/written.
 * Acts as a buffer when reading/writing coaster CSV files.
 */
public class TrackCoasterCSVEntry {
    private final String[] columns = new String[7];

    public TrackCoasterCSVEntry() {
        Arrays.fill(this.columns, "");
    }

    /**
     * Writes the next line to a CSV file.
     * 
     * @param writer
     * @throws IOException
     */
    public void writeTo(CSVWriter writer) throws IOException {
        writer.writeNext(this.columns);
    }

    /**
     * Reads the next line from a CSV file. Returns True if the line was read.
     * 
     * @param reader
     * @return True if line was read, False if not
     * @throws IOException
     */
    public boolean readFrom(CSVReader reader) throws IOException {
        String[] result = reader.readNext();
        if (result == null) {
            return false;
        } else {
            for (int i = 0; i < this.columns.length; i++) {
                this.columns[i] = (i >= result.length) ? "" : result[i];
            }
            return true;
        }
    }

    public Type getType() {
        return Type.fromTypeStr(this.columns[0]);
    }

    public void setType(Type type) {
        this.columns[0] = type.name();
    }

    public Vector getPosition() {
        try {
            return new Vector(Double.parseDouble(this.columns[1]),
                              Double.parseDouble(this.columns[2]),
                              Double.parseDouble(this.columns[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void setPosition(Vector pos) {
        this.columns[1] = Double.toString(pos.getX());
        this.columns[2] = Double.toString(pos.getY());
        this.columns[3] = Double.toString(pos.getZ());
    }

    public Vector getOrientation() {
        try {
            return new Vector(Double.parseDouble(this.columns[4]),
                              Double.parseDouble(this.columns[5]),
                              Double.parseDouble(this.columns[6]));
        } catch (NumberFormatException ex) {
            return new Vector(0, 1, 0);
        }
    }

    public void setOrientation(Vector up) {
        this.columns[4] = Double.toString(up.getX());
        this.columns[5] = Double.toString(up.getY());
        this.columns[6] = Double.toString(up.getZ());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.columns.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(this.columns[i]);
        }
        return sb.toString();
    }

    public static enum Type {
        UNKNOWN, ROOT, NODE, LINK;

        public static Type fromTypeStr(String type) {
            for (Type t : values()) {
                if (t.name().equals(type)) {
                    return t;
                }
            }
            return UNKNOWN;
        }
    }
}
