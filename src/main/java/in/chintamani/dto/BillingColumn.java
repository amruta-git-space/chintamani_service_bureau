package in.chintamani.dto;

import java.util.Locale;

/**
 * The required columns of a billing sheet (requirements 5.3), in their expected order.
 * Columns are identified by header text, not by position alone.
 */
public enum BillingColumn {
    SR_NO("Sr. No"),
    BILLING_REFERENCE("Billing Reference"),
    CLIENT_NAME("Client Name"),
    CLIENT_PHONE("Client Phone"),
    SEND_BILL("Send Bill"),
    HELPER_NAME("Helper Name"),
    SHIFT("Shift"),
    DAY("Day"),
    RATE("Rate"),
    TOTAL_AMOUNT("Total Amount");

    private final String header;

    BillingColumn(String header) {
        this.header = header;
    }

    public String header() {
        return header;
    }

    public boolean matches(String text) {
        return normalize(header).equals(normalize(text));
    }

    /** Trims, collapses inner spaces and ignores case, as required for header and label matching. */
    public static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
