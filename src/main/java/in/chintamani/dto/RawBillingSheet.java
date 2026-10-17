package in.chintamani.dto;

import java.util.List;

/**
 * What was found on a billing sheet, before any rule is applied.
 *
 * @param sheetName  the sheet that was asked for
 * @param empty      true if the sheet is missing or has no non-blank cell
 * @param startLabel cell A1
 * @param startDate  cell A2
 * @param endLabel   cell A3
 * @param endDate    cell A4
 * @param headers    row 6, columns A to J
 * @param rows       data rows from row 7 down to, not including, the first row where A to J are all blank
 */
public record RawBillingSheet(String sheetName, boolean empty,
                              CellValue startLabel, CellValue startDate,
                              CellValue endLabel, CellValue endDate,
                              List<CellValue> headers, List<RawRow> rows) {

    public RawBillingSheet {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }

    public static RawBillingSheet empty(String sheetName) {
        CellValue blank = CellValue.blank();
        return new RawBillingSheet(sheetName, true, blank, blank, blank, blank, List.of(), List.of());
    }
}
