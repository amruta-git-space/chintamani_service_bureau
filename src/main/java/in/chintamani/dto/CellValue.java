package in.chintamani.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

/**
 * One spreadsheet cell copied into plain Java, with no Apache POI types. Formula cells carry their
 * cached result; formulas are never evaluated by the application.
 */
public record CellValue(Type type, String text, BigDecimal number, LocalDate date) {

    public enum Type { BLANK, TEXT, NUMBER, DATE, ERROR }

    private static final CellValue BLANK = new CellValue(Type.BLANK, null, null, null);
    private static final DateTimeFormatter TEXT_DATE =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

    public static CellValue blank() {
        return BLANK;
    }

    /** Text that is empty or only spaces counts as blank. */
    public static CellValue text(String text) {
        return text == null || text.isBlank() ? BLANK : new CellValue(Type.TEXT, text, null, null);
    }

    public static CellValue number(BigDecimal number) {
        return new CellValue(Type.NUMBER, null, number, null);
    }

    public static CellValue date(LocalDate date) {
        return new CellValue(Type.DATE, null, null, date);
    }

    /** An Excel error value such as "#REF!". */
    public static CellValue error(String errorText) {
        return new CellValue(Type.ERROR, errorText, null, null);
    }

    public boolean isBlank() {
        return type == Type.BLANK;
    }

    /** The cell as a date: an Excel date, or text written exactly as dd-MM-yyyy. Otherwise empty. */
    public Optional<LocalDate> asDate() {
        if (type == Type.DATE) {
            return Optional.of(date);
        }
        if (type == Type.TEXT) {
            try {
                return Optional.of(LocalDate.parse(text.trim(), TEXT_DATE));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
