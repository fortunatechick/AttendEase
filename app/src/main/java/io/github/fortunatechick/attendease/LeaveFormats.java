package io.github.fortunatechick.attendease;

import android.content.Context;

import androidx.core.content.ContextCompat;

import io.github.fortunatechick.attendease.data.LeaveRequest;
import io.github.fortunatechick.attendease.data.LeaveRules;

final class LeaveFormats {

    private LeaveFormats() {
    }

    static String range(LeaveRequest r) {
        if (r.fromDate == r.toDate) return Formats.date(r.fromDate);
        return Formats.shortDate(r.fromDate) + " – " + Formats.date(r.toDate);
    }

    static String statusLabel(String status) {
        switch (status) {
            case LeaveRequest.APPROVED:
                return "APPROVED";
            case LeaveRequest.REJECTED:
                return "REJECTED";
            case LeaveRequest.PARTIAL:
                return "PARTIAL";
            default:
                return "AWAITING DECISION";
        }
    }

    static int statusColor(Context context, String status) {
        switch (status) {
            case LeaveRequest.APPROVED:
                return ContextCompat.getColor(context, R.color.safe);
            case LeaveRequest.REJECTED:
                return ContextCompat.getColor(context, R.color.risk);
            case LeaveRequest.PARTIAL:
                return ContextCompat.getColor(context, R.color.brand);
            default:
                return ContextCompat.getColor(context, R.color.pending);
        }
    }

    static String decisionLabel(String decision) {
        if (LeaveRules.APPROVED.equals(decision)) return "Approved";
        if (LeaveRules.REJECTED.equals(decision)) return "Rejected";
        return "Pending";
    }
}
