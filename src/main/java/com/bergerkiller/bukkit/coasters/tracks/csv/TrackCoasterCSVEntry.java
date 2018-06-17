package com.bergerkiller.bukkit.coasters.tracks.csv;

import java.io.IOException;
import java.util.Arrays;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

/**
 * Stores the contents of a single CSV entry being read/written.
 * Acts as a buffer when reading/writing coaster CSV files.
 */
public class TrackCoasterCSVEntry {
    private final String[] columns = new String[10];

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
        }

        // Detect use of tab characters instead of commas
        // This may occur with foreign formats
        // TODO: Deal with quotes around the values?
        if (result.length == 1) {
            String[] by_tabs = result[0].split("\t");
            if (by_tabs.length > 1) {
                result = by_tabs;
            }
        }

        // Detect NoLimits CSV format and translate it into nodes
        // Doesn't matter first entry is not ROOT, no previous entry was
        // read before so that works perfectly fine.
        if (result.length >= 13 && ParseUtil.isNumeric(result[0])) {
            result = new String[] {
                    "NODE",
                    result[1], result[2], result[3],
                    result[10], result[11], result[12]
            };
        }

        // Read
        for (int i = 0; i < this.columns.length; i++) {
            this.columns[i] = (i >= result.length) ? "" : result[i];
        }
        return true;
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

    public IntVector3 getRailBlock() {
        if (this.columns[7].isEmpty()) {
            return null;
        }
        try {
            return new IntVector3(Integer.parseInt(this.columns[7]),
                                  Integer.parseInt(this.columns[8]),
                                  Integer.parseInt(this.columns[9]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void setRailBlock(IntVector3 railsBlock) {
        if (railsBlock == null) {
            this.columns[7] = "";
            this.columns[8] = "";
            this.columns[9] = "";
        } else {
            this.columns[7] = Integer.toString(railsBlock.x);
            this.columns[8] = Integer.toString(railsBlock.y);
            this.columns[9] = Integer.toString(railsBlock.z);
        }
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
