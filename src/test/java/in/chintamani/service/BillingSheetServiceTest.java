package in.chintamani.service;

import in.chintamani.dto.BillingSheetCheck;
import in.chintamani.dto.UploadedBillingWorkbook;
import in.chintamani.dto.ValidationIssue;
import in.chintamani.dto.ValidationRule;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillingSheetServiceTest {

    private final BillingSheetService service =
            new BillingSheetService(new BillingSheetReader(), new BillingSheetStructureValidator());

    private static final String SHEET = "Jul 15-30";

    private Workbook workbook;
    private Sheet sheet;

    private void startWithValidSheet() {
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet(SHEET);
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd-mmm-yyyy"));

        row(0).createCell(0).setCellValue("Billing Start Date");
        row(1).createCell(0).setCellValue(LocalDate.of(2026, 7, 15));
        row(1).getCell(0).setCellStyle(dateStyle);
        row(2).createCell(0).setCellValue("Billing End Date");
        row(3).createCell(0).setCellValue(LocalDate.of(2026, 7, 30));
        row(3).getCell(0).setCellStyle(dateStyle);

        String[] headers = {"Sr. No", "Billing Reference", "Client Name", "Client Phone", "Send Bill",
                "Helper Name", "Shift", "Day", "Rate", "Total Amount"};
        for (int i = 0; i < headers.length; i++) {
            row(5).createCell(i).setCellValue(headers[i]);
        }
        dataRow(6, 1, "20260730-0001");
        dataRow(7, 2, "20260730-0002");
    }

    private Row row(int index) {
        Row row = sheet.getRow(index);
        return row != null ? row : sheet.createRow(index);
    }

    private void dataRow(int index, int srNo, String reference) {
        Row row = row(index);
        row.createCell(0).setCellValue(srNo);
        row.createCell(1).setCellValue(reference);
        row.createCell(2).setCellValue("Shri Khandwe");
        row.createCell(3).setCellValue("9800000000");
        row.createCell(4).setCellValue("Yes");
        row.createCell(5).setCellValue("Sharada");
        row.createCell(6).setCellValue("Night");
        row.createCell(7).setCellValue(15);
        row.createCell(8).setCellValue(700);
        row.createCell(9).setCellValue(10500);
    }

    private BillingSheetCheck check(String sheetName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return service.check(new UploadedBillingWorkbook("Bill.xlsx", out.toByteArray(), List.of(sheetName)), sheetName);
    }

    private List<ValidationRule> rules(BillingSheetCheck check) {
        return check.result().issues().stream().map(ValidationIssue::rule).toList();
    }

    @Test
    void validSheetPassesAndExposesPeriodAndLineItemCount() throws IOException {
        startWithValidSheet();

        BillingSheetCheck check = check(SHEET);

        assertThat(check.blocked()).isFalse();
        assertThat(check.startDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(check.endDate()).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(check.lineItemCount()).isEqualTo(2);
    }

    @Test
    void textDatesInDdMmYyyyAreAccepted() throws IOException {
        startWithValidSheet();
        row(1).getCell(0).setCellValue("15-07-2026");
        row(3).getCell(0).setCellValue("30-07-2026");

        BillingSheetCheck check = check(SHEET);

        assertThat(check.blocked()).isFalse();
        assertThat(check.startDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void wrongLabelIsBlocking() throws IOException {
        startWithValidSheet();
        row(0).getCell(0).setCellValue("Start Date");

        BillingSheetCheck check = check(SHEET);

        assertThat(check.blocked()).isTrue();
        assertThat(rules(check)).containsExactly(ValidationRule.S_03);
    }

    @Test
    void badDateIsBlocking() throws IOException {
        startWithValidSheet();
        row(3).getCell(0).setCellValue("30-Jul-2026");

        assertThat(rules(check(SHEET))).containsExactly(ValidationRule.S_04);
    }

    @Test
    void endBeforeStartIsBlocking() throws IOException {
        startWithValidSheet();
        row(3).getCell(0).setCellValue("01-07-2026");

        assertThat(rules(check(SHEET))).containsExactly(ValidationRule.S_05);
    }

    @Test
    void missingHeaderIsBlocking() throws IOException {
        startWithValidSheet();
        row(5).getCell(5).setCellValue("Description");

        BillingSheetCheck check = check(SHEET);

        assertThat(rules(check)).containsExactly(ValidationRule.S_06);
        assertThat(check.result().issues().get(0).message()).isEqualTo("The column 'Helper Name' is missing.");
    }

    @Test
    void sheetWithoutDataRowsIsBlocking() throws IOException {
        startWithValidSheet();
        sheet.removeRow(row(6));
        sheet.removeRow(row(7));

        assertThat(rules(check(SHEET))).containsExactly(ValidationRule.S_07);
    }

    @Test
    void emptySheetIsBlocking() throws IOException {
        workbook = new XSSFWorkbook();
        workbook.createSheet(SHEET);

        assertThat(rules(check(SHEET))).containsExactly(ValidationRule.S_02);
    }

    @Test
    void sampleWorkbookJulySheetHasCorrectStructure() throws IOException {
        Path sample = Path.of("src/test/resources/Bill_Q2_2026-27.xlsx");
        UploadedBillingWorkbook uploaded = new UploadedBillingWorkbook(
                "Bill_Q2_2026-27.xlsx", Files.readAllBytes(sample), List.of(SHEET));

        BillingSheetCheck check = service.check(uploaded, SHEET);

        assertThat(check.result().issues()).isEmpty();
        assertThat(check.startDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(check.endDate()).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(check.lineItemCount()).isEqualTo(4);
    }
}
