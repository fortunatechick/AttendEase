package io.github.fortunatechick.attendease.data;

import java.util.Collection;

// The leave model in plain Java, so it can be unit-tested.
public final class LeaveRules {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private LeaveRules() {
    }

    // What a lecture's status becomes after a decision on the leave that covers it.
    public static class Effect {
        public final String effectiveStatus;
        public final boolean manualOverride;

        Effect(String effectiveStatus, boolean manualOverride) {
            this.effectiveStatus = effectiveStatus;
            this.manualOverride = manualOverride;
        }
    }

    // Pending -> neither attended nor missed. Approved -> exempt, counted per the
    // approval-convention setting. Rejected -> back to what it was: the student's own
    // manual mark if they had one, otherwise whatever the latest PDF says.
    public static Effect effectOf(String decision, String sourceStatus,
                                  String previousStatus, boolean previousManual) {
        switch (decision) {
            case PENDING:
                return new Effect(Status.PENDING_LEAVE, false);
            case APPROVED:
                return new Effect(Status.EXEMPT, false);
            default:
                if (previousManual && previousStatus != null) {
                    return new Effect(previousStatus, true);
                }
                return new Effect(Status.fromSource(sourceStatus), false);
        }
    }

    // SUBMITTED while nothing is decided, APPROVED or REJECTED when every lecture agrees,
    // PARTIAL for any mix.
    public static String requestStatus(Collection<String> decisions) {
        boolean anyPending = false, anyApproved = false, anyRejected = false;
        for (String d : decisions) {
            if (PENDING.equals(d)) anyPending = true;
            else if (APPROVED.equals(d)) anyApproved = true;
            else if (REJECTED.equals(d)) anyRejected = true;
        }
        int kinds = (anyPending ? 1 : 0) + (anyApproved ? 1 : 0) + (anyRejected ? 1 : 0);
        if (kinds > 1) return LeaveRequest.PARTIAL;
        if (anyApproved) return LeaveRequest.APPROVED;
        if (anyRejected) return LeaveRequest.REJECTED;
        return LeaveRequest.SUBMITTED;
    }

    // Lectures already covered by a leave can't be put in another one.
    public static boolean canBeAddedToLeave(Lecture lecture) {
        return !Status.isStudentOwned(lecture.effectiveStatus);
    }
}
