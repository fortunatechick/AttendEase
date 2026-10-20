package io.github.fortunatechick.attendease.calc;

// What an approved medical leave means for the percentage. Institutions differ.
public enum ApprovalConvention {

    // The approved lecture counts as attended: it stays in the total and in the attended count.
    COUNTED_AS_ATTENDED,

    // The approved lecture leaves the total entirely, as if it had never been held.
    EXCLUDED_FROM_TOTAL;

    public static ApprovalConvention fromPreference(String value) {
        if (value != null) {
            for (ApprovalConvention c : values()) {
                if (c.name().equals(value)) return c;
            }
        }
        return COUNTED_AS_ATTENDED;
    }
}
