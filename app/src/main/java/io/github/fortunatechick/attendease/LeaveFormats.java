package io.github.fortunatechick.attendease;

import android.content.Context;

import androidx.core.content.ContextCompat;

import io.github.fortunatechick.attendease.data.LeaveRequest;
import io.github.fortunatechick.attendease.data.LeaveRules;

final class LeaveFormats {

    private LeaveFormats() {
    }

    static String range(Context context, LeaveRequest r) {
        if (r.fromDate == r.toDate) return Formats.date(r.fromDate);
        return context.getString(R.string.date_range, Formats.shortDate(r.fromDate), Formats.date(r.toDate));
    }

    static String statusLabel(Context context, String status) {
        switch (status) {
            case LeaveRequest.APPROVED:
                return context.getString(R.string.leave_status_approved);
            case LeaveRequest.REJECTED:
                return context.getString(R.string.leave_status_rejected);
            case LeaveRequest.PARTIAL:
                return context.getString(R.string.leave_status_partial);
            default:
                return context.getString(R.string.leave_status_awaiting);
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

    static String decisionLabel(Context context, String decision) {
        if (LeaveRules.APPROVED.equals(decision)) return context.getString(R.string.decision_approved);
        if (LeaveRules.REJECTED.equals(decision)) return context.getString(R.string.decision_rejected);
        return context.getString(R.string.decision_pending);
    }

    static String submittedTo(Context context, LeaveRequest r) {
        return context.getString(LeaveRequest.TO_HOD.equals(r.submittedTo)
                ? R.string.submitted_to_hod : R.string.submitted_to_coordinator);
    }
}
