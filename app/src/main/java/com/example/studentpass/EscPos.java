package com.example.studentpass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class EscPos {

    private EscPos() {
    }

    static byte[] receipt(String passText) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        if (passText == null) {
            passText = "";
        }

        try {
            // 1. Reset printer (ESC @)
            stream.write(new byte[]{0x1B, 0x40});

            // 2. Set alignment to Center (ESC a 1)
            stream.write(new byte[]{0x1B, 0x61, 0x01});

            // 3. Double height & width for header (GS ! 0x11)
            stream.write(new byte[]{0x1D, 0x21, 0x11});
            stream.write("HALL PASS\r\n".getBytes(StandardCharsets.ISO_8859_1));

            // 4. Reset font size to normal (GS ! 0x00)
            stream.write(new byte[]{0x1D, 0x21, 0x00});

            // 5. Print horizontal divider line
            stream.write("--------------------------------\r\n".getBytes(StandardCharsets.ISO_8859_1));

            // 6. Left align for pass details (ESC a 0)
            stream.write(new byte[]{0x1B, 0x61, 0x00});

            // 7. Normalize line breaks to avoid \r\r\n duplicates
            String normalized = passText.replace("\r\n", "\n").replace("\n", "\r\n");
            stream.write((normalized + "\r\n").getBytes(StandardCharsets.ISO_8859_1));

            // 8. Divider line & Signature
            stream.write("--------------------------------\r\n".getBytes(StandardCharsets.ISO_8859_1));
            stream.write("\r\nSignature: ______________\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));

            // 9. Feed paper 4 lines (ESC d 4) to clear the tear bar
            stream.write(new byte[]{0x1B, 0x64, 0x04});

            // 10. Partial paper cut command (GS V 66 0) - ignored safely if printer has no auto-cutter
            stream.write(new byte[]{0x1D, 0x56, 0x42, 0x00});

        } catch (IOException e) {
            e.printStackTrace();
        }

        return stream.toByteArray();
    }
}