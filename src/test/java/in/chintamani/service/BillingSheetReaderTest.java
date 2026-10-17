package in.chintamani.service;

import in.chintamani.dto.CellValue;
import in.chintamani.dto.RawBillingSheet;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BillingSheetReaderTest {

    private final BillingSheetReader reader = new BillingSheetReader();

    private final Workbook workbook = new XSSFWorkbook();
    private final Sheet sheet = workbook.createSheet("Jul 15-30");

    private Row row(int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private void text(int rowIndex, int column, String value) {
        row(rowIndex).createCell(column).setCellValue(value);
    }

    private void number(int rowIndex, int column, double value) {
        row(rowIndex).createCell(column).setCellValue(value);
    }

    private void dateCell(int rowIndex, int column, LocalDate date) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd-mmm-yyyy"));
        row(rowIndex).createCell(column).setCellValue(date);
        row(rowIndex).getCell(column).setCellStyle(style);
    }

    private void headers() {
        String[] names = {"Sr. No", "Billing Reference", "Client Name", "Client Phone", "Send Bill",
                "Helper Name", "Shift", "Day", "Rate", "Total Amount"};
        for (int i = 0; i < names.length; i++) {
            text(5, i, names[i]);
        }
    }

    private void dataRow(int rowIndex, double srNo, String reference) {
        number(rowIndex, 0, srNo);
        text(rowIndex, 1, reference);
        text(rowIndex, 2, "Shri Khandwe");
        text(rowIndex, 3, "9800000000");
        text(rowIndex, 4, "Yes");
        text(rowIndex, 5, "Sharada");
        text(rowIndex, 6, "Night");
        number(rowIndex, 7, 15);
        number(rowIndex, 8, 700);
    }

    @Test
    void readsLabelsAndDateCells() {
        text(0, 0, "Billing Start Date");
        dateCell(1, 0, LocalDate.of(2026, 7, 15));
        text(2, 0, "Billing End Date");
        dateCell(3, 0, LocalDate.of(2026, 7, 30));

        RawBillingSheet raw = reader.read(workbook, "Jul 15-30");

        assertThat(raw.empty()).isFalse();
        assertThat(raw.startLabel()).isEqualTo(CellValue.text("Billing Start Date"));
        assertThat(raw.startDate()).isEqualTo(CellValue.date(LocalDate.of(2026, 7, 15)));
        assertThat(raw.endLabel()).isEqualTo(CellValue.text("Billing End Date"));
        assertThat(raw.endDate()).isEqualTo(CellValue.date(LocalDate.of(2026, 7, 30)));
    }

    @Test
    void textDatesAreLeftAsTextForTheValidatorToJudge() {
        text(0, 0, "Billing Start Date");
        text(1, 0, "15-07-2026");

        assertThat(reader.read(workbook, "Jul 15-30").startDate()).isEqualTo(CellValue.text("15-07-2026"));
    }

    @Test
    void readsHeadersAndDataRowsWithExcelRowNumbers() {
        text(0, 0, "Billing Start Date");
        headers();
        dataRow(6, 1, "20260730-0001");
        dataRow(7, 2, "20260730-0001");

        RawBillingSheet raw = reader.read(workbook, "Jul 15-30");

        assertThat(raw.headers()).hasSize(10);
        assertThat(raw.headers().get(8)).isEqualTo(CellValue.text("Rate"));
        assertThat(raw.rows()).extracting(r -> r.excelRow()).containsExactly(7, 8);
        assertThat(raw.rows().get(0).cells().get(1)).isEqualTo(CellValue.text("20260730-0001"));
        assertThat(raw.rows().get(0).cells().get(8).number()).isEqualByComparingTo("700");
    }

    @Test
    void dataEndsAtFirstBlankRow() {
        headers();
        dataRow(6, 1, "20260730-0001");
        dataRow(8, 2, "20260730-0002");

        assertThat(reader.read(workbook, "Jul 15-30").rows()).hasSize(1);
    }

    @Test
    void formattedButEmptyRowsAndColumnsAreIgnored() {
        headers();
        dataRow(6, 1, "20260730-0001");
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        for (int r = 7; r < 60; r++) {
            for (int c = 0; c < 23; c++) {
                row(r).createCell(c).setCellStyle(style);
            }
        }
        text(10, 15, "   ");

        RawBillingSheet raw = reader.read(workbook, "Jul 15-30");

        assertThat(raw.rows()).hasSize(1);
        assertThat(raw.headers()).hasSize(10);
    }

    @Test
    void formulaCellsReturnTheirCachedResultWithoutRecalculating() {
        headers();
        dataRow(6, 1, "20260730-0001");
        row(6).createCell(9).setCellFormula("H7*I7");
        workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
        number(6, 7, 20);

        CellValue total = reader.read(workbook, "Jul 15-30").rows().get(0).cells().get(9);

        assertThat(total.type()).isEqualTo(CellValue.Type.NUMBER);
        assertThat(total.number()).isEqualByComparingTo("10500");
    }

    @Test
    void errorCellsAreReportedAsErrors() {
        headers();
        dataRow(6, 1, "20260730-0001");
        row(6).createCell(9).setCellErrorValue(FormulaError.REF.getCode());

        CellValue total = reader.read(workbook, "Jul 15-30").rows().get(0).cells().get(9);

        assertThat(total).isEqualTo(CellValue.error("#REF!"));
    }

    @Test
    void missingOrEmptySheetIsReportedEmpty() {
        workbook.createSheet("Blank");

        assertThat(reader.read(workbook, "No such sheet").empty()).isTrue();
        assertThat(reader.read(workbook, "Blank").empty()).isTrue();
    }
}
