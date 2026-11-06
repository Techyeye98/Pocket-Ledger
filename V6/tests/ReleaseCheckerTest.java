package com.pocketledger.v2;

import org.json.JSONObject;

public final class ReleaseCheckerTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static String release(String tag, boolean draft, boolean prerelease) throws Exception {
        return new JSONObject().put("tag_name", tag).put("draft", draft).put("prerelease", prerelease).toString();
    }
    private static void status(int http, String body, ReleaseChecker.Status expected) {
        check(ReleaseChecker.readResponse(http, body, "1.0.0").status == expected, "Unexpected result for HTTP " + http + ": " + body);
    }
    public static void main(String[] args) throws Exception {
        check(ReleaseChecker.isNewer("v1.10.0", "1.9.9"), "Compare numbers, not text");
        check(ReleaseChecker.isNewer("2.0.0", "1.99.99"), "Major update");
        check(ReleaseChecker.isNewer("1.0.1", "1.0.0"), "Patch update");
        check(!ReleaseChecker.isNewer("1.0.0", "1.0.0"), "Equal version");
        check(!ReleaseChecker.isNewer("0.99.0", "1.0.0"), "No downgrade");
        check(!ReleaseChecker.isNewer("2.0.0-beta.1", "1.0.0"), "Reject prerelease tag");
        check(!ReleaseChecker.isNewer("2.0.0", "Unknown"), "Unknown installed version is not an update");
        check(!ReleaseChecker.isNewer("1.0.0+build.7", "v1.0.0"), "Build metadata does not change precedence");
        check(ReleaseChecker.isNewer("999999999999999999999.0.0", "1.0.0"), "Numeric overflow must not crash");
        for (String tag : new String[]{"latest", "1.1", "01.1.0", "1.2.3.4", "1.2.3\n", "1.2.3<script>", ""}) {
            check(ReleaseChecker.stableVersion(tag) == null, "Reject malformed tag: " + tag);
        }
        check("1.2.3".equals(ReleaseChecker.stableVersion("V1.2.3+android.4")), "Normalize display version");
        check(ReleaseChecker.stableVersion(null) == null, "Reject null version");
        check(ReleaseChecker.stableVersion("1.2.3+" + new String(new char[130]).replace('\0', 'a')) == null, "Bound untrusted version length");
        status(200, release("v1.1.0", false, false), ReleaseChecker.Status.UPDATE);
        status(200, release("v1.0.0", false, false), ReleaseChecker.Status.CURRENT);
        status(200, release("v0.9.0", false, false), ReleaseChecker.Status.CURRENT);
        status(200, release("v2.0.0", true, false), ReleaseChecker.Status.FAILED);
        status(200, release("v2.0.0", false, true), ReleaseChecker.Status.FAILED);
        status(200, release("v2.0.0-rc.1", false, false), ReleaseChecker.Status.FAILED);
        status(200, "{}", ReleaseChecker.Status.FAILED);
        status(200, "not JSON", ReleaseChecker.Status.FAILED);
        status(200, "[]", ReleaseChecker.Status.FAILED);
        status(404, "", ReleaseChecker.Status.NO_RELEASE);
        for (int code : new int[]{301, 401, 403, 429, 500, 503}) status(code, "", ReleaseChecker.Status.FAILED);
        check(ReleaseChecker.readResponse(200, release("v2.0.0", false, false), "Unknown").status == ReleaseChecker.Status.FAILED, "Unknown local version must not claim current");
        check(ReleaseChecker.RELEASES_URL.equals("https://github.com/Techyeye98/Pocket-Ledger/releases/latest"), "Use the fixed release destination");
        System.out.println("PASS: " + checks + " release checks: numeric versions, stable-only filtering, malformed JSON/tags, HTTP failures and fixed release link.");
    }
}
