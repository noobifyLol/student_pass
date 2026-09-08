package com.example.studentpass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class EscPos {

    private EscPos() {
    }

    static byte[] receipt(String passText) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        try {
            // 1. Reset printer (ESC @)
            stream.write(new byte[]{0x1B, 0x40});

            // 2. Set line spacing / alignment to Center (ESC a 1)
            stream.write(new byte[]{0x1B, 0x61, 0x01});

            // 3. Double height & width for header (GS ! 0x11)
            stream.write(new byte[]{0x1D, 0x21, 0x11});
            stream.write("HALL PASS\r\n".getBytes(StandardCharsets.ISO_8859_1));

            // 4. Reset font size to normal (GS ! 0x00)
            stream.write(new byte[]{0x1D, 0x21, 0x00});

            // 5. Left align for pass details (ESC a 0)
            stream.write(new byte[]{0x1B, 0x61, 0x00});

            // 6. Print body (Ensure \r\n is used for line breaks)
            String formattedPass = passText.replace("\n", "\r\n");
            stream.write(("\r\n" + formattedPass + "\r\n").getBytes(StandardCharsets.ISO_8859_1));

            // 7. Signature line
            stream.write("\r\nSignature: ______________\r\n".getBytes(StandardCharsets.ISO_8859_1));

            // 8. Feed paper (ESC d 5) so it clears the tear bar
            stream.write(new byte[]{0x1B, 0x64, 0x05});

        } catch (IOException e) {
            e.printStackTrace();
        }

        return stream.toByteArray();
    }
}