package in.chintamani.service;

import in.chintamani.dto.UploadedBillingWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingUploadServiceTest {

    private final BillingUploadService service = new BillingUploadService();

    @Test
    void readsSheetNamesFromXlsx() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("Jul 1-15");
        workbook.createSheet("Jul 15-30");

        UploadedBillingWorkbook result = service.readWorkbook(upload("Bill.xlsx", toBytes(workbook)));

        assertThat(result.fileName()).isEqualTo("Bill.xlsx");
        assertThat(result.sheetNames()).containsExactly("Jul 1-15", "Jul 15-30");
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void readsSheetNamesFromXls() throws IOException {
        Workbook workbook = new HSSFWorkbook();
        workbook.createSheet("Jul 15-30");

        UploadedBillingWorkbook result = service.readWorkbook(upload("Bill.XLS", toBytes(workbook)));

        assertThat(result.sheetNames()).containsExactly("Jul 15-30");
    }

    @Test
    void excludesHiddenAndVeryHiddenSheets() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("Visible");
        workbook.createSheet("Hidden");
        workbook.createSheet("Very hidden");
        workbook.setSheetVisibility(1, SheetVisibility.HIDDEN);
        workbook.setSheetVisibility(2, SheetVisibility.VERY_HIDDEN);

        UploadedBillingWorkbook result = service.readWorkbook(upload("Bill.xlsx", toBytes(workbook)));

        assertThat(result.sheetNames()).containsExactly("Visible");
    }

    @Test
    void rejectsWorkbookWithNoVisibleSheets() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("First");
        workbook.createSheet("Second");
        workbook.setActiveSheet(1);
        workbook.setSheetVisibility(0, SheetVisibility.HIDDEN);
        workbook.setSheetVisibility(1, SheetVisibility.VERY_HIDDEN);

        assertRejected(upload("Bill.xlsx", toBytes(workbook)), "no visible sheets");
    }

    @Test
    void stripsPathFromFileName() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("Jul 15-30");

        UploadedBillingWorkbook result = service.readWorkbook(
                upload("C:\\Users\\me\\Documents\\Bill.xlsx", toBytes(workbook)));

        assertThat(result.fileName()).isEqualTo("Bill.xlsx");
    }

    @Test
    void rejectsMissingFile() {
        assertRejected(null, "choose an Excel file");
        assertRejected(new MockMultipartFile("file", "", "application/octet-stream", new byte[0]),
                "choose an Excel file");
    }

    @Test
    void rejectsEmptyFile() {
        assertRejected(upload("Bill.xlsx", new byte[0]), "is empty");
    }

    @Test
    void rejectsWrongFileType() {
        assertRejected(upload("Bill.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)),
                "Only Excel files", "You chose a .pdf file");
    }

    @Test
    void rejectsNonExcelContentWithExcelExtension() {
        assertRejected(upload("Bill.xlsx", "%PDF-1.4 not a workbook".getBytes(StandardCharsets.UTF_8)),
                "could not be opened as an Excel workbook");
    }

    @Test
    void rejectsOversizedFile() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];

        assertRejected(upload("Bill.xlsx", tooBig), "larger than 5 MB");
    }

    @Test
    void rejectsPasswordProtectedWorkbook() throws Exception {
        assertRejected(upload("Bill.xlsx", passwordProtectedXlsx()), "password-protected");
    }

    @Test
    void readsSampleWorkbook() throws IOException {
        Path sample = Path.of("src/test/resources/Bill_Q2_2026-27.xlsx");

        UploadedBillingWorkbook result = service.readWorkbook(
                upload("Bill_Q2_2026-27.xlsx", Files.readAllBytes(sample)));

        assertThat(result.sheetNames()).contains("Jul 15-30");
    }

    private void assertRejected(MultipartFile file, String... messageParts) {
        assertThatThrownBy(() -> service.readWorkbook(file))
                .isInstanceOf(InvalidBillingWorkbookException.class)
                .satisfies(e -> {
                    for (String part : messageParts) {
                        assertThat(e.getMessage()).contains(part);
                    }
                });
    }

    private MockMultipartFile upload(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName, "application/octet-stream", content);
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] passwordProtectedXlsx() throws Exception {
        try (POIFSFileSystem fs = new POIFSFileSystem();
             Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Jul 15-30");
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword("secret");
            try (OutputStream os = encryptor.getDataStream(fs)) {
                workbook.write(os);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }
}
