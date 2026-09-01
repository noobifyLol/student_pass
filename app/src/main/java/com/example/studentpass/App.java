package com.example.studentpass;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    
    // ui stuff
    private FrameLayout cameraContainer;
    private PreviewView previewView;
    private Button captureButton;

    private LinearLayout resultLayout;
    private TextView passTitleTextView;
    private TextView passDetailsTextView;
    private Button printButton;
    private Button usbPrintButton;
    private Button scanNextButton;

    // camera / ml kit
    private TextRecognizer textRecognizer;
    private ExecutorService cameraExecutor;
    private boolean isProcessingFrame = false;

    // print stuff, one printer per cable basically. both send the exact same pass
    private BluetoothPassPrinter passPrinter;
    private UsbPassPrinter usbPassPrinter;
    private String currentPassText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. spin up the offline ml kit engine (this is the thing that actually reads text off the card)
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();
        passPrinter = new BluetoothPassPrinter(this);
        usbPassPrinter = new UsbPassPrinter(this);

        // 2. build the screen
        buildMainUI();

        // 3. ask for camera perms then start it up, if we already have perms just go
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void buildMainUI() {
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // camera screen, this one shows first when u open the app
        cameraContainer = new FrameLayout(this);
        cameraContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        previewView = new PreviewView(this);
        previewView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        cameraContainer.addView(previewView);

        captureButton = new Button(this);
        captureButton.setText("CAPTURE ID CARD");
        captureButton.setTextSize(18);
        captureButton.setBackgroundColor(Color.parseColor("#6200EE"));
        captureButton.setTextColor(Color.WHITE);
        
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnParams.gravity = Gravity.BOTTOM;
        btnParams.setMargins(64, 0, 64, 64);
        captureButton.setLayoutParams(btnParams);
        captureButton.setOnClickListener(v -> triggerCapture());
        cameraContainer.addView(captureButton);

        // result screen, stays hidden til we actually scan something
        resultLayout = new LinearLayout(this);
        resultLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        resultLayout.setOrientation(LinearLayout.VERTICAL);
        resultLayout.setGravity(Gravity.CENTER);
        resultLayout.setPadding(64, 64, 64, 64);
        resultLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));
        resultLayout.setVisibility(View.GONE);

        passTitleTextView = new TextView(this);
        passTitleTextView.setText("HALL PASS");
        passTitleTextView.setTextSize(32);
        passTitleTextView.setTypeface(null, Typeface.BOLD);
        passTitleTextView.setTextColor(Color.parseColor("#4CAF50"));
        passTitleTextView.setGravity(Gravity.CENTER);
        passTitleTextView.setPadding(0, 0, 0, 32);
        resultLayout.addView(passTitleTextView);

        passDetailsTextView = new TextView(this);
        passDetailsTextView.setTextSize(20);
        passDetailsTextView.setTextColor(Color.BLACK);
        passDetailsTextView.setGravity(Gravity.CENTER);
        passDetailsTextView.setPadding(0, 0, 0, 48);
        resultLayout.addView(passDetailsTextView);

        printButton = new Button(this);
        printButton.setText("PRINT VIA BLUETOOTH");
        printButton.setTextSize(18);
        printButton.setBackgroundColor(Color.parseColor("#0277BD"));
        printButton.setTextColor(Color.WHITE);
        printButton.setPadding(32, 16, 32, 16);
        printButton.setOnClickListener(v -> passPrinter.print(currentPassText));
        resultLayout.addView(printButton);

        // same pass, just for when the printers plugged in with an otg cable instead of paired
        usbPrintButton = new Button(this);
        usbPrintButton.setText("PRINT VIA USB");
        usbPrintButton.setTextSize(18);
        usbPrintButton.setBackgroundColor(Color.parseColor("#00796B"));
        usbPrintButton.setTextColor(Color.WHITE);
        usbPrintButton.setPadding(32, 16, 32, 16);
        usbPrintButton.setOnClickListener(v -> usbPassPrinter.print(currentPassText));
        resultLayout.addView(usbPrintButton);

        scanNextButton = new Button(this);
        scanNextButton.setText("OK (SCAN NEXT)");
        scanNextButton.setTextSize(18);
        scanNextButton.setBackgroundColor(Color.parseColor("#6200EE"));
        scanNextButton.setTextColor(Color.WHITE);
        scanNextButton.setPadding(32, 16, 32, 16);
        scanNextButton.setOnClickListener(v -> resetToCameraScreen());
        resultLayout.addView(scanNextButton);

        rootLayout.addView(cameraContainer);
        rootLayout.addView(resultLayout);
        setContentView(rootLayout);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processCameraFrame);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void triggerCapture() {
        isProcessingFrame = true; // just tells the analyzer below to grab the very next frame that comes in
        Toast.makeText(this, "Reading Card...", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processCameraFrame(@NonNull ImageProxy imageProxy) {
        if (!isProcessingFrame || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        textRecognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    isProcessingFrame = false;
                    imageProxy.close();
                    parseAndDisplayCardData(visionText);
                })
                .addOnFailureListener(e -> {
                    isProcessingFrame = false;
                    imageProxy.close();
                    runOnUiThread(() -> Toast.makeText(App.this, "Scan Failed, Try Again", Toast.LENGTH_SHORT).show());
                });
    }

    private void parseAndDisplayCardData(Text visionText) {
        // idCardParser does the actual work here, reads top left down and grabs the first line
        // that looks like a name to it
        String studentName = IdCardParser.extractName(visionText);
        String date = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

        currentPassText = "NAME: " + studentName + "\n"
                + "DATE: " + date + "\n"
                + "TIME: " + time;

        runOnUiThread(() -> {
            passDetailsTextView.setText(currentPassText);
            cameraContainer.setVisibility(View.GONE);
            resultLayout.setVisibility(View.VISIBLE);
        });
    }

    private void resetToCameraScreen() {
        resultLayout.setVisibility(View.GONE);
        cameraContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BluetoothPassPrinter.REQUEST_BLUETOOTH_PERMISSION) {
            passPrinter.onPromptAnswered(grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        } else if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission required!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothPassPrinter.REQUEST_ENABLE_BLUETOOTH) {
            passPrinter.onPromptAnswered(resultCode == RESULT_OK);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        usbPassPrinter.release();
    }
}