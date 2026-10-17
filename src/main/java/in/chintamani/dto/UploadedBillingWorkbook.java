package in.chintamani.dto;

import java.util.List;

/**
 * An uploaded billing workbook held in the HTTP session while the operator picks a sheet.
 * Only visible sheets are listed, in workbook order.
 */
public record UploadedBillingWorkbook(String fileName, byte[] content, List<String> sheetNames) {
}
