package com.example.nativeexpenseentry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class ApiClient {
    static Result submit(String address, ExpensePayload payload) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(address);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return Result.fail("Use an HTTPS Apps Script URL.");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST"); connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = payload.json().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String text = stream == null ? "" : read(stream).trim();
            if (status >= 200 && status < 300 && "success".equalsIgnoreCase(text)) return Result.ok();
            return Result.fail("The server did not confirm the save" + (status > 0 ? " (HTTP " + status + ")." : "."));
        } catch (java.net.SocketTimeoutException e) { return Result.fail("The request timed out. Check your connection and try again."); }
        catch (Exception e) { return Result.fail("Could not reach the Apps Script endpoint. Check the URL and connection."); }
        finally { if (connection != null) connection.disconnect(); }
    }
    static Result test(String address) {
        try {
            URL url = new URL(address);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return Result.fail("Use an HTTPS Apps Script URL.");
            HttpURLConnection c = (HttpURLConnection) url.openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(true);
            int status = c.getResponseCode();
            return status >= 200 && status < 300 ? Result.ok("Endpoint reachable (HTTP " + status + ").") : Result.fail("Endpoint returned HTTP " + status + ".");
        } catch (java.net.SocketTimeoutException e) { return Result.fail("Connection test timed out."); }
        catch (Exception e) { return Result.fail("Could not reach the endpoint. Check the HTTPS URL and connection."); }
    }
    private static String read(InputStream stream) throws Exception { try (BufferedReader r = new BufferedReader(new InputStreamReader(stream))) { StringBuilder b = new StringBuilder(); String line; while ((line=r.readLine()) != null) b.append(line); return b.toString(); } }
    static final class Result { final boolean success; final String message; private Result(boolean s,String m){success=s;message=m;} static Result ok(){return new Result(true,"Success");} static Result ok(String m){return new Result(true,m);} static Result fail(String m){return new Result(false,m);} }
}
