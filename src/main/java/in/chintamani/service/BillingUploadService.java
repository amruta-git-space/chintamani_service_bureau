package in.chintamani.service;

import in.chintamani.dto.UploadedBillingWorkbook;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BillingUploadService {

    static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    public UploadedBillingWorkbook readWorkbook(MultipartFile file) {
        if (file == null || isBlank(file.getOriginalFilename())) {
            throw new InvalidBillingWorkbookException("Please choose an Excel file before clicking Upload.");
        }
        if (file.isEmpty()) {
            throw new InvalidBillingWorkbookException("The file you chose is empty. Please choose the correct file.");
        }

        String fileName = stripPath(file.getOriginalFilename());
        validateExtension(fileName);

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidBillingWorkbookException("This file is larger than 5 MB. Please upload a smaller file.");
        }

        byte[] content = readBytes(file);
        return new UploadedBillingWorkbook(fileName, content, readVisibleSheetNames(content));
    }

    private void validateExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return;
        }
        int dot = fileName.lastIndexOf('.');
        String chosen = dot >= 0 && dot < fileName.length() - 1
                ? " You chose a " + fileName.substring(dot) + " file."
                : "";
        throw new InvalidBillingWorkbookException("Only Excel files (.xlsx or .xls) can be uploaded." + chosen);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidBillingWorkbookException("This file could not be read. Please try uploading it again.");
        }
    }

    private List<String> readVisibleSheetNames(byte[] content) {
        List<String> names = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                if (workbook.getSheetVisibility(i) == SheetVisibility.VISIBLE) {
                    names.add(workbook.getSheetName(i));
                }
            }
        } catch (EncryptedDocumentException e) {
            throw new InvalidBillingWorkbookException("This file is password-protected. "
                    + "Please remove the password in Excel and upload it again.");
        } catch (IOException | RuntimeException e) {
            throw new InvalidBillingWorkbookException(
                    "This file could not be opened as an Excel workbook. Please check the file and try again.");
        }

        if (names.isEmpty()) {
            throw new InvalidBillingWorkbookException("This workbook has no visible sheets. "
                    + "Please unhide the billing sheet in Excel and upload again.");
        }
        return names;
    }

    private String stripPath(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
