package in.chintamani.service;

import in.chintamani.dto.BillingSheetCheck;
import in.chintamani.dto.RawBillingSheet;
import in.chintamani.dto.UploadedBillingWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Entry point for checking the sheet an operator selected: reads it, then applies the validators.
 */
@Service
public class BillingSheetService {

    private final BillingSheetReader reader;
    private final BillingSheetStructureValidator structureValidator;

    public BillingSheetService(BillingSheetReader reader, BillingSheetStructureValidator structureValidator) {
        this.reader = reader;
        this.structureValidator = structureValidator;
    }

    public BillingSheetCheck check(UploadedBillingWorkbook uploaded, String sheetName) {
        // The bytes were already opened successfully at upload, so a failure here is unexpected.
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(uploaded.content()))) {
            RawBillingSheet sheet = reader.read(workbook, sheetName);
            return new BillingSheetCheck(sheet, structureValidator.validate(sheet));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not reopen the uploaded workbook " + uploaded.fileName(), e);
        }
    }
}
