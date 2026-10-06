package com.jeevani.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 41;
    private static final int REQUEST_REPORT = 42;
    private static final String TEAL = "#0F766E";
    private static final String DARK_TEAL = "#073C38";
    private static final String PAGE_BG = "#F5FAF9";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ApiClient api;
    private LinearLayout root;
    private LinearLayout content;
    private String screen = "dashboard";
    private String preferredLanguage = "en";
    private JSONObject profile = new JSONObject();
    private JSONArray reports = new JSONArray();
    private JSONArray contacts = new JSONArray();
    private JSONArray chatMessages = new JSONArray();
    private Double lastLatitude;
    private Double lastLongitude;
    private Uri reportUri;
    private String reportDate = "";
    private String doctorHospital = "";
    private boolean geminiConsent;
    private LocationListener activeLocationListener;
    private Runnable locationTimeout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);
        if (api.isAuthenticated()) {
            startPage("Loading Jeevani AI", "Connecting securely to your Jeevani server…");
            loadAccount();
        } else {
            showLogin();
        }
    }

    @Override
    protected void onDestroy() {
        stopLocationRequest();
        io.shutdownNow();
        super.onDestroy();
    }

    private void loadAccount() {
        runIo(() -> {
            api.discoverBaseUrl(api.baseUrl());
            profile = api.request("GET", "/api/auth/me", null);
            preferredLanguage = profile.optString("preferred_language", "en");
            refreshAccountData();
            runOnUiThread(() -> showScreen("dashboard"));
        });
    }

    private void refreshAccountData() throws Exception {
        reports = api.getArray("/api/reports");
        contacts = api.getArray("/api/emergency-contacts");
    }

    private void showLogin() {
        startPage("Sign in to Jeevani AI", "Your health information, reports, and emergency support in one place.");
        EditText server = field("Backend server URL", api.baseUrl(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        add(server);
        add(button("Detect and save backend", () -> detectBackend(server)));
        EditText email = field("Email", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = field("Password", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(email);
        add(password);
        add(button("Login", () -> {
            String backendUrl = server.getText().toString();
            String emailAddress = email.getText().toString().trim();
            String passwordValue = password.getText().toString();
            runIo(() -> {
                api.discoverBaseUrl(backendUrl);
                JSONObject body = new JSONObject();
                body.put("email", emailAddress);
                body.put("password", passwordValue);
                api.request("POST", "/api/auth/login", body);
                profile = api.request("GET", "/api/auth/me", null);
                preferredLanguage = profile.optString("preferred_language", "en");
                refreshAccountData();
                runOnUiThread(() -> showScreen("dashboard"));
            });
        }));
        add(button("Create account", () -> showRegister()));
        add(note("Connect this app to your Jeevani backend. Android emulator default: http://10.0.2.2:8000. For a phone, use a reachable HTTPS server address."));
    }

    private void showRegister() {
        startPage("Create your account", "Your account is secured by the Jeevani AI backend.");
        EditText server = field("Backend server URL", api.baseUrl(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText name = field("Full name", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email = field("Email", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = field("Password (at least 6 characters)", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText phone = field("Phone", "", InputType.TYPE_CLASS_PHONE);
        EditText blood = field("Blood group (optional)", "", InputType.TYPE_CLASS_TEXT);
        add(server);
        add(name);
        add(email);
        add(password);
        add(phone);
        add(blood);
        add(button("Detect backend", () -> detectBackend(server)));
        add(button("Create account", () -> {
            String backendUrl = server.getText().toString();
            String fullName = name.getText().toString().trim();
            String emailAddress = email.getText().toString().trim();
            String passwordValue = password.getText().toString();
            String phoneNumber = phone.getText().toString().trim();
            String bloodGroup = blood.getText().toString().trim();
            runIo(() -> {
                api.discoverBaseUrl(backendUrl);
                JSONObject body = new JSONObject();
                body.put("name", fullName);
                body.put("email", emailAddress);
                body.put("password", passwordValue);
                body.put("phone", phoneNumber);
                body.put("blood_group", bloodGroup);
                body.put("preferred_language", "en");
                api.request("POST", "/api/auth/register", body);
                runOnUiThread(() -> {
                    toast("Account created. Please log in.");
                    showLogin();
                });
            });
        }));
        add(button("Back to login", this::showLogin));
    }

    private void showScreen(String target) {
        screen = target;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(PAGE_BG));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        header.setBackgroundColor(color(DARK_TEAL));
        TextView brand = text("✚  Jeevani AI", 20, "#FFFFFF", true);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button sos = button("SOS", () -> showScreen("sos"));
        header.addView(sos);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setBackgroundColor(color("#FFFFFF"));
        String[][] tabs = {
                {"Home", "dashboard"}, {"Reports", "reports"}, {"Assistant", "chat"},
                {"Hospitals", "hospitals"}, {"Contacts", "contacts"}, {"Profile", "profile"}
        };
        for (String[] tab : tabs) {
            Button item = button(tab[0], () -> showScreen(tab[1]));
            item.setTextSize(11);
            navigation.addView(item, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        root.addView(navigation);

        switch (target) {
            case "reports": showReports(); break;
            case "chat": showChat(); break;
            case "hospitals": showHospitals(); break;
            case "contacts": showContacts(); break;
            case "profile": showProfile(); break;
            case "sos": showSOS(); break;
            default: showDashboard();
        }
    }

    private void showDashboard() {
        add(heading("Good to see you, " + profile.optString("name", "there")));
        add(note("Keep your health information organized. Upload reports, review AI insights, and keep emergency support ready."));
        add(button("Upload a medical report", () -> showScreen("reports")));
        add(button("Find nearby hospitals", () -> showScreen("hospitals")));
        add(button("Emergency SOS", () -> showScreen("sos")));
        add(heading("Your summary"));
        add(note("Medical reports: " + reports.length()));
        add(note("Emergency contacts: " + contacts.length()));
        add(note("Language: " + languageName(preferredLanguage)));
        add(note("AI output is informational only and is not a medical diagnosis."));
    }

    private void showReports() {
        add(heading("Medical reports"));
        add(note("Your reports and AI summaries are private to your account."));
        add(button("Upload PDF or image", this::chooseReport));
        if (reports.length() == 0) {
            add(note("No reports yet. Upload a PDF or image to begin."));
            return;
        }
        for (int i = 0; i < reports.length(); i++) {
            JSONObject report = reports.optJSONObject(i);
            if (report == null) continue;
            final int reportId = report.optInt("id");
            addCard(report.optString("report_name", "Medical report"),
                    report.optString("report_type", "Other") + " · " + report.optString("report_date", "Date not provided")
                            + "\n" + report.optString("priority", "Pending")
                            + "\n" + report.optString("summary", ""));
            add(button("Open report " + reportId, () -> openReport(reportId)));
            add(button("Delete report " + reportId, () -> confirmDeleteReport(reportId)));
        }
    }

    private void chooseReport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/jpeg", "image/png", "image/webp"});
        startActivityForResult(intent, REQUEST_REPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_REPORT || resultCode != Activity.RESULT_OK || data == null) return;
        reportUri = data.getData();
        if (reportUri == null) return;
        promptUploadDetails();
    }

    private void promptUploadDetails() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText date = dialogField("Report date (YYYY-MM-DD)");
        EditText doctor = dialogField("Doctor / hospital (optional)");
        CheckBox consent = new CheckBox(this);
        consent.setText("I consent to send extracted report text to Gemini for analysis.");
        form.addView(date);
        form.addView(doctor);
        form.addView(consent);
        new AlertDialog.Builder(this)
                .setTitle("Upload report")
                .setView(form)
                .setPositiveButton("Upload", (dialog, which) -> {
                    reportDate = date.getText().toString().trim();
                    doctorHospital = doctor.getText().toString().trim();
                    geminiConsent = consent.isChecked();
                    uploadSelectedReport();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void uploadSelectedReport() {
        runIo(() -> {
            String filename = displayName(reportUri);
            String mime = getContentResolver().getType(reportUri);
            if (mime == null) mime = "application/octet-stream";
            try (InputStream input = getContentResolver().openInputStream(reportUri)) {
                if (input == null) throw new IllegalStateException("Could not open the selected file");
                api.upload("/api/reports/upload", "file", filename, mime, input,
                        reportDate, doctorHospital, geminiConsent);
            }
            refreshAccountData();
            runOnUiThread(() -> {
                toast("Report uploaded and analyzed");
                showScreen("reports");
            });
        });
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (Exception ignored) {
            // Fall back to the document URI name.
        }
        String name = uri.getLastPathSegment();
        return name == null ? "report" : name;
    }

    private void openReport(int id) {
        runIo(() -> {
            JSONObject report = api.request("GET", "/api/reports/" + id, null);
            runOnUiThread(() -> showReportDetails(report));
        });
    }

    private void showReportDetails(JSONObject report) {
        StringBuilder details = new StringBuilder();
        details.append("Summary\n").append(report.optString("summary", "No summary available.")).append("\n\n");
        details.append("Findings\n").append(lines(report.optJSONArray("findings"))).append("\n\n");
        details.append("Precautions\n").append(lines(report.optJSONArray("precautions"))).append("\n\n");
        details.append("AI-generated information is not a diagnosis. Review results with a qualified clinician.");
        new AlertDialog.Builder(this).setTitle(report.optString("report_name", "Medical report"))
                .setMessage(details.toString()).setPositiveButton("Close", null).show();
    }

    private static String lines(JSONArray values) {
        if (values == null) return "No information available.";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length(); i++) result.append("• ").append(values.optString(i)).append('\n');
        return result.toString().trim();
    }

    private void confirmDeleteReport(int reportId) {
        new AlertDialog.Builder(this).setTitle("Delete report?")
                .setMessage("This removes the report and its analysis from your account.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> runIo(() -> {
                    api.request("DELETE", "/api/reports/" + reportId, null);
                    refreshAccountData();
                    runOnUiThread(() -> showScreen("reports"));
                })).show();
    }

    private void showContacts() {
        add(heading("Emergency contacts"));
        add(note("These contacts are used when you create an SOS alert."));
        add(button("Add contact", () -> editContact(null)));
        if (contacts.length() == 0) add(note("No emergency contacts added."));
        for (int i = 0; i < contacts.length(); i++) {
            JSONObject contact = contacts.optJSONObject(i);
            if (contact == null) continue;
            int id = contact.optInt("id");
            addCard(contact.optString("name"), contact.optString("relationship", "Emergency contact")
                    + "\n" + contact.optString("phone") + "\n" + contact.optString("email"));
            add(button("Edit " + contact.optString("name"), () -> editContact(contact)));
            add(button("Delete " + contact.optString("name"), () -> deleteContact(id)));
        }
    }

    private void editContact(JSONObject contact) {
        boolean editing = contact != null;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = dialogField("Name");
        EditText relationship = dialogField("Relationship");
        EditText phone = dialogField("Phone");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText email = dialogField("Email");
        email.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (editing) {
            name.setText(contact.optString("name"));
            relationship.setText(contact.optString("relationship"));
            phone.setText(contact.optString("phone"));
            email.setText(contact.optString("email"));
        }
        form.addView(name);
        form.addView(relationship);
        form.addView(phone);
        form.addView(email);
        new AlertDialog.Builder(this).setTitle(editing ? "Edit emergency contact" : "Add emergency contact")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(editing ? "Save changes" : "Add contact", (dialog, which) -> runIo(() -> {
                    JSONObject body = new JSONObject();
                    body.put("name", name.getText().toString().trim());
                    body.put("relationship", relationship.getText().toString().trim());
                    body.put("phone", phone.getText().toString().trim());
                    body.put("email", email.getText().toString().trim());
                    String path = "/api/emergency-contacts" + (editing ? "/" + contact.optInt("id") : "");
                    api.request(editing ? "PUT" : "POST", path, body);
                    refreshAccountData();
                    runOnUiThread(() -> showScreen("contacts"));
                })).show();
    }

    private void deleteContact(int contactId) {
        new AlertDialog.Builder(this).setTitle("Delete contact?")
                .setMessage("This contact will no longer receive SOS message drafts.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> runIo(() -> {
                    api.request("DELETE", "/api/emergency-contacts/" + contactId, null);
                    refreshAccountData();
                    runOnUiThread(() -> showScreen("contacts"));
                })).show();
    }

    private void showProfile() {
        add(heading("Your profile"));
        EditText name = field("Full name", profile.optString("name"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone = field("Phone", profile.optString("phone"), InputType.TYPE_CLASS_PHONE);
        EditText dob = field("Date of birth", profile.optString("dob"), InputType.TYPE_CLASS_DATETIME);
        EditText gender = field("Gender", profile.optString("gender"), InputType.TYPE_CLASS_TEXT);
        EditText blood = field("Blood group", profile.optString("blood_group"), InputType.TYPE_CLASS_TEXT);
        EditText language = field("Language code (en, te, hi, ta, kn, ml)", preferredLanguage, InputType.TYPE_CLASS_TEXT);
        add(name);
        add(phone);
        add(dob);
        add(gender);
        add(blood);
        add(language);
        add(button("Save profile", () -> runIo(() -> {
            JSONObject body = new JSONObject();
            body.put("name", name.getText().toString().trim());
            body.put("phone", phone.getText().toString().trim());
            body.put("dob", dob.getText().toString().trim());
            body.put("gender", gender.getText().toString().trim());
            body.put("blood_group", blood.getText().toString().trim());
            body.put("preferred_language", language.getText().toString().trim());
            api.request("PUT", "/api/user/profile", body);
            profile = api.request("GET", "/api/auth/me", null);
            preferredLanguage = profile.optString("preferred_language", "en");
            runOnUiThread(() -> showScreen("profile"));
        })));
        add(button("Log out", this::logout));
    }

    private void logout() {
        runIo(() -> {
            try {
                api.request("POST", "/api/auth/logout", new JSONObject());
            } finally {
                api.clearSession();
            }
            profile = new JSONObject();
            reports = new JSONArray();
            contacts = new JSONArray();
            chatMessages = new JSONArray();
            runOnUiThread(this::showLogin);
        });
    }

    private void showChat() {
        add(heading("Health Assistant"));
        add(note("Ask about Jeevani or general health education. This assistant cannot diagnose or replace a clinician. Do not use it for emergencies."));
        for (int i = 0; i < chatMessages.length(); i++) {
            JSONObject message = chatMessages.optJSONObject(i);
            if (message != null) addCard(message.optString("role").equals("user") ? "You" : "Jeevani AI",
                    message.optString("content"));
        }
        EditText prompt = field("Your message", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        prompt.setMinLines(2);
        add(prompt);
        add(button("Send", () -> {
            String text = prompt.getText().toString().trim();
            if (text.isEmpty()) {
                toast("Type a message first");
                return;
            }
            try {
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", text);
                chatMessages.put(userMessage);
            } catch (org.json.JSONException exception) {
                showError("Could not prepare that message. Please try again.");
                return;
            }
            runIo(() -> {
                JSONObject body = new JSONObject();
                body.put("messages", chatMessages);
                JSONObject response = api.request("POST", "/api/chat", body);
                chatMessages.put(new JSONObject().put("role", "assistant").put("content", response.optString("reply")));
                runOnUiThread(() -> showScreen("chat"));
            });
        }));
        add(note("Chat history is kept only in this app session and is cleared when you log out."));
    }

    private void showHospitals() {
        add(heading("Nearby hospitals and clinics"));
        add(note("Find OpenStreetMap healthcare listings within 5 km. Listings may be incomplete. Do not use this map for emergency routing."));
        add(button("Use my location and search nearby", this::requestLocationForHospitals));
        add(button("Search near Hyderabad default location", () -> searchHospitals(17.3850, 78.4867)));
    }

    private void requestLocationForHospitals() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        requestDeviceLocation(location -> searchHospitals(location.getLatitude(), location.getLongitude()),
                () -> showError("Could not get your location. Search near the default area instead."));
    }

    private void searchHospitals(double latitude, double longitude) {
        runIo(() -> {
            String query = "[out:json][timeout:20];(nwr[\"amenity\"~\"^(hospital|clinic)$\"](around:5000,"
                    + latitude + "," + longitude + "););out center tags;";
            JSONArray results = null;
            Exception failure = null;
            String[] endpoints = {
                    "https://overpass-api.de/api/interpreter",
                    "https://overpass.kumi.systems/api/interpreter",
                    "https://overpass.private.coffee/api/interpreter"
            };
            for (String endpoint : endpoints) {
                try {
                    results = overpass(endpoint, query);
                    break;
                } catch (Exception exception) {
                    failure = exception;
                }
            }
            if (results == null) throw new IllegalStateException(
                    "All public hospital search services are unavailable. Try again later.", failure);
            JSONArray hospitals = results;
            runOnUiThread(() -> showHospitalResults(hospitals, latitude, longitude));
        });
    }

    private JSONArray overpass(String endpoint, String query) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        byte[] payload = ("data=" + ApiClient.encode(query)).getBytes(StandardCharsets.UTF_8);
        try {
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (status < 200 || status >= 300) throw new IllegalStateException("OpenStreetMap returned " + status);
            try (InputStream response = stream; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = response.read(buffer)) != -1) output.write(buffer, 0, length);
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name())).optJSONArray("elements");
            }
        } finally {
            connection.disconnect();
        }
    }

    private void showHospitalResults(JSONArray elements, double originLat, double originLng) {
        screen = "hospitals";
        showScreen("hospitals");
        add(note("Search center: " + originLat + ", " + originLng));
        if (elements == null || elements.length() == 0) {
            add(note("No hospitals or clinics found within 5 km."));
            return;
        }
        for (int i = 0; i < elements.length(); i++) {
            JSONObject item = elements.optJSONObject(i);
            if (item == null) continue;
            JSONObject tags = item.optJSONObject("tags");
            if (tags == null) tags = new JSONObject();
            JSONObject center = item.optJSONObject("center");
            double lat = item.has("lat") ? item.optDouble("lat", Double.NaN) : center == null ? Double.NaN : center.optDouble("lat", Double.NaN);
            double lng = item.has("lon") ? item.optDouble("lon", Double.NaN) : center == null ? Double.NaN : center.optDouble("lon", Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) continue;
            String name = tags.optString("name", "Unnamed healthcare facility");
            String type = tags.optString("healthcare", tags.optString("amenity", "Healthcare facility"));
            addCard(name, type + "\n" + tags.optString("addr:street", "Address not listed"));
            add(button("Open location in map", () -> openMap(lat, lng)));
            String phone = tags.optString("phone", tags.optString("contact:phone", ""));
            if (!phone.isEmpty()) add(button("Call " + phone, () -> dial(phone)));
        }
        add(note("Map data © OpenStreetMap contributors. Verify availability and call emergency services directly when needed."));
    }

    private void openMap(double latitude, double longitude) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/?mlat=" + latitude + "&mlon=" + longitude
                        + "#map=16/" + latitude + "/" + longitude));
        startActivity(intent);
    }

    private void showSOS() {
        add(heading("Emergency SOS"));
        add(note("Save an SOS alert with your location when available. You choose to open a message draft or phone call for each registered contact. This app does not call emergency services."));
        add(button("Create SOS alert", this::createSOS));
        add(button("Show recent SOS alerts", this::loadSOSHistory));
    }

    private void createSOS() {
        if (contacts.length() == 0) {
            toast("Add at least one emergency contact first");
            showScreen("contacts");
            return;
        }
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        requestDeviceLocation(location -> persistSOS(location.getLatitude(), location.getLongitude()),
                () -> persistSOS(null, null));
    }

    private void persistSOS(Double latitude, Double longitude) {
        lastLatitude = latitude;
        lastLongitude = longitude;
        runIo(() -> {
            JSONObject body = new JSONObject();
            if (latitude != null && longitude != null) {
                body.put("latitude", latitude);
                body.put("longitude", longitude);
            }
            JSONObject result = api.request("POST", "/api/sos", body);
            runOnUiThread(() -> showSOSConfirmation(result));
        });
    }

    private void showSOSConfirmation(JSONObject result) {
        String message = result.optString("message");
        JSONArray recipients = result.optJSONArray("contacts");
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView messageView = text(message, 14, "#7F1D1D", false);
        messageView.setPadding(dp(12), dp(12), dp(12), dp(12));
        dialogContent.addView(messageView);
        dialogContent.addView(text("Choose an action for each contact. SMS opens a draft for you to review and send.", 14, "#334155", false));
        if (recipients != null) {
            for (int i = 0; i < recipients.length(); i++) {
                JSONObject contact = recipients.optJSONObject(i);
                if (contact == null) continue;
                String name = contact.optString("name", "Contact");
                String phone = contact.optString("phone", "");
                dialogContent.addView(text(name + " · " + phone, 16, DARK_TEAL, true));
                LinearLayout row = new LinearLayout(this);
                Button sms = button("Text " + name, () -> composeSms(phone, message));
                Button call = button("Call " + name, () -> dial(phone));
                row.addView(sms, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(call, new LinearLayout.LayoutParams(0, -2, 1));
                dialogContent.addView(row);
            }
        }
        dialogContent.addView(button("Find nearby hospitals", () -> {
            showScreen("hospitals");
            if (lastLatitude != null && lastLongitude != null) searchHospitals(lastLatitude, lastLongitude);
            else requestLocationForHospitals();
        }));
        scroll.addView(dialogContent);
        new AlertDialog.Builder(this).setTitle("SOS saved")
                .setView(scroll).setPositiveButton("Done", null).show();
    }

    private void composeSms(String phone, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)));
        intent.putExtra("sms_body", message);
        startActivity(intent);
    }

    private void dial(String phone) {
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))));
    }

    private void loadSOSHistory() {
        runIo(() -> {
            JSONArray alerts = api.getArray("/api/sos");
            runOnUiThread(() -> {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < alerts.length() && i < 10; i++) {
                    JSONObject alert = alerts.optJSONObject(i);
                    if (alert == null) continue;
                    text.append(formatAlertTime(alert.optString("created_at")))
                            .append("\n").append(alert.optString("message"))
                            .append("\n\n");
                }
                new AlertDialog.Builder(this).setTitle("Recent SOS alerts")
                        .setMessage(text.length() == 0 ? "No SOS alerts recorded." : text.toString())
                        .setPositiveButton("Close", null).show();
            });
        });
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) return;
        boolean granted = false;
        for (int result : grantResults) granted |= result == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            if (screen.equals("sos")) persistSOS(null, null);
            else showError("Location permission denied. Search near the default location instead.");
            return;
        }
        if (screen.equals("sos")) createSOS();
        else requestLocationForHospitals();
    }

    private void requestDeviceLocation(java.util.function.Consumer<Location> success, Runnable failure) {
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            String provider = manager.getBestProvider(criteria, true);
            Location last = provider == null ? null : manager.getLastKnownLocation(provider);
            if (last != null && System.currentTimeMillis() - last.getTime() < 5 * 60 * 1000) {
                success.accept(last);
                return;
            }
            if (provider == null) {
                failure.run();
                return;
            }
            stopLocationRequest();
            activeLocationListener = location -> {
                stopLocationRequest();
                success.accept(location);
            };
            manager.requestLocationUpdates(provider, 0, 0, activeLocationListener, Looper.getMainLooper());
            locationTimeout = () -> {
                stopLocationRequest();
                failure.run();
            };
            handler.postDelayed(locationTimeout, 12000);
        } catch (SecurityException exception) {
            failure.run();
        }
    }

    private void stopLocationRequest() {
        if (locationTimeout != null) handler.removeCallbacks(locationTimeout);
        locationTimeout = null;
        if (activeLocationListener != null) {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            try {
                manager.removeUpdates(activeLocationListener);
            } catch (SecurityException ignored) {
                // The permission may have been revoked while the request was active.
            }
            activeLocationListener = null;
        }
    }

    private void runIo(ThrowingRunnable work) {
        io.execute(() -> {
            try {
                work.run();
            } catch (Exception exception) {
                runOnUiThread(() -> showError(errorMessage(exception)));
            }
        });
    }

    private void detectBackend(EditText server) {
        String enteredUrl = server.getText().toString();
        runIo(() -> {
            String address = api.discoverBaseUrl(enteredUrl);
            runOnUiThread(() -> {
                server.setText(address);
                toast("Backend connected: " + address);
            });
        });
    }

    private String errorMessage(Exception exception) {
        if (exception instanceof ApiClient.BackendUnavailableException) {
            return "Could not connect to the Jeevani backend. Start it with "
                    + "\"uvicorn backend.app:app --host 0.0.0.0 --port 8000\". "
                    + "For an Android emulator, use http://10.0.2.2:8000. "
                    + "For a physical phone, enter a reachable HTTPS backend URL "
                    + "(or a LAN address if your server and network allow it). "
                    + exception.getMessage();
        }
        if (exception instanceof java.net.SocketTimeoutException
                || exception instanceof java.net.ConnectException
                || exception instanceof java.net.UnknownHostException
                || exception instanceof java.io.IOException) {
            return "Could not reach backend at " + api.baseUrl() + ". Check that the backend is running "
                    + "and the address is reachable. Emulator default: http://10.0.2.2:8000. "
                    + "A physical phone needs a reachable HTTPS backend URL. Details: "
                    + exception.getMessage();
        }
        String message = exception.getMessage();
        if (message == null || message.isEmpty()) return "Request failed. Check your connection and server address.";
        if (exception instanceof ApiClient.ApiException
                && ((ApiClient.ApiException) exception).statusCode == 401) {
            api.clearSession();
            runOnUiThread(this::showLogin);
            return "Please log in again.";
        }
        return message;
    }

    private void showError(String message) {
        toast(message);
        if (content != null) add(note(message));
    }

    private void startPage(String title, String subtitle) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(PAGE_BG));
        setContentView(root);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(28), dp(22), dp(30));
        scroll.addView(content);
        root.addView(scroll);
        add(heading(title));
        add(note(subtitle));
    }

    private EditText field(String label, String value, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(inputType != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        input.setHint(label);
        input.setText(value);
        input.setInputType(inputType);
        input.setTextSize(16);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackgroundColor(color("#FFFFFF"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        input.setLayoutParams(params);
        return input;
    }

    private EditText dialogField(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        return input;
    }

    private TextView heading(String value) {
        TextView view = text(value, 24, DARK_TEAL, true);
        view.setPadding(0, dp(6), 0, dp(10));
        return view;
    }

    private TextView note(String value) {
        TextView view = text(value, 15, "#506563", false);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackgroundColor(color("#EAF5F3"));
        addSpacing(view, 8);
        return view;
    }

    private void addCard(String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(color("#FFFFFF"));
        TextView titleView = text(title, 17, DARK_TEAL, true);
        TextView bodyView = text(body, 14, "#506563", false);
        card.addView(titleView);
        card.addView(bodyView);
        addSpacing(card, 8);
        add(card);
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(color("#FFFFFF"));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(TEAL)));
        button.setOnClickListener(view -> action.run());
        addSpacing(button, 7);
        return button;
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private void add(View view) {
        if (content == null) return;
        content.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addSpacing(View view, int bottomDp) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        LinearLayout.LayoutParams params = current instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) current
                : new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(bottomDp);
        view.setLayoutParams(params);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(String value) {
        return android.graphics.Color.parseColor(value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String languageName(String code) {
        switch (code) {
            case "te": return "Telugu";
            case "hi": return "Hindi";
            case "ta": return "Tamil";
            case "kn": return "Kannada";
            case "ml": return "Malayalam";
            default: return "English";
        }
    }

    private static String formatAlertTime(String value) {
        try {
            return DateFormat.getDateTimeInstance().format(
                    Date.from(java.time.Instant.parse(value)));
        } catch (Exception ignored) {
            return value;
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
