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

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * same hall pass as the bluetooth one, just down a usb cable instead.
 *
 * u need a usb-otg adapter for this, thats the little dongle that lets the phone be the host
 * for once instead of the thing getting charged, then the printer plugs straight into that.
 * theres no permission to put in the manifest for this one, android pops its own "let this app
 * talk to the usb device" box the first time instead. same 4 steps as bluetooth -- find the
 * printer, get the ok from that popup, open the pipe, then just write the text over.
 */
final class UsbPassPrinter {

    /** our own private broadcast, android sends it back to us once the user answers the usb popup */
    private static final String ACTION_USB_PERMISSION = "com.example.studentpass.USB_PERMISSION";

    /** how long we sit waiting on the cable before giving up, printers are slow but not this slow */
    private static final int SEND_TIMEOUT_MS = 5000;

    private final Activity activity;
    private String pendingPass;

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
        // NOT_EXPORTED cause this broadcast is ours, no other app has any business sending it to us
        ContextCompat.registerReceiver(activity, permissionReceiver,
                new IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** app.java calls this when the screen closes so we dont leave the receiver sitting there registered */
    void release() {
        activity.unregisterReceiver(permissionReceiver);
    }

    /** prints the pass. if android hasnt asked about this printer yet it asks first n comes right back here once u answer */
    void print(String passText) {
        pendingPass = passText;

        UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            toast("This phone has no USB host support.");
            return;
        }

        List<UsbDevice> printers = new ArrayList<>();
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (pipeTo(device) != null) {
                printers.add(device);
            }
        }
        if (printers.isEmpty()) {
            toast("Plug the printer in with an OTG adapter first.");
            return;
        }

        String[] names = new String[printers.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = nameOf(printers.get(i));
        }
        new AlertDialog.Builder(activity)
                .setTitle("Print pass to")
                .setItems(names, (dialog, which) -> askThenSend(manager, printers.get(which), passText))
                .show();
    }

    private void askThenSend(UsbManager manager, UsbDevice device, String passText) {
        if (manager.hasPermission(device)) {
            send(device, passText);
            return;
        }
        // MUTABLE cause android fills in which device u picked n whether u said yes before handing this
        // back to us, and setPackage keeps it aimed at us only, android 14 refuses to build it otherwise
        Intent answer = new Intent(ACTION_USB_PERMISSION).setPackage(activity.getPackageName());
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
        manager.requestPermission(device, PendingIntent.getBroadcast(activity, 0, answer, flags));
    }

    private void send(UsbDevice device, String passText) {
        toast("Printing...");
        new Thread(() -> {
            UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
            Pipe pipe = pipeTo(device);
            UsbDeviceConnection connection = manager == null || pipe == null ? null : manager.openDevice(device);
            if (connection == null) {
                activity.runOnUiThread(() -> toast("Could not reach the printer."));
                return;
            }
            try {
                byte[] bytes = EscPos.receipt(passText);
                boolean sent = false;
                if (connection.claimInterface(pipe.iface, true)) { // true = boot whatever driver had it, its ours now
                    sent = connection.bulkTransfer(pipe.endpoint, bytes, bytes.length, SEND_TIMEOUT_MS) == bytes.length;
                    connection.releaseInterface(pipe.iface);
                }
                boolean printed = sent;
                activity.runOnUiThread(() -> toast(printed ? "Pass printed." : "Could not reach the printer."));
            } finally {
                connection.close();
            }
        }).start();
    }

    /**
     * finds the way out of the phone and into the printer. proper printers announce themselves as usb
     * class 7 so we grab those first, but the cheap ones just say "vendor specific", so if we never see
     * a real one we settle for any pipe thats bulk and pointing outwards, which is a printer anyway.
     */
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

    private static String nameOf(UsbDevice device) {
        String name = device.getProductName();
        return name != null ? name : device.getDeviceName();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    /** just the interface plus the one endpoint on it that actually carries bytes out, thats it */
    private static final class Pipe {
        final UsbInterface iface;
        final UsbEndpoint endpoint;

        Pipe(UsbInterface iface, UsbEndpoint endpoint) {
            this.iface = iface;
            this.endpoint = endpoint;
        }
    }
}
