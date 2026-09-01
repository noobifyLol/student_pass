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
 * finds the persons name on an id card.
 *
 * keeping this one simple on purpose -- just reads the card top left to bottom right like
 * a normal person would and grabs the first line that looks like a name. "looks like a
 * name" is basically 4 cheap checks, no numbers in it, not too long, max 4 words, and its
 * not some word that belongs to the school instead of the actual student.
 */
final class IdCardParser {

    static final String UNKNOWN = "Unknown Student";

    /** words that are always the card's, never gonna be someones actual name */
    private static final Pattern CARD_WORDS = Pattern.compile(
            "\\b(UNIVERSITY|COLLEGE|SCHOOLS?|ACADEMY|COUNTY|DISTRICT|PUBLIC|"
                    + "STUDENT|STAFF|FACULTY|VISITOR|EMPLOYEE|"
                    + "ID|CARD|IDENTIFICATION|VALID|EXPIRES?|ISSUED|SIGNATURE|"
                    + "GRADE|CLASS|YEAR|PROGRAM|DEPARTMENT|DEPT|ENGINEERING|SYSTEMS|SCIENCES?|"
                    + "TECHNOLOGY|LIBRARY|HALL|PASS|DOB|BIRTH|ADDRESS|PHONE|EMAIL|EDU)\\b");

    /** catches stuff like "Name:" or "Student Name -" and grabs whatever comes right after it */
    private static final Pattern NAME_LABEL = Pattern.compile(
            "^(?:student\\s+|full\\s+)?names?\\s*[:\\-]\\s*", Pattern.CASE_INSENSITIVE);

    /** letters plus whatever punctuation actual names use (apostrophes, hyphens etc) */
    private static final Pattern NAME_CHARS = Pattern.compile("[\\p{L}][\\p{L} .,'\\-]*");

    /** if 2 lines are within this many pixels of each other we just treat them as the same row */
    private static final int SAME_ROW_PIXELS = 24;

    private IdCardParser() {
    }

    static String extractName(Text visionText) {
        List<Line> lines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box != null && line.getText() != null) {
                    // run `adb logcat -s StudentPass` if u wanna see exactly what the camera actually read off the card
                    Log.d("StudentPass", "read: " + line.getText());
                    lines.add(new Line(line.getText(), box.top, box.left));
                }
            }
        }
        return extractName(lines);
    }

    /** split this part out from the ml kit call so we can actually test it on a regular computer, no phone needed */
    static String extractName(List<Line> lines) {
        List<Line> inReadingOrder = new ArrayList<>(lines);
        Collections.sort(inReadingOrder, (a, b) -> Math.abs(a.top - b.top) > SAME_ROW_PIXELS
                ? Integer.compare(a.top, b.top)     // higher up the card wins, goes first
                : Integer.compare(a.left, b.left)); // same row so just go left to right like reading normally

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
                && NAME_CHARS.matcher(text).matches()          // letters only so this auto kills anything with a number in it
                && !CARD_WORDS.matcher(text.toUpperCase(Locale.US)).find();
    }

    /** cleans up the raw ocr text, drops a "Name:" label if theres one, squashes double spaces, trims junk off the ends */
    private static String clean(String raw) {
        return NAME_LABEL.matcher(raw.trim()).replaceFirst("")
                .replaceAll("\\s+", " ")
                .replaceAll("^[^\\p{L}]+|[^\\p{L}.']+$", "")
                .trim();
    }

    /** school rosters print it as "Wang, Prince" but the pass just reads better as "Prince Wang" so flip it */
    private static String flipLastCommaFirst(String name) {
        int comma = name.indexOf(',');
        if (comma <= 0 || name.indexOf(',', comma + 1) > 0) {
            return name;
        }
        String last = name.substring(0, comma).trim();
        String first = name.substring(comma + 1).trim();
        return first.isEmpty() || last.isEmpty() ? name : first + " " + last;
    }

    /** just holds one line of text plus where it sat on the card, thats it */
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
