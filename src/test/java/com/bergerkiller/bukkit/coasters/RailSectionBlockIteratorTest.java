package com.bergerkiller.bukkit.coasters;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.bukkit.coasters.util.RailSectionBlockIterator;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.controller.components.RailPath;

public class RailSectionBlockIteratorTest {

    @Test
    public void testIterator() {
        RailPath.Point p0 = new RailPath.Point(0.45648946023063963,1.8923327746278384,1.770718648404808);
        RailPath.Point p1 = new RailPath.Point(4.093611166091037,4.381399987094868,3.3969002901065504);
        RailPath.Segment segment = new RailPath.Segment(p0, p1);
        RailSectionBlockIterator iter = new RailSectionBlockIterator(segment, new IntVector3(0, 0, 0));
        assertEquals(new IntVector3(0, 1, 1), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(0, 2, 1), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(0, 2, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(1, 2, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(2, 2, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(2, 3, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(3, 3, 2), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(3, 3, 3), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(3, 4, 3), iter.block());
        assertTrue(iter.next());
        assertEquals(new IntVector3(4, 4, 3), iter.block());
        assertFalse(iter.next());
    }
}
