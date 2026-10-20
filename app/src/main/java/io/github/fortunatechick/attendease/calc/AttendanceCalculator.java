package io.github.fortunatechick.attendease.calc;

import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.Status;
import io.github.fortunatechick.attendease.data.Subject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Turns lectures into attendance figures. Holds the settings that change the maths:
// the threshold, the approval convention and the semester length. Plain Java, no Android.
public class AttendanceCalculator {

    public static final int DEFAULT_THRESHOLD = 80;
    public static final int DEFAULT_SEMESTER_WEEKS = 15;

    public static class SubjectSummary {
        public final long subjectId;
        public final AttendanceStats combined;
        public final AttendanceStats theory;
        public final AttendanceStats practical;

        SubjectSummary(long subjectId, AttendanceStats combined,
                       AttendanceStats theory, AttendanceStats practical) {
            this.subjectId = subjectId;
            this.combined = combined;
            this.theory = theory;
            this.practical = practical;
        }
    }

    private final ApprovalConvention convention;
    private final int thresholdPercent;
    private final int semesterWeeks;

    public AttendanceCalculator(ApprovalConvention convention, int thresholdPercent) {
        this(convention, thresholdPercent, DEFAULT_SEMESTER_WEEKS);
    }

    public AttendanceCalculator(ApprovalConvention convention, int thresholdPercent, int semesterWeeks) {
        this.convention = convention;
        this.thresholdPercent = thresholdPercent;
        this.semesterWeeks = semesterWeeks;
    }

    public int semesterWeeks() {
        return semesterWeeks;
    }

    // Lectures per week: what the student set for the subject, otherwise estimated from
    // the lectures recorded so far.
    public static WeeklyPattern patternFor(Subject subject, List<Lecture> lectures, long semesterStartMillis) {
        WeeklyPattern estimate = WeeklyPattern.infer(lectures, semesterStartMillis);
        int theory = subject.theoryPerWeek != null ? subject.theoryPerWeek : estimate.theory;
        int lab = subject.labPerWeek != null ? subject.labPerWeek : estimate.lab;
        return new WeeklyPattern(theory, lab);
    }

    public SemesterBudget budget(Subject subject, List<Lecture> lectures, long semesterStartMillis) {
        WeeklyPattern pattern = patternFor(subject, lectures, semesterStartMillis);
        return new SemesterBudget(semesterWeeks, pattern.total(), stats(lectures));
    }

    public int thresholdPercent() {
        return thresholdPercent;
    }

    public AttendanceStats stats(List<Lecture> lectures) {
        int present = 0, absent = 0, pending = 0, exempt = 0, notUpdated = 0;
        for (Lecture l : lectures) {
            switch (l.effectiveStatus) {
                case Status.PRESENT:
                    present++;
                    break;
                case Status.ABSENT:
                    absent++;
                    break;
                case Status.PENDING_LEAVE:
                    pending++;
                    break;
                case Status.EXEMPT:
                    exempt++;
                    break;
                default:
                    notUpdated++;
            }
        }
        return new AttendanceStats(present, absent, pending, exempt, notUpdated, convention, thresholdPercent);
    }

    public SubjectSummary summarise(long subjectId, List<Lecture> lectures) {
        List<Lecture> theory = new ArrayList<>();
        List<Lecture> practical = new ArrayList<>();
        for (Lecture l : lectures) {
            if (l.isTheory()) theory.add(l); else practical.add(l);
        }
        return new SubjectSummary(subjectId, stats(lectures), stats(theory), stats(practical));
    }

    // One summary per subject id, in the order the subjects first appear.
    public Map<Long, SubjectSummary> summariseBySubject(List<Lecture> lectures) {
        Map<Long, List<Lecture>> grouped = new LinkedHashMap<>();
        for (Lecture l : lectures) {
            List<Lecture> list = grouped.get(l.subjectId);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(l.subjectId, list);
            }
            list.add(l);
        }
        Map<Long, SubjectSummary> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Lecture>> e : grouped.entrySet()) {
            result.put(e.getKey(), summarise(e.getKey(), e.getValue()));
        }
        return result;
    }
}
