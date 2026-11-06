package com.pocketledger.v2;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public release metadata only; no ledger data, credentials or installation ID. */
final class ReleaseChecker {
    static final String REPOSITORY_URL = "https://github.com/Techyeye98/Pocket-Ledger";
    static final String RELEASES_URL = REPOSITORY_URL + "/releases/latest";
    static final String API_URL = "https://api.github.com/repos/Techyeye98/Pocket-Ledger/releases/latest";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Pattern STABLE = Pattern.compile(
            "^[vV]?((?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*))(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    enum Status { UPDATE, CURRENT, NO_RELEASE, FAILED }
    static final class Result {
        final Status status;
        final String version;
        Result(Status status, String version) { this.status = status; this.version = version; }
    }

    static Result check(String installedVersion) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "PocketLedger-Android");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) return readResponse(status, "", installedVersion);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > MAX_RESPONSE_BYTES) return failed();
                    body.write(buffer, 0, count);
                }
                return readResponse(status, new String(body.toByteArray(), StandardCharsets.UTF_8), installedVersion);
            }
        } catch (Exception ignored) {
            return failed();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Result readResponse(int status, String body, String installedVersion) {
        if (status == HttpURLConnection.HTTP_NOT_FOUND) return new Result(Status.NO_RELEASE, "");
        if (status != HttpURLConnection.HTTP_OK) return failed();
        try {
            JSONObject release = new JSONObject(body);
            if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return failed();
            String version = stableVersion(release.optString("tag_name", ""));
            if (version == null || stableVersion(installedVersion) == null) return failed();
            return new Result(isNewer(version, installedVersion) ? Status.UPDATE : Status.CURRENT, version);
        } catch (Exception ignored) {
            return failed();
        }
    }

    static String stableVersion(String value) {
        if (value == null || value.length() > 128) return null;
        Matcher match = STABLE.matcher(value);
        return match.matches() ? match.group(1) : null;
    }

    static boolean isNewer(String candidate, String installed) {
        String latest = stableVersion(candidate), current = stableVersion(installed);
        if (latest == null || current == null) return false;
        String[] left = latest.split("\\."), right = current.split("\\.");
        for (int i = 0; i < 3; i++) {
            int order = new BigInteger(left[i]).compareTo(new BigInteger(right[i]));
            if (order != 0) return order > 0;
        }
        return false;
    }

    private static Result failed() { return new Result(Status.FAILED, ""); }
}
