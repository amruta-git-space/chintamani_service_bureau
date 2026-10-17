package in.chintamani.service;

import in.chintamani.dto.BillingColumn;
import in.chintamani.dto.CellValue;
import in.chintamani.dto.RawBillingSheet;
import in.chintamani.dto.RawRow;
import in.chintamani.dto.ValidationIssue;
import in.chintamani.dto.ValidationResult;
import in.chintamani.dto.ValidationRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillingSheetStructureValidatorTest {

    private final BillingSheetStructureValidator validator = new BillingSheetStructureValidator();

    private static final CellValue START_LABEL = CellValue.text("Billing Start Date");
    private static final CellValue END_LABEL = CellValue.text("Billing End Date");
    private static final CellValue JUL_15 = CellValue.date(LocalDate.of(2026, 7, 15));
    private static final CellValue JUL_30 = CellValue.date(LocalDate.of(2026, 7, 30));

    private static List<CellValue> validHeaders() {
        return Arrays.stream(BillingColumn.values()).map(c -> CellValue.text(c.header())).toList();
    }

    private static List<RawRow> oneRow() {
        return List.of(new RawRow(7, List.of(CellValue.number(BigDecimal.ONE))));
    }

    private static RawBillingSheet sheet(CellValue startLabel, CellValue startDate, CellValue endLabel,
                                         CellValue endDate, List<CellValue> headers, List<RawRow> rows) {
        return new RawBillingSheet("Jul 15-30", false, startLabel, startDate, endLabel, endDate, headers, rows);
    }

    private static RawBillingSheet validSheet() {
        return sheet(START_LABEL, JUL_15, END_LABEL, JUL_30, validHeaders(), oneRow());
    }

    private static RawBillingSheet withStartDate(CellValue startDate) {
        return sheet(START_LABEL, startDate, END_LABEL, JUL_30, validHeaders(), oneRow());
    }

    private static RawBillingSheet withHeaders(List<CellValue> headers) {
        return sheet(START_LABEL, JUL_15, END_LABEL, JUL_30, headers, oneRow());
    }

    private List<ValidationRule> rulesOf(ValidationResult result) {
        return result.issues().stream().map(ValidationIssue::rule).toList();
    }

    @Test
    void validSheetHasNoIssues() {
        assertThat(validator.validate(validSheet()).issues()).isEmpty();
    }

    @Test
    void emptySheetReportsOnlyS02() {
        ValidationResult result = validator.validate(RawBillingSheet.empty("Jul 15-30"));

        assertThat(rulesOf(result)).containsExactly(ValidationRule.S_02);
        assertThat(result.issues().get(0).message()).isEqualTo("The sheet 'Jul 15-30' is empty or could not be found.");
        assertThat(result.hasBlockingErrors()).isTrue();
    }

    @Test
    void s03WrongLabelPointsToTheCell() {
        RawBillingSheet sheet = sheet(CellValue.text("Start Date"), JUL_15, END_LABEL, JUL_30, validHeaders(), oneRow());

        ValidationResult result = validator.validate(sheet);

        assertThat(rulesOf(result)).containsExactly(ValidationRule.S_03);
        assertThat(result.issues().get(0).location()).isEqualTo("Cell A1");
        assertThat(result.issues().get(0).message())
                .isEqualTo("Cell A1 must say 'Billing Start Date' but it contains 'Start Date'.");
    }

    @Test
    void s03MissingEndLabelSaysNothing() {
        RawBillingSheet sheet = sheet(START_LABEL, JUL_15, CellValue.blank(), JUL_30, validHeaders(), oneRow());

        ValidationResult result = validator.validate(sheet);

        assertThat(result.issues().get(0).location()).isEqualTo("Cell A3");
        assertThat(result.issues().get(0).message()).endsWith("but it contains nothing.");
    }

    @Test
    void s03IgnoresCaseAndExtraSpaces() {
        RawBillingSheet sheet = sheet(CellValue.text("  billing   START date "), JUL_15, END_LABEL, JUL_30,
                validHeaders(), oneRow());

        assertThat(validator.validate(sheet).issues()).isEmpty();
    }

    @Test
    void s04AcceptsExcelDatesAndStrictDdMmYyyyText() {
        assertThat(validator.validate(withStartDate(JUL_15)).issues()).isEmpty();
        assertThat(validator.validate(withStartDate(CellValue.text("15-07-2026"))).issues()).isEmpty();
        assertThat(validator.validate(withStartDate(CellValue.text(" 15-07-2026 "))).issues()).isEmpty();
    }

    @Test
    void s04RejectsOtherFormsAndNonDates() {
        List<CellValue> bad = List.of(CellValue.text("31-02-2026"), CellValue.text("1-7-2026"),
                CellValue.text("15-Jul-2026"), CellValue.text("2026-07-15"), CellValue.text("15 July"),
                CellValue.number(new BigDecimal("46218")), CellValue.blank(), CellValue.error("#REF!"));

        for (CellValue cell : bad) {
            ValidationResult result = validator.validate(withStartDate(cell));

            assertThat(rulesOf(result)).as(String.valueOf(cell)).containsExactly(ValidationRule.S_04);
            assertThat(result.issues().get(0).location()).isEqualTo("Cell A2");
        }
    }

    @Test
    void s04MessageShowsWhatWasFound() {
        ValidationResult result = validator.validate(withStartDate(CellValue.text("15 July")));

        assertThat(result.issues().get(0).message()).isEqualTo(
                "Cell A2 must contain the Billing Start Date as a date (for example 15-07-2026). Found: '15 July'.");
    }

    @Test
    void s05StartAfterEndIsReported() {
        RawBillingSheet sheet = sheet(START_LABEL, CellValue.date(LocalDate.of(2026, 8, 15)), END_LABEL, JUL_30,
                validHeaders(), oneRow());

        ValidationResult result = validator.validate(sheet);

        assertThat(rulesOf(result)).containsExactly(ValidationRule.S_05);
        assertThat(result.issues().get(0).message())
                .isEqualTo("Billing Start Date (15-Aug-2026) is after Billing End Date (30-Jul-2026).");
    }

    @Test
    void s05AllowsSameDayAndIsSkippedWhenADateIsInvalid() {
        RawBillingSheet sameDay = sheet(START_LABEL, JUL_30, END_LABEL, JUL_30, validHeaders(), oneRow());
        assertThat(validator.validate(sameDay).issues()).isEmpty();

        RawBillingSheet badStart = withStartDate(CellValue.text("rubbish"));
        assertThat(rulesOf(validator.validate(badStart))).containsExactly(ValidationRule.S_04);
    }

    @Test
    void s06MissingHeaderIsReported() {
        List<CellValue> headers = new ArrayList<>(validHeaders());
        headers.set(BillingColumn.RATE.ordinal(), CellValue.text("Description"));

        ValidationResult result = validator.validate(withHeaders(headers));

        assertThat(rulesOf(result)).containsExactly(ValidationRule.S_06);
        assertThat(result.issues().get(0).location()).isEqualTo("Row 6");
        assertThat(result.issues().get(0).message()).isEqualTo("The column 'Rate' is missing.");
    }

    @Test
    void s06DuplicateHeaderListsTheColumns() {
        List<CellValue> headers = new ArrayList<>(validHeaders());
        headers.set(BillingColumn.TOTAL_AMOUNT.ordinal(), CellValue.text("rate"));

        ValidationResult result = validator.validate(withHeaders(headers));

        assertThat(rulesOf(result)).containsExactly(ValidationRule.S_06, ValidationRule.S_06);
        assertThat(result.issues()).extracting(ValidationIssue::message).containsExactly(
                "The column 'Rate' appears more than once (columns I, J).",
                "The column 'Total Amount' is missing.");
    }

    @Test
    void s06HeadersMayBeInAnyOrderAndIgnoreCaseAndSpaces() {
        List<CellValue> headers = new ArrayList<>(validHeaders());
        headers.set(0, CellValue.text(" SHIFT "));
        headers.set(BillingColumn.SHIFT.ordinal(), CellValue.text("sr.  no"));

        assertThat(validator.validate(withHeaders(headers)).issues()).isEmpty();
    }

    @Test
    void s07NoDataRowsIsReported() {
        RawBillingSheet sheet = sheet(START_LABEL, JUL_15, END_LABEL, JUL_30, validHeaders(), List.of());

        ValidationResult result = validator.validate(sheet);

        assertThat(rulesOf(result)).containsExactly(ValidationRule.S_07);
        assertThat(result.issues().get(0).location()).isEqualTo("Row 7");
    }

    @Test
    void s07IsSkippedWhenHeadersAreBroken() {
        List<CellValue> headers = new ArrayList<>(validHeaders());
        headers.set(BillingColumn.RATE.ordinal(), CellValue.blank());
        RawBillingSheet sheet = sheet(START_LABEL, JUL_15, END_LABEL, JUL_30, headers, List.of());

        assertThat(rulesOf(validator.validate(sheet))).containsExactly(ValidationRule.S_06);
    }

    @Test
    void independentProblemsAreReportedTogether() {
        RawBillingSheet sheet = sheet(CellValue.text("Start Date"), CellValue.text("rubbish"), END_LABEL, JUL_30,
                List.of(), oneRow());

        ValidationResult result = validator.validate(sheet);

        assertThat(rulesOf(result).stream().distinct()).containsExactly(
                ValidationRule.S_03, ValidationRule.S_04, ValidationRule.S_06);
        assertThat(result.issues()).hasSize(12);
        assertThat(result.hasBlockingErrors()).isTrue();
    }
}
