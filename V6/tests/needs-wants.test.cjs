const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { DatabaseSync } = require('node:sqlite');
const root = path.resolve(__dirname, '..');

// Exercise the actual migration SQL and compare with Room's generated fresh schema.
const db = new DatabaseSync(':memory:');
db.exec('CREATE TABLE transactions (transactionId TEXT NOT NULL PRIMARY KEY, amount REAL NOT NULL, category TEXT, paymentMode TEXT, remarks TEXT, date TEXT, time TEXT, deviceName TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, syncStatus TEXT, deleted INTEGER NOT NULL)');
db.exec("INSERT INTO transactions VALUES ('old-synced',500,'Food','UPI','groceries','14-10-2026','12:00:00','phone',1000,2000,'SYNCED',0), ('pending',90,'Food','Cash','','14-10-2026','13:00:00','phone',2000,3000,'SYNC_PENDING',0), ('deleted',20,'Other','Cash','','13-10-2026','13:00:00','phone',4000,5000,'SYNC_PENDING',1)");
const before = db.prepare('SELECT * FROM transactions ORDER BY transactionId').all();
const javaRoot = path.join(root, 'NativeExpenseEntry/app/src/main/java/com/pocketledger/v2');
const migrationSource = fs.readFileSync(path.join(javaRoot, 'AppDatabase.java'), 'utf8');
const migrationSql = migrationSource.match(/db\.execSQL\("([^"]+)"\)/)[1];
db.exec(migrationSql);
for (const row of db.prepare('SELECT * FROM transactions ORDER BY transactionId').all()) {
  assert.equal(row.spendingType, 'UNTAGGED');
  delete row.spendingType;
  assert.deepEqual(row, before.find(old => old.transactionId === row.transactionId));
}
const migration23 = migrationSource.slice(migrationSource.indexOf('static final Migration MIGRATION_2_3'), migrationSource.indexOf('try (android.database.Cursor'));
for (const match of migration23.matchAll(/db\.execSQL\("([^"\n]+)"\)/g)) db.exec(match[1]);
const generated = fs.readFileSync(path.join(root, 'NativeExpenseEntry/app/build/generated/ap_generated_sources/debug/out/com/pocketledger/v2/AppDatabase_Impl.java'), 'utf8');
const fresh = new DatabaseSync(':memory:');
fresh.exec(generated.match(/db\.execSQL\("(CREATE TABLE IF NOT EXISTS `transactions`[^"\n]+)"\)/)[1]);
const columns = connection => connection.prepare('PRAGMA table_info(transactions)').all()
  .map(({cid, ...column}) => column).sort((a, b) => a.name.localeCompare(b.name));
assert.deepEqual(columns(db), columns(fresh));
db.close(); fresh.close();

// In-memory Sheets stub: run the real script without touching a user's Sheet.
const headers = ['Date','Month','Time','Category','Amount','Payment Mode','Remarks','Device Name','Transaction Id','Created At','Updated At','Deleted'];
function makeSheet(customColumn = false) {
  const data = [[...headers, ...(customColumn ? ['My own column'] : [])]];
  return {
    data, columns: customColumn ? 13 : 12,
    getLastColumn() { return Math.max(...data.map(row => row.length)); },
    getLastRow() { return data.length; },
    getMaxColumns() { return this.columns; },
    insertColumnsAfter(start, count) { this.columns += count; },
    appendRow(row) { data.push([...row]); },
    getRange(row, col, rows = 1, cols = 1) {
      return {
        getValue() { return data[row - 1]?.[col - 1] ?? ''; },
        setValue(value) { while (data.length < row) data.push([]); data[row - 1][col - 1] = value; return this; },
        getValues() { return Array.from({length: rows}, (_, r) => Array.from({length: cols}, (_, c) => data[row + r - 1]?.[col + c - 1] ?? '')); }
      };
    },
    getDataRange() { return this.getRange(1, 1, data.length, this.getLastColumn()); }
  };
}
let sheet = makeSheet(true);
const context = vm.createContext({
  SpreadsheetApp: {getActiveSpreadsheet: () => ({getSheetByName: () => sheet})},
  ContentService: {MimeType: {JSON: 'json'}, createTextOutput: text => ({text, setMimeType() { return this; }})}
});
vm.runInContext(fs.readFileSync(path.join(root, 'Backend/AppsScript.gs'), 'utf8'), context);
const expense = {action:'CREATE',transactionId:'id-1',date:'14-10-2026',month:'October',time:'12:00:00',amount:500,category:'Food',paymentMode:'UPI',remarks:'',createdAt:1000,updatedAt:1000,spendingType:'NEED'};
assert.equal(context.runRowOp(sheet, expense).spendingType, 'NEED');
assert.equal(sheet.data[0][12], 'My own column');
assert.equal(sheet.data[0][13], 'Spending Type');
assert.equal(sheet.data[1][13], 'NEED');
assert.equal(context.runRowOp(sheet, {...expense, action:'UPDATE',spendingType:'WANT',updatedAt:2000}).spendingType, 'WANT');
assert.equal(sheet.data.length, 2, 'Tag update must reuse the existing row');
assert.equal(sheet.data[1][8], 'id-1', 'Stable ID preserved');
context.runRowOp(sheet, {...expense, action:'UPDATE',spendingType:'UNTAGGED'});
assert.equal(sheet.data[1][13], 'UNTAGGED', 'Clearing tag survives sync');
context.runRowOp(sheet, {...expense, action:'UPDATE',spendingType:'NEED'});
const legacyPayload = {...expense, action:'UPDATE'}; delete legacyPayload.spendingType;
context.runRowOp(sheet, legacyPayload);
assert.equal(sheet.data[1][13], 'NEED', 'Older clients must not erase tags');
assert.equal(context.runRowOp(sheet, {...expense, spendingType:'invalid'}).status, 'error');
assert.equal(sheet.data.length, 2);
let response = JSON.parse(context.handleFetch().text);
assert.equal(response.spendingTypeSchema, 1);
assert.equal(response.transactions[0].spendingType, 'NEED');
assert.equal(JSON.parse(context.doGet({parameter:{action:'CAPABILITIES'}}).text).spendingTypeSchema, 1);
context.runRowOp(sheet, {...expense,action:'DELETE'});
assert.equal(JSON.parse(context.handleFetch().text).transactions[0].deleted, true);
sheet = makeSheet();
sheet.data.push(['14-10-2026','October','12:00:00','Food',10,'Cash','','phone','old-id',1000,1000,false]);
response = JSON.parse(context.handleFetch().text);
assert.equal(response.transactions[0].spendingType, 'UNTAGGED', 'Older Sheet rows stay untagged');
assert.equal(sheet.data.length, 2);
const bundled = fs.readFileSync(path.join(root, 'NativeExpenseEntry/app/src/main/assets/Pocket-Ledger-AppsScript.gs.txt'));
assert.deepEqual(bundled, fs.readFileSync(path.join(root, 'Backend/AppsScript.gs')));
console.log('PASS: migration preserves rows and matches Room schema; Sheet create/update/clear/restore/delete, stable IDs, custom columns, legacy clients, and bundled script.');
