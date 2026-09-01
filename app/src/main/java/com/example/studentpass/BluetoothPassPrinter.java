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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * prints the hall pass to whatever bluetooth receipt printer is paired up.
 *
 * only uses devices that are already paired in android settings, so we dont scan for
 * anything and dont need location permission either (android makes u ask for location
 * if u wanna scan for new devices, kinda annoying but this way we skip it). basically
 * 4 steps -- make sure bluetooth is actually on and we have perms, let the user pick
 * which printer, open a connection to it, then just write the text over.
 */
@SuppressLint("MissingPermission") // hasPermission() below checks this by hand every time so its a false alarm
final class BluetoothPassPrinter {

    /** this is the "serial port profile" uuid, basically the plain raw bytes channel that pretty much every printer has */
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    static final int REQUEST_ENABLE_BLUETOOTH = 201;
    static final int REQUEST_BLUETOOTH_PERMISSION = 202;

    private final Activity activity;
    private String pendingPass;

    BluetoothPassPrinter(Activity activity) {
        this.activity = activity;
    }

    /** prints the pass. if bluetooth is off or we dont have perms yet, it just asks first n comes right back to this once youp answer */
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

    /** app.java calls this after either popup closes (the perm one or the turn bluetooth on one) so we can pick back up where we left off */
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
                out.write(EscPos.receipt(passText));
                out.flush();
                Thread.sleep(400); // gotta give the printer like half a sec to actually finish before we slam the socket shut
                activity.runOnUiThread(() -> toast("Pass printed."));
            } catch (Exception e) {
                activity.runOnUiThread(() -> toast("Could not reach the printer."));
            }
        }).start();
    }

    boolean hasPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S // on older androids this perm just gets granted automatically at install, no popup
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
