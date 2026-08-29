package com.example.studentpass;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prints a hall pass on a paired Bluetooth receipt printer.
 *
 * Only devices already paired in Android settings are used, so there is no scanning and
 * no location permission. Four steps: make sure Bluetooth is usable, let the user pick a
 * printer, open a socket to it, write the text.
 */
@SuppressLint("MissingPermission") // hasPermission() guards every Bluetooth call below
final class BluetoothPassPrinter {

    /** Serial Port Profile: the "plain bytes" channel every ESC/POS printer exposes. */
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    static final int REQUEST_ENABLE_BLUETOOTH = 201;
    static final int REQUEST_BLUETOOTH_PERMISSION = 202;

    private final Activity activity;
    private String pendingPass;

    BluetoothPassPrinter(Activity activity) {
        this.activity = activity;
    }

    /** Print the pass, asking for Bluetooth permission or power first if needed. */
    void print(String passText) {
        pendingPass = passText;

        BluetoothAdapter adapter = adapter();
        if (adapter == null) {
            toast("This phone has no Bluetooth.");
            return;
        }
        if (!hasPermission()) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSION);
            return;
        }
        if (!adapter.isEnabled()) {
            activity.startActivityForResult(
                    new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            return;
        }

        List<BluetoothDevice> printers = new ArrayList<>(adapter.getBondedDevices());
        if (printers.isEmpty()) {
            toast("Pair the printer in Android Bluetooth settings first.");
            return;
        }

        String[] names = new String[printers.size()];
        for (int i = 0; i < names.length; i++) {
            String name = printers.get(i).getName();
            names[i] = name != null ? name : printers.get(i).getAddress();
        }
        new AlertDialog.Builder(activity)
                .setTitle("Print pass to")
                .setItems(names, (dialog, which) -> send(printers.get(which), passText))
                .show();
    }

    /** The activity forwards both Android prompts back here so the print can continue. */
    void onPromptAnswered(boolean allowed) {
        if (allowed && pendingPass != null) {
            print(pendingPass);
        } else {
            toast("Bluetooth is needed to print.");
        }
    }

    private void send(BluetoothDevice printer, String passText) {
        toast("Printing...");
        new Thread(() -> {
            try (BluetoothSocket socket = printer.createRfcommSocketToServiceRecord(SPP_UUID)) {
                socket.connect();
                OutputStream out = socket.getOutputStream();
                out.write(receipt(passText));
                out.flush();
                Thread.sleep(400); // let the printer drain before the socket closes
                activity.runOnUiThread(() -> toast("Pass printed."));
            } catch (Exception e) {
                activity.runOnUiThread(() -> toast("Could not reach the printer."));
            }
        }).start();
    }

    /** ESC/POS: a few control bytes wrapped around plain text. */
    static byte[] receipt(String passText) {
        char esc = 27;      // ESC: the byte that starts every printer command
        char big = 48;      // font flag for double width + height
        char off = 0;       // "back to default" for the command before it
        char centre = 1;

        String body = "" + esc + '@'                 // reset the printer
                + esc + 'a' + centre
                + esc + '!' + big
                + "HALL PASS\n"
                + esc + '!' + off                    // normal size again
                + esc + 'a' + off                    // left aligned again
                + "\n" + passText + "\n"
                + "\nSignature: ______________\n"
                + "\n\n\n";                          // feed paper past the tear bar
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }

    boolean hasPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S // older Android grants it at install
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
