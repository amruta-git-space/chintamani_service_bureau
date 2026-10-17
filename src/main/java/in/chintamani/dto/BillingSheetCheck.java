package in.chintamani.dto;

import java.time.LocalDate;

/**
 * The outcome of checking one billing sheet. The period dates and line item count are only
 * meaningful when the result has no blocking errors.
 */
public record BillingSheetCheck(RawBillingSheet sheet, ValidationResult result) {

    public boolean blocked() {
        return result.hasBlockingErrors();
    }

    public LocalDate startDate() {
        return sheet.startDate().asDate().orElseThrow();
    }

    public LocalDate endDate() {
        return sheet.endDate().asDate().orElseThrow();
    }

    public int lineItemCount() {
        return sheet.rows().size();
    }
}
