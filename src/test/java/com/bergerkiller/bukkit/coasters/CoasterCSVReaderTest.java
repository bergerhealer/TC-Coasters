package com.bergerkiller.bukkit.coasters;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.bukkit.util.Vector;
import org.junit.Test;

import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.AnimationStateNodeEntry;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.CSVEntry;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.LinkNodeEntry;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.NoLimits2Entry;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.NodeEntry;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSV.RootNodeEntry;
import com.bergerkiller.bukkit.coasters.tracks.csv.TrackCoasterCSVReader;
import com.bergerkiller.bukkit.common.bases.IntVector3;

public class CoasterCSVReaderTest {

    @Test
    public void testTCCFormat() throws Throwable {
        // Standards TC-Coasters CSV files
        CSVEntry e;
        try (TrackCoasterCSVReader reader = openReader(
                "\"ROOT\",\"-77.1\",\"4.8\",\"271.32\",\"0.0\",\"1.0\",\"0.0\",\"-72\",\"4\",\"281\"\r\n" + 
                "\"ANIM\",\"-77.6\",\"4.8\",\"271.32\",\"0.0\",\"1.0\",\"0.0\",\"-72\",\"4\",\"281\",\"a\"\r\n" +
                "\"ANIM\",\"-77.2\",\"4.8\",\"271.32\",\"0.0\",\"1.0\",\"0.0\",\"\",\"\",\"\",\"b\"\r\n" + 
                "\"LINK\",\"-77.5\",\"4.8\",\"271.8\"\r\n" + 
                "\"NODE\",\"-78.1\",\"4.0\",\"286.1\",\"0.0\",\"1.0\",\"0.0\"\r\n" + 
                "\"NODE\",\"-77.5\",\"4.8\",\"275.8\",\"0.0\",\"1.0\",\"-2.22E-16\",\"-72\",\"4\",\"279\""
        )) {
             // ROOT
             e = reader.readNextEntry();
             assertEquals(RootNodeEntry.class, e.getClass());
             assertEquals(new Vector(-77.1, 4.8, 271.32), ((RootNodeEntry) e).pos);
             assertEquals(new Vector(0.0, 1.0, 0.0), ((RootNodeEntry) e).up);
             assertEquals(new IntVector3(-72, 4, 281), ((RootNodeEntry) e).rail);

             // ANIM (with rail block)
             e = reader.readNextEntry();
             assertEquals(AnimationStateNodeEntry.class, e.getClass());
             assertEquals("a", ((AnimationStateNodeEntry) e).name);
             assertEquals(new Vector(-77.6, 4.8, 271.32), ((AnimationStateNodeEntry) e).pos);
             assertEquals(new Vector(0.0, 1.0, 0.0), ((AnimationStateNodeEntry) e).up);
             assertEquals(new IntVector3(-72, 4, 281), ((AnimationStateNodeEntry) e).rail);

             // ANIM (without rail block)
             e = reader.readNextEntry();
             assertEquals(AnimationStateNodeEntry.class, e.getClass());
             assertEquals("b", ((AnimationStateNodeEntry) e).name);
             assertEquals(new Vector(-77.2, 4.8, 271.32), ((AnimationStateNodeEntry) e).pos);
             assertEquals(new Vector(0.0, 1.0, 0.0), ((AnimationStateNodeEntry) e).up);
             assertEquals(null, ((AnimationStateNodeEntry) e).rail);

             // LINK
             e = reader.readNextEntry();
             assertEquals(LinkNodeEntry.class, e.getClass());
             assertEquals(new Vector(-77.5, 4.8, 271.8), ((LinkNodeEntry) e).pos);

             // NODE (without rail block)
             e = reader.readNextEntry();
             assertEquals(NodeEntry.class, e.getClass());
             assertEquals(new Vector(-78.1, 4.0, 286.1), ((NodeEntry) e).pos);
             assertEquals(new Vector(0.0, 1.0, 0.0), ((NodeEntry) e).up);
             assertEquals(null, ((NodeEntry) e).rail);

             // NODE (with rail block)
             e = reader.readNextEntry();
             assertEquals(NodeEntry.class, e.getClass());
             assertEquals(new Vector(-77.5, 4.8, 275.8), ((NodeEntry) e).pos);
             assertEquals(new Vector(0.0, 1.0, -2.22E-16), ((NodeEntry) e).up);
             assertEquals(new IntVector3(-72, 4, 279), ((NodeEntry) e).rail);

             // EOF
             assertNull(reader.readNextEntry());
        }
    }

    @Test
    public void testNoLimitsFormat1() throws Throwable {
        // NoLimits CSV Files, with tabs between the fields and each value quoted
        // Includes a header at the top
        CSVEntry e;
        try (TrackCoasterCSVReader reader = openReader(
                "\"No.\"\t\"PosX\"\t\"PosY\"\t\"PosZ\"\t\"FrontX\"\t\"FrontY\"\t\"FrontZ\"\t\"LeftX\"\t\"LeftY\"\t\"LeftZ\"\t\"UpX\"\t\"UpY\"\t\"UpZ\"\r\n" + 
                "1\t43.345145\t8.695299\t-33.675375\t0.005115\t0.425082\t-0.905141\t-0.938856\t-0.309581\t-0.150694\t-0.344272\t0.850568\t0.397507\r\n" + 
                "2\t43.350331\t8.906957\t-34.128325\t0.020909\t0.419768\t-0.907390\t-0.941258\t-0.297695\t-0.159407\t-0.337040\t0.857422\t0.388886\r\n" + 
                "3\t43.942754\t10.606781\t-38.238981\t0.261420\t0.323027\t-0.909568\t-0.943069\t0.286225\t-0.169398\t0.205621\t0.902069\t0.379462"
        )) {
            // 1
            e = reader.readNextEntry();
            assertEquals(NoLimits2Entry.class, e.getClass());
            assertEquals(1, ((NoLimits2Entry) e).no);
            assertEquals(new Vector(43.345145, 8.695299, -33.675375), ((NoLimits2Entry) e).pos);
            assertEquals(new Vector(0.005115, 0.425082, -0.905141), ((NoLimits2Entry) e).front);
            assertEquals(new Vector(-0.938856, -0.309581, -0.150694), ((NoLimits2Entry) e).left);
            assertEquals(new Vector(-0.344272, 0.850568, 0.397507), ((NoLimits2Entry) e).up);

            // 2
            e = reader.readNextEntry();
            assertEquals(NoLimits2Entry.class, e.getClass());
            assertEquals(2, ((NoLimits2Entry) e).no);
            assertEquals(new Vector(43.350331, 8.906957, -34.128325), ((NoLimits2Entry) e).pos);
            assertEquals(new Vector(0.020909, 0.419768, -0.907390), ((NoLimits2Entry) e).front);
            assertEquals(new Vector(-0.941258, -0.297695, -0.159407), ((NoLimits2Entry) e).left);
            assertEquals(new Vector(-0.337040, 0.857422, 0.388886), ((NoLimits2Entry) e).up);

            // 3
            e = reader.readNextEntry();
            assertEquals(NoLimits2Entry.class, e.getClass());
            assertEquals(3, ((NoLimits2Entry) e).no);
            assertEquals(new Vector(43.942754, 10.606781, -38.238981), ((NoLimits2Entry) e).pos);
            assertEquals(new Vector(0.261420, 0.323027, -0.909568), ((NoLimits2Entry) e).front);
            assertEquals(new Vector(-0.943069, 0.286225, -0.169398), ((NoLimits2Entry) e).left);
            assertEquals(new Vector(0.205621, 0.902069, 0.379462), ((NoLimits2Entry) e).up);

            // EOF
            assertNull(reader.readNextEntry());
        }
    }

    @Test
    public void testNoLimitsFormat2() throws Throwable {
        // NoLimits CSV Files, with tabs between the fields and the entire lines quoted
        // No header at the top
        // Adds support for https://github.com/Buam/nolimits2-csv-exporter
        CSVEntry e;
        try (TrackCoasterCSVReader reader = openReader(
                "\"1\t43.345145\t8.695299\t-33.675375\t0.005115\t0.425082\t-0.905141\t-0.938856\t-0.309581\t-0.150694\t-0.344272\t0.850568\t0.397507\"\r\n" + 
                "\"2\t43.350331\t8.906957\t-34.128325\t0.020909\t0.419768\t-0.907390\t-0.941258\t-0.297695\t-0.159407\t-0.337040\t0.857422\t0.388886\"\r\n" + 
                "\"3\t43.942754\t10.606781\t-38.238981\t0.261420\t0.323027\t-0.909568\t-0.943069\t0.286225\t-0.169398\t0.205621\t0.902069\t0.379462\""
        )) {
            // 1
            e = reader.readNextEntry();
            assertEquals(NoLimits2Entry.class, e.getClass());
            assertEquals(1, ((NoLimits2Entry) e).no);
            assertEquals(new Vector(43.345145, 8.695299, -33.675375), ((NoLimits2Entry) e).pos);
            assertEquals(new Vector(0.005115, 0.425082, -0.905141), ((NoLimits2Entry) e).front);
            assertEquals(new Vector(-0.938856, -0.309581, -0.150694), ((NoLimits2Entry) e).left);
            assertEquals(new Vector(-0.344272, 0.850568, 0.397507), ((NoLimits2Entry) e).up);

            // 2
            e = reader.readNextEntry();
            assertEquals(NoLimits2Entry.class, e.getClass());
            assertEquals(2, ((NoLimits2Entry) e).no);
            assertEquals(new Vector(43.350331, 8.906957, -34.128325), ((NoLimits2Entry) e).pos);
            assertEquals(new Vector(0.020909, 0.419768, -0.907390), ((NoLimits2Entry) e).front);
            assertEquals(new Vector(-0.941258, -0.297695, -0.159407), ((NoLimits2Entry) e).left);
            assertEquals(new Vector(-0.337040, 0.857422, 0.388886), ((NoLimits2Entry) e).up);

            // 3
            e = reader.readNextEntry();
            assertEquals(NoLimits2Entry.class, e.getClass());
            assertEquals(3, ((NoLimits2Entry) e).no);
            assertEquals(new Vector(43.942754, 10.606781, -38.238981), ((NoLimits2Entry) e).pos);
            assertEquals(new Vector(0.261420, 0.323027, -0.909568), ((NoLimits2Entry) e).front);
            assertEquals(new Vector(-0.943069, 0.286225, -0.169398), ((NoLimits2Entry) e).left);
            assertEquals(new Vector(0.205621, 0.902069, 0.379462), ((NoLimits2Entry) e).up);

            // EOF
            assertNull(reader.readNextEntry());
        }
    }

    private TrackCoasterCSVReader openReader(String text) throws UnsupportedEncodingException, IOException {
        return new TrackCoasterCSVReader(new ByteArrayInputStream(text.getBytes("UTF-8")));
    }
}
