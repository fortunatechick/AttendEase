package io.github.fortunatechick.attendease;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.os.BundleCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.fortunatechick.attendease.calc.AttendanceCalculator;
import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.calc.SemesterBudget;
import io.github.fortunatechick.attendease.calc.WeeklyPattern;
import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.LeaveRepository;
import io.github.fortunatechick.attendease.data.LeaveRequest;
import io.github.fortunatechick.attendease.data.LeaveRules;
import io.github.fortunatechick.attendease.data.Prefs;
import io.github.fortunatechick.attendease.data.Status;
import io.github.fortunatechick.attendease.data.Subject;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class SubjectDetailActivity extends BaseActivity {

    public static final String EXTRA_SUBJECT_ID = "subject_id";
    private static final String DELETE = "delete";
    private static final String LEAVE = "leave";
    private static final String REMOVE = "remove";
    private static final String OPEN = "open";

    private static class DetailData {
        Subject subject;
        List<Lecture> lectures;
        AttendanceCalculator.SubjectSummary summary;
        int semesterWeeks;
        long semesterStart = -1;
        WeeklyPattern estimate;
        WeeklyPattern pattern;
        SemesterBudget budget;
    }

    private AttendanceRepository repo;
    private long subjectId;
    private DetailData data;

    private static final String KEY_FILTER = "filter";
    private static final String KEY_SCROLL = "scroll";

    private View header;
    private RecyclerView list;
    private LectureAdapter adapter;
    private ChipGroup filterChips;
    // List position from before a rotation or fold; applied once the lectures are back.
    private Parcelable pendingScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_detail);
        showBackArrow();

        repo = new AttendanceRepository(this);
        subjectId = getIntent().getLongExtra(EXTRA_SUBJECT_ID, -1);

        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        header = LayoutInflater.from(this).inflate(R.layout.item_detail_header, list, false);
        filterChips = header.findViewById(R.id.chips_filter);
        filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> showFilteredLectures());
        // The header lives inside the list, outside the saved view tree, so restore it by hand.
        if (savedInstanceState != null) {
            int filter = savedInstanceState.getInt(KEY_FILTER, View.NO_ID);
            if (filter != View.NO_ID) filterChips.check(filter);
            pendingScroll = BundleCompat.getParcelable(savedInstanceState, KEY_SCROLL, Parcelable.class);
        }

        adapter = new LectureAdapter(header, this::onLectureLongPress);
        list.setAdapter(adapter);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_FILTER, filterChips.getCheckedChipId());
        if (list.getLayoutManager() != null) {
            out.putParcelable(KEY_SCROLL, list.getLayoutManager().onSaveInstanceState());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDataChanged() {
        load();
    }

    private void load() {
        Prefs prefs = new Prefs(this);
        Background.load(() -> {
            DetailData d = new DetailData();
            d.subject = repo.subjects.byId(subjectId);
            if (d.subject == null) return d;
            d.lectures = repo.lectures.forSubject(subjectId);
            AttendanceCalculator calc = prefs.calculator();
            d.summary = calc.summarise(subjectId, d.lectures);
            d.semesterWeeks = calc.semesterWeeks();
            d.semesterStart = repo.lectures.semesterStart(d.subject.semesterId);
            if (d.semesterStart >= 0) {
                d.estimate = WeeklyPattern.infer(d.lectures, d.semesterStart);
                d.pattern = AttendanceCalculator.patternFor(d.subject, d.lectures, d.semesterStart);
                SemesterBudget b = calc.budget(d.subject, d.lectures, d.semesterStart);
                d.budget = b.isKnown() ? b : null;
            }
            return d;
        }, d -> {
            if (isDestroyed()) return;
            if (d.subject == null) {
                finish();
                return;
            }
            data = d;
            bindHeader();
            showFilteredLectures();
        });
    }

    private void bindHeader() {
        setTitle(data.subject.displayName());

        TextView canonical = header.findViewById(R.id.txt_canonical);
        boolean renamed = !data.subject.displayName().equals(data.subject.canonicalName);
        canonical.setText(getString(R.string.subject_name_in_pdf, data.subject.canonicalName));
        canonical.setVisibility(renamed ? View.VISIBLE : View.GONE);

        AttendanceStats theory = data.summary.theory;
        AttendanceStats practical = data.summary.practical;
        bindFigure(header.findViewById(R.id.fig_combined), getString(R.string.subject_combined), data.summary.combined);
        bindFigure(header.findViewById(R.id.fig_theory), getString(R.string.component_theory), theory);
        bindFigure(header.findViewById(R.id.fig_practical), getString(R.string.component_practical), practical);
        bindSemester();
        header.findViewById(R.id.fig_theory).setVisibility(total(theory) > 0 ? View.VISIBLE : View.GONE);
        header.findViewById(R.id.fig_practical).setVisibility(total(practical) > 0 ? View.VISIBLE : View.GONE);
    }

    private void bindFigure(View card, String title, AttendanceStats s) {
        int color = Formats.standingColor(this, s.standing());
        ((TextView) card.findViewById(R.id.fig_title)).setText(title);

        TextView percent = card.findViewById(R.id.fig_percent);
        percent.setText(s.displayText());
        percent.setTextColor(color);

        ((TextView) card.findViewById(R.id.fig_counts)).setText(getString(R.string.subject_counts,
                s.decidedAttended(), s.decidedHeld(), s.missed()));

        TextView pending = card.findViewById(R.id.fig_pending);
        if (s.hasPending()) {
            pending.setText(getString(R.string.subject_pending_range, Formats.pending(this, s),
                    AttendanceStats.formatPercent(s.confirmedPercent()),
                    AttendanceStats.formatPercent(s.ifApprovedPercent()),
                    AttendanceStats.formatPercent(s.ifRejectedPercent())));
            pending.setVisibility(View.VISIBLE);
        } else {
            pending.setVisibility(View.GONE);
        }

        TextView advice = card.findViewById(R.id.fig_advice);
        advice.setVisibility(View.VISIBLE);
        if (!s.hasData()) {
            advice.setText(Formats.noLecturesMarked(this));
        } else {
            String text = Formats.advice(this, s.advice());
            if (s.hasPending()) {
                text += "\n" + getString(R.string.subject_if_rejected, Formats.advice(this, s.worstCaseAdvice()));
            }
            advice.setText(text);
        }
        advice.setTextColor(s.advice().isBelowThreshold()
                ? ContextCompat.getColor(this, R.color.risk) : ContextCompat.getColor(this, R.color.safe));
    }

    // The college counts absences over the whole semester, theory and lab together, so the
    // budget goes on the combined card; the component cards show their planned share.
    private void bindSemester() {
        View combined = header.findViewById(R.id.fig_combined);
        TextView semester = combined.findViewById(R.id.fig_semester);
        TextView advice = combined.findViewById(R.id.fig_advice);
        SemesterBudget b = data.budget;
        if (b != null) {
            semester.setText(getString(data.subject.hasCustomPattern()
                    ? R.string.subject_budget_set_by_you : R.string.subject_budget_estimated,
                    Formats.budgetSummary(this, b)));
            semester.setVisibility(View.VISIBLE);
            advice.setText(Formats.budgetAdvice(this, b));
            advice.setTextColor(Formats.budgetColor(this, b));
        } else {
            semester.setText(R.string.subject_set_lectures_per_week);
            semester.setVisibility(View.VISIBLE);
        }

        bindComponentPlan(header.findViewById(R.id.fig_theory), data.pattern == null ? 0 : data.pattern.theory);
        bindComponentPlan(header.findViewById(R.id.fig_practical), data.pattern == null ? 0 : data.pattern.lab);
    }

    private void bindComponentPlan(View card, int perWeek) {
        TextView semester = card.findViewById(R.id.fig_semester);
        TextView advice = card.findViewById(R.id.fig_advice);
        if (data.budget == null || perWeek == 0) {
            semester.setVisibility(View.GONE);
            return;
        }
        semester.setText(getString(R.string.subject_planned, Formats.lectures(this, perWeek * data.semesterWeeks)));
        semester.setVisibility(View.VISIBLE);
        advice.setVisibility(View.GONE);
    }

    private void showWeeklyPatternDialog() {
        if (data == null) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_weekly_pattern, null);
        EditText theory = view.findViewById(R.id.edit_theory);
        EditText lab = view.findViewById(R.id.edit_lab);
        TextView hint = view.findViewById(R.id.txt_estimate);
        if (data.pattern != null) {
            theory.setText(String.valueOf(data.pattern.theory));
            lab.setText(String.valueOf(data.pattern.lab));
        }
        // Worked examples use this semester's length and the attendance target.
        int weeks = data.semesterWeeks;
        int allowedShare = 100 - data.summary.combined.thresholdPercent;
        String examples = getString(R.string.weekly_examples, weeks, 4 * weeks, 4 * weeks * allowedShare / 100,
                3 * weeks, 3 * weeks * allowedShare / 100);
        hint.setText(data.estimate == null
                ? getString(R.string.weekly_no_estimate) + " " + examples
                : getString(R.string.weekly_estimate, data.estimate.theory, data.estimate.lab) + " " + examples);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_subject_detail_lectures_per_week)
                .setView(view)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    Integer t = parseCount(theory.getText().toString());
                    Integer l = parseCount(lab.getText().toString());
                    Background.run(() -> {
                        repo.subjects.setWeeklyPattern(subjectId, t == null ? 0 : t, l == null ? 0 : l);
                    }, this::load);
                })
                .setNeutralButton(R.string.weekly_use_estimate, (d, w) -> Background.run(() -> {
                    repo.subjects.setWeeklyPattern(subjectId, null, null);
                }, this::load))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static Integer parseCount(String text) {
        try {
            int n = Integer.parseInt(text.trim());
            return Math.max(0, Math.min(n, 20));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int total(AttendanceStats s) {
        return s.present + s.absent + s.pending + s.exempt + s.notUpdated;
    }

    private void showFilteredLectures() {
        if (data == null) return;
        int checked = filterChips.getCheckedChipId();
        List<Lecture> filtered = new ArrayList<>();
        for (Lecture l : data.lectures) {
            if (matchesFilter(l.effectiveStatus, checked)) filtered.add(l);
        }
        adapter.setLectures(filtered);
        if (pendingScroll != null && list.getLayoutManager() != null) {
            list.getLayoutManager().onRestoreInstanceState(pendingScroll);
            pendingScroll = null;
        }
        ((TextView) header.findViewById(R.id.txt_list_hint)).setText(data.lectures.isEmpty()
                ? R.string.subject_no_lectures_hint : R.string.detail_header_long_press_a_lecture_to);
    }

    private static boolean matchesFilter(String status, int chipId) {
        if (chipId == R.id.chip_present) {
            return Status.PRESENT.equals(status) || Status.EXEMPT.equals(status);
        } else if (chipId == R.id.chip_absent) {
            return Status.ABSENT.equals(status);
        } else if (chipId == R.id.chip_pending) {
            return Status.PENDING_LEAVE.equals(status);
        } else if (chipId == R.id.chip_not_updated) {
            return Status.NOT_UPDATED.equals(status);
        }
        return true;
    }

    // Manual status change, for lectures the PDF hasn't caught up on, plus medical leave.
    private void onLectureLongPress(Lecture lecture) {
        if (Status.PENDING_LEAVE.equals(lecture.effectiveStatus) || Status.EXEMPT.equals(lecture.effectiveStatus)) {
            showLeaveOutcomeOptions(lecture);
            return;
        }
        String when = getString(R.string.date_time, Formats.date(lecture.dateMillis), Formats.time(lecture.startTime));

        List<String> labels = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        labels.add(getString(R.string.status_present));
        statuses.add(Status.PRESENT);
        labels.add(getString(R.string.status_absent));
        statuses.add(Status.ABSENT);
        labels.add(getString(R.string.lecture_not_updated_yet));
        statuses.add(Status.NOT_UPDATED);
        boolean canReset = lecture.manualOverride && lecture.sourceStatus != null && lecture.srNo > 0;
        if (canReset) {
            labels.add(getString(R.string.lecture_back_to_pdf, Formats.statusLabel(this, Status.fromSource(lecture.sourceStatus))));
            statuses.add(null);
        }
        labels.add(getString(R.string.lecture_leave_submitted));
        statuses.add(LEAVE);
        if (lecture.isHandAdded()) {
            labels.add(getString(R.string.lecture_delete));
            statuses.add(DELETE);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dot_join, when, Formats.component(this, lecture.component)))
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String chosen = statuses.get(which);
                    if (LEAVE.equals(chosen)) {
                        askSubmittedTo(lecture);
                        return;
                    }
                    if (DELETE.equals(chosen)) {
                        Background.run(() -> {
                            repo.lectures.delete(lecture.id);
                        }, this::load);
                        return;
                    }
                    String newStatus = chosen == null ? Status.fromSource(lecture.sourceStatus) : chosen;
                    boolean manual = chosen != null;
                    Background.run(() -> {
                        repo.lectures.setEffectiveStatus(lecture.id, newStatus, manual);
                    }, this::load);
                })
                .show();
    }

    // Marks the lecture as covered by a medical certificate that's awaiting a decision.
    private void askSubmittedTo(Lecture lecture) {
        String[] options = {getString(R.string.submitted_to_hod), getString(R.string.submitted_to_coordinator)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_leave_certificate_submitted_to)
                .setItems(options, (d, which) -> {
                    String to = which == 0 ? LeaveRequest.TO_HOD : LeaveRequest.TO_COORDINATOR;
                    LeaveRepository leaveRepo = new LeaveRepository(this);
                    Background.load(() -> {
                        long id = leaveRepo.markOnLeave(lecture.id, to, todayUtc());
                        return id;
                    }, id -> {
                        if (isDestroyed()) return;
                        Toast.makeText(this, id < 0 ? R.string.lecture_cannot_leave : R.string.lecture_marked_leave,
                                Toast.LENGTH_LONG).show();
                        load();
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // For a lecture on medical leave: record the outcome right here.
    private void showLeaveOutcomeOptions(Lecture lecture) {
        String when = getString(R.string.date_time, Formats.date(lecture.dateMillis), Formats.time(lecture.startTime));
        boolean pending = Status.PENDING_LEAVE.equals(lecture.effectiveStatus);
        LeaveRepository leaveRepo = new LeaveRepository(this);
        Long leaveId = lecture.leaveId;
        if (leaveId == null) return;

        List<String> labels = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        if (pending) {
            labels.add(getString(R.string.status_leave_approved));
            actions.add(LeaveRules.APPROVED);
        } else {
            labels.add(getString(R.string.lecture_back_to_awaiting));
            actions.add(LeaveRules.PENDING);
        }
        labels.add(getString(R.string.lecture_leave_rejected));
        actions.add(LeaveRules.REJECTED);
        labels.add(getString(R.string.lecture_remove_from_leave));
        actions.add(REMOVE);
        labels.add(getString(R.string.lecture_open_leave));
        actions.add(OPEN);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dot_join, when, getString(pending ? R.string.lecture_leave_awaiting : R.string.lecture_leave_approved_lower)))
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String action = actions.get(which);
                    if (OPEN.equals(action)) {
                        Intent intent = new Intent(this, LeaveDetailActivity.class);
                        intent.putExtra(LeaveDetailActivity.EXTRA_LEAVE_ID, leaveId.longValue());
                        startActivity(intent);
                        return;
                    }
                    Background.run(() -> {
                        if (REMOVE.equals(action)) leaveRepo.removeLecture(leaveId, lecture.id);
                        else leaveRepo.decide(leaveId, lecture.id, action);
                    }, this::load);
                })
                .show();
    }

    private static long todayUtc() {
        java.util.Calendar local = java.util.Calendar.getInstance();
        java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH),
                local.get(java.util.Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    // Folds this subject into another one of the same semester, e.g. two spellings of one course.
    private void showMergeDialog() {
        if (data == null) return;
        long semesterId = data.subject.semesterId;
        Background.load(() -> repo.subjects.forSemester(semesterId), all -> {
            if (isDestroyed()) return;
            List<Subject> others = new ArrayList<>();
            for (Subject s : all) if (s.id != subjectId) others.add(s);
            if (others.isEmpty()) {
                Toast.makeText(this, R.string.merge_nothing_to_merge, Toast.LENGTH_LONG).show();
                return;
            }
            String[] names = new String[others.size()];
            for (int i = 0; i < others.size(); i++) names[i] = others.get(i).displayName();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.merge_pick_title, data.subject.displayName()))
                    .setItems(names, (d, which) -> confirmMerge(others.get(which)))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        });
    }

    private void confirmMerge(Subject into) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.merge_confirm_title, into.displayName()))
                .setMessage(getResources().getQuantityString(R.plurals.merge_confirm_message, data.lectures.size(),
                        data.lectures.size(), data.subject.displayName(), into.displayName(), data.subject.canonicalName))
                .setPositiveButton(R.string.merge_action, (d, w) -> Background.run(() -> {
                    repo.mergeSubjects(subjectId, into.id);
                }, () -> {
                    Intent intent = new Intent(this, SubjectDetailActivity.class);
                    intent.putExtra(EXTRA_SUBJECT_ID, into.id);
                    startActivity(intent);
                    finish();
                }))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeleteSubject() {
        if (data == null) return;
        boolean fromPdf = false;
        for (Lecture l : data.lectures) if (!l.isHandAdded()) fromPdf = true;
        String message = getResources().getQuantityString(R.plurals.delete_subject_message,
                data.lectures.size(), data.lectures.size());
        if (fromPdf) message += " " + getString(R.string.delete_subject_comes_back);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_subject_title, data.subject.displayName()))
                .setMessage(message)
                .setPositiveButton(R.string.action_delete, (d, w) -> Background.run(() -> {
                    repo.subjects.delete(subjectId);   // lectures go with it (ON DELETE CASCADE)
                }, this::finish))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showRenameDialog() {
        if (data == null) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(data.subject.displayName());
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_subject_detail_rename_subject)
                .setMessage(getString(R.string.rename_message, data.subject.canonicalName))
                .setView(box)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = data.subject.canonicalName;
                    String finalName = name;
                    Background.run(() -> repo.subjects.setDisplayName(subjectId, finalName), this::load);
                })
                .setNeutralButton(R.string.rename_use_pdf_name, (d, w) ->
                        Background.run(() -> repo.subjects.setDisplayName(subjectId, data.subject.canonicalName),
                                this::load))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.subject_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_rename) {
            showRenameDialog();
            return true;
        } else if (item.getItemId() == R.id.action_add_lecture) {
            Intent intent = new Intent(this, AddLectureActivity.class);
            intent.putExtra(AddLectureActivity.EXTRA_SUBJECT_ID, subjectId);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.action_weekly) {
            showWeeklyPatternDialog();
            return true;
        } else if (item.getItemId() == R.id.action_merge) {
            showMergeDialog();
            return true;
        } else if (item.getItemId() == R.id.action_delete_subject) {
            confirmDeleteSubject();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
