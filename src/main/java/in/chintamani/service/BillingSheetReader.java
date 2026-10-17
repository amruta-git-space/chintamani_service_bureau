package in.chintamani.service;

import in.chintamani.dto.CellValue;
import in.chintamani.dto.RawBillingSheet;
import in.chintamani.dto.RawRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies a billing sheet out of Apache POI into plain records. It applies no rules: judging the
 * content is the job of the validators. Layout follows requirements 5.2.
 */
@Service
public class BillingSheetReader {

    private static final int COLUMN_COUNT = 10;         // columns A to J
    private static final int HEADER_ROW_INDEX = 5;      // Excel row 6
    private static final int FIRST_DATA_ROW_INDEX = 6;  // Excel row 7

    public RawBillingSheet read(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null || hasNoContent(sheet)) {
            return RawBillingSheet.empty(sheetName);
        }
        return new RawBillingSheet(sheetName, false,
                cellAt(sheet, 0, 0), cellAt(sheet, 1, 0),
                cellAt(sheet, 2, 0), cellAt(sheet, 3, 0),
                readColumns(sheet, HEADER_ROW_INDEX), readDataRows(sheet));
    }

    private boolean hasNoContent(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (!toCellValue(cell).isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<RawRow> readDataRows(Sheet sheet) {
        List<RawRow> rows = new ArrayList<>();
        for (int index = FIRST_DATA_ROW_INDEX; index <= sheet.getLastRowNum(); index++) {
            List<CellValue> cells = readColumns(sheet, index);
            if (cells.stream().allMatch(CellValue::isBlank)) {
                break;  // the first fully blank row ends the data (Q-04)
            }
            rows.add(new RawRow(index + 1, cells));
        }
        return rows;
    }

    private List<CellValue> readColumns(Sheet sheet, int rowIndex) {
        List<CellValue> cells = new ArrayList<>();
        for (int column = 0; column < COLUMN_COUNT; column++) {
            cells.add(cellAt(sheet, rowIndex, column));
        }
        return cells;
    }

    private CellValue cellAt(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(columnIndex);
        return cell == null ? CellValue.blank() : toCellValue(cell);
    }

    private CellValue toCellValue(Cell cell) {
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> CellValue.text(cell.getStringCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? CellValue.date(cell.getLocalDateTimeCellValue().toLocalDate())
                    : CellValue.number(new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue())));
            case BOOLEAN -> CellValue.text(cell.getBooleanCellValue() ? "TRUE" : "FALSE");
            case ERROR -> CellValue.error(FormulaError.forInt(cell.getErrorCellValue()).getString());
            default -> CellValue.blank();
        };
    }
}
