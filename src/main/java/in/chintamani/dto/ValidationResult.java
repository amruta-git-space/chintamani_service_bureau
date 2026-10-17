package in.chintamani.dto;

import java.util.List;

public record ValidationResult(List<ValidationIssue> issues) {

    public ValidationResult {
        issues = List.copyOf(issues);
    }

    /** Errors that stop the whole sheet from being processed. */
    public List<ValidationIssue> blockingErrors() {
        return issues.stream().filter(issue -> issue.rule().blocksSheet()).toList();
    }

    public boolean hasBlockingErrors() {
        return issues.stream().anyMatch(issue -> issue.rule().blocksSheet());
    }
}
