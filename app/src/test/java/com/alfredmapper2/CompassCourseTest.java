package com.alfredmapper2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CompassCourseTest {
    @Test
    public void normalizesNegativeBearing() {
        assertEquals(270f, CompassCourse.normalize(-90f), 0.001f);
    }

    @Test
    public void normalizesBearingAboveFullCircle() {
        assertEquals(20f, CompassCourse.normalize(380f), 0.001f);
    }
}
