package com.example.studentpass;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
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
import java.util.HashSet;
import java.util.Set;
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

    // Where a student can go. BATHROOM is first so it's what the picker starts on.
    // OTHER shows a text box so the teacher can type anything that isn't on the list.
    private static final String DEFAULT_DESTINATION = "BATHROOM";
    private static final String OTHER_DESTINATION = "OTHER";
    private static final String[] DESTINATIONS = {
            DEFAULT_DESTINATION, "NURSE", "MAIN OFFICE", "COUNSELOR", "LIBRARY",
            "WATER FOUNTAIN", "LOCKER", OTHER_DESTINATION
    };

    private FrameLayout cameraContainer;
    private PreviewView previewView;
    private Button captureButton;
    private EditText teacherNameInput;
    private CheckBox defaultTeacherCheckBox;
    private Spinner destinationSpinner;
    private EditText otherDestinationInput;

    private LinearLayout resultLayout;
    private TextView passTitleTextView;
    private TextView passDetailsTextView;
    private Button printButton;
    private Button usbPrintButton;
    private Button scanNextButton;
    private Button activePassesButton;

    private LinearLayout activePassesLayout;
    private LinearLayout activePassesList;
    private LinearLayout passLogLayout;
    private LinearLayout passLogList;
    private CheckBox selectAllLogCheckBox;
    private final Set<String> selectedLogIds = new HashSet<>();
    private boolean changingLogSelection;

    private TextRecognizer textRecognizer;
    private ExecutorService cameraExecutor;
    private boolean isProcessingFrame = false;

    private BluetoothPassPrinter passPrinter;
    private UsbPassPrinter usbPassPrinter;
    private String currentPassText;

    private SharedPreferences prefs;
    private String currentTeacherName = "";
    private String currentStudentName = "";
    private String currentDestination = DEFAULT_DESTINATION;
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
        rootLayout.addView(passLogLayout);
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

        Button passLogButton = new Button(this);
        passLogButton.setText("VIEW PASS LOG");
        passLogButton.setOnClickListener(v -> showPassLog());
        activePassesLayout.addView(passLogButton, activePassesLayout.getChildCount() - 1);

        buildPassLogUI();
    }

    private void buildPassLogUI() {
        passLogLayout = new LinearLayout(this);
        passLogLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        passLogLayout.setOrientation(LinearLayout.VERTICAL);
        passLogLayout.setPadding(32, 48, 32, 32);
        passLogLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));
        passLogLayout.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("PASS LOG");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#263238"));
        passLogLayout.addView(title);

        TextView message = new TextView(this);
        message.setText("Saved pass and return history. Select entries to delete them.");
        message.setTextSize(15);
        message.setTextColor(Color.DKGRAY);
        message.setPadding(0, 0, 0, 8);
        passLogLayout.addView(message);

        selectAllLogCheckBox = new CheckBox(this);
        selectAllLogCheckBox.setText("SELECT ALL");
        selectAllLogCheckBox.setTextSize(16);
        selectAllLogCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (changingLogSelection) {
                return;
            }
            selectedLogIds.clear();
            if (isChecked) {
                for (PassRecord record : loadPassRecords()) {
                    selectedLogIds.add(record.id);
                }
            }
            refreshPassLog();
        });
        passLogLayout.addView(selectAllLogCheckBox);

        ScrollView scrollView = new ScrollView(this);
        passLogList = new LinearLayout(this);
        passLogList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(passLogList);
        passLogLayout.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button deleteSelectedButton = new Button(this);
        deleteSelectedButton.setText("DELETE SELECTED");
        deleteSelectedButton.setTextColor(Color.WHITE);
        deleteSelectedButton.setBackgroundColor(Color.parseColor("#C62828"));
        deleteSelectedButton.setOnClickListener(v -> confirmDeleteSelected());
        passLogLayout.addView(deleteSelectedButton);

        Button deleteAllButton = new Button(this);
        deleteAllButton.setText("DELETE ALL LOG DATA");
        deleteAllButton.setTextColor(Color.WHITE);
        deleteAllButton.setBackgroundColor(Color.parseColor("#8E0000"));
        deleteAllButton.setOnClickListener(v -> confirmDeleteAll());
        passLogLayout.addView(deleteAllButton);

        Button closeButton = new Button(this);
        closeButton.setText("DONE");
        closeButton.setOnClickListener(v -> hidePassLog());
        passLogLayout.addView(closeButton);
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

        addDestinationPicker(panel);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.gravity = Gravity.TOP;
        panel.setLayoutParams(panelParams);
        return panel;
    }

    private void addDestinationPicker(LinearLayout panel) {
        TextView label = new TextView(this);
        label.setText("DESTINATION");
        label.setTextSize(14);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(Color.WHITE);
        label.setPadding(0, 16, 0, 0);
        panel.addView(label);

        destinationSpinner = new Spinner(this);
        // The panel is dark, so the selected item is drawn in white. The dropdown list
        // itself opens on a light background, so it keeps the normal dark text.
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, DESTINATIONS) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(18);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        destinationSpinner.setAdapter(adapter);
        destinationSpinner.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        panel.addView(destinationSpinner);

        otherDestinationInput = new EditText(this);
        otherDestinationInput.setHint("Where is the student going?");
        otherDestinationInput.setHintTextColor(Color.parseColor("#88FFFFFF"));
        otherDestinationInput.setTextColor(Color.WHITE);
        otherDestinationInput.setTextSize(18);
        otherDestinationInput.setSingleLine(true);
        otherDestinationInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        otherDestinationInput.setVisibility(View.GONE);
        panel.addView(otherDestinationInput);

        destinationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean isOther = OTHER_DESTINATION.equals(DESTINATIONS[position]);
                otherDestinationInput.setVisibility(isOther ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    /** What the picker is set to right now. Empty if OTHER is picked but nothing was typed. */
    private String selectedDestination() {
        String picked = String.valueOf(destinationSpinner.getSelectedItem());
        if (OTHER_DESTINATION.equals(picked)) {
            return otherDestinationInput.getText().toString().trim().toUpperCase(Locale.US);
        }
        return picked;
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

    private void showPassLog() {
        selectedLogIds.clear();
        refreshPassLog();
        activePassesLayout.setVisibility(View.GONE);
        passLogLayout.setVisibility(View.VISIBLE);
    }

    private void hidePassLog() {
        passLogLayout.setVisibility(View.GONE);
        activePassesLayout.setVisibility(View.VISIBLE);
        refreshActivePasses();
    }

    private void refreshPassLog() {
        List<PassRecord> records = loadPassRecords();
        selectedLogIds.retainAll(recordIds(records));
        passLogList.removeAllViews();

        changingLogSelection = true;
        selectAllLogCheckBox.setChecked(!records.isEmpty() && selectedLogIds.size() == records.size());
        changingLogSelection = false;

        if (records.isEmpty()) {
            TextView emptyMessage = new TextView(this);
            emptyMessage.setText("No saved pass records.");
            emptyMessage.setTextSize(18);
            emptyMessage.setTextColor(Color.DKGRAY);
            emptyMessage.setGravity(Gravity.CENTER);
            emptyMessage.setPadding(0, 48, 0, 48);
            passLogList.addView(emptyMessage);
            return;
        }

        // Newest records first, while leaving their order in saved storage unchanged.
        for (int i = records.size() - 1; i >= 0; i--) {
            passLogList.addView(buildPassLogCard(records.get(i)));
        }
    }

    private Set<String> recordIds(List<PassRecord> records) {
        Set<String> ids = new HashSet<>();
        for (PassRecord record : records) {
            ids.add(record.id);
        }
        return ids;
    }

    private View buildPassLogCard(PassRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 16, 24, 16);
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);

        CheckBox selectCheckBox = new CheckBox(this);
        selectCheckBox.setText(record.studentName);
        selectCheckBox.setTextSize(20);
        selectCheckBox.setTypeface(null, Typeface.BOLD);
        selectCheckBox.setChecked(selectedLogIds.contains(record.id));
        selectCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedLogIds.add(record.id);
            } else {
                selectedLogIds.remove(record.id);
            }
            updateSelectAllState();
        });
        card.addView(selectCheckBox);

        TextView details = new TextView(this);
        String teacherLine = record.teacherName.isEmpty() ? "" : "Teacher: " + record.teacherName + "\n";
        String returnLine = record.returnedAt == 0
                ? "Status: Still out"
                : "Back: " + formatDateTime(record.returnedAt);
        details.setText(teacherLine + "Destination: " + record.destination + "\nOut: "
                + formatDateTime(record.issuedAt) + "\n" + returnLine);
        details.setTextSize(16);
        details.setTextColor(Color.DKGRAY);
        card.addView(details);
        return card;
    }

    private void updateSelectAllState() {
        List<PassRecord> records = loadPassRecords();
        changingLogSelection = true;
        selectAllLogCheckBox.setChecked(!records.isEmpty() && selectedLogIds.size() == records.size());
        changingLogSelection = false;
    }

    private void confirmDeleteSelected() {
        if (selectedLogIds.isEmpty()) {
            Toast.makeText(this, "Select one or more log entries first.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete selected entries?")
                .setMessage("This removes " + selectedLogIds.size() + " saved log entr"
                        + (selectedLogIds.size() == 1 ? "y." : "ies."))
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (dialog, which) -> deleteSelectedLogRecords())
                .show();
    }

    private void deleteSelectedLogRecords() {
        List<PassRecord> records = loadPassRecords();
        List<PassRecord> keptRecords = new ArrayList<>();
        for (PassRecord record : records) {
            if (!selectedLogIds.contains(record.id)) {
                keptRecords.add(record);
            }
        }
        savePassRecords(keptRecords);
        selectedLogIds.clear();
        refreshPassLog();
        updateActivePassesButton();
        Toast.makeText(this, "Selected log entries deleted.", Toast.LENGTH_SHORT).show();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all pass log data?")
                .setMessage("This permanently removes every saved pass and return record.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE ALL", (dialog, which) -> {
                    savePassRecords(new ArrayList<>());
                    selectedLogIds.clear();
                    refreshPassLog();
                    updateActivePassesButton();
                    Toast.makeText(this, "All pass log data deleted.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void recordCurrentPass() {
        if (currentPassRecorded || currentStudentName.isEmpty()) {
            return;
        }

        List<PassRecord> records = loadPassRecords();
        records.add(new PassRecord(
                UUID.randomUUID().toString(), currentStudentName, currentTeacherName,
                currentDestination, currentPassIssuedAt, 0
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
                        jsonRecord.optString("destination", DEFAULT_DESTINATION),
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
        String destination = selectedDestination();
        if (destination.isEmpty()) {
            Toast.makeText(this, "Type where the student is going first.", Toast.LENGTH_SHORT).show();
            return;
        }
        currentDestination = destination;
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
        pass.append("DESTINATION: ").append(currentDestination).append("\n")
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
        if (passLogLayout != null && passLogLayout.getVisibility() == View.VISIBLE) {
            hidePassLog();
        } else if (activePassesLayout != null && activePassesLayout.getVisibility() == View.VISIBLE) {
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
