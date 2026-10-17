package in.chintamani.service;

/**
 * Thrown when an uploaded billing workbook is rejected. The message is written for the operator.
 */
public class InvalidBillingWorkbookException extends RuntimeException {

    public InvalidBillingWorkbookException(String message) {
        super(message);
    }
}
