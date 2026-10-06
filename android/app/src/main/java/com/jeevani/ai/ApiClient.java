package com.jeevani.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ApiClient {
    private static final String PREFS = "jeevani_mobile";
    private static final String COOKIE_KEY = "session_cookie";
    private static final String BASE_URL_KEY = "backend_url";
    private static final int TIMEOUT_MS = 30000;
    private static final int PROBE_TIMEOUT_MS = 4000;

    private final SharedPreferences preferences;

    ApiClient(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String baseUrl() {
        return preferences.getString(BASE_URL_KEY, BuildConfig.API_BASE_URL);
    }

    void setBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        preferences.edit().putString(BASE_URL_KEY, normalized).apply();
    }

    String discoverBaseUrl(String enteredUrl) throws IOException {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, enteredUrl);
        addCandidate(candidates, baseUrl());
        addCandidate(candidates, "http://10.0.2.2:8000");
        addCandidate(candidates, "http://localhost:8000");

        List<String> failures = new ArrayList<>();
        for (String candidate : candidates) {
            try {
                if (probe(candidate)) {
                    setBaseUrl(candidate);
                    return candidate;
                }
                failures.add(candidate + " returned an unsuccessful health-check response");
            } catch (IOException exception) {
                failures.add(candidate + ": " + exception.getMessage());
            }
        }
        throw new BackendUnavailableException(failures);
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (value == null || value.trim().isEmpty()) return;
        candidates.add(value.trim().replaceAll("/+$", ""));
    }

    private boolean probe(String candidate) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(candidate + "/api/health");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(PROBE_TIMEOUT_MS);
            connection.setReadTimeout(PROBE_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    boolean isAuthenticated() {
        return preferences.contains(COOKIE_KEY);
    }

    void clearSession() {
        preferences.edit().remove(COOKIE_KEY).apply();
    }

    JSONObject request(String method, String path, JSONObject body) throws Exception {
        byte[] bytes = body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = open(method, path, "application/json");
        if (bytes != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        return readJson(connection);
    }

    JSONArray getArray(String path) throws Exception {
        return readJsonArray(open("GET", path, "application/json"));
    }

    JSONObject upload(String path, String fieldName, String filename, String mimeType,
                      InputStream content, String reportDate, String doctorHospital,
                      boolean geminiConsent) throws Exception {
        String boundary = "JeevaniBoundary" + System.nanoTime();
        HttpURLConnection connection = open("POST", path, "application/json");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (OutputStream output = connection.getOutputStream()) {
            writeField(output, boundary, "report_date", reportDate);
            writeField(output, boundary, "doctor_hospital", doctorHospital);
            writeField(output, boundary, "gemini_consent", Boolean.toString(geminiConsent));
            writeUtf8(output, "--" + boundary + "\r\n");
            writeUtf8(output, "Content-Disposition: form-data; name=\"" + fieldName
                    + "\"; filename=\"" + safeFilename(filename) + "\"\r\n");
            writeUtf8(output, "Content-Type: " + mimeType + "\r\n\r\n");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = content.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            writeUtf8(output, "\r\n--" + boundary + "--\r\n");
        }
        return readJson(connection);
    }

    private HttpURLConnection open(String method, String path, String accept) throws Exception {
        URL url = new URL(baseUrl() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept", accept);
        String cookie = preferences.getString(COOKIE_KEY, null);
        if (cookie != null) connection.setRequestProperty("Cookie", cookie);
        return connection;
    }

    private JSONObject readJson(HttpURLConnection connection) throws Exception {
        try {
            saveSessionCookie(connection);
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = readText(stream);
            JSONObject payload = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (status < 200 || status >= 300) {
                Object detail = payload.opt("detail");
                String message = detail instanceof JSONArray
                        ? "Invalid input. Check the form and try again."
                        : payload.optString("detail", payload.optString("message", "Request failed (" + status + ")"));
                throw new ApiException(status, message);
            }
            return payload;
        } finally {
            connection.disconnect();
        }
    }

    private JSONArray readJsonArray(HttpURLConnection connection) throws Exception {
        try {
            saveSessionCookie(connection);
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = readText(stream);
            if (status < 200 || status >= 300) {
                JSONObject payload = text.isEmpty() ? new JSONObject() : new JSONObject(text);
                throw new ApiException(status, payload.optString("detail", "Request failed (" + status + ")"));
            }
            return new JSONArray(text);
        } finally {
            connection.disconnect();
        }
    }

    private void saveSessionCookie(HttpURLConnection connection) {
        String setCookie = connection.getHeaderField("Set-Cookie");
        if (setCookie != null && setCookie.startsWith("jeevani_session=")) {
            String cookie = setCookie.split(";", 2)[0];
            preferences.edit().putString(COOKIE_KEY, cookie).apply();
        }
    }

    private static String readText(InputStream input) throws Exception {
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    private static void writeField(OutputStream output, String boundary, String name, String value) throws Exception {
        writeUtf8(output, "--" + boundary + "\r\n");
        writeUtf8(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeUtf8(output, value == null ? "" : value);
        writeUtf8(output, "\r\n");
    }

    private static void writeUtf8(OutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeFilename(String filename) {
        return filename == null ? "report" : filename.replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    static final class ApiException extends Exception {
        final int statusCode;

        ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    static final class BackendUnavailableException extends IOException {
        BackendUnavailableException(List<String> failures) {
            super("Could not reach the Jeevani backend. Tried: " + String.join("; ", failures));
        }
    }
}
