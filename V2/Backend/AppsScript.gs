/**
 * Pocket Ledger V2 — Google Apps Script Backend
 *
 * V2 Contract:
 *   - POST requests with JSON body containing "action" field
 *   - Actions: CREATE, UPDATE, DELETE, FETCH / RESTORE, BATCH
 *   - Every single-op CREATE/UPDATE/DELETE returns structured JSON:
 *       success: { "status":"success", "transactionId":"<id>", "action":"<CREATE|UPDATE|DELETE>" }
 *       error:   { "status":"error", "message":"..." }
 *   - BATCH (DECISION #4 — one HTTP POST for the whole pending queue, defeats
 *     Apps Script cold-start latency) takes an "items" array and returns one
 *     structured result per item so a failure never blocks the rest:
 *       req:    { "action":"BATCH", "items":[ { "action":"CREATE", ... }, ... ] }
 *       resp:   { "status":"success", "results":[ { "status":"success",
 *                   "transactionId":"<id>", "action":"CREATE" } | { "status":"error", "message":"..." } ] }
 *   - Android confirms success ONLY when status, transactionId, and action all match.
 *   - UPDATE is a LENIENT UPSERT (DECISION #3): if the row can't be found by
 *     transactionId or by content, it is appended as a new row instead of erroring.
 *   - transactionId is the stable identity (never row number); CREATE rejects a blank transactionId.
 *   - createdAt is supplied by Android and preserved; Date.now() is only a fallback.
 *   - Month = "September" (not "September-2026")
 *   - Emojis in category/paymentMode are preserved as-is
 *   - GET request = connection test → returns "ok", or FETCH if ?action=FETCH
 *
 * Column order in V2 Sheet:
 *   A: Date | B: Month | C: Time | D: Category | E: Amount
 *   F: Payment Mode | G: Remarks | H: Device Name | I: Transaction Id
 *   J: Created At (epoch ms) | K: Updated At (epoch ms) | L: Deleted
 *
 * IMPORTANT:
 *   V2 Android app never uses row number as identity.
 *   Always find rows by matching column I (Transaction Id).
 */

var SHEET_NAME = "Transactions";

function doGet(e) {
  if (e && e.parameter && (e.parameter.action === "FETCH" || e.parameter.action === "RESTORE")) {
    return handleFetch();
  }
  return ContentService.createTextOutput("ok");
}

function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);
    var action  = payload.action;

    if (action === "CREATE")           return handleCreate(payload);
    if (action === "UPDATE")           return handleUpdate(payload);
    if (action === "DELETE")           return handleDelete(payload);
    if (action === "BATCH")            return handleBatch(payload);
    if (action === "FETCH" || action === "RESTORE") return handleFetch();

    return errorResult("unknown action: " + action);
  } catch (err) {
    var msg = (err && err.message) ? err.message : String(err);
    return errorResult(msg);
  }
}

// ── CREATE │ UPDATE │ DELETE ─────────────────────────────────────────────────
//
// Every operation exists in two forms:
//   - handleXxx(p)      → getSheet() + runRowOp() + formatting, returns the JSON
//                         response for the single-op endpoint.
//   - runRowOp(sheet, p)→ per-item worker shared by single ops and BATCH.
//                         Never throws; every outcome is a plain result object
//                         so a batch can render one result per item.
//
// The workers return plain objects (successObject/errorObject); the single
// handlers wrap them with jsonOutput for the wire.

// ── single-op endpoints ───────────────────────────────────────────────────────

function handleCreate(p) {
  var sheet = getSheet();
  var r = createRow(sheet, p, "CREATE");
  applySheetFormatting(sheet);
  return jsonOutput(r);
}

function handleUpdate(p) {
  var sheet = getSheet();
  var r = upsertRow(sheet, p);
  applySheetFormatting(sheet);
  return jsonOutput(r);
}

function handleDelete(p) {
  var sheet = getSheet();
  var r = deleteRow(sheet, p);
  applySheetFormatting(sheet);
  return jsonOutput(r);
}

// ── BATCH (DECISION #4) ──────────────────────────────────────────────────────

function handleBatch(p) {
  var items = p.items;
  if (!items || items.length === 0) {
    return errorResult("BATCH requires a non-empty 'items' array");
  }
  var sheet = getSheet();
  var results = [];
  for (var i = 0; i < items.length; i++) {
    results.push(runRowOp(sheet, items[i]));
  }
  applySheetFormatting(sheet);
  return jsonOutput({ status: "success", results: results });
}

/**
 * Dispatch ONE item (CREATE/UPDATE/DELETE) against an open sheet and return a
 * plain result object — never thrown, so one bad item can't kill the batch:
 *   { status:"success", transactionId:"<id>", action:"<req action>" }
 *   { status:"error", message:"..." }
 */
function runRowOp(sheet, p) {
  var action = String((p && p.action) || "");
  try {
    if (action === "CREATE") return createRow(sheet, p, "CREATE");
    if (action === "UPDATE") return upsertRow(sheet, p);
    if (action === "DELETE") return deleteRow(sheet, p);
    return errorObject("unknown action: " + action);
  } catch (e) {
    return errorObject((e && e.message) ? e.message : String(e));
  }
}

/**
 * CREATE — never append a row without a stable transactionId. If the id already
 * exists, or the identical content exists under a different/missing id, update
 * in place instead of appending a duplicate.
 */
function createRow(sheet, p, action) {
  var transactionId = String(p.transactionId || "").trim(); // column I (9)
  if (!transactionId) return errorObject("transactionId is required");

  var existing = findRowByTransactionId(sheet, transactionId);
  if (existing > 0) {
    return updateRow(sheet, existing, transactionId, p, action);
  }

  // Prevent duplicates: same content with a different/missing id → backfill id
  // into column I and update instead of appending a new row.
  var contentMatch = findRowByContent(sheet, p);
  if (contentMatch > 1) {
    sheet.getRange(contentMatch, 9).setValue(transactionId);
    return updateRow(sheet, contentMatch, transactionId, p, action);
  }

  appendNewRow(sheet, p, transactionId);
  return successObject(action, transactionId);
}

/**
 * UPDATE — lenient upsert (DECISION #3).
 * 1) Match by transactionId; 2) else match by content (backfills the id into
 * column I); 3) else the row is genuinely absent → APPEND as a new row rather
 * than failing, so a transaction can never get stuck as "transactionId not found".
 * Always echoes the client-requested action ("UPDATE"), not the internal
 * operation, so the client's strict action echo check still passes for a row
 * that had to be appended.
 */
function upsertRow(sheet, p) {
  var transactionId = String(p.transactionId || "").trim();
  if (!transactionId) return errorObject("transactionId is required");

  var row = findRowByTransactionId(sheet, transactionId);
  if (row < 2) {
    row = findRowByContent(sheet, p);
    if (row > 1) {
      sheet.getRange(row, 9).setValue(transactionId); // backfill id into column I
    } else {
      appendNewRow(sheet, p, transactionId);
      return successObject("UPDATE", transactionId);
    }
  }
  return updateRow(sheet, row, transactionId, p, "UPDATE");
}

/**
 * DELETE — tombstone (col L = true), never hard-deletes the row (safe audit
 * trail). If the row is already absent, confirm success anyway so a local
 * soft-deleted transaction can clear its pending-sync marker instead of
 * deadlocking forever.
 */
function deleteRow(sheet, p) {
  var transactionId = String(p.transactionId || "").trim();
  var row = findRowByTransactionId(sheet, transactionId);
  if (row < 2) {
    return successObject("DELETE", transactionId);
  }
  sheet.getRange(row, 12).setValue(true);                       // L Deleted
  if (p.updatedAt) sheet.getRange(row, 11).setValue(p.updatedAt); // K Updated At
  return successObject("DELETE", transactionId);
}

/** Overwrite the non-identity columns of an existing row. Column I is identity and is NEVER touched. */
function updateRow(sheet, row, transactionId, p, action) {
  if (p.date        !== undefined) sheet.getRange(row, 1).setValue(p.date);                    // A Date
  if (p.month       !== undefined) sheet.getRange(row, 2).setValue(formatMonth(p.month));     // B Month
  if (p.time        !== undefined) sheet.getRange(row, 3).setValue(p.time);                    // C Time
  if (p.category    !== undefined) sheet.getRange(row, 4).setValue(p.category);                // D Category
  if (p.amount      !== undefined) sheet.getRange(row, 5).setValue(p.amount);                  // E Amount
  if (p.paymentMode !== undefined) sheet.getRange(row, 6).setValue(p.paymentMode);             // F Payment Mode
  if (p.remarks     !== undefined) sheet.getRange(row, 7).setValue(p.remarks);                 // G Remarks
  if (p.deviceName  !== undefined) sheet.getRange(row, 8).setValue(p.deviceName);              // H Device Name
  // I Transaction Id (9) is the identity column — intentionally never overwritten here.
  if (p.updatedAt   !== undefined) sheet.getRange(row, 11).setValue(p.updatedAt);              // K Updated At
  return successObject(action, transactionId);
}

/** Append a brand-new data row with the client-supplied transactionId. */
function appendNewRow(sheet, p, transactionId) {
  var createdAt = validEpoch(p.createdAt, Date.now());
  var updatedAt = validEpoch(p.updatedAt, createdAt);
  sheet.appendRow([
    p.date,               // A Date
    formatMonth(p.month), // B Month — "September" not "September-2026"
    p.time,               // C Time
    p.category,           // D Category — "Food & Dining 🍔" emojis preserved
    p.amount,             // E Amount
    p.paymentMode,        // F Payment Mode — "UPI 📱"
    p.remarks || "",      // G Remarks
    p.deviceName || "",   // H Device Name
    transactionId,        // I Transaction Id (identity)
    createdAt,            // J Created At — supplied by Android; Date.now() only as fallback
    updatedAt,            // K Updated At
    false                 // L Deleted
  ]);
}

// ── FETCH / RESTORE ───────────────────────────────────────────────────────────

function handleFetch() {
  var sheet = getSheet();
  var data = sheet.getDataRange().getValues();
  if (!data || data.length <= 1) {
    return jsonOutput({ status: "success", transactions: [] });
  }

  var header = data[0];
  // New V2 layout: "Transaction Id" lives in column 9 (index 8).
  var isV2 = header.length >= 9 && String(header[8]).trim().toLowerCase() === "transaction id";

  var list = [];
  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    // Skip completely empty rows
    var isEmpty = true;
    for (var j = 0; j < row.length; j++) {
      if (row[j] !== "") { isEmpty = false; break; }
    }
    if (isEmpty) continue;

    if (isV2) {
      var txId = String(row[8] || "").trim(); // I Transaction Id
      if (!txId) {
        // Fallback for empty ID in V2 sheet: deterministic UUID
        txId = generateLegacyId(formatDateCell(row[0]), formatTimeCell(row[2]), row[4], row[6], i);
      }
      var tx = {
        transactionId: txId,
        date: formatDateCell(row[0]),              // A Date
        month: formatMonth(row[1]),                // B Month
        time: formatTimeCell(row[2]),              // C Time
        category: String(row[3] || "").trim(),     // D Category
        amount: Number(row[4]) || 0,               // E Amount
        paymentMode: String(row[5] || "").trim(),  // F Payment Mode
        remarks: String(row[6] || "").trim(),      // G Remarks
        deviceName: String(row[7] || "").trim(),   // H Device Name
        createdAt: Number(row[9]) || Date.now(),   // J Created At (epoch ms)
        updatedAt: Number(row[10]) || (Number(row[9]) || Date.now()), // K Updated At
        deleted: row[11] === true || String(row[11]).toLowerCase() === "true" // L Deleted
      };
      list.push(tx);
    } else {
      // Legacy V1 Sheet:
      // Col 0: date | Col 1: month | Col 2: time | Col 3: category
      // Col 4: amount | Col 5: paymentMode | Col 6: remarks | Col 7: deviceName
      var dStr = formatDateCell(row[0]);
      var tStr = formatTimeCell(row[2]);
      var amt  = Number(row[4]) || 0;
      var rem  = String(row[6] || "").trim();
      var legId = generateLegacyId(dStr, tStr, amt, rem, i);

      var legacyTx = {
        transactionId: legId,
        date: dStr,
        month: formatMonth(row[1]),
        time: tStr,
        amount: amt,
        category: String(row[3] || "").trim(),
        paymentMode: String(row[5] || "").trim(),
        remarks: rem,
        deviceName: String(row[7] || "").trim(),
        createdAt: parseEpoch(dStr, tStr),
        updatedAt: parseEpoch(dStr, tStr),
        deleted: false
      };
      list.push(legacyTx);
    }
  }

  return jsonOutput({ status: "success", transactions: list });
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function getSheet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow([
      "Date","Month","Time","Category","Amount",
      "Payment Mode","Remarks","Device Name",
      "Transaction Id","Created At","Updated At","Deleted"
    ]);
  }
  return sheet;
}

function findRowByTransactionId(sheet, transactionId) {
  var data = sheet.getDataRange().getValues();
  for (var i = 1; i < data.length; i++) {
    // Transaction Id lives in column I (index 8). Row number is never identity.
    if (String(data[i][8]).trim() === String(transactionId).trim()) return i + 1;
  }
  return -1;
}

/**
 * Fallback matcher used when a transactionId isn't found in the sheet yet
 * (e.g. data pasted from another source, or synced via an older script that
 * didn't store IDs). Matches on date, time (HH:mm), amount, category and
 * payment mode — the same logical transaction entered twice with identical
 * fields is extremely rare, so this is safe for expense tracking.
 *
 * Returns the 1-based row number, or -1 when no match is found.
 */
function findRowByContent(sheet, p) {
  var data = sheet.getDataRange().getValues();
  var date       = String(p.date       || "").trim();
  var time       = String(p.time       || "").trim().substring(0, 5); // HH:mm
  var amount     = Number(p.amount) || 0;
  var category   = String(p.category   || "").trim();
  var paymentMode = String(p.paymentMode || "").trim();

  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    var rDate = formatDateCell(row[0]);
    var rTime = formatTimeCell(row[2]).substring(0, 5); // HH:mm
    var rAmt  = Number(row[4]) || 0;
    var rCat  = String(row[3] || "").trim();
    var rPay  = String(row[5] || "").trim();

    if (rDate === date && rTime === time && rAmt === amount &&
        rCat === category && rPay === paymentMode) {
      return i + 1;
    }
  }
  return -1;
}

function formatDateCell(val) {
  if (!val) return "";
  if (val instanceof Date) {
    var d = ("0" + val.getDate()).slice(-2);
    var m = ("0" + (val.getMonth() + 1)).slice(-2);
    var y = val.getFullYear();
    return d + "-" + m + "-" + y;
  }
  return String(val).trim();
}

function formatTimeCell(val) {
  if (!val) return "";
  if (val instanceof Date) {
    var h = ("0" + val.getHours()).slice(-2);
    var m = ("0" + val.getMinutes()).slice(-2);
    var s = ("0" + val.getSeconds()).slice(-2);
    return h + ":" + m + ":" + s;
  }
  return String(val).trim();
}

function formatMonth(val) {
  var s = String(val || "").trim();
  if (s.indexOf("-") > 0) {
    return s.split("-")[0].trim();
  }
  return s;
}

function generateLegacyId(date, time, amount, remarks, rowIdx) {
  var raw = "legacy_" + date + "_" + time + "_" + amount + "_" + remarks + "_" + rowIdx;
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.MD5, raw);
  var hex = "";
  for (var i = 0; i < bytes.length; i++) {
    var b = (bytes[i] < 0 ? bytes[i] + 256 : bytes[i]).toString(16);
    hex += (b.length === 1 ? "0" + b : b);
  }
  return hex.substring(0,8) + "-" + hex.substring(8,12) + "-4" + hex.substring(13,16) + "-a" + hex.substring(17,20) + "-" + hex.substring(20,32);
}

function parseEpoch(dateStr, timeStr) {
  try {
    if (!dateStr) return Date.now();
    var parts = dateStr.split("-");
    if (parts.length === 3) {
      var d = parseInt(parts[0], 10);
      var m = parseInt(parts[1], 10) - 1;
      var y = parseInt(parts[2], 10);
      var h = 0, min = 0, s = 0;
      if (timeStr) {
        var tParts = timeStr.split(":");
        if (tParts.length >= 2) {
          h = parseInt(tParts[0], 10);
          min = parseInt(tParts[1], 10);
          if (tParts.length >= 3) s = parseInt(tParts[2], 10);
        }
      }
      return new Date(y, m, d, h, min, s).getTime();
    }
  } catch (ignored) {}
  return Date.now();
}

function jsonOutput(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/** Plain success object — shared by single-op responses and per-item BATCH results. */
function successObject(action, transactionId) {
  return {
    status: "success",
    transactionId: String(transactionId),
    action: String(action)
  };
}

/** Plain error object — shared by single-op responses and per-item BATCH results. */
function errorObject(message) {
  return { status: "error", message: String(message) };
}

function errorResult(message) {
  return jsonOutput(errorObject(message));
}

/** Use supplied epoch millis only when valid (positive finite number); otherwise fallback. */
function validEpoch(value, fallback) {
  var n = Number(value);
  return (isFinite(n) && n > 0) ? n : fallback;
}

// ── Sheet formatting (display only) ───────────────────────────────────────────
//
// Applied after CREATE / UPDATE / DELETE writes so freshly synced rows match.
// Display-only changes:
//   - center-align the used A:L range (header row included)
//   - Date column A → "dd/MM/yy"   (e.g. 11/01/26)
//   - Time column C → "HH:mm"      (e.g. 11:10, no seconds)
// Date/time cells stored as text in the known Android formats are converted to
// real Date values (same instant) purely so these number formats can render.
// Each step below is guarded independently so a hiccup in one can never skip
// the others, and ALL failures are swallowed so a formatting issue can never
// fail a sync write (that would otherwise make doPost return {status:error} and
// the Android client skip markSynced).
// No data, IDs, columns, order, or sync semantics are changed.

function applySheetFormatting(sheet) {
  if (!sheet) return;
  var lastRow = sheet.getLastRow();
  if (lastRow < 1) return;

  // 1) Center-align the whole used range, header included (A:L, fixed). Always
  //    runs. Uses an explicit 12-column range so no column can be missed.
  try {
    sheet.getRange(1, 1, lastRow, 12)
        .setHorizontalAlignment(SpreadsheetApp.HorizontalAlignment.CENTER);
  } catch (e) {}

  // 2) Make sure Date (A) and Time (C) hold real Date values, not text, so the
  //    number formats below can render. (appendRow may auto-parse a "11-01-2026"
  //    string into a Date with Sheets' DEFAULT dd/mm/yyyy format — converting
  //    here and re-formatting in step 3 fixes that.) Best-effort only.
  var dataRows = lastRow - 1;
  if (dataRows > 0) {
    try {
      var bloc = sheet.getRange(2, 1, dataRows, 3);
      var vals = bloc.getValues();
      var touched = false;
      for (var i = 0; i < dataRows; i++) {
        var d = parseDateCell(vals[i][0]);
        var t = parseTimeCell(vals[i][2]);
        if (d !== vals[i][0]) { vals[i][0] = d; touched = true; }
        if (t !== vals[i][2]) { vals[i][2] = t; touched = true; }
      }
      if (touched) bloc.setValues(vals);
    } catch (e) {}
  }

  // 3) Number formats — always run, independently of conversion.
  try { sheet.getRange(1, 1, lastRow, 1).setNumberFormat("dd/MM/yy"); } catch (e) {}
  try { sheet.getRange(1, 3, lastRow, 1).setNumberFormat("HH:mm"); } catch (e) {}
}

/**
 * One-shot manual reformat for the configured sheet (SHEET_NAME). Run once from the Apps
 * Script editor if you want every existing row re-formatted immediately without
 * waiting for the next sync write (otherwise any CREATE/UPDATE/DELETE formats
 * the whole used range anyway).
 */
function formatNow() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) return "Sheet '" + SHEET_NAME + "' not found.";
  applySheetFormatting(sheet);
  return "Formatted " + sheet.getLastRow() + " rows of " + SHEET_NAME + ".";
}

/** Convert a "dd-MM-yyyy" text date (or existing Date) into a Date; otherwise unchanged. */
function parseDateCell(val) {
  if (val instanceof Date) return val;
  var s = String(val || "").trim();
  var m = s.match(/^(\d{2})-(\d{2})-(\d{4})$/);
  if (m) return new Date(parseInt(m[3], 10), parseInt(m[2], 10) - 1, parseInt(m[1], 10));
  return val;
}

/** Convert a "HH:mm[:ss]" text time (or existing Date) into a Date; otherwise unchanged. */
function parseTimeCell(val) {
  if (val instanceof Date) return val;
  var s = String(val || "").trim();
  var m = s.match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/);
  if (m) return new Date(1899, 11, 30, parseInt(m[1], 10), parseInt(m[2], 10), m[3] ? parseInt(m[3], 10) : 0);
  return val;
}

// ── V1 → V2 MIGRATION (ONE-TIME, MANUAL) ──────────────────────────────────────
//
// Migrates rows in the configured sheet (SHEET_NAME).
// All sync operations (CREATE/UPDATE/DELETE/FETCH) are intentionally NOT used here.

/**
 * One-time migration for the configured sheet (SHEET_NAME).
 *
 * For every DATA row where column I (Transaction Id) is blank it:
 *   1. generates a permanent UUID (Utilities.getUuid())
 *   2. writes it into column I (Transaction Id)
 *   3. builds createdAt (column J) from the row's existing Date (col A) + Time (col C)
 *   4. sets updatedAt (column K) equal to createdAt
 *   5. sets deleted (column L) = false
 * It leaves every other cell in the row exactly as-is, never appends rows,
 * and skips any row that already has a transactionId — so it is safe to run
 * again: already-migrated rows stay unchanged and no duplicates are created.
 */
function migrateTransactionsV2() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    throw new Error("Sheet '" + SHEET_NAME + "' not found. Nothing was migrated.");
  }

  var lastRow = sheet.getLastRow();
  if (lastRow < 2) {
    return { status: "ok", migrated: 0, note: "No data rows in " + SHEET_NAME + "." };
  }

  // Read the full 12-column V2 block, header row included.
  var values = sheet.getRange(1, 1, lastRow, 12).getValues();

  var migrated = 0;
  for (var i = 1; i < values.length; i++) {   // row 1 = header → start at index 1
    var row = values[i];

    var transactionId = String(row[8] || "").trim(); // I Transaction Id
    if (transactionId) {
      continue; // already migrated — leave completely unchanged
    }

    var dateStr = formatDateCell(row[0]);  // A Date → "dd-MM-yyyy"
    var timeStr = formatTimeCell(row[2]);  // C Time → "HH:mm:ss"
    var createdAt = parseEpoch(dateStr, timeStr);
    var updatedAt = createdAt;

    row[8]  = Utilities.getUuid();  // I Transaction Id
    row[9]  = createdAt;            // J Created At  (epoch ms)
    row[10] = updatedAt;            // K Updated At  (epoch ms)
    row[11] = false;                // L Deleted

    migrated++;
  }

  if (migrated > 0) {
    sheet.getRange(1, 1, lastRow, 12).setValues(values);
  }

  return {
    status: "ok",
    migrated: migrated,
    skipped: (values.length - 1) - migrated
  };
}

/**
 * Adds a manual menu so the migration can be run once from the Apps Script
 * editor / bound spreadsheet. Does not run anything by itself.
 */
function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu("V2 Migration")
    .addItem("Migrate " + SHEET_NAME + " (one-time)", "migrateTransactionsV2")
    .addToUi();
}
