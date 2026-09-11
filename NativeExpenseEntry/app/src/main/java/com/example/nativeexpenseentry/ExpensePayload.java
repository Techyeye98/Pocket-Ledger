package com.example.nativeexpenseentry;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ExpensePayload {
    final String date, month, time, category, amount, paymentMode, remarks, deviceName;
    ExpensePayload(String category, String amount, String paymentMode, String remarks, String deviceName) {
        Date now = new Date();
        this.date = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(now);
        this.month = new SimpleDateFormat("MMMM-yyyy", Locale.US).format(now);
        this.time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(now);
        this.category = category; this.amount = amount; this.paymentMode = paymentMode; this.remarks = remarks; this.deviceName = deviceName;
    }
    String json() throws Exception { return new JSONObject().put("date",date).put("month",month).put("time",time).put("category",category).put("amount",amount).put("paymentMode",paymentMode).put("remarks",remarks).put("deviceName",deviceName).toString(); }
}
