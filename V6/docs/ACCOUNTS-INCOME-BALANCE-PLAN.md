# Accounts, income, balances, and money owed

Status: final proposed implementation plan; implementation has not started.
Date: 15 October 2026.
Scope: current V6 native Android app and its optional Google Sheets mirror.

## 1. Product contract

Pocket Ledger will track where money is held, money earned, personal spending, transfers, and money owed between people. Ordinary expense entry must stay quick. All features work offline; the local database remains authoritative.

Users can create any number of named Bank, Cash, Wallet, or Credit card accounts, such as ICICI, SBI, FamPay, and Cash. A user wanting simple tracking can keep one account called My money. Overall is a view of these same accounts, not a second ledger or a destructive mode switch.

One configured currency applies across all accounts. Currency-symbol changes do not convert money. Multiple currencies and exchange-rate transfers are outside this release.

## 2. Account setup and lifecycle

- Account fields: stable ID, name, type, opening amount, opening effective date/time, display order, archived status, creation/update metadata, and sync state.
- Bank/Cash/Wallet setup asks for balance available. Credit card setup asks for amount owed, allowing an explicit credit balance when the issuer owes the user money.
- Opening balance means the amount immediately before ledger activity at the chosen effective timestamp. UI uses clear wording and defaults to now when entering today's actual balance.
- Earlier transactions stay in historical reports but do not change that account's opening-based current balance. Entries exactly at the boundary are included; store a precise timestamp to avoid ambiguous same-day cutoffs.
- Adding an opening amount is neither income nor expense. Opening money and debt never reset monthly.
- Rename/reorder freely. Delete only unused accounts with no transaction or obligation references, through an explicit confirmation. Otherwise archive.
- Archiving hides an account from new-entry choices, but preserves its history and includes its balance in Overall. Explain that users should transfer remaining funds or settle a card before archiving if they want a zero balance. Unarchive is supported.
- Opening-balance edits require a preview of the resulting balance; existing records are never silently rewritten. Later reconciliation adjustments are explicit dated adjustments with a reason, excluded from income/spending.
- Bank/wallet/cash balances may go negative; warn without blocking, since incomplete records and overdrafts are possible. Credit limits are not enforced.

## 3. Payment method connects directly to account

Expense entry keeps one Payment method picker. Choices point to stable account IDs; users never need to choose both a method and an account.

- ICICI Bank -> ICICI balance; Cash -> Cash balance; ICICI Credit Card -> that card's amount owed.
- Optional display routes such as SBI · UPI or ICICI · Debit card point to the underlying SBI/ICICI account. Multiple routes can share an account without duplicating its balance.
- UPI alone cannot identify a balance. A legacy UPI/Card label must be linked to an account during setup; it is never guessed.
- Account management automatically maintains its primary payment choice. Payment-route settings allow optional aliases linked to accounts.
- Defaults and last-used selection use stable IDs. Archiving an account repairs an invalid default; the picker never silently substitutes an archived account during saving.
- Income uses the same account selection concept, labelled Received in. Borrowing proceeds cannot be received into a credit card account in this release. Ordinary earned income goes to Bank/Cash/Wallet; merchant card refunds use a separate refund action.

## 4. Entry flows and widget

### Ordinary expense

Widget tap -> existing expense sheet -> amount + category + Payment method -> Save expense -> return to launcher. The saved default/last valid payment choice remains preselected. No transaction-type menu, person picker, or account-setup wizard is added before this sheet.

The Quick Settings tile and expense shortcut keep this same expense-first behavior. Existing widget sizes and branding remain. Account setup happens in the app; a migrated user who has not set up accounts can still save an expense as Needs account without losing it. Surface a quiet setup prompt and flag that balances are incomplete.

### In-app creation

- Main Add action opens Expense by default, with an Expense / Income switch.
- Transfer and Lena / Dena are secondary actions available from account and people screens and the Add screen's More actions.
- Income: amount, income category, receiving account, date/time, optional note. Initial categories: Salary, Freelance, Gift, Interest, Other; users can manage them separately from expense categories.
- Expense: amount, expense category, paying account through Payment method, date/time, optional note and Need/Want.
- Need/Want applies only to the user's personal expense amount. Income/transfers/settlements never enter those charts.
- An optional For a friend / Split payment action expands fields only when needed. Full group management is not introduced.
- Preserve an unsaved draft across rotation/recreation. Disable repeated saves and commit locally once before starting optional sync.

## 5. Balance and Home rules

For each non-card account:

Current balance = opening amount + all eligible signed account movements since its opening timestamp.

An account movement is the actual money entering/leaving that account, not automatically income/expense. Borrowing, lending, reimbursements, transfers, and card payments all affect account balances correctly without distorting reports.

Credit cards use a consistent internal signed balance: debt is negative and issuer credit is positive. UI presents debt as Amount owed and positive issuer credit as Credit balance.

Home account selector: Overall / individual account. Remember selection. Switching views changes no stored money and creates no transactions.

Overall summary:

- Available money: sum of Bank/Cash/Wallet signed balances.
- Credit card dues: sum of negative card balances expressed as positive amounts owed.
- Card credit, when present: separate supporting amount.
- Net balance: available money + signed card balances. Card credit is not called spendable cash.
- To receive / To pay: separate people totals, excluded from Available money and Net balance. Detail text says Net balance excludes money owed between people; avoid calling it total net worth.

Individual account summary shows its current balance or card dues, plus relevant history and account actions. No misleading combined Cash + credit-limit amount.

Date filters change period Income, Expenses, Net change, and charts. The Current balance/Amount owed hero remains current and clearly labelled. Net change means earned income minus personal spending; it is not an account reconciliation total. An account's history includes transfers and settlements so actual balance changes remain understandable.

Historical period totals remain visible even before account opening dates, with a note where their scope differs from current balance. Unassigned legacy records are visible in Overall reports and flagged; they are not silently assigned to any account balance.

## 6. Transfers and credit cards

- Transfer asks From, To, amount, timestamp, optional note. Accounts must differ; amounts must be positive.
- A transfer is one logical operation with two linked account effects saved atomically. Editing/deleting affects both sides. One Activity item; neither side counts as income/expense.
- SBI -> Cash represents an ATM withdrawal. Bank -> card represents paying a card bill. Card purchases count as expenses at purchase time, and card payments do not count again.
- A card bill payment lowers bank money and lowers card debt equally, leaving Overall net balance unchanged.
- General transfers originate in Bank/Cash/Wallet; credit-card cash advances, card-to-card transfers, fees, and interest automation are outside this release. Users can record actual fees/interest manually as expenses.
- Merchant refund is linked to the original expense and credited to the selected account/card on the refund date. It reverses personal spending rather than inflating income. Validate cumulative refunds against the refundable amount; shared-purchase refunds require explicit allocation to personal/friend portions before saving.

## 7. Lena / Dena: optional people ledger

Keep one People screen with To receive and To pay sections. People are manually named; no contact permission is necessary. Every balance has linked dated records and partial settlements. Show separate open receivables/payables even for the same person; do not silently offset them.

Supported operations:

| Scenario | Money/account effect | Personal report effect | People effect |
| --- | --- | --- | --- |
| Pay a 1,200 bill; own share 400 | Paying account -1,200; card debt +1,200 if paid by card | Expense 400 | Receivable 800 allocated to named friends |
| Lend friend 2,000 | Bank/Cash/Wallet -2,000 | No expense | Receivable 2,000 |
| Buy item for friend for 1,500 | Paying account -1,500 or card debt +1,500 | Expense 0 | Receivable 1,500 |
| Receive repayment 500 | Receiving Bank/Cash/Wallet +500 | No income | Chosen receivable -500 |
| Borrow 2,000 | Receiving Bank/Cash/Wallet +2,000 | No income | Payable 2,000 |
| Friend pays your 600 bill | No owned-account movement | Expense 600 | Payable 600 |
| Repay friend 300 | Paying Bank/Cash/Wallet -300 | No expense | Chosen payable -300 |

- Shared-payment screen accepts own share and named friend shares; own + friend shares must exactly equal the amount paid. Equal-split shortcut can follow later; manual amounts cover the first release.
- Friend-only purchase is the same shared-payment flow with own share zero. No separate parallel accounting system.
- Settlement chooses an open item, amount, account, and actual receipt/payment date. Partial settlements show remaining amounts and history.
- Block repayment above the chosen outstanding amount; user can explicitly record any excess as another operation. No automatic income classification.
- Receivables do not become available money until received. Payables do not reduce a bank account until paid.
- Settlement cannot predate its originating item. Editing the original amount cannot make already-settled money exceed the revised amount. Explain the dependency and require correcting settlements first; do not silently remove them.
- Cancellation of a debt without money movement uses an explicit write-off/forgiveness action. Receivable write-off records a personal loss expense once; payable forgiveness records other income once. Both close the appropriate obligation and leave account cash unchanged.
- Person archive preserves referenced records; deletion is allowed only when unreferenced. Open debts remain visible.

## 8. Activity and reporting

- Activity filters: All, Expense, Income, Transfer, Lena/Dena; combine with account, person, category, date, and search where applicable.
- Use explicit labels and +/- amounts as well as color. A shared expense displays both Total paid and Your expense, with person shares in detail.
- Transfers and settlements appear once per logical operation in Overall; account detail shows that account's effect.
- Category spending, daily expense averages, and Need/Want use personal expenses only, including an expense paid for the user by a friend. Refunds reduce spending on their recorded date; allow a negative net category in reports and use a refund-aware display rather than negative donut slices.
- Income reports exclude opening amounts, transfers, borrowing, and repayments.
- Edits/deletions recompute derived balances. Operations with settlements/refunds cannot be deleted without resolving their dependants explicitly. Deletion leaves sync tombstones.
- Clearly flag account history before its balance start, unmapped legacy records, and any balance that has not been initialized. Do not show a guessed balance as confirmed cash.

## 9. Data and calculation approach

Extend the existing Room architecture, without adding a finance framework or rewriting unrelated screens.

- Accounts: stable UUIDs and lifecycle/opening metadata.
- Payment routes: existing picker labels linked to account UUIDs; a default route per account.
- Transactions: stable existing UUID, explicit operation kind, exact amount, effective timestamp, relevant account/source/destination IDs, category, personal share, notes, spending tag, update/sync/deletion metadata, and links to original operations where applicable.
- People and obligation items: stable IDs; each item records receivable/payable direction, person, original operation and allocated amount. Settlements reference one obligation and their actual account movement.
- Derive account effects and report effects through one shared calculation layer used by Home, Activity, account detail, and exports. Do not maintain independently incremented cached balance fields.
- Use integer minor currency units (paise for INR) for new canonical amounts and sums. Use BigDecimal for legacy decimal conversion with a documented rounding rule; show a migration warning if an existing value requires rounding. Keep legacy serialized amounts compatible at import boundaries.
- Define account cutoff comparisons and date filtering once. Existing records retain their dates; new/backdated entries use their effective transaction timestamp rather than creation time for financial reports. Preserve createdAt separately for identity/history.
- One logical multi-part operation commits in a Room transaction, including its people allocations. Enforce amount bounds, checked arithmetic, valid references, and split/settlement constraints before saving.
- Categories are separate for income and expenses. Stable account identity survives renaming and duplicate-looking names; the picker disambiguates names when needed.

## 10. Existing-user migration

1. Versioned, non-destructive Room migration; old transactions become EXPENSE with personal share equal to original amount. Preserve IDs, tags, notes, dates, deletion and sync metadata.
2. Introduce accounts with a short setup accessible from Home. Users can choose one My money account or multiple named accounts; no forced multi-account setup.
3. Offer explicit mapping of existing payment labels to accounts. Cash can be suggested, but no mapping is committed without user action. UPI/Card require an explicit choice. Mapping preview shows affected record counts and cutoff behavior.
4. Let users choose today's actual balance with a current timestamp or a historical starting balance with an explicit start point. Explain that today's opening amount already includes earlier spending; historical entries before the opening timestamp are not subtracted again.
5. Keep unmapped records as Needs account, included in historical expense totals, and allow quick capture to continue until setup is complete. Show completeness status on balances.
6. Prevent a new post-opening unmapped entry from disappearing from the user's awareness: surface a persistent account-assignment count/action. Assigning an entry later recalculates the appropriate balance.
7. After migration, users may explicitly reclassify an old expense as lending/shared spending; never infer which purchases belonged to friends.

## 11. Backups, Sheets, and compatibility

- Bump backup schema to include accounts, routes, operation kinds, people, obligations, settlements, opening balances, archive/deletion state, and relevant settings. Continue accepting existing version 1/2 backups as expenses.
- Validate the whole import and all references before committing. Merge by stable ID/update metadata; import dependencies in a single database transaction. Re-import must not duplicate balances, transfers, debts, or settlements.
- Include logical tombstones and child records so a restored deleted operation cannot reappear. Preserve or surface conflicts instead of silently producing an incomplete transfer or settlement graph.
- Update Apps Script, the Android bundled script copy, and setup instructions together. Keep old transaction columns for compatibility and add versioned fields/tabs for accounts, people, and linked operations.
- Add an explicit backend capability/schema check. New ledger data stays pending when the configured backend cannot preserve it; never flatten income, transfers, or settlements into old expense rows or report them synced.
- Sync new operations with all required account/person/obligation dependencies. Backend validates/stages the complete logical operation and acknowledges it only when all required records are persisted. Retry by stable IDs and recover incomplete writes; do not assume Sheets supports a database-style transaction.
- Restore fetches and validates a complete ledger snapshot before local merge. Restore/import must preserve account opening cutoffs and derived balances.
- Old clients/backends cannot safely edit the richer ledger. New-schema Sheets require the upgraded app/script; communicate compatibility clearly and protect unsupported operations from legacy-client writes through schema gating in the upgraded backend.
- Export as Text includes account, operation type, personal share, transfers, and outstanding people totals with clear labels.

## 12. Delivery sequence and exit checks

### Phase 1: ledger foundation and migration

Room schemas/migrations, exact money, effective dates, shared calculation layer, migration previews, legacy mapping rules. Verify old data is preserved and all account/report effects match the scenario table.

### Phase 2: accounts and quick entry

Account management, one linked Payment method picker, opening setup, Expense/Income switch, separate categories, widget/tile integration. Verify normal expense capture has the same required interactions as today and can still save offline before account setup.

### Phase 3: transfers, card payments, and refunds

Atomic transfer operations, card debt display/payment, linked refunds, edit/delete rules. Verify ATM withdrawals/card payments do not change personal expense or income totals, retries cannot create half or duplicate transfers, and net balance remains unchanged by moving owned money or paying a card bill.

### Phase 4: people ledger

People screen, lend/borrow, shared payment, friend-paid expense, partial repayment, write-off/forgiveness and dependency safeguards. Verify paying 1,200 with a personal share of 400 leaves 800 receivable; receiving that 800 later restores cash without increasing income.

### Phase 5: Home and Activity

Overall/account selection, available money/card dues/net balance, period summaries, completeness states, history and filters. Review small screens, dark/light theme, large text, accessibility labels, keyboard behavior, and empty states.

### Phase 6: full portability and release validation

Backups, import, text export, backend capabilities, dependency-safe sync/restore, updated docs. Shipping gate: upgrade from a populated current database; backup/restore round-trip; repeated sync/import; offline and failed-sync recovery; edits/deletions with dependants; archived accounts; opening cutoffs; decimal precision/overflow; and real-device widget save/return-to-launcher behavior.

Backup/sync fields are defined in Phase 1 and maintained with each feature, then integrated and verified in Phase 6. Do not release an intermediate build that can save new ledger data but lose it on backup/restore.

## 13. Explicitly deferred

Automatic bank/UPI connections, SMS parsing, recurring income, budgets, interest calculation, credit limits, statement billing cycles, payment reminders, contact import, group invitations, automatic equal-split rounding, multi-currency, and an iPhone Shortcut redesign. These are separate additions and not required for this ledger to work.

## 14. Representative end-to-end check

Start with SBI 10,000, Cash 1,000, and card debt 2,000: available 11,000; net 9,000.

1. Receive salary 20,000 into SBI: SBI 30,000; income 20,000.
2. Move 1,000 SBI -> Cash: SBI 29,000; Cash 2,000; reports unchanged.
3. Pay shared bill 1,200 from SBI, own share 400: SBI 27,800; personal expense 400; receivable 800.
4. Receive friend repayment 800 into Cash: Cash 2,800; receivable zero; income unchanged.
5. Buy personal item for 500 on card: card debt 2,500; personal expense total 900.
6. Pay card 2,000 from SBI: SBI 25,800; card debt 500; reports unchanged.

Final available money 28,600; card dues 500; net balance 28,100; period income 20,000; personal expenses 900; net change 19,100; nothing owed by the friend. Opening net 9,000 + net change 19,100 = final net 28,100 for this fully settled example.
