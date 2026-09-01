package com.example.studentpass;

import java.nio.charset.StandardCharsets;

/**
 * turns the pass text into the bytes a receipt printer wants.
 *
 * esc/pos is the language these printers speak, its really just plain text with a few weird
 * control bytes mixed in. lives in its own file now cause bluetooth and usb send the exact
 * same thing, only difference is which wire it goes down.
 */
final class EscPos {

    private EscPos() {
    }

    static byte[] receipt(String passText) {
        char esc = 27;      // this is the ESC byte, basically tells the printer "hey next couple bytes are a command not actual text"
        char big = 48;      // this number makes the font double width and double height
        char off = 0;       // puts whatever came before back to normal / default
        char centre = 1;

        String body = "" + esc + '@'                 // resets the printer back to default settings
                + esc + 'a' + centre
                + esc + '!' + big
                + "HALL PASS\n"
                + esc + '!' + off                    // ok normal size text again
                + esc + 'a' + off                    // and back to left aligned
                + "\n" + passText + "\n"
                + "\nSignature: ______________\n"
                + "\n\n\n";                          // just feeding some blank paper so u can actually tear it off after
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }
}
