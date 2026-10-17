package in.chintamani.service;

import in.chintamani.dto.BillingColumn;
import in.chintamani.dto.CellValue;
import in.chintamani.dto.RawBillingSheet;
import in.chintamani.dto.ValidationIssue;
import in.chintamani.dto.ValidationResult;
import in.chintamani.dto.ValidationRule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Applies the sheet-level rules S-02 to S-07 (requirements 7.1) to a sheet already read into plain
 * records. All findings are collected together, except where a rule depends on another having passed.
 */
@Service
public class BillingSheetStructureValidator {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static final String START_LABEL = "Billing Start Date";
    private static final String END_LABEL = "Billing End Date";
    private static final int HEADER_ROW = 6;
    private static final int FIRST_DATA_ROW = 7;

    public ValidationResult validate(RawBillingSheet sheet) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (sheet.empty()) {  // S-02: nothing else would add information
            issues.add(ValidationIssue.at(ValidationRule.S_02, null, null, sheet.sheetName()));
            return new ValidationResult(issues);
        }

        checkLabel(issues, sheet.startLabel(), 1, START_LABEL);
        checkLabel(issues, sheet.endLabel(), 3, END_LABEL);

        Optional<LocalDate> start = checkDate(issues, sheet.startDate(), 2, START_LABEL);
        Optional<LocalDate> end = checkDate(issues, sheet.endDate(), 4, END_LABEL);
        if (start.isPresent() && end.isPresent() && start.get().isAfter(end.get())) {  // S-05
            issues.add(ValidationIssue.at(ValidationRule.S_05, 2, "A",
                    start.get().format(DISPLAY_DATE), end.get().format(DISPLAY_DATE)));
        }

        boolean headersOk = checkHeaders(issues, sheet.headers());
        if (headersOk && sheet.rows().isEmpty()) {  // S-07
            issues.add(ValidationIssue.at(ValidationRule.S_07, FIRST_DATA_ROW, null));
        }
        return new ValidationResult(issues);
    }

    /** S-03: the label in column A must match, ignoring case and extra spaces. */
    private void checkLabel(List<ValidationIssue> issues, CellValue cell, int row, String expected) {
        if (!BillingColumn.normalize(expected).equals(BillingColumn.normalize(cell.text()))) {
            issues.add(ValidationIssue.at(ValidationRule.S_03, row, "A", "A" + row, expected, describe(cell)));
        }
    }

    /** S-04: an Excel date, or text exactly in dd-MM-yyyy form. */
    private Optional<LocalDate> checkDate(List<ValidationIssue> issues, CellValue cell, int row, String label) {
        Optional<LocalDate> date = cell.asDate();
        if (date.isEmpty()) {
            issues.add(ValidationIssue.at(ValidationRule.S_04, row, "A", "A" + row, label, describe(cell)));
        }
        return date;
    }

    /** S-06: every required header appears on row 6 exactly once. Returns true if none is missing or repeated. */
    private boolean checkHeaders(List<ValidationIssue> issues, List<CellValue> headers) {
        boolean ok = true;
        for (BillingColumn column : BillingColumn.values()) {
            List<String> letters = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                if (column.matches(headers.get(i).text())) {
                    letters.add(String.valueOf((char) ('A' + i)));
                }
            }
            if (letters.size() != 1) {
                ok = false;
                String problem = letters.isEmpty()
                        ? "is missing"
                        : "appears more than once (columns " + String.join(", ", letters) + ")";
                issues.add(ValidationIssue.at(ValidationRule.S_06, HEADER_ROW, null, column.header(), problem));
            }
        }
        return ok;
    }

    private String describe(CellValue cell) {
        return switch (cell.type()) {
            case BLANK -> "nothing";
            case TEXT, ERROR -> "'" + cell.text() + "'";
            case NUMBER -> "'" + cell.number().toPlainString() + "'";
            case DATE -> "'" + cell.date().format(DISPLAY_DATE) + "'";
        };
    }
}
