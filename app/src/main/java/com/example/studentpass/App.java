package com.example.studentpass;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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

    private static final String PREFS_NAME = "student_pass_prefs";
    private static final String KEY_DEFAULT_TEACHER = "default_teacher";

    private static final String DESTINATION = "BATHROOM";

    private FrameLayout cameraContainer;
    private PreviewView previewView;
    private Button captureButton;
    private EditText teacherNameInput;
    private CheckBox defaultTeacherCheckBox;

    private LinearLayout resultLayout;
    private TextView passTitleTextView;
    private TextView passDetailsTextView;
    private Button printButton;
    private Button usbPrintButton;
    private Button scanNextButton;

    private TextRecognizer textRecognizer;
    private ExecutorService cameraExecutor;
    private boolean isProcessingFrame = false;

    private BluetoothPassPrinter passPrinter;
    private UsbPassPrinter usbPassPrinter;
    private String currentPassText;

    private SharedPreferences prefs;
    private String currentTeacherName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();
        passPrinter = new BluetoothPassPrinter(this);
        usbPassPrinter = new UsbPassPrinter(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        buildMainUI();

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

        cameraContainer.addView(buildTeacherPanel());

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

    private View buildTeacherPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(48, 48, 48, 32);
        panel.setBackgroundColor(Color.parseColor("#CC000000"));

        TextView label = new TextView(this);
        label.setText("TEACHER");
        label.setTextSize(14);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(Color.WHITE);
        panel.addView(label);

        teacherNameInput = new EditText(this);
        teacherNameInput.setHint("Teacher name");
        teacherNameInput.setHintTextColor(Color.parseColor("#88FFFFFF"));
        teacherNameInput.setTextColor(Color.WHITE);
        teacherNameInput.setTextSize(18);
        teacherNameInput.setSingleLine(true);
        teacherNameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        panel.addView(teacherNameInput);

        defaultTeacherCheckBox = new CheckBox(this);
        defaultTeacherCheckBox.setText("Set as default (remember this name)");
        defaultTeacherCheckBox.setTextColor(Color.WHITE);
        panel.addView(defaultTeacherCheckBox);

        String savedTeacher = prefs.getString(KEY_DEFAULT_TEACHER, "");
        if (!savedTeacher.isEmpty()) {
            teacherNameInput.setText(savedTeacher);
            teacherNameInput.setSelection(savedTeacher.length());
            defaultTeacherCheckBox.setChecked(true);
        }

        defaultTeacherCheckBox.setOnCheckedChangeListener((box, isChecked) -> {
            if (isChecked) {
                saveDefaultTeacher(teacherNameInput.getText().toString().trim());
                Toast.makeText(this, "Teacher name saved", Toast.LENGTH_SHORT).show();
            } else {
                prefs.edit().remove(KEY_DEFAULT_TEACHER).apply();
            }
        });

        teacherNameInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override public void afterTextChanged(Editable s) {
                if (defaultTeacherCheckBox.isChecked()) {
                    saveDefaultTeacher(s.toString().trim());
                }
            }
        });

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.TOP;
        panel.setLayoutParams(panelParams);
        return panel;
    }

    private void saveDefaultTeacher(String name) {
        if (name.isEmpty()) {
            prefs.edit().remove(KEY_DEFAULT_TEACHER).apply();
        } else {
            prefs.edit().putString(KEY_DEFAULT_TEACHER, name).apply();
        }
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
        currentTeacherName = teacherNameInput.getText().toString().trim();
        isProcessingFrame = true;
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
        String studentName = IdCardParser.extractName(visionText);
        String date = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

        StringBuilder pass = new StringBuilder();
        pass.append("NAME: ").append(studentName).append("\n");
        if (!currentTeacherName.isEmpty()) {
            pass.append("TEACHER: ").append(currentTeacherName).append("\n");
        }
        pass.append("DESTINATION: ").append(DESTINATION).append("\n")
                .append("DATE: ").append(date).append("\n")
                .append("TIME: ").append(time);

        currentPassText = pass.toString();

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
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            passPrinter.onPromptAnswered(allGranted);
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
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        cameraExecutor.shutdown();
        usbPassPrinter.release();
    }
}