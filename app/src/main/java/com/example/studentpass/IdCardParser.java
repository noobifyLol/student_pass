package com.example.studentpass;

import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Finds the person's name on an ID card.
 *
 * The rule is deliberately simple: read the card top-left to bottom-right, like a person
 * would, and take the first line that looks like a name. "Looks like a name" is four
 * cheap checks -- no digits, not too long, at most four words, and not a word that
 * belongs to the school rather than the student.
 */
final class IdCardParser {

    static final String UNKNOWN = "Unknown Student";

    /** Words that belong to the card, never to the person. */
    private static final Pattern CARD_WORDS = Pattern.compile(
            "\\b(UNIVERSITY|COLLEGE|SCHOOLS?|ACADEMY|COUNTY|DISTRICT|PUBLIC|"
                    + "STUDENT|STAFF|FACULTY|VISITOR|EMPLOYEE|"
                    + "ID|CARD|IDENTIFICATION|VALID|EXPIRES?|ISSUED|SIGNATURE|"
                    + "GRADE|CLASS|YEAR|PROGRAM|DEPARTMENT|DEPT|ENGINEERING|SYSTEMS|SCIENCES?|"
                    + "TECHNOLOGY|LIBRARY|HALL|PASS|DOB|BIRTH|ADDRESS|PHONE|EMAIL|EDU)\\b");

    /** A "Name:" / "Student Name -" label, with whatever follows it captured. */
    private static final Pattern NAME_LABEL = Pattern.compile(
            "^(?:student\\s+|full\\s+)?names?\\s*[:\\-]\\s*", Pattern.CASE_INSENSITIVE);

    /** Letters plus the punctuation real names use. */
    private static final Pattern NAME_CHARS = Pattern.compile("[\\p{L}][\\p{L} .,'\\-]*");

    /** Lines within this many pixels of each other count as the same row. */
    private static final int SAME_ROW_PIXELS = 24;

    private IdCardParser() {
    }

    static String extractName(Text visionText) {
        List<Line> lines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box != null && line.getText() != null) {
                    // `adb logcat -s StudentPass` shows exactly what the camera read.
                    Log.d("StudentPass", "read: " + line.getText());
                    lines.add(new Line(line.getText(), box.top, box.left));
                }
            }
        }
        return extractName(lines);
    }

    /** Split out from the ML Kit call so it can be unit tested on a plain computer. */
    static String extractName(List<Line> lines) {
        List<Line> inReadingOrder = new ArrayList<>(lines);
        Collections.sort(inReadingOrder, (a, b) -> Math.abs(a.top - b.top) > SAME_ROW_PIXELS
                ? Integer.compare(a.top, b.top)     // higher line first
                : Integer.compare(a.left, b.left)); // same row, so left to right

        for (Line line : inReadingOrder) {
            String text = clean(line.text);
            if (looksLikeName(text)) {
                return flipLastCommaFirst(text);
            }
        }
        return UNKNOWN;
    }

    private static boolean looksLikeName(String text) {
        return text.length() >= 3
                && text.length() <= 40
                && text.split(" ").length <= 4
                && NAME_CHARS.matcher(text).matches()          // letters only, so no digits
                && !CARD_WORDS.matcher(text.toUpperCase(Locale.US)).find();
    }

    /** Tidy the raw line: drop a "Name:" label, collapse spaces, trim stray marks. */
    private static String clean(String raw) {
        return NAME_LABEL.matcher(raw.trim()).replaceFirst("")
                .replaceAll("\\s+", " ")
                .replaceAll("^[^\\p{L}]+|[^\\p{L}.']+$", "")
                .trim();
    }

    /** Rosters print "Wang, Prince"; a pass reads better as "Prince Wang". */
    private static String flipLastCommaFirst(String name) {
        int comma = name.indexOf(',');
        if (comma <= 0 || name.indexOf(',', comma + 1) > 0) {
            return name;
        }
        String last = name.substring(0, comma).trim();
        String first = name.substring(comma + 1).trim();
        return first.isEmpty() || last.isEmpty() ? name : first + " " + last;
    }

    /** One line of text and where it sits on the card. */
    static final class Line {
        final String text;
        final int top;
        final int left;

        Line(String text, int top, int left) {
            this.text = text;
            this.top = top;
            this.left = left;
        }
    }
}
