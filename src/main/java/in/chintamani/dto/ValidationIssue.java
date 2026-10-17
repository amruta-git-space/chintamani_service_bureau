package in.chintamani.dto;

/**
 * One finding from validating a billing sheet.
 *
 * @param rule             the rule that failed (gives scope and severity)
 * @param row              Excel row number, or null if not tied to a row
 * @param column           Excel column letter (e.g. "A"), or null if not tied to a column
 * @param billingReference the bill this belongs to, if known; null otherwise
 * @param message          plain-language text for the operator
 */
public record ValidationIssue(ValidationRule rule, Integer row, String column, String billingReference, String message) {

    /** An issue located at a row/column (either may be null) that is not tied to a bill. */
    public static ValidationIssue at(ValidationRule rule, Integer row, String column, Object... messageArgs) {
        return new ValidationIssue(rule, row, column, null, rule.message(messageArgs));
    }

    /** Where to look in Excel, e.g. "Cell A2" or "Row 6". */
    public String location() {
        if (row != null && column != null) {
            return "Cell " + column + row;
        }
        if (row != null) {
            return "Row " + row;
        }
        return "Sheet";
    }
}
