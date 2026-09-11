# Backend compatibility

The reference Apps Script `doPost(e)` parses `e.postData.contents` as JSON, then appends one row to the `Transactions` sheet in this exact order:

| Column | JSON field |
|---|---|
| A | `date` |
| B | `month` |
| C | `time` |
| D | `category` |
| E | `amount` |
| F | `paymentMode` |
| G | `remarks` |
| H | `deviceName` |

The script returns the exact text response `Success`. Pocket Ledger preserves all eight field names and emits a success notification only for a 2xx response whose trimmed body equals `Success`, ignoring case. Category and payment-mode values are intentionally sent exactly as configured in canonical suffix format, including their emoji (for example `Food & Dining 🍔` and `UPI 📱`).

No backend code is included, copied, or modified by this project. The Apps Script URL remains device-local and user-configurable. The app requires HTTPS and sends no credentials or API key because the reference architecture does not use one.

Date formatting follows the Android macro reference: device-local `d/M/yyyy`, full month name (`MMMM`), and 24-hour `H:mm` time. Amount remains a JSON string, as in the MacroDroid reference payload.

The reference backend offers no non-mutating health endpoint. Therefore the app's Test Connection action uses GET only to check HTTPS reachability and clearly documents that it does not prove a `doPost` save. It deliberately does not POST a fake row.
