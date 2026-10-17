package in.chintamani.dto;

import java.util.List;

/**
 * A data row of a billing sheet as read from Excel.
 *
 * @param excelRow the row number as seen in Excel (first data row is 7)
 * @param cells    columns A to J, in order
 */
public record RawRow(int excelRow, List<CellValue> cells) {

    public RawRow {
        cells = List.copyOf(cells);
    }
}
