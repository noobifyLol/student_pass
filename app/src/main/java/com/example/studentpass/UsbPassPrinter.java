package com.example.studentpass;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class UsbPassPrinter {

    private static final String ACTION_USB_PERMISSION = "com.example.studentpass.USB_PERMISSION";
    private static final int SEND_TIMEOUT_MS = 5000;

    private final Activity activity;
    private String pendingPass;
    private UsbDevice openDevice;
    private UsbDeviceConnection connection;
    private Pipe openPipe;

private final AtomicBoolean sending = new AtomicBoolean(false);
    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean allowed = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (allowed && device != null && pendingPass != null) {
                send(device, pendingPass);
            } else {
                toast("USB access is needed to print.");
            }
        }
    };

    UsbPassPrinter(Activity activity) {
        this.activity = activity;
        // The permission broadcast is sent by the system to this app only (see setPackage
        // in askThenSend), so it must not be exported. ContextCompat handles the
        // pre-Tiramisu dispatch itself, but still requires a real flag on targetSdk 34.
        ContextCompat.registerReceiver(activity, permissionReceiver,
                new IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    void release() {
        try {
            activity.unregisterReceiver(permissionReceiver);
        } catch (IllegalArgumentException ignored) {
            // Unregistered safely
        }
    }
    private void closeConnection() {
    if (connection != null) {
        if (openPipe != null) {
            connection.releaseInterface(openPipe.iface);
        }
        connection.close();
    }
    connection = null;
    openPipe = null;
    openDevice = null;
}

    void print(String passText) {
        pendingPass = passText;

        UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            toast("This phone has no USB host support.");
            return;
        }

        // Run USB device discovery on a background thread to prevent UI freezing on Galaxy S8
        new Thread(() -> {
            List<UsbDevice> printers = new ArrayList<>();
            for (UsbDevice device : manager.getDeviceList().values()) {
                if (pipeTo(device) != null) {
                    printers.add(device);
                }
            }

            if (printers.isEmpty()) {
                activity.runOnUiThread(() -> toast("Plug the printer in with an OTG adapter first."));
                return;
            }

            // Fetch product names on background thread (getProductName causes synchronous I/O)
            String[] names = new String[printers.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = nameOf(printers.get(i));
            }

            // Switch back to Main Thread only to show the UI dialog
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                if (printers.size() == 1) {
                    askThenSend(manager, printers.get(0), passText);
                } else {
                    new AlertDialog.Builder(activity)
                            .setTitle("Print pass to")
                            .setItems(names, (dialog, which) -> askThenSend(manager, printers.get(which), passText))
                            .show();
                }
            });
        }).start();
    }

    private void askThenSend(UsbManager manager, UsbDevice device, String passText) {
        if (manager.hasPermission(device)) {
            send(device, passText);
            return;
        }

        Intent answer = new Intent(ACTION_USB_PERMISSION).setPackage(activity.getPackageName());
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
        manager.requestPermission(device, PendingIntent.getBroadcast(activity, 0, answer, flags));
    }
    /**
 * HP's firmware only knows a job is done when it sees a formal job/language boundary
 * (or its own I/O timeout gives up) -- ESC/POS carries none of that, so on its own the
 * M527 just waits until you unplug it or tap Print. Universal Exit Language + a PJL
 * end-of-job gives it that boundary explicitly. This is HP-specific, which is why it's
 * appended here and not inside EscPos.receipt() itself.
 */
private static byte[] withJobTerminator(byte[] escPosPayload) {
    byte[] uel = {0x1B, 0x25, 0x2D, 0x31, 0x32, 0x33, 0x34, 0x35, 0x58}; // ESC%-12345X
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try {
        stream.write(escPosPayload);
        stream.write(uel);
        stream.write("@PJL EOJ\r\n".getBytes(StandardCharsets.ISO_8859_1));
        stream.write(uel);
    } catch (IOException e) {
        e.printStackTrace();
    }
    return stream.toByteArray();
}
    private static Pipe pipeTo(UsbDevice device) {
    Pipe fallback = null;
    for (int i = 0; i < device.getInterfaceCount(); i++) {
        UsbInterface iface = device.getInterface(i);
        for (int j = 0; j < iface.getEndpointCount(); j++) {
            UsbEndpoint endpoint = iface.getEndpoint(j);
            if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK
                    || endpoint.getDirection() != UsbConstants.USB_DIR_OUT) {
                continue;
            }
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                return new Pipe(iface, endpoint);
            }
            if (fallback == null) {
                fallback = new Pipe(iface, endpoint);
            }
        }
    }
    return fallback;
}
    private void send(UsbDevice device, String passText) {
    if (!sending.compareAndSet(false, true)) {
        toast("Still printing the last pass -- hang on a second.");
        return;
    }
    toast("Printing...");
    new Thread(() -> {
        try {
            if (connection == null || openDevice == null
                    || !openDevice.getDeviceName().equals(device.getDeviceName())) {
                closeConnection();
                UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
                Pipe freshPipe = pipeTo(device);
                UsbDeviceConnection freshConnection =
                        manager == null || freshPipe == null ? null : manager.openDevice(device);
                boolean claimed = freshConnection != null
                        && freshPipe.endpoint.getMaxPacketSize() > 0
                        && freshConnection.claimInterface(freshPipe.iface, true);
                if (!claimed) {
                    if (freshConnection != null) {
                        freshConnection.close();
                    }
                    activity.runOnUiThread(() -> toast("Could not reach the printer."));
                    return;
                }
                connection = freshConnection;
                openPipe = freshPipe;
                openDevice = device;
            }

            byte[] bytes = withJobTerminator(EscPos.receipt(passText));
            boolean sent = true;
            int offset = 0;
            while (offset < bytes.length) {
                int length = Math.min(bytes.length - offset, openPipe.endpoint.getMaxPacketSize());
                byte[] chunk = new byte[length];
                System.arraycopy(bytes, offset, chunk, 0, length);

                int written = connection.bulkTransfer(openPipe.endpoint, chunk, length, SEND_TIMEOUT_MS);
                if (written <= 0) {
                    sent = false;
                    break;
                }
                offset += written;
            }
            if (!sent) {
                closeConnection(); // clearly bad now -- force a clean reopen next time
            }
            boolean printed = sent;
            activity.runOnUiThread(() -> toast(printed ? "Pass printed." : "Could not reach the printer."));
        } finally {
            sending.set(false);
        }
    }).start();
}
    private static String nameOf(UsbDevice device) {
        String name = device.getProductName();
        return name != null ? name : device.getDeviceName();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private static final class Pipe {
        final UsbInterface iface;
        final UsbEndpoint endpoint;

        Pipe(UsbInterface iface, UsbEndpoint endpoint) {
            this.iface = iface;
            this.endpoint = endpoint;
        }
    }
}