package in.chintamani.dto;

/**
 * Catalogue of billing validation rules (requirements section 7). Each rule's id, scope, severity and
 * wording are defined here only. Whether a failed rule blocks the sheet or invalidates a bill follows
 * from scope and severity, so it is derived rather than stored.
 */
public enum ValidationRule {

    // 7.1 Structure rules (S-01, the file check, is enforced at upload by BillingUploadService)
    S_02("S-02", Scope.SHEET, Severity.ERROR,
            "The sheet '%s' is empty or could not be found."),
    S_03("S-03", Scope.SHEET, Severity.ERROR,
            "Cell %s must say '%s' but it contains %s."),
    S_04("S-04", Scope.SHEET, Severity.ERROR,
            "Cell %s must contain the %s as a date (for example 15-07-2026). Found: %s."),
    S_05("S-05", Scope.SHEET, Severity.ERROR,
            "Billing Start Date (%s) is after Billing End Date (%s)."),
    S_06("S-06", Scope.SHEET, Severity.ERROR,
            "The column '%s' %s."),
    S_07("S-07", Scope.SHEET, Severity.ERROR,
            "No billing rows were found. Line items must start on row 7, directly below the column headings.");

    /** What the rule looks at. */
    public enum Scope { SHEET, ROW, BILL }

    public enum Severity { ERROR, WARNING }

    private final String id;
    private final Scope scope;
    private final Severity severity;
    private final String messageTemplate;

    ValidationRule(String id, Scope scope, Severity severity, String messageTemplate) {
        this.id = id;
        this.scope = scope;
        this.severity = severity;
        this.messageTemplate = messageTemplate;
    }

    public String id() {
        return id;
    }

    public Scope scope() {
        return scope;
    }

    public Severity severity() {
        return severity;
    }

    /** A failed sheet-level error stops processing: no bills are shown. */
    public boolean blocksSheet() {
        return scope == Scope.SHEET && severity == Severity.ERROR;
    }

    /** A failed row- or bill-level error marks the affected bill INVALID; other bills carry on. */
    public boolean invalidatesBill() {
        return severity == Severity.ERROR && (scope == Scope.ROW || scope == Scope.BILL);
    }

    public String message(Object... args) {
        return messageTemplate.formatted(args);
    }
}
