package com.example.studentpass;

import static org.junit.Assert.assertEquals;

import com.example.studentpass.IdCardParser.Line;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * each Line down here is just (text, top, left), same position stuff ml kit gives us back
 * for where that line sat on the card. these tests run fine on a regular computer btw, dont
 * need an actual phone or anything: ./gradlew testDebugUnitTest
 */
public class IdCardParserTest {

    @Test
    public void takesTheTopLeftName() {
        List<Line> lines = Arrays.asList(
                new Line("Prince Wang", 40, 30),
                new Line("UMD Cyber-Physical Systems Engineering", 200, 30),
                new Line("Summer Program 2026", 260, 30));

        assertEquals("Prince Wang", IdCardParser.extractName(lines));
    }

    @Test
    public void skipsSchoolHeadersAndIdNumbers() {
        List<Line> lines = Arrays.asList(
                new Line("MONTGOMERY COUNTY PUBLIC SCHOOLS", 20, 30),
                new Line("Student ID: 1234567", 70, 30),
                new Line("JANE Q. DOE", 120, 30));

        assertEquals("JANE Q. DOE", IdCardParser.extractName(lines));
    }

    @Test
    public void stripsANameLabel() {
        List<Line> lines = Arrays.asList(
                new Line("CENTRAL HIGH SCHOOL", 20, 30),
                new Line("Name: Maria Sanchez", 80, 30));

        assertEquals("Maria Sanchez", IdCardParser.extractName(lines));
    }

    @Test
    public void readsSameRowLeftToRight() {
        List<Line> lines = Arrays.asList(
                new Line("Grade 11", 50, 400),   // same row as the name but way further right
                new Line("Alex Rivera", 52, 30));

        assertEquals("Alex Rivera", IdCardParser.extractName(lines));
    }

    @Test
    public void flipsLastCommaFirst() {
        List<Line> lines = Arrays.asList(new Line("Wang, Prince", 40, 30));

        assertEquals("Prince Wang", IdCardParser.extractName(lines));
    }

    @Test
    public void blankScanSaysUnknown() {
        assertEquals(IdCardParser.UNKNOWN, IdCardParser.extractName(new ArrayList<Line>()));
    }
}
