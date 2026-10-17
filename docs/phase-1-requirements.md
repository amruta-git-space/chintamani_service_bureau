# Chintamani Service Bureau — Phase 1 Requirements

| --                  | --                                                                               |
|---------------------|----------------------------------------------------------------------------------|
| **Document status** | Draft v0.2 — review comments incorporated                                        |
| **Date**            | 2026-10-10                                                                       |
| **Scope**           | Phase 1 (Part 1: Upload & Billing Summary, Part 2: Approval & WhatsApp Dispatch) |
| **Reference input** | `sample-files/Bill_Q2_2026-27.xlsx`                                              |

> Items marked **[CONFIRM]** are assumptions that must be confirmed before implementation.
> Items marked **[TBD]** are deliberately deferred to Part 2 design.
> See [Section 12 — Open Questions](#12-open-questions--decisions).

---

## 1. Background

Chintamani Service Bureau places Nurses and Helpers with elderly clients/patients. Each billing period,
the bureau prepares an Excel workbook listing, for every client, the helpers who served them, the shift,
the number of days and the rate. Bills are currently communicated to clients manually.

Phase 1 digitizes the **validation, summarization and dispatch** of these bills. The Excel workbook
remains the source of truth for bill content in Phase 1; the application does not create or edit bills.

## 2. Scope

### 2.1 In scope — Phase 1 Part 1 (Upload & Billing Summary)
1. Upload an Excel workbook.
2. Select the billing-period sheet to process.
3. Validate the workbook and the sheet structure.
4. Validate individual bills (line items).
5. Group line items by **Billing Reference**.
6. Calculate the total amount for each bill.
7. Display a **Billing Summary** (Thymeleaf, server-rendered).

### 2.2 In scope — Phase 1 Part 2 (Approval & Dispatch)
1. User reviews and approves the Billing Summary.
2. Corrections are made **only** by editing the Excel file and re-uploading (no in-app editing). 3
3. After approval, a WhatsApp message with bill details is sent for each individual bill. 
4. Dispatch status is stored in PostgreSQL so that, after correction, bills already sent are not sent again. 
5. The UI displays the status against each bill.

### 2.3 Out of scope for Phase 1 (future phases; split not yet decided)
- Client management.
- Helper management.
- Assigning helpers to clients.
- Billing calculations and rates (rates come from the workbook in Phase 1).
- Generating the Excel workbook automatically.
- Reviewing and manually amending generated bills before dispatch.
- Attendance, payments and reporting.

Phase 1 design should not block these, but must not build for them speculatively.

## 3. Glossary

| Term                     | Meaning                                                                           |
|--------------------------|-----------------------------------------------------------------------------------|
| **Workbook**             | The uploaded Excel file, e.g. `Bill_Q2_2026-27.xlsx` (one per quarter).           |
| **Billing-period sheet** | One worksheet in the workbook covering one billing period, e.g. `Jul 15-30`.      |
| **Billing period**       | The date range given by *Billing Start Date* and *Billing End Date* on the sheet. |
| **Line item**            | One data row on the sheet (one helper/shift, for one client).                     |
| **Billing Reference**    | Identifier shared by all line items that make up one bill, e.g. `20260730-0001`.  |
| **Bill**                 | All line items sharing one Billing Reference, sent to one client.                 |
| **Dispatch**             | Sending a bill to the client over WhatsApp.                                       |
| **Upload / Batch**       | One processed upload of one sheet, identified by workbook file name + sheet name. |

## 4. Users

| Role                | Description                                                                                |
|---------------------|--------------------------------------------------------------------------------------------|
| **Bureau operator** | Staff member who uploads the workbook, reviews the summary, approves and dispatches bills. |

- Phase 1 assumes a single role. Login is not required in Phase 1.
- Clients (bill recipients) do not use the application; they only receive WhatsApp messages.

## 5. Input Workbook Specification

Derived from `sample-files/Bill_Q2_2026-27.xlsx`.

### 5.1 Workbook
| Property                       | Rule                                                                                                        |
|--------------------------------|-------------------------------------------------------------------------------------------------------------|
| Format                         | `.xlsx` (Excel 2007+) and `.xls` (Excel 97–2003). `.csv` and other file types are rejected.                 |
| Max file size                  | 5 MB.                                                                                                       |
| File name                      | Must be kept **exactly the same** across re-uploads of the same workbook (used to match re-uploads — FR-9). |
| Sheets                         | One or more billing-period sheets. Sample contains two sheets: `Jul 15-30` and `Aug 01-15`.                 |
| Password-protected / encrypted | Rejected with a clear message.                                                                              |

### 5.2 Sheet layout

| Cell / Row    | Content                                                         | Sample value |
|---------------|-----------------------------------------------------------------|--------------|
| `A1`          | Label `Billing Start Date`                                      | —            |
| `A2`          | Billing Start Date (Excel date)                                 | 15-Jul-2026  |
| `A3`          | Label `Billing End Date`                                        | —            |
| `A4`          | Billing End Date (Excel date)                                   | 30-Jul-2026  |
| Row 5         | Blank                                                           | —            |
| Row 6         | Column headers                                                  | see 5.3      |
| Row 7 onwards | Line items, until the first row where columns A–J are all blank | —            |

The sample sheet contains many *formatted but empty* rows and cells (up to row 274, columns up to W).
These must be ignored, not treated as errors.

### 5.3 Columns (row 6)

| Col | Header              | Type                    | Required | Notes                                                                                                                       |
|-----|---------------------|-------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------|
| A   | `Sr. No`            | Integer                 | Yes      | 1, 2, 3 … running across the sheet.                                                                                         |
| B   | `Billing Reference` | Text                    | Yes      | Format `yyyyMMdd-NNNN`, e.g. `20260730-0001`. Prefix matches Billing End Date.                                              |
| C   | `Client Name`       | Text                    | Yes      | e.g. `Shri Khandwe`.                                                                                                        |
| D   | `Client Phone`      | Text/Number             | Yes      | Indian mobile number: exactly 10 digits, no spaces, hyphens or country code. `+91` is added by the application at dispatch. |
| E   | `Send Bill`         | `Yes` / `No`            | Yes      | Controls whether the bill is dispatched.                                                                                    |
| F   | `Helper Name`       | Text                    | Yes      | Name of the helper/nurse.                                                                                                   |
| G   | `Shift`             | `Day` / `Night` / `24h` | Yes      | Name of the shift.                                                                                                          |
| H   | `Day`               | Number                  | Yes      | Number of days, e.g. 15. May be fractional for half days (up to 2 decimals).                                                |
| I   | `Rate`              | Amount (INR)            | Yes      | Per-day rate, e.g. 700, 600. Bureau charges are already included in the rate.                                               |
| J   | `Total Amount`      | Amount (INR)            | Yes      | Excel formula `=H*I`; the application reads the calculated (cached) value.                                                  |

- Columns are identified by **header text**, matched case-insensitively and ignoring extra spaces,
  not by position alone.
- Extra columns after `J` are ignored.

### 5.4 Sample data (Jul 15-30)

> **Sample file update pending:** the current sample still has header `Description` in `F6` and
> Sr. No values `1, 2, 4, 5`. It must be updated to `Helper Name` and `1–4` to match this specification.

| Sr | Billing Ref   | Client       | Send | Helper Name      | Shift | Day | Rate | Total  |
|----|---------------|--------------|------|------------------|-------|-----|------|--------|
| 1  | 20260730-0001 | Shri Khandwe | Yes  | Sharada Khanekar | Night | 15  | 700  | 10,500 |
| 2  | 20260730-0001 | Shri Khandwe | Yes  | Savita Kurhade   | Day   | 15  | 600  | 9,000  |
| 3  | 20260730-0002 | Sujit Phatak | Yes  | Abc              | Day   | 10  | 600  | 6,000  |
| 4  | 20260730-0003 | Sudhir Patki | No   | Xyz              | Night | 12  | 700  | 8,400  |

Expected Billing Summary:

| Billing Ref     | Client       | Bill Total  | Send Bill |
|-----------------|--------------|-------------|-----------|
| 20260730-0001   | Shri Khandwe | ₹19,500     | Yes       |
| 20260730-0002   | Sujit Phatak | ₹6,000      | Yes       |
| 20260730-0003   | Sudhir Patki | ₹8,400      | No        |
| **Grand total** |              | **₹33,900** |           |

This sample becomes an automated test fixture.

## 6. Functional Requirements — Part 1

### FR-1 Upload workbook
- The operator can choose a file and upload it from a web page.
- The server rejects a missing or empty file, the wrong file type, an oversized file, or a file it cannot read as a workbook.
- Rejections show a clear message on the same page. Nothing is stored for a rejected file.

**Acceptance:** Uploading the sample file takes the operator to sheet selection. Uploading a `.pdf` or an empty file shows an error.

### FR-2 Select billing-period sheet
- After upload, the application lists all visible sheets of the workbook in a dropdown labeled **"Billing Period"**.
- The operator selects exactly one sheet to process.
- If the workbook has only one sheet, it is still shown for confirmation rather than auto-selected.
- Hidden sheets are excluded.
- While the operator selects a sheet, the uploaded file is held temporarily in the HTTP session or a temp file. It is not persisted to the database in Part 1.

### FR-3 Validate workbook & sheet structure
Structure errors are **blocking**: no bills are shown, only the list of errors.
See rules `S-xx` in Section 7.1.

### FR-4 Validate individual bills
Line-item and bill-level rules (`L-xx`, `B-xx` in Sections 7.2 and 7.3) are applied to every row and every bill.
- All errors are collected and reported together. Validation does not stop at the first error.
- Every error identifies the **row number as seen in Excel**, the **column**, the **Billing Reference** (where known), and a plain-language message.
- **An error affects only its own bill.** If any line item of a bill has an ERROR (L-xx), or the bill fails a bill-level rule (B-xx), the **whole bill** is marked **INVALID**:
    - it is highlighted on the summary;
    - it is never dispatched, whatever its `Send Bill` value;
    - all other valid bills proceed normally.
- **Rows without a usable Billing Reference** (missing, or failing L-04) cannot be grouped into any bill. They are listed as errors and excluded from all bills and totals.
- If Client Name differs between the line items of one bill, the name from the **first** line item of that bill is used, and warning B-01 is shown.

### FR-5 Group line items by Billing Reference
- Line items are grouped by exact Billing Reference (after trimming spaces).
- Bills are ordered by Billing Reference, ascending. Line items within a bill keep their sheet order.

### FR-6 Calculate bill total
- **Line total** = `Day × Rate`, calculated by the application.
- The application compares its calculated line total with the `Total Amount` cell. A mismatch is a validation error (L-10).
- **Bill total** = sum of the calculated line totals of the bill.
- **Grand total** = sum of the bill totals of all **valid** bills on the sheet. The total of INVALID bills is shown separately.
- All money calculations use `BigDecimal`, never floating point, and are rounded to 2 decimal places (half-up). Decimals are not normally expected in rates.

### FR-7 Display Billing Summary
The summary page shows:
- **Header:** file name, sheet name, billing period (start–end), upload time, number of bills (valid / INVALID), number of line items, grand total.
- **Bills table:** one row per bill showing Billing Reference, Client Name, Client Phone, Send Bill, status, number of line items, and bill total. Each bill can be expanded to show its line items: Helper Name, Shift, Day, Rate, and line total.
- **Errors panel:** all validation errors and warnings, linked to the affected bill where possible. INVALID bills are visually highlighted.
- **Formatting:** amounts in INR with Indian digit grouping (e.g. ₹1,00,000.00) and dates as `dd-MMM-yyyy`.
- Bills with `Send Bill = No` are listed but visually marked as *will not be sent*.

## 7. Validation Rules Catalogue

Severity:
- **Structure ERROR (S-xx):** blocks the whole sheet.
- **ERROR (L-xx, B-xx):** marks the affected bill **INVALID**. Other bills are unaffected.
- **WARNING:** displayed only. Does not affect dispatch.

### 7.1 Structure (sheet-level) — all ERROR, blocking
| ID   | Rule                                                                                       |
|------|--------------------------------------------------------------------------------------------|
| S-01 | File is a readable, non-encrypted `.xlsx` or `.xls` workbook.                              |
| S-02 | The selected sheet exists and is not empty.                                                |
| S-03 | `A1` = `Billing Start Date` and `A3` = `Billing End Date` (labels).                        |
| S-04 | `A2` and `A4` contain valid dates.                                                         |
| S-05 | Billing Start Date ≤ Billing End Date.                                                     |
| S-06 | Row 6 contains all required headers from 5.3. No required header is missing or duplicated. |
| S-07 | At least one line item exists.                                                             |

### 7.2 Line item (row-level)
| ID   | Rule                                                                                                                                           | Severity                        |
|------|------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|
| L-01 | All required cells are present (Sr. No, Billing Reference, Client Name, Client Phone, Send Bill, Helper Name, Shift, Day, Rate, Total Amount). | ERROR                           |
| L-02 | Sr. No is a positive integer, unique within the sheet.                                                                                         | ERROR                           |
| L-03 | Sr. No is sequential (1, 2, 3 …).                                                                                                              | WARNING                         |
| L-04 | Billing Reference matches `^\d{8}-\d{4}$`.                                                                                                     | ERROR (row excluded — see FR-4) |
| L-05 | The date prefix of the Billing Reference equals the Billing End Date.                                                                          | WARNING                         |
| L-06 | Send Bill is `Yes` or `No` (case-insensitive).                                                                                                 | ERROR                           |
| L-07 | Shift is `Day`, `Night` or `24h` (case-insensitive).                                                                                           | ERROR                           |
| L-08 | Day is a number > 0, at most 2 decimals, and ≤ the number of days in the billing period.                                                       | ERROR                           |
| L-09 | Rate is a number > 0, at most 2 decimals.                                                                                                      | ERROR                           |
| L-10 | Total Amount equals Day × Rate (to 2 decimal places).                                                                                          | ERROR                           |
| L-11 | Cells contain no Excel error values (`#REF!`, `#VALUE!` …).                                                                                    | ERROR                           |
| L-12 | Client Phone is exactly 10 digits, with no spaces, hyphens, `+91` or other characters.                                                         | ERROR                           |

### 7.3 Bill level (per Billing Reference)
| ID   | Rule                                                                                          | Severity |
|------|-----------------------------------------------------------------------------------------------|----------|
| B-01 | Client Name is identical on all line items of the bill (first line's name is used otherwise). | WARNING  |
| B-02 | Client Phone is identical on all line items of the bill.                                      | ERROR    |
| B-03 | Send Bill is identical on all line items of the bill.                                         | ERROR    |
| B-04 | All line items of one bill are on consecutive rows.                                           | WARNING  |
| B-05 | The bill total is > 0.                                                                        | ERROR    |
| B-06 | The same client (name + phone) does not appear under two different Billing References.        | WARNING  |

## 8. Functional Requirements — Part 2

### FR-8 Review & approve
- There is a single **Approve** button. It approves **every valid bill with `Send Bill = Yes`** that has not already been sent.
- **Safety check:** the server dispatches only bills that are valid, `Send Bill = Yes` and `PENDING`. The browser is never trusted to decide which bills are eligible.
- The `Approve` button is disabled when there is no eligible bill.
- **Confirmation prompt:** clicking Approve opens a confirmation dialog before anything is sent, showing:
    - number of bills to be sent and their total amount;
    - number of bills skipped (`Send Bill = No`);
    - number of INVALID bills;
    - number of bills already sent.

  Dispatch starts only when the operator confirms. Cancelling sends nothing.
- At approval, the parsed bills and line items are **persisted** in PostgreSQL together with the approval time and the uploaded file's name and checksum.

### FR-9 Corrections via re-upload
- The application does not offer bill editing for now. This may be added in a future phase.
- To correct a bill, the operator edits the Excel file and uploads it again.
- A re-upload is matched to an earlier batch by **exact workbook file name + exact sheet name**. If either differs, the upload is treated as a new batch.
- On re-upload, the summary shows the previous dispatch status of each bill, matched by Billing Reference. Rules for re-uploads:
    - Bills already **SENT** are never sent again and are not re-validated.
    - If a `SENT` bill's content (client phone, line items or total) differs from what was sent, the bill is **highlighted as "Changed after sending"**. It is still not re-sent; the operator handles it manually.
    - Bills that were **INVALID**, **FAILED** or are **new** are validated again and are eligible for dispatch after approval.
    - Bills that are **ERRORED** are not dispatched again (see FR-11).

### FR-10 WhatsApp dispatch
- After approval, one WhatsApp message is sent per eligible bill to its Client Phone (prefixed with `+91`).
- Eligible means: valid, `Send Bill = Yes`, and status `PENDING`.
- Message content follows the template in Section 11.
- Dispatch runs one bill at a time. A failure on one bill does not stop the others.
- **[TBD] Synchronous vs background dispatch.** Current volume is 150–160 bills per period. To be decided during Part 2 design.
- **[TBD] WhatsApp provider.** To be decided before Part 2. Provider credentials come from environment variables and are never committed.

### FR-11 Dispatch status persistence
Each bill's dispatch is tracked in PostgreSQL with:
- status
- attempt count
- last attempt time
- provider message ID (on success)
- error code and message (on failure)

**Bill statuses:**

| Status           | Meaning                                                                                                                         | Dispatched on next approval?        |
|------------------|---------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| `PENDING`        | Valid, `Send Bill = Yes`, not yet sent.                                                                                         | Yes                                 |
| `SENT`           | Accepted by the WhatsApp provider.                                                                                              | No, never                           |
| `FAILED`         | Send rejected because of bill data, e.g. invalid phone number **[CONFIRM]** (Q-25).                                             | Yes, after a corrected re-upload    |
| `ERRORED`        | Send failed for a non-data reason, e.g. network or provider outage. Requires **manual send**; the reason is recorded and shown. | No, never                           |
| `INVALID`        | Failed validation (FR-4).                                                                                                       | Yes, once corrected and re-uploaded |
| `NOT_TO_BE_SENT` | `Send Bill = No`.                                                                                                               | No                                  |

```
PENDING ──send ok──────────────▶ SENT
   │
   ├──data rejected────────────▶ FAILED  ──(corrected re-upload + approval)──▶ PENDING
   │
   └──network/provider error───▶ ERRORED   (manual send; never retried)

INVALID  ──(corrected re-upload)──▶ PENDING
NOT_TO_BE_SENT                      (Send Bill = No)
```

- Only `PENDING` bills are dispatched.
- `SENT` means *accepted by the WhatsApp provider*. Tracking *delivered* and *read* receipts is not required.
- There is no automatic or manual **Retry** action in the application.

### FR-12 Display status
- The summary page shows each bill's status, the last error message (if FAILED or ERRORED), and the time of the last attempt.
- ERRORED bills are highlighted as *needs manual send*. SENT bills that changed in a re-upload are highlighted as *changed after sending*.
- A **batch view** lists past uploads showing:
    - workbook name, sheet name and billing period;
    - upload time and approval time;
    - counts of SENT / FAILED / ERRORED / PENDING / INVALID / NOT_TO_BE_SENT bills.

## 9. Data to be Persisted (conceptual — schema to be designed during implementation)

| Concept          | Key attributes                                                                                                                                         |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| Billing batch    | workbook file name, sheet name (together unique), billing start/end date, file checksum, uploaded at, approved at                                      |
| Bill             | batch, billing reference, client name, client phone, send bill, bill total, status, content fingerprint of what was sent (for "changed after sending") |
| Bill line item   | bill, Sr. No, helper name, shift, days, rate, line total                                                                                               |
| Dispatch attempt | bill, outcome, attempted at, provider message ID, error code/message                                                                                   |

- Phase 1 Part 1 persists nothing.
- Storing the original uploaded file is not required in Phase 1.
- The schema is managed with versioned migrations (Flyway proposed; to be agreed).

## 10. Non-Functional Requirements

| Area            | Requirement                                                                                                                                                                                                                                                              |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Technology      | Java 21, Spring Boot 4, Spring MVC, Thymeleaf, Spring Data JPA, PostgreSQL, Apache POI, Maven. Single deployable; no SPA, no microservices.                                                                                                                              |
| Security        | No credentials in source control (DB and WhatsApp secrets come from environment / external config). Upload size limited. Uploaded content treated as untrusted. Formulas are not evaluated by the application, which reads only cached values. CSRF protection on forms. |
| Privacy         | Client phone numbers are personal data. They are not logged in full; logs mask them (e.g. `98******21`).                                                                                                                                                                 |
| Reliability     | One bill's dispatch failure never affects another. Protection against double dispatch: a bill already SENT is never re-sent, including when the operator double-clicks, resubmits or re-uploads.                                                                         |
| Usability       | Errors are written in plain language and point to the Excel row and column so the operator can fix the file quickly. Approve requires explicit confirmation.                                                                                                             |
| Performance     | A sheet of up to 500 line items is validated and summarised in under 3 seconds.                                                                                                                                                                                          |
| Locale          | Times in IST (Asia/Kolkata). Amounts in INR with Indian grouping.                                                                                                                                                                                                        |
| Testability     | Parser, validators and calculations unit-tested. Sample workbook and deliberately broken variants used as fixtures. Controller tests for upload and validation. Repository tests against PostgreSQL.                                                                     |
| Maintainability | Thin controllers. Logic in services. DTOs between the web and service layers.                                                                                                                                                                                            |

## 11. WhatsApp Message Template

```
Chintamani Service Bureau

Billing Period: {billing_start_date} to {billing_end_date}
Client: {client_name}

{helper_name_1} - {shift_1}
{day_1} x {rate_1} = {line_total_1}

{helper_name_2} - {shift_2}
{day_2} x {rate_2} = {line_total_2}

… one block per line item …

Total Amount: {bill_total}

Kindly make the payment.
Thank you.
```

- `{bill_total}` is the sum of the line totals of the bill.
- **[TBD] Provider constraints.** WhatsApp requires business-initiated messages to use a pre-approved template. Template variables cannot contain line breaks, and the number of variables is fixed. The variable-length line-item section must therefore be redesigned during Part 2 implementation, once the provider is chosen.

## 12. Open Questions / Decisions

| ID   | Question                                                                                                                                                                                                                                                                                                     | Answer                                                                                                |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| Q-01 | Do users need to log in? How many operators?                                                                                                                                                                                                                                                                 | No login for Phase 1.                                                                                 |
| Q-02 | Accept only `.xlsx`, or also `.xls`?                                                                                                                                                                                                                                                                         | `.xlsx` and `.xls`.                                                                                   |
| Q-03 | Maximum upload file size?                                                                                                                                                                                                                                                                                    | 5 MB.                                                                                                 |
| Q-04 | How is the end of the data detected?                                                                                                                                                                                                                                                                         | Stop at the first row where columns A–J are all blank.                                                |
| Q-05 | Is the layout fixed?                                                                                                                                                                                                                                                                                         | Fixed positions for the dates. Headers located on row 6 by name.                                      |
| Q-06 | Must the Billing Reference date prefix equal the Billing End Date? Is `NNNN` unique per period?                                                                                                                                                                                                              | Warning on mismatch. Unique within a period.                                                          |
| Q-07 | Phone format?                                                                                                                                                                                                                                                                                                | Exactly 10 digits, no hyphens/spaces; `+91` added at dispatch; one number per bill.                   |
| Q-08 | Complete list of Shift values?                                                                                                                                                                                                                                                                               | `Day`, `Night`, `24h`.                                                                                |
| Q-09 | Can Day be fractional? Can Rate have paise?                                                                                                                                                                                                                                                                  | Day can have decimals (half days). Rates typically have no paise. Up to 2 decimals allowed for both.  |
| Q-10 | Hidden sheets? Auto-select single sheet?                                                                                                                                                                                                                                                                     | Exclude hidden sheets; always show the list for confirmation.                                         |
| Q-11 | Keep the original uploaded Excel?                                                                                                                                                                                                                                                                            | No. Store file name + checksum only.                                                                  |
| Q-12 | If some bills have errors, block the whole sheet?                                                                                                                                                                                                                                                            | No. Invalid bills are skipped; valid bills still go out.                                              |
| Q-13 | Is the sheet name meaningful?                                                                                                                                                                                                                                                                                | Yes — workbook name + sheet name identify a batch for re-uploads. No check against the billing dates. |
| Q-14 | Approve the whole sheet at once, or individual bills?                                                                                                                                                                                                                                                        | One Approve button approving all valid `Send Bill = Yes` bills, with a confirmation prompt.           |
| Q-15 | How is a re-upload recognised?                                                                                                                                                                                                                                                                               | Exact workbook file name + exact sheet name. The operator must keep the file name unchanged.          |
| Q-16 | Already-SENT bill changed in re-upload?                                                                                                                                                                                                                                                                      | Never re-sent. Highlighted as "Changed after sending".                                                |
| Q-17 | Message wording, language, attachments?                                                                                                                                                                                                                                                                      | English text message per Section 11; no PDF in Phase 1.                                               |
| Q-18 | Typical number of bills per period?                                                                                                                                                                                                                                                                          | 150–160. Synchronous vs background dispatch is **[TBD]** in Part 2.                                   |
| Q-19 | WhatsApp provider and business account?                                                                                                                                                                                                                                                                      | **[TBD]** — decided before Part 2.                                                                    |
| Q-20 | "Accepted by provider" sufficient, or delivery/read receipts needed?                                                                                                                                                                                                                                         | Accepted is sufficient.                                                                               |
| Q-21 | "Retry failed" action?                                                                                                                                                                                                                                                                                       | No. Non-data failures are marked ERRORED for manual send and never retried.                           |
| Q-22 | Expected workbook size?                                                                                                                                                                                                                                                                                      | ≤ 6 sheets, ≤ 500 rows.                                                                               |
| Q-23 | Other line-item types? Negative amounts?                                                                                                                                                                                                                                                                     | No; amounts must be positive.                                                                         |
| Q-24 | Bureau Charges?                                                                                                                                                                                                                                                                                              | Removed as a concept. Charges are included in the helper rate before upload.                          |
| Q-25 | Are dispatch failures split into two kinds? Proposed: failures caused by bill data (e.g. invalid phone number rejected by WhatsApp) become `FAILED` and are re-sent after a corrected re-upload. Failures not caused by data (network/provider) become `ERRORED`, need a manual send, and are never re-sent. | Confirmed.                                                                                            |                                                                                           |

## 13. Proposed Delivery Increments (for discussion)

1. Project skeleton: Maven, Spring Boot 4, Thymeleaf layout, PostgreSQL connection via environment configuration, health page.
2. Upload page + sheet selection (FR-1, FR-2).
3. Sheet parsing + structure validation (FR-3) with tests on the sample and broken variants.
4. Line-item and bill validation, grouping, totals (FR-4 to FR-6).
5. Billing Summary page (FR-7). **Part 1 complete.**
6. Persistence of batches, bills and line items + approval with confirmation (FR-8, FR-9).
7. WhatsApp integration behind a small sender interface, with a fake sender for tests (FR-10).
8. Dispatch status tracking and status display (FR-11, FR-12). **Part 2 complete.**
