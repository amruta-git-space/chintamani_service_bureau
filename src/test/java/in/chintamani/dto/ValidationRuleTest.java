package in.chintamani.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRuleTest {

    @Test
    void structureErrorsBlockTheSheetAndDoNotInvalidateBills() {
        for (ValidationRule rule : ValidationRule.values()) {
            if (rule.scope() == ValidationRule.Scope.SHEET) {
                assertThat(rule.severity()).as(rule.id()).isEqualTo(ValidationRule.Severity.ERROR);
                assertThat(rule.blocksSheet()).as(rule.id()).isTrue();
                assertThat(rule.invalidatesBill()).as(rule.id()).isFalse();
            }
        }
    }

    @Test
    void ruleIdsAreUniqueAndFollowTheCatalogue() {
        assertThat(ValidationRule.values()).extracting(ValidationRule::id)
                .doesNotHaveDuplicates()
                .containsExactly("S-02", "S-03", "S-04", "S-05", "S-06", "S-07");
    }

    @Test
    void messageFillsInArguments() {
        assertThat(ValidationRule.S_03.message("A1", "Billing Start Date", "'Start Date'"))
                .isEqualTo("Cell A1 must say 'Billing Start Date' but it contains 'Start Date'.");
    }

    @Test
    void issueLocationPointsToCellOrRow() {
        assertThat(ValidationIssue.at(ValidationRule.S_03, 1, "A", "A1", "x", "y").location()).isEqualTo("Cell A1");
        assertThat(ValidationIssue.at(ValidationRule.S_06, 6, null, "Rate", "is missing").location()).isEqualTo("Row 6");
        assertThat(ValidationIssue.at(ValidationRule.S_02, null, null, "Jul").location()).isEqualTo("Sheet");
    }

    @Test
    void resultReportsBlockingErrors() {
        ValidationIssue issue = ValidationIssue.at(ValidationRule.S_07, 7, null);

        assertThat(new ValidationResult(List.of()).hasBlockingErrors()).isFalse();
        assertThat(new ValidationResult(List.of(issue)).hasBlockingErrors()).isTrue();
        assertThat(new ValidationResult(List.of(issue)).blockingErrors()).containsExactly(issue);
    }

    @Test
    void headerMatchingIgnoresCaseAndExtraSpaces() {
        assertThat(BillingColumn.SR_NO.matches("  sr.   NO ")).isTrue();
        assertThat(BillingColumn.RATE.matches("Rates")).isFalse();
        assertThat(BillingColumn.RATE.matches(null)).isFalse();
    }
}
