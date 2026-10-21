/**
 * @OnlyCurrentDoc
 * Pocket Ledger — ledger sync and current accounts/income/people dashboard
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
var PL_SUPPORT_SHEET_NAME = "Sync Support - Do not edit";

function doGet(e) {
  if (e && e.parameter && e.parameter.action === "CAPABILITIES")
    return jsonOutput({ status: "success", spendingTypeSchema: 1, ledgerSchema: 1, dashboardSchema: 1 });
  if (e && e.parameter && (e.parameter.action === "FETCH" || e.parameter.action === "RESTORE")) {
    if (ledgerIsActive()) return errorResult("Use the upgraded app to restore the full ledger.");
    return handleFetch();
  }
  return ContentService.createTextOutput("ok");
}

function doPost(e) {
  try {
    var payload = JSON.parse(e.postData.contents);
    var action  = payload.action;
    if (action === "LEDGER_PUSH" || action === "LEDGER_FETCH" || action === "DASHBOARD_REFRESH") return ledgerEndpoint(payload);
    var legacyLock=LockService.getScriptLock();legacyLock.waitLock(30000);
    try {
    if (ledgerIsActive() && action !== "CAPABILITIES") return errorResult("This Sheet uses accounts and people. Update the app before editing or restoring it.");

    if (action === "CREATE")           return handleCreate(payload);
    if (action === "UPDATE")           return handleUpdate(payload);
    if (action === "DELETE")           return handleDelete(payload);
    if (action === "BATCH")            return handleBatch(payload);
    if (action === "FETCH" || action === "RESTORE") return handleFetch();

    return errorResult("unknown action: " + action);
    } finally { legacyLock.releaseLock(); }
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
  var r = runRowOp(sheet, p);
  applySheetFormatting(sheet);
  return jsonOutput(r);
}

function handleUpdate(p) {
  var sheet = getSheet();
  var r = runRowOp(sheet, p);
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
    if (action !== "DELETE" && p.spendingType !== undefined &&
        ["NEED", "WANT", "UNTAGGED"].indexOf(p.spendingType) < 0)
      return errorObject("Invalid spendingType");
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
  return successObject(action, transactionId, normalizeSpendingType(p.spendingType));
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
      return successObject("UPDATE", transactionId, normalizeSpendingType(p.spendingType));
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
  var typeColumn = getSpendingTypeColumn(sheet);
  if (p.spendingType !== undefined) sheet.getRange(row, typeColumn).setValue(p.spendingType);
  return successObject(action, transactionId, normalizeSpendingType(sheet.getRange(row, typeColumn).getValue()));
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
  sheet.getRange(sheet.getLastRow(), getSpendingTypeColumn(sheet)).setValue(normalizeSpendingType(p.spendingType));
}

// ── FETCH / RESTORE ───────────────────────────────────────────────────────────

function handleFetch() {
  var sheet = getSheet();
  var typeColumn = getSpendingTypeColumn(sheet);
  var data = sheet.getDataRange().getValues();
  if (!data || data.length <= 1) {
    return jsonOutput({ status: "success", spendingTypeSchema: 1, transactions: [] });
  }

  var header = data[0];
  var canvasTypeColumn=header.indexOf('Type');
  // New V2 layout: "Transaction Id" lives in column 9 (index 8).
  var isV2 = header.length >= 9 && String(header[8]).trim().toLowerCase() === "transaction id";

  var list = [];
  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    if(canvasTypeColumn>=0&&String(row[canvasTypeColumn])==='ACCOUNT_SNAPSHOT')continue;
    // Skip completely empty rows
    var isEmpty = true;
    for (var j = 0; j < row.length; j++) {
      if (row[j] !== "") { isEmpty = false; break; }
    }
    if (isEmpty) continue;

    // Clearing visible cells can leave account metadata or custom values behind.
    if (!row[0] && (row[4] === '' || row[4] == null) && !String(row[8] || '').trim()) continue;

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
        spendingType: normalizeSpendingType(row[typeColumn - 1]),
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
        spendingType: normalizeSpendingType(row[typeColumn - 1]),
        deviceName: String(row[7] || "").trim(),
        createdAt: parseEpoch(dStr, tStr),
        updatedAt: parseEpoch(dStr, tStr),
        deleted: false
      };
      list.push(legacyTx);
    }
  }

  return jsonOutput({ status: "success", spendingTypeSchema: 1, transactions: list });
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function getSheet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var support = ss.getSheetByName(PL_SUPPORT_SHEET_NAME);
  if (support) return support;
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
function successObject(action, transactionId, spendingType) {
  var result = {
    status: "success",
    transactionId: String(transactionId),
    action: String(action)
  };
  if (spendingType !== undefined) result.spendingType = spendingType;
  return result;
}

function normalizeSpendingType(value) {
  return value === "NEED" || value === "WANT" ? value : "UNTAGGED";
}

/** Append after existing columns; never shift identity columns or overwrite custom data. */
function getSpendingTypeColumn(sheet) {
  var lastColumn = Math.max(12, sheet.getLastColumn());
  var headers = sheet.getRange(1, 1, 1, lastColumn).getValues()[0];
  for (var i = 12; i < headers.length; i++)
    if (String(headers[i]).trim().toLowerCase() === "spending type") return i + 1;
  var column = lastColumn + 1;
  if (column > sheet.getMaxColumns()) sheet.insertColumnsAfter(sheet.getMaxColumns(), column - sheet.getMaxColumns());
  sheet.getRange(1, column).setValue("Spending Type");
  return column;
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
    .createMenu("Pocket Ledger")
    .addItem("Set up / refresh latest dashboard", "setupPocketLedger")
    .addItem("Use clean Transactions + separate sync support", "setupCleanTransactions")
    .addSeparator()
    .addItem("Migrate " + SHEET_NAME + " (one-time)", "migrateTransactionsV2")
    .addToUi();
}

// Ledger schema 1: richer records alongside the original Transactions columns.
function ledgerIsActive() {
  var s=getSheet(),h=s.getRange(1,1,1,s.getLastColumn()).getValues()[0],c=h.indexOf('PL Snapshot Pointer');
  if(c>=0&&s.getRange(2,c+1).getValue())return true;
  var m=SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Ledger Metadata');
  return !!(m&&m.getRange(1,1).getValue());
}
function ledgerCheck(ok,msg){if(!ok)throw new Error(msg);}
function ledgerEmpty(){return {ledgerSchema:1,accounts:[],paymentRoutes:[],people:[],transactions:[]};}
var LEDGER_KEYS={accounts:'accountId',paymentRoutes:'routeId',people:'personId',transactions:'transactionId'};
function ledgerRead(){
  var current=plReadSnapshot();if(current)return plReadCanvasEdits(current);
  var book=SpreadsheetApp.getActiveSpreadsheet(),meta=book.getSheetByName('Ledger Metadata'),revision=meta?String(meta.getRange(1,1).getValue()):'';
  if(!revision){
    var old=JSON.parse(handleFetch().getContent()),out=ledgerEmpty();
    out.transactions=(old.transactions||[]).map(function(t){
      t.kind='EXPENSE';t.amountMinor=Math.round((Number(t.amount)+Number.EPSILON)*100);t.personalMinor=t.amountMinor;
      var d=String(t.date).split('-'),h=String(t.time||'00:00:00').split(':');
      t.effectiveAt=d.length===3?new Date(+d[2],+d[1]-1,+d[0],+h[0],+h[1],+h[2]).getTime():Number(t.createdAt);
      t.accountId='';t.destinationId='';t.routeId='';t.personId='';t.originalId='';t.shares={};t.syncStatus='SYNC_PENDING';return t;
    });return out;
  }
  var sheet=book.getSheetByName('Ledger Records');ledgerCheck(sheet,'Missing ledger records');var result=ledgerEmpty();
  sheet.getDataRange().getValues().forEach(function(row){if(String(row[0])===revision){ledgerCheck(Array.isArray(result[row[1]]),'Invalid collection');result[row[1]].push(JSON.parse(row[7]));}});
  var count=Object.keys(LEDGER_KEYS).reduce(function(n,k){return n+result[k].length;},0);
  ledgerCheck(count===Number(meta.getRange(1,2).getValue()),'Incomplete ledger snapshot');ledgerValidate(result);return result;
}
function ledgerMerge(local,remote){
  var result=ledgerEmpty();Object.keys(LEDGER_KEYS).forEach(function(group){var map=Object.create(null),key=LEDGER_KEYS[group];local[group].forEach(function(x){map[x[key]]=x;});remote[group].forEach(function(x){var old=map[x[key]];if(!old||x.updatedAt>=old.updatedAt)map[x[key]]=x;});result[group]=Object.keys(map).map(function(id){return map[id];});});ledgerValidate(result);return result;
}
function ledgerValidate(l){
  ledgerCheck(l&&l.ledgerSchema===1,'Unsupported ledger schema');var maps={},max=9000000000000000;
  function integer(n){return Number.isSafeInteger(n)&&Math.abs(n)<=max;}
  function add(a,b){ledgerCheck(integer(a+b),'Total exceeds supported amount');return a+b;}
  Object.keys(LEDGER_KEYS).forEach(function(group){
    ledgerCheck(Array.isArray(l[group]),'Missing '+group);maps[group]=Object.create(null);
    l[group].forEach(function(x){var id=x[LEDGER_KEYS[group]];ledgerCheck(typeof id==='string'&&id&&id.length<=160&&!maps[group][id],'Invalid or duplicate ID');ledgerCheck(integer(x.updatedAt)&&x.updatedAt>=0,'Invalid update timestamp');ledgerCheck(JSON.stringify(x).length<45000,'Record too large');maps[group][id]=x;});
  });
  l.accounts.forEach(function(a){ledgerCheck(typeof a.name==='string'&&a.name.trim()&&['BANK','CASH','WALLET','CARD'].indexOf(a.type)>=0,'Invalid account');ledgerCheck(integer(a.openingMinor)&&integer(a.openingAt)&&a.openingAt>0&&a.openingAt<=Date.now(),'Invalid opening balance');});
  l.people.forEach(function(p){ledgerCheck(typeof p.name==='string'&&p.name.trim(),'Invalid person');});
  l.paymentRoutes.forEach(function(r){ledgerCheck(typeof r.label==='string'&&r.label.trim()&&maps.accounts[r.accountId]&&(r.deleted||!maps.accounts[r.accountId].deleted),'Invalid payment route');});
  var kinds=['EXPENSE','INCOME','TRANSFER','LEND','BORROW','FRIEND_PAID','REPAYMENT_IN','REPAYMENT_OUT','WRITE_OFF_RECEIVABLE','FORGIVE_PAYABLE','REFUND','ADJUSTMENT'];
  var obligations=Object.create(null),personalRefunds=Object.create(null),friendRefunds=Object.create(null);
  l.transactions.forEach(function(t){
    ledgerCheck(kinds.indexOf(t.kind)>=0&&integer(t.amountMinor)&&integer(t.personalMinor),'Invalid transaction amount/type');
    if(t.deleted)return;
    ledgerCheck(t.amountMinor>0||t.kind==='ADJUSTMENT'&&t.amountMinor!==0,'Amount must be positive');
    ledgerCheck(integer(t.effectiveAt)&&t.effectiveAt>0&&t.effectiveAt<=Date.now(),'Invalid entry date');
    var a=maps.accounts[t.accountId],noAccount=['FRIEND_PAID','WRITE_OFF_RECEIVABLE','FORGIVE_PAYABLE'].indexOf(t.kind)>=0;
    ledgerCheck(noAccount?!t.accountId:t.kind==='EXPENSE'&&!t.accountId||a&&!a.deleted,'Choose an account');
    if(['INCOME','BORROW','LEND','REPAYMENT_IN','REPAYMENT_OUT','TRANSFER'].indexOf(t.kind)>=0)ledgerCheck(a&&a.type!=='CARD','Use bank, cash or wallet');
    if(t.kind==='TRANSFER')ledgerCheck(t.accountId!==t.destinationId&&maps.accounts[t.destinationId]&&!maps.accounts[t.destinationId].deleted,'Invalid transfer destination');else ledgerCheck(!t.destinationId,'Unexpected destination account');
    if(t.routeId)ledgerCheck(maps.paymentRoutes[t.routeId]&&maps.paymentRoutes[t.routeId].accountId===t.accountId,'Payment route mismatch');
    ledgerCheck(t.shares&&typeof t.shares==='object'&&!Array.isArray(t.shares),'Invalid shares');var total=0;
    Object.keys(t.shares).forEach(function(p){var n=t.shares[p];ledgerCheck(maps.people[p]&&!maps.people[p].deleted&&integer(n)&&n>0,'Invalid person/share');total=add(total,n);if(t.kind==='EXPENSE')obligations[t.transactionId+'/'+p]={remaining:n,receive:true};});
    if(t.kind==='EXPENSE'||t.kind==='REFUND')ledgerCheck(t.personalMinor>=0&&add(t.personalMinor,total)===t.amountMinor,'Shares must equal the total');else ledgerCheck(total===0,'Unexpected friend shares');
    if(['LEND','BORROW','FRIEND_PAID','REPAYMENT_IN','REPAYMENT_OUT','WRITE_OFF_RECEIVABLE','FORGIVE_PAYABLE'].indexOf(t.kind)>=0)ledgerCheck(maps.people[t.personId]&&!maps.people[t.personId].deleted,'Missing person');
    if(['LEND','BORROW','FRIEND_PAID'].indexOf(t.kind)>=0)obligations[t.transactionId+'/'+t.personId]={remaining:t.amountMinor,receive:t.kind==='LEND'};
    if(t.kind==='FRIEND_PAID')ledgerCheck(t.personalMinor===t.amountMinor,'Invalid personal share');
  });
  l.transactions.forEach(function(t){
    if(t.deleted)return;var child=['REFUND','REPAYMENT_IN','REPAYMENT_OUT','WRITE_OFF_RECEIVABLE','FORGIVE_PAYABLE'].indexOf(t.kind)>=0;
    if(!child){ledgerCheck(!t.originalId,'Unexpected original entry');return;}
    var origin=maps.transactions[t.originalId];ledgerCheck(origin&&!origin.deleted&&origin.transactionId!==t.transactionId&&t.effectiveAt>=origin.effectiveAt,'Original entry is missing or later than repayment');
    if(t.kind==='REFUND'){
      ledgerCheck(origin.kind==='EXPENSE','Only an expense can be refunded');personalRefunds[t.originalId]=add(personalRefunds[t.originalId]||0,t.personalMinor);ledgerCheck(personalRefunds[t.originalId]<=origin.personalMinor,'Refund exceeds your share');
      Object.keys(t.shares).forEach(function(p){var key=t.originalId+'/'+p;friendRefunds[key]=add(friendRefunds[key]||0,t.shares[p]);ledgerCheck(obligations[key]&&friendRefunds[key]<=(origin.shares[p]||0),'Refund exceeds friend share');obligations[key].remaining=add(obligations[key].remaining,-t.shares[p]);});
    }else{var debt=obligations[t.originalId+'/'+t.personId],receive=t.kind==='REPAYMENT_IN'||t.kind==='WRITE_OFF_RECEIVABLE';ledgerCheck(debt&&debt.receive===receive,'Wrong repayment person/direction');debt.remaining=add(debt.remaining,-t.amountMinor);}
  });
  Object.keys(obligations).forEach(function(key){ledgerCheck(obligations[key].remaining>=0,'Repayment exceeds outstanding amount');});
}
function ledgerEndpoint(p){
  var lock=LockService.getScriptLock();lock.waitLock(30000);
  try{
    ledgerCheck(p.ledgerSchema===1,'Update app for ledger sync');
    if(p.action==='LEDGER_FETCH')return jsonOutput({status:'success',ledgerSchema:1,ledger:ledgerRead()});
    if(p.action==='DASHBOARD_REFRESH'){ledgerRefreshReports(ledgerRead());return jsonOutput({status:'success',ledgerSchema:1,dashboardSchema:1});}
    ledgerCheck(typeof p.requestId==='string'&&p.requestId,'Missing request ID');ledgerValidate(p.ledger);
    var merged=ledgerMerge(ledgerRead(),p.ledger);
    plWriteSnapshot(merged);
    var reportWarning='';try{ledgerRefreshReports(merged);}catch(reportError){reportWarning=String(reportError.message||reportError);}
    return jsonOutput({status:'success',ledgerSchema:1,dashboardSchema:1,dashboardWarning:reportWarning,requestId:p.requestId,ledger:merged});
  }catch(e){return errorResult(String(e.message||e));}finally{lock.releaseLock();}
}

// Readable reports are derived from the committed snapshot, never used as sync inputs.
// Financial summaries feed the existing Canvas; no report tabs are created.
function ledgerReportModel(l,now){
  ledgerValidate(l);now=now||Date.now();
  var accounts=Object.create(null),people=Object.create(null),transactions=Object.create(null),debts=Object.create(null);
  function add(a,b){var n=a+b;ledgerCheck(Number.isSafeInteger(n)&&Math.abs(n)<=9000000000000000,'Report total exceeds supported amount');return n;}
  l.accounts.forEach(function(a){accounts[a.accountId]=a;});l.people.forEach(function(p){people[p.personId]=p;});l.transactions.forEach(function(t){transactions[t.transactionId]=t;});
  function name(map,id){return id?(map[id]?map[id].name:'Unknown'):'Unassigned';}
  function expense(t){if(t.kind==='EXPENSE'||t.kind==='FRIEND_PAID')return t.personalMinor;if(t.kind==='REFUND')return -t.personalMinor;if(t.kind==='WRITE_OFF_RECEIVABLE')return t.amountMinor;return 0;}
  function income(t){return t.kind==='INCOME'||t.kind==='FORGIVE_PAYABLE'?t.amountMinor:0;}
  function effect(t,id){if(t.kind==='TRANSFER')return t.accountId===id?-t.amountMinor:t.destinationId===id?t.amountMinor:0;if(!id||t.accountId!==id)return 0;if(['EXPENSE','LEND','REPAYMENT_OUT'].indexOf(t.kind)>=0)return -t.amountMinor;if(['INCOME','BORROW','REPAYMENT_IN','REFUND','ADJUSTMENT'].indexOf(t.kind)>=0)return t.amountMinor;return 0;}
  var active=l.transactions.filter(function(t){return !t.deleted&&t.effectiveAt<=now;}),unassigned=0;
  active.forEach(function(t){if(t.kind==='EXPENSE'){if(!t.accountId)unassigned++;Object.keys(t.shares).forEach(function(p){debts[t.transactionId+'/'+p]={person:p,receive:true,remaining:t.shares[p]};});}if(['LEND','BORROW','FRIEND_PAID'].indexOf(t.kind)>=0)debts[t.transactionId+'/'+t.personId]={person:t.personId,receive:t.kind==='LEND',remaining:t.amountMinor};});
  active.forEach(function(t){if(!t.originalId)return;if(t.kind==='REFUND')Object.keys(t.shares).forEach(function(p){var d=debts[t.originalId+'/'+p];if(d)d.remaining=add(d.remaining,-t.shares[p]);});else{var d=debts[t.originalId+'/'+t.personId];if(d)d.remaining=add(d.remaining,-t.amountMinor);}});
  var accountRows=l.accounts.filter(function(a){return !a.deleted;}).sort(function(a,b){return a.position-b.position||a.name.localeCompare(b.name);}).map(function(a){
    var change=0;active.forEach(function(t){if(t.effectiveAt>=a.openingAt)change=add(change,effect(t,a.accountId));});var balance=add(a.openingMinor,change),card=a.type==='CARD';
    return [a.name,a.type,a.openingMinor/100,change/100,balance/100,card?0:balance/100,card?Math.max(0,-balance)/100:0,card?Math.max(0,balance)/100:0,a.archived?'Archived':'Active',a.accountId,new Date(a.openingAt)];
  });
  var personRows=l.people.filter(function(p){return !p.deleted;}).map(function(p){var receive=0,pay=0;Object.keys(debts).forEach(function(key){var d=debts[key];if(d.person===p.personId){if(d.receive)receive=add(receive,d.remaining);else pay=add(pay,d.remaining);}});return [p.name,receive/100,pay/100,(receive-pay)/100,p.archived?'Archived':'Active',p.personId];});
  var activity=active.slice().sort(function(a,b){return b.effectiveAt-a.effectiveAt;}).map(function(t){var origin=transactions[t.originalId],tag=t.kind==='REFUND'&&origin?origin.spendingType:t.spendingType;
    return [new Date(t.effectiveAt),t.kind,t.category||'Other',name(accounts,t.accountId),t.destinationId?name(accounts,t.destinationId):'',t.amountMinor/100,expense(t)/100,income(t)/100,(income(t)-expense(t))/100,tag||'UNTAGGED',t.personId?name(people,t.personId):'',t.paymentMode||'',t.remarks||'',t.transactionId];
  });
  return {accounts:accountRows,people:personRows,activity:activity,unassigned:unassigned,categories:Array.from(new Set(active.filter(function(t){return expense(t)!==0;}).map(function(t){return t.category||'Other';}))).sort()};
}
// Canvas sync keeps the original Dashboard and uses only the existing Transactions tab.
var PL_CANVAS_HEADERS=['Type','Account','Account type','Destination account','Personal amount','Income','Account balance','Card dues','Person','To receive','To pay','Account ID','Destination ID','Person ID','Original entry ID'];
var PL_STORAGE_HEADERS=['PL Snapshot A','PL Snapshot B','PL Snapshot Pointer','PL Snapshot Count'];
function plHeaders(create){
  var s=getSheet(),h=s.getRange(1,1,1,s.getLastColumn()).getValues()[0];
  if(create){
    getSpendingTypeColumn(s);h=s.getRange(1,1,1,s.getLastColumn()).getValues()[0];
    PL_CANVAS_HEADERS.concat(PL_STORAGE_HEADERS).forEach(function(name){if(h.indexOf(name)<0){h.push(name);if(s.getMaxColumns()<h.length)s.insertColumnsAfter(s.getMaxColumns(),h.length-s.getMaxColumns());s.getRange(1,h.length).setValue(name);}});
    ledgerCheck(PL_STORAGE_HEADERS.every(function(name,i){return h.indexOf(name)===h.indexOf(PL_STORAGE_HEADERS[0])+i;}),'Sync storage columns must remain together');
    if(s.hideColumns)s.hideColumns(h.indexOf(PL_STORAGE_HEADERS[0])+1,4);
  }
  return {sheet:s,headers:h};
}
function plReadSnapshot(){
  var layout=plHeaders(false),h=layout.headers,s=layout.sheet,c=h.indexOf('PL Snapshot Pointer');
  if(c<0)return null;var pointer=s.getRange(2,c+1,1,2).getValues()[0];if(!pointer[0])return null;
  ledgerCheck(pointer[0]==='A'||pointer[0]==='B','Invalid snapshot pointer');
  var count=Number(pointer[1]);ledgerCheck(Number.isSafeInteger(count)&&count>=0&&count<=s.getMaxRows()-1,'Invalid snapshot count');
  var out=ledgerEmpty(),column=h.indexOf('PL Snapshot '+pointer[0])+1;ledgerCheck(column>0,'Missing snapshot column');
  if(count)s.getRange(2,column,count,1).getValues().forEach(function(row){var item=JSON.parse(row[0]);ledgerCheck(Object.prototype.hasOwnProperty.call(LEDGER_KEYS,item.group),'Invalid snapshot collection');out[item.group].push(item.value);});
  ledgerValidate(out);return out;
}
function plWriteSnapshot(ledger){
  var layout=plHeaders(true),s=layout.sheet,h=layout.headers,pc=h.indexOf('PL Snapshot Pointer')+1;
  var slot=s.getRange(2,pc).getValue()==='A'?'B':'A',rows=[];
  Object.keys(LEDGER_KEYS).forEach(function(group){ledger[group].forEach(function(value){rows.push([JSON.stringify({group:group,value:value})]);});});
  if(s.getMaxRows()<rows.length+1)s.insertRowsAfter(s.getMaxRows(),rows.length+1-s.getMaxRows());
  if(rows.length)s.getRange(2,h.indexOf('PL Snapshot '+slot)+1,rows.length,1).setNumberFormat('@').setValues(rows);
  SpreadsheetApp.flush();
  // Commit the inactive buffer only after all records have been written successfully.
  s.getRange(2,pc,1,2).setValues([[slot,rows.length]]);SpreadsheetApp.flush();
}
function plLiteral(value){return typeof value==='string'&&/^[=+@-]/.test(value)?"'"+value:value;}
function plReadCanvasEdits(ledger){
  var layout=plHeaders(false),h=layout.headers,rows=layout.sheet.getDataRange().getValues(),byId=Object.create(null);
  ledger.transactions.forEach(function(t){byId[t.transactionId]=t;});
  function cell(row,name){var c=h.indexOf(name);return c<0?'':row[c];}
  rows.slice(1).forEach(function(row){
    var kind=String(cell(row,'Type')||'EXPENSE'),id=String(row[8]||'');
    if(!id||kind==='ACCOUNT_SNAPSHOT'||kind==='ACCOUNT'||!row[0])return;
    var old=byId[id],updated=Number(row[10])||0;
    if(old&&updated<=old.updatedAt)return;
    var t=old?JSON.parse(JSON.stringify(old)):{transactionId:id,shares:{},routeId:'',syncStatus:'SYNC_PENDING'};
    t.kind=kind;t.amountMinor=Math.round(Number(row[4])*100);
    var personal=cell(row,'Personal amount');t.personalMinor=personal!==''?Math.round(Number(personal)*100):kind==='EXPENSE'||kind==='FRIEND_PAID'?t.amountMinor:0;
    t.accountId=String(cell(row,'Account ID')||t.accountId||'');t.destinationId=String(cell(row,'Destination ID')||t.destinationId||'');t.personId=String(cell(row,'Person ID')||t.personId||'');t.originalId=String(cell(row,'Original entry ID')||t.originalId||'');
    t.date=formatDateCell(row[0]);t.time=formatTimeCell(row[2]);t.effectiveAt=old&&t.date===old.date&&t.time===old.time?old.effectiveAt:parseEpoch(t.date,t.time);t.amount=t.amountMinor/100;
    t.category=String(row[3]||'');t.paymentMode=String(row[5]||'');t.remarks=String(row[6]||'');t.deviceName=String(row[7]||'');t.createdAt=old?old.createdAt:Number(row[9])||updated;t.updatedAt=updated;
    t.deleted=row[11]===true||String(row[11]).toLowerCase()==='true';t.spendingType=normalizeSpendingType(cell(row,'Spending Type'));
    if(!old)ledger.transactions.push(t);else ledger.transactions[ledger.transactions.indexOf(old)]=t;
    byId[id]=t;
  });ledgerValidate(ledger);return ledger;
}
function ledgerRefreshReports(ledger){
  var layout=plHeaders(true),s=layout.sheet,h=layout.headers,data=s.getDataRange().getValues(),model=ledgerReportModel(ledger,Date.now()),byId=Object.create(null),accounts=Object.create(null),people=Object.create(null),desired=[];
  ledger.accounts.forEach(function(a){accounts[a.accountId]=a;});ledger.people.forEach(function(p){people[p.personId]=p;});
  for(var i=1;i<data.length;i++){var id=String(data[i][8]||'');if(id){ledgerCheck(byId[id]===undefined,'Duplicate transaction ID in Sheet');byId[id]=i;}}
  ledger.transactions.forEach(function(t){var a=accounts[t.accountId],dest=accounts[t.destinationId],person=people[t.personId];
    var original=ledger.transactions.filter(function(x){return x.transactionId===t.originalId;})[0];
    desired.push({'Date':t.date||new Date(t.effectiveAt),'Month':t.month||'', 'Time':t.time||'', 'Category':t.category||'', 'Amount':t.amountMinor/100,'Payment Mode':t.paymentMode||'', 'Remarks':t.remarks||'', 'Device Name':t.deviceName||'', 'Transaction Id':t.transactionId,'Created At':t.createdAt,'Updated At':t.updatedAt,'Deleted':!!t.deleted,'Spending Type':t.kind==='REFUND'&&original?original.spendingType||'UNTAGGED':t.spendingType||'UNTAGGED','Type':t.kind,'Account':a?a.name:'','Account type':a?a.type:'','Destination account':dest?dest.name:'','Personal amount':t.personalMinor/100,'Income':t.kind==='INCOME'||t.kind==='FORGIVE_PAYABLE'?t.amountMinor/100:0,'Account balance':'','Card dues':'','Person':person?person.name:'','To receive':'','To pay':'','Account ID':t.accountId||'','Destination ID':t.destinationId||'','Person ID':t.personId||'','Original entry ID':t.originalId||''});
  });
  var stamp=Date.now();
  model.accounts.forEach(function(a){desired.push({'Transaction Id':'PL_ACCOUNT:'+a[9],'Type':'ACCOUNT_SNAPSHOT','Account':a[0],'Account type':a[1],'Account balance':a[4],'Card dues':a[6],'Account ID':a[9],'Updated At':stamp,'Deleted':false});});
  model.people.forEach(function(p){desired.push({'Transaction Id':'PL_PERSON:'+p[5],'Type':'ACCOUNT_SNAPSHOT','Person':p[0],'To receive':p[1],'To pay':p[2],'Person ID':p[5],'Updated At':stamp,'Deleted':false});});
  var visible=['Date','Month','Time','Category','Amount','Payment Mode','Remarks','Device Name','Transaction Id','Created At','Updated At','Deleted','Spending Type'].concat(PL_CANVAS_HEADERS),touched=Object.create(null);
  desired.forEach(function(values){var id=values['Transaction Id'],r=byId[id];if(r===undefined){r=data.length;data.push([]);byId[id]=r;}touched[id]=true;visible.forEach(function(name){data[r][h.indexOf(name)]=Object.prototype.hasOwnProperty.call(values,name)?plLiteral(values[name]):'';});});
  for(var r=1;r<data.length;r++){
    var id=String(data[r][8]||'');
    if(/^PL_(ACCOUNT|PERSON):/.test(id)&&!touched[id])data[r][h.indexOf('Deleted')]=true;
    // Storage-only rows must not become zero-amount expenses in the Canvas.
    if(!id&&!data[r].slice(0,8).some(function(v){return v!==''&&v!=null;}))data[r][h.indexOf('Type')]='ACCOUNT_SNAPSHOT';
  }
  var count=data.length-1;if(s.getMaxRows()<data.length)s.insertRowsAfter(s.getMaxRows(),data.length-s.getMaxRows());
  // Write owned columns only; custom columns, formulas, layout and Dashboard are untouched.
  var columns=visible.map(function(name){return h.indexOf(name);}).sort(function(a,b){return a-b;});
  for(var k=0;k<columns.length;){var start=columns[k],end=start;while(k+1<columns.length&&columns[k+1]===end+1){k++;end++;}if(count)s.getRange(2,start+1,count,end-start+1).setValues(data.slice(1).map(function(row){return Array.from({length:end-start+1},function(_,j){return row[start+j]===undefined?'':row[start+j];});}));k++;}
  if(SpreadsheetApp.getActiveSpreadsheet().getSheetByName(PL_SUPPORT_SHEET_NAME))plRefreshCleanTransactions(ledger);
  SpreadsheetApp.flush();return model;
}
function setupPocketLedger(){var lock=LockService.getScriptLock();lock.waitLock(30000);try{var ledger=ledgerRead();plWriteSnapshot(ledger);ledgerRefreshReports(ledger);}finally{lock.releaseLock();}}

// The raw sheet keeps its ID so the existing Canvas retains its original data binding.
function setupCleanTransactions(){
  var lock=LockService.getScriptLock();lock.waitLock(30000);
  try{
    var book=SpreadsheetApp.getActiveSpreadsheet(),ledger=ledgerRead(),raw=getSheet();
    ledgerValidate(ledger);
    if(raw.getName()!==PL_SUPPORT_SHEET_NAME)raw.setName(PL_SUPPORT_SHEET_NAME);
    plWriteSnapshot(ledger);ledgerRefreshReports(ledger);
    raw.hideSheet();
  }finally{lock.releaseLock();}
}
function plRefreshCleanTransactions(ledger){
  var book=SpreadsheetApp.getActiveSpreadsheet(),sheet=book.getSheetByName(SHEET_NAME);
  if(!sheet)sheet=book.insertSheet(SHEET_NAME);
  var headers=['Date','Month','Time','Category','Amount','Payment Mode','Account','Remarks','Type','Spending Type','Personal amount'];
  var oldHeight=sheet.getLastRow(),accounts=Object.create(null),rows=[];
  ledger.accounts.forEach(function(a){accounts[a.accountId]=a.name;});
  ledger.transactions.filter(function(t){return !t.deleted;}).sort(function(a,b){return b.effectiveAt-a.effectiveAt||a.transactionId.localeCompare(b.transactionId);}).forEach(function(t){
    var month=t.month||new Date(t.effectiveAt).toLocaleString('en-US',{month:'long'});
    rows.push([t.date||formatDateCell(new Date(t.effectiveAt)),month,t.time||formatTimeCell(new Date(t.effectiveAt)),t.category||'',t.amountMinor/100,t.paymentMode||'',accounts[t.accountId]||'',t.remarks||'',t.kind,t.spendingType==='UNTAGGED'?'':t.spendingType||'',t.kind==='EXPENSE'||t.kind==='REFUND'||t.kind==='FRIEND_PAID'?t.personalMinor/100:''].map(plLiteral));
  });
  if(sheet.getMaxColumns()<headers.length)sheet.insertColumnsAfter(sheet.getMaxColumns(),headers.length-sheet.getMaxColumns());
  if(sheet.getMaxRows()<rows.length+1)sheet.insertRowsAfter(sheet.getMaxRows(),rows.length+1-sheet.getMaxRows());
  sheet.getRange(1,1,1,headers.length).setValues([headers]);
  if(rows.length)sheet.getRange(2,1,rows.length,headers.length).setValues(rows);
  if(oldHeight>rows.length+1)sheet.getRange(rows.length+2,1,oldHeight-rows.length-1,headers.length).clearContent();
  sheet.getRange(1,1).setNote('Report only. Edit entries in the app or Dashboard. Sync Support holds balances, IDs and deletion markers; do not clear it.');
  sheet.setFrozenRows(1);
  sheet.getRange(1,1,1,headers.length).setFontWeight('bold').setBackground('#d9ead3').setWrap(true);
  sheet.getRange(2,5,Math.max(1,rows.length),1).setNumberFormat('#,##0.00');
  sheet.getRange(2,11,Math.max(1,rows.length),1).setNumberFormat('#,##0.00');
}
