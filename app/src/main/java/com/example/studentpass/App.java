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
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class App extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    private static final String PREFS_NAME = "student_pass_prefs";
    private static final String KEY_DEFAULT_TEACHER = "default_teacher";
    private static final String KEY_PASS_LOG = "pass_log";

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
    private Button activePassesButton;

    private LinearLayout activePassesLayout;
    private LinearLayout activePassesList;

    private TextRecognizer textRecognizer;
    private ExecutorService cameraExecutor;
    private boolean isProcessingFrame = false;

    private BluetoothPassPrinter passPrinter;
    private UsbPassPrinter usbPassPrinter;
    private String currentPassText;

    private SharedPreferences prefs;
    private String currentTeacherName = "";
    private String currentStudentName = "";
    private long currentPassIssuedAt;
    private boolean currentPassRecorded;

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

        activePassesButton = new Button(this);
        activePassesButton.setTextSize(16);
        activePassesButton.setBackgroundColor(Color.parseColor("#455A64"));
        activePassesButton.setTextColor(Color.WHITE);
        activePassesButton.setOnClickListener(v -> showActivePasses());
        FrameLayout.LayoutParams activeButtonParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        activeButtonParams.gravity = Gravity.BOTTOM | Gravity.START;
        activeButtonParams.setMargins(64, 0, 0, 176);
        activePassesButton.setLayoutParams(activeButtonParams);
        cameraContainer.addView(activePassesButton);

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
        printButton.setOnClickListener(v -> {
            recordCurrentPass();
            passPrinter.print(currentPassText);
        });
        resultLayout.addView(printButton);

        usbPrintButton = new Button(this);
        usbPrintButton.setText("PRINT VIA USB");
        usbPrintButton.setTextSize(18);
        usbPrintButton.setBackgroundColor(Color.parseColor("#00796B"));
        usbPrintButton.setTextColor(Color.WHITE);
        usbPrintButton.setPadding(32, 16, 32, 16);
        usbPrintButton.setOnClickListener(v -> {
            recordCurrentPass();
            usbPassPrinter.print(currentPassText);
        });
        resultLayout.addView(usbPrintButton);

        scanNextButton = new Button(this);
        scanNextButton.setText("OK (SCAN NEXT)");
        scanNextButton.setTextSize(18);
        scanNextButton.setBackgroundColor(Color.parseColor("#6200EE"));
        scanNextButton.setTextColor(Color.WHITE);
        scanNextButton.setPadding(32, 16, 32, 16);
        scanNextButton.setOnClickListener(v -> resetToCameraScreen());
        resultLayout.addView(scanNextButton);

        buildActivePassesUI();

        rootLayout.addView(cameraContainer);
        rootLayout.addView(resultLayout);
        rootLayout.addView(activePassesLayout);
        setContentView(rootLayout);
        updateActivePassesButton();
    }

    private void buildActivePassesUI() {
        activePassesLayout = new LinearLayout(this);
        activePassesLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        activePassesLayout.setOrientation(LinearLayout.VERTICAL);
        activePassesLayout.setPadding(32, 48, 32, 32);
        activePassesLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));
        activePassesLayout.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("STUDENTS CURRENTLY OUT");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#263238"));
        title.setPadding(0, 0, 0, 16);
        activePassesLayout.addView(title);

        TextView instructions = new TextView(this);
        instructions.setText("Tap BACK when a student returns. Their return time is saved in the pass log.");
        instructions.setTextSize(15);
        instructions.setTextColor(Color.DKGRAY);
        instructions.setPadding(0, 0, 0, 16);
        activePassesLayout.addView(instructions);

        ScrollView scrollView = new ScrollView(this);
        activePassesList = new LinearLayout(this);
        activePassesList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(activePassesList);
        activePassesLayout.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button closeButton = new Button(this);
        closeButton.setText("DONE");
        closeButton.setOnClickListener(v -> hideActivePasses());
        activePassesLayout.addView(closeButton);
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

    private void showActivePasses() {
        refreshActivePasses();
        cameraContainer.setVisibility(View.GONE);
        resultLayout.setVisibility(View.GONE);
        activePassesLayout.setVisibility(View.VISIBLE);
    }

    private void hideActivePasses() {
        activePassesLayout.setVisibility(View.GONE);
        cameraContainer.setVisibility(View.VISIBLE);
    }

    private void recordCurrentPass() {
        if (currentPassRecorded || currentStudentName.isEmpty()) {
            return;
        }

        List<PassRecord> records = loadPassRecords();
        records.add(new PassRecord(
                UUID.randomUUID().toString(), currentStudentName, currentTeacherName,
                DESTINATION, currentPassIssuedAt, 0
        ));
        savePassRecords(records);
        currentPassRecorded = true;
        updateActivePassesButton();
        Toast.makeText(this, currentStudentName + " is now marked as out.", Toast.LENGTH_SHORT).show();
    }

    private void refreshActivePasses() {
        activePassesList.removeAllViews();
        List<PassRecord> records = loadPassRecords();
        boolean hasActivePasses = false;

        for (PassRecord record : records) {
            if (record.returnedAt != 0) {
                continue;
            }
            hasActivePasses = true;
            activePassesList.addView(buildActivePassCard(record));
        }

        if (!hasActivePasses) {
            TextView emptyMessage = new TextView(this);
            emptyMessage.setText("No students are currently out.");
            emptyMessage.setTextSize(18);
            emptyMessage.setTextColor(Color.DKGRAY);
            emptyMessage.setGravity(Gravity.CENTER);
            emptyMessage.setPadding(0, 48, 0, 48);
            activePassesList.addView(emptyMessage);
        }
        updateActivePassesButton();
    }

    private View buildActivePassCard(PassRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);
        card.setBackgroundColor(Color.WHITE);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);

        TextView name = new TextView(this);
        name.setText(record.studentName);
        name.setTextSize(21);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(Color.BLACK);
        card.addView(name);

        TextView details = new TextView(this);
        String teacherLine = record.teacherName.isEmpty() ? "" : "Teacher: " + record.teacherName + "\n";
        details.setText(teacherLine + "Destination: " + record.destination + "\nOut: "
                + formatDateTime(record.issuedAt));
        details.setTextSize(16);
        details.setTextColor(Color.DKGRAY);
        details.setPadding(0, 8, 0, 8);
        card.addView(details);

        Button backButton = new Button(this);
        backButton.setText("BACK (STUDENT RETURNED)");
        backButton.setTextColor(Color.WHITE);
        backButton.setBackgroundColor(Color.parseColor("#388E3C"));
        backButton.setOnClickListener(v -> markStudentReturned(record.id));
        card.addView(backButton);
        return card;
    }

    private void markStudentReturned(String id) {
        List<PassRecord> records = loadPassRecords();
        for (PassRecord record : records) {
            if (record.id.equals(id)) {
                record.returnedAt = System.currentTimeMillis();
                savePassRecords(records);
                Toast.makeText(this, record.studentName + " marked as returned.", Toast.LENGTH_SHORT).show();
                refreshActivePasses();
                return;
            }
        }
    }

    private void updateActivePassesButton() {
        if (activePassesButton == null) {
            return;
        }
        int activeCount = 0;
        for (PassRecord record : loadPassRecords()) {
            if (record.returnedAt == 0) {
                activeCount++;
            }
        }
        activePassesButton.setText("ACTIVE PASSES (" + activeCount + ")");
    }

    private List<PassRecord> loadPassRecords() {
        List<PassRecord> records = new ArrayList<>();
        String savedLog = prefs.getString(KEY_PASS_LOG, "[]");
        try {
            JSONArray jsonRecords = new JSONArray(savedLog);
            for (int i = 0; i < jsonRecords.length(); i++) {
                JSONObject jsonRecord = jsonRecords.getJSONObject(i);
                records.add(new PassRecord(
                        jsonRecord.getString("id"),
                        jsonRecord.getString("studentName"),
                        jsonRecord.optString("teacherName", ""),
                        jsonRecord.optString("destination", DESTINATION),
                        jsonRecord.getLong("issuedAt"),
                        jsonRecord.optLong("returnedAt", 0)
                ));
            }
        } catch (JSONException e) {
            Log.e("StudentPass", "Could not read saved pass log", e);
        }
        return records;
    }

    private void savePassRecords(List<PassRecord> records) {
        JSONArray jsonRecords = new JSONArray();
        for (PassRecord record : records) {
            try {
                JSONObject jsonRecord = new JSONObject();
                jsonRecord.put("id", record.id);
                jsonRecord.put("studentName", record.studentName);
                jsonRecord.put("teacherName", record.teacherName);
                jsonRecord.put("destination", record.destination);
                jsonRecord.put("issuedAt", record.issuedAt);
                jsonRecord.put("returnedAt", record.returnedAt);
                jsonRecords.put(jsonRecord);
            } catch (JSONException e) {
                Log.e("StudentPass", "Could not save pass record", e);
            }
        }
        prefs.edit().putString(KEY_PASS_LOG, jsonRecords.toString()).apply();
    }

    private String formatDateTime(long time) {
        return new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(new Date(time));
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // fixed resolution instead of letting CameraX auto-pick one -- auto-selection is
                // what trips up older/quirky camera2 HALs (older Samsung chipsets in particular)
                Size targetResolution = new Size(1280, 720);

                Preview preview = new Preview.Builder()
                        .setTargetResolution(targetResolution)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(targetResolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processCameraFrame);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                // was only catching ExecutionException/InterruptedException before, which let any
                // other camera2/HAL failure (common on older devices) crash the whole app on open
                Log.e("StudentPass", "Camera failed to start", e);
                Toast.makeText(this, "Camera failed to start on this device.", Toast.LENGTH_LONG).show();
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
        currentStudentName = studentName;
        currentPassIssuedAt = System.currentTimeMillis();
        currentPassRecorded = false;
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
    public void onBackPressed() {
        if (activePassesLayout != null && activePassesLayout.getVisibility() == View.VISIBLE) {
            hideActivePasses();
        } else {
            super.onBackPressed();
        }
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

    private static final class PassRecord {
        final String id;
        final String studentName;
        final String teacherName;
        final String destination;
        final long issuedAt;
        long returnedAt;

        PassRecord(String id, String studentName, String teacherName, String destination,
                   long issuedAt, long returnedAt) {
            this.id = id;
            this.studentName = studentName;
            this.teacherName = teacherName;
            this.destination = destination;
            this.issuedAt = issuedAt;
            this.returnedAt = returnedAt;
        }
    }
}
