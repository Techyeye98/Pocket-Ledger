package com.pocketledger.v2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * V2 sync client for Google Apps Script communication.
 *
 * Operations:
 *   - test(address)       → connection test (GET)
 *   - push(address, tx)   → CREATE / UPDATE / DELETE (POST)
 *   - restore(address)    → FETCH all transactions from Sheet (POST/GET)
 *
 * V2 Architecture:
 *   Local Room DB is the PRIMARY source of truth.
 *   Google Sheets is a secondary mirror.
 *   Restore imports transactions into local DB without wiping existing data.
 */
final class SyncClient {

    /**
     * Connection test — simple HTTPS GET to the configured Web App URL.
     *
     * Success requires BOTH: the final HTTP status is 2xx (redirects are followed)
     * AND the trimmed body is "ok". Never echoes the raw body into the visible UI:
     * user-facing messages stay short, while the technical detail goes to the
     * `detail` channel used by the app's Diagnostics screen.
     */
    static Result test(String address) {
        try {
            String resp = executeWithRedirects(address, "GET", null).trim();
            if (!"ok".equalsIgnoreCase(resp)) {
                return Result.fail("Connection reached the endpoint, but it did not return the expected response.",
                        "Endpoint response: " + truncate(resp));
            }
            return Result.ok("Connected");
        } catch (java.net.SocketTimeoutException e) {
            return Result.fail("Connection test timed out.");
        } catch (IllegalArgumentException e) {
            return Result.fail("The URL is not a valid HTTPS Apps Script URL.");
        } catch (Exception e) {
            return Result.fail("Could not reach the endpoint." + httpStatus(e),
                    e.getMessage() == null || e.getMessage().trim().isEmpty() ? "Network request failed." : e.getMessage());
        }
    }

    /**
     * If the failure is an HTTP status failure ("HTTP nnn: …"), return just the status
     * so the UI can show e.g. "Could not reach the endpoint. HTTP 500" without the body.
     */
    private static String httpStatus(Exception e) {
        String m = e.getMessage();
        if (m != null) {
            String t = m.trim();
            if (t.startsWith("HTTP ")) {
                int colon = t.indexOf(':');
                if (colon > 0) return " " + t.substring(0, colon);
            }
        }
        return "";
    }

    /**
     * Push every pending transaction in ONE HTTP POST (action "BATCH").
     *
     * Cold-start defier (DECISION #4): each Apps Script web-app invocation pays
     * ~1.5s cold-start, so 26 serial pushes can take 40s+ and run into sheet-lock
     * contention. One request with all items completes in roughly one round-trip.
     *
     * The server returns one result object per received item:
     *   { "status":"success", "results":[
     *       { "status":"success", "transactionId":"<id>", "action":"CREATE" },
     *       { "status":"error",   "message":"..." } ] }
     *
     * A transaction is confirmed — and may be marked SYNCED — ONLY when its own
     * result is success AND echoes the exact transactionId AND the requested
     * action, the same strict contract as the single-op push(). Request-level
     * failures (transport, malformed JSON, server-reported error) confirm nothing.
     * Server results for ids we never sent are ignored (can't confirm a false match).
     */
    static BatchResult pushBatch(String address, List<PendingOp> ops, ConfigStore store) {
        if (ops == null || ops.isEmpty()) {
            return new BatchResult(Collections.emptyList(), "");
        }
        try {
            JSONObject batch = new JSONObject();
            batch.put("action", "BATCH");
            JSONArray items = new JSONArray();
            for (PendingOp op : ops) {
                String month = monthFromDate(op.tx.date); // "September" not "September-2026"
                items.put(buildJsonObject(op.action, op.tx, month, store));
            }
            batch.put("items", items);
            byte[] body = batch.toString().getBytes(StandardCharsets.UTF_8);

            String text = executeWithRedirects(address, "POST", body).trim();
            if (text.isEmpty()) {
                return new BatchResult(Collections.emptyList(), "Server response was empty; batch not confirmed.");
            }

            JSONObject resp = new JSONObject(text);
            if (!"success".equalsIgnoreCase(resp.optString("status", ""))) {
                String message = resp.optString("message", resp.optString("error", "Server reported failure"));
                return new BatchResult(Collections.emptyList(), "Server response: " + message);
            }

            JSONArray results = resp.optJSONArray("results");
            if (results == null) {
                return new BatchResult(Collections.emptyList(), "Batch response was missing per-item results.");
            }

            List<String> confirmed = new ArrayList<>();
            String firstErr = "";
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                String id = item.optString("transactionId", "").trim();
                PendingOp op = findOp(ops, id);
                if (op == null) continue; // server echoed an id we never sent — ignore

                String echoedAction = item.optString("action", "").trim();
                boolean ok = "success".equalsIgnoreCase(item.optString("status", ""))
                        && !id.isEmpty()
                        && op.action.equalsIgnoreCase(echoedAction);
                if (ok) {
                    confirmed.add(id);
                } else if (firstErr.isEmpty()) {
                    firstErr = item.optString("message", "Item " + op.action + " failed for " + id);
                }
            }

            String lastError = firstErr;
            int unconfirmed = ops.size() - confirmed.size();
            if (unconfirmed > 0 && lastError.isEmpty()) {
                lastError = "One or more transactions were not confirmed by the server.";
            }
            return new BatchResult(confirmed, lastError);
        } catch (java.net.SocketTimeoutException e) {
            return new BatchResult(Collections.emptyList(), "Request timed out.");
        } catch (org.json.JSONException e) {
            return new BatchResult(Collections.emptyList(), "Malformed JSON response from server: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return new BatchResult(Collections.emptyList(), e.getMessage());
        } catch (Exception e) {
            return new BatchResult(Collections.emptyList(), "Network error: " + e.getMessage());
        }
    }

    /** Locate a pending operation by its transactionId; null when nothing matches. */
    private static PendingOp findOp(List<PendingOp> ops, String id) {
        for (PendingOp op : ops) {
            if (op.tx.transactionId.equals(id)) return op;
        }
        return null;
    }

    private static String truncate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    /**
     * Fetch/restore all transactions from the configured Google Sheet.
     * Supports both V2 full schema and legacy V1 rows.
     */
    static RestoreResult restore(String address) {
        try {
            JSONObject req = new JSONObject();
            req.put("action", "FETCH");
            byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);

            String response = executeWithRedirects(address, "POST", body).trim();
            if (response.isEmpty()) {
                return RestoreResult.fail("Empty response received from server.");
            }

            JSONObject json = new JSONObject(response);
            String status = json.optString("status", "");
            if (!"success".equalsIgnoreCase(status)) {
                String error = json.optString("error", json.optString("message", "Server reported failure"));
                return RestoreResult.fail(error);
            }

            JSONArray arr = json.optJSONArray("transactions");
            if (arr == null || arr.length() == 0) {
                return RestoreResult.ok(Collections.emptyList());
            }

            List<TransactionEntity> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("transactionId", "").trim();
                if (id.isEmpty()) continue; // Ignore entries without ID

                TransactionEntity tx = new TransactionEntity();
                tx.transactionId = id;
                tx.amount        = o.optDouble("amount", 0.0);
                tx.category      = o.optString("category", "").trim();
                tx.paymentMode   = o.optString("paymentMode", "").trim();
                tx.remarks       = o.optString("remarks", "").trim();
                tx.date          = o.optString("date", "").trim();
                tx.time          = o.optString("time", "").trim();
                tx.deviceName    = o.optString("deviceName", "").trim();
                tx.createdAt     = o.optLong("createdAt", System.currentTimeMillis());
                tx.updatedAt     = o.optLong("updatedAt", tx.createdAt);
                tx.syncStatus    = TransactionEntity.SYNC_SYNCED;
                tx.deleted       = o.optBoolean("deleted", false);

                list.add(tx);
            }

            return RestoreResult.ok(list);
        } catch (java.net.SocketTimeoutException e) {
            return RestoreResult.fail("Connection timed out while fetching sheet data.");
        } catch (org.json.JSONException e) {
            return RestoreResult.fail("Malformed JSON response from Apps Script: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return RestoreResult.fail(e.getMessage());
        } catch (Exception e) {
            return RestoreResult.fail("Restore error: " + e.getMessage());
        }
    }

    // ── HTTPS / Redirect handling ─────────────────────────────────────────────

    /**
     * Executes an HTTP request following Google Apps Script 302 redirects to Google User Content URLs.
     */
    private static String executeWithRedirects(String address, String method, byte[] body) throws Exception {
        String currentUrl = address;
        for (int redirectCount = 0; redirectCount < 5; redirectCount++) {
            URL url = new URL(currentUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                throw new IllegalArgumentException("Use an HTTPS Apps Script URL.");
            }

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(45000);
            conn.setInstanceFollowRedirects(false); // Handle redirect manually for POST -> GET 302

            if ("POST".equalsIgnoreCase(method) && body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            }

            int status = conn.getResponseCode();

            // Google Apps Script redirects web app requests with HTTP 302 to script.googleusercontent.com
            if (status == 301 || status == 302 || status == 303 || status == 307) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && !location.isEmpty()) {
                    currentUrl = location;
                    method = "GET"; // Redirect target is fetched via GET
                    body = null;
                    continue;
                }
            }

            InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String response = stream == null ? "" : read(stream);
            conn.disconnect();

            if (status >= 200 && status < 300) {
                return response;
            } else {
                throw new Exception("HTTP " + status + ": " + response);
            }
        }
        throw new Exception("Too many redirects");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JSONObject buildJsonObject(String action, TransactionEntity tx, String month, ConfigStore store) throws org.json.JSONException {
        JSONObject obj = new JSONObject();
        obj.put("action", action);
        obj.put("transactionId", tx.transactionId);
        obj.put("amount", tx.amount);
        obj.put("category", store.backendValue(tx.category));   // includes emoji suffix
        obj.put("paymentMode", store.backendValue(tx.paymentMode));
        obj.put("remarks", tx.remarks);
        obj.put("date", tx.date);
        obj.put("month", month);
        obj.put("time", tx.time);
        obj.put("deviceName", tx.deviceName);
        obj.put("createdAt", tx.createdAt);
        obj.put("updatedAt", tx.updatedAt);
        return obj;
    }

    private static String buildJson(String action, TransactionEntity tx, String month, ConfigStore store) throws org.json.JSONException {
        return buildJsonObject(action, tx, month, store).toString();
    }

    /**
     * Extract month name from date "dd-MM-yyyy" → "September"
     * Month is stored as full name only (not "September-2026").
     */
    private static String monthFromDate(String date) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            Date d = parser.parse(date);
            return new SimpleDateFormat("MMMM", Locale.US).format(d);
        } catch (Exception e) { return ""; }
    }

    private static String read(InputStream stream) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            return b.toString();
        }
    }

    // ── Result Classes ────────────────────────────────────────────────────────

    static final class Result {
        final boolean success;
        final String  message;
        /** Technical detail intended for the app's Diagnostics screen, not the visible dialog. */
        final String  detail;
        private Result(boolean s, String m, String d) { success = s; message = m; detail = d == null ? "" : d; }
        static Result ok(String m)   { return new Result(true, m, ""); }
        static Result fail(String m) { return new Result(false, m, m); }
        static Result fail(String m, String d) { return new Result(false, m, d); }
    }

    static final class RestoreResult {
        final boolean success;
        final String message;
        final List<TransactionEntity> transactions;

        private RestoreResult(boolean s, String m, List<TransactionEntity> t) {
            this.success = s;
            this.message = m;
            this.transactions = t != null ? t : Collections.emptyList();
        }

        static RestoreResult ok(List<TransactionEntity> list) {
            return new RestoreResult(true, "Success", list);
        }

        static RestoreResult fail(String m) {
            return new RestoreResult(false, m, Collections.emptyList());
        }
    }

    /** One transaction plus the action to send it with, queued for a batch push. */
    static final class PendingOp {
        final TransactionEntity tx;
        final String action;

        PendingOp(TransactionEntity tx, String action) {
            this.tx = tx;
            this.action = action;
        }
    }

    /**
     * Outcome of a batch push.
     *   confirmedIds: transactionIds the server individually confirmed (strict echo match).
     *   lastError:    first item failure message, or the request-level error (empty if all synced).
     */
    static final class BatchResult {
        final List<String> confirmedIds;
        final String lastError;

        BatchResult(List<String> confirmedIds, String lastError) {
            this.confirmedIds = confirmedIds != null ? confirmedIds : Collections.emptyList();
            this.lastError    = lastError == null ? "" : lastError;
        }
    }
}
