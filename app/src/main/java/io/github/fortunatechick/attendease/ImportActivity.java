package io.github.fortunatechick.attendease;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.ViewModelProvider;

import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.ImportSummary;
import io.github.fortunatechick.attendease.parser.LectureRecord;
import io.github.fortunatechick.attendease.parser.ParseResult;
import io.github.fortunatechick.attendease.parser.StudentProfile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Map;

public class ImportActivity extends BaseActivity {

    // Set when the PDF was dropped onto our own dashboard, so it isn't treated as "opened
    // from another app".
    static final String EXTRA_FROM_DROP = "from_drop";

    private ImportViewModel model;
    private boolean fromOutside;

    private View groupProgress, groupError, groupReview, barButtons;
    private TextView txtProgress, txtError;

    private final ActivityResultLauncher<Intent> pickPdf = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null) {
                    model.read(result.getData().getData());
                } else if (model.stage().getValue() == ImportViewModel.Stage.IDLE) {
                    // Backed out of the picker without choosing anything.
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);
        setTitle(R.string.menu_main_upload_attendance_pdf);
        showBackArrow();

        groupProgress = findViewById(R.id.group_progress);
        groupError = findViewById(R.id.group_error);
        groupReview = findViewById(R.id.group_review);
        barButtons = findViewById(R.id.bar_buttons);
        txtProgress = findViewById(R.id.txt_progress);
        txtError = findViewById(R.id.txt_error);

        findViewById(R.id.btn_pick_again).setOnClickListener(v -> openPicker());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> leave());
        findViewById(R.id.btn_save).setOnClickListener(v -> model.save());

        model = new ViewModelProvider(this).get(ImportViewModel.class);
        model.stage().observe(this, this::render);

        // Opened from outside: the student tapped "Open" (or "Share") on a downloaded PDF,
        // typically right after downloading it from the SAP portal.
        Uri incoming = incomingPdf(getIntent());
        fromOutside = incoming != null && !getIntent().getBooleanExtra(EXTRA_FROM_DROP, false);
        if (savedInstanceState == null) {
            if (incoming != null) model.read(incoming); else openPicker();
        }
    }

    // Other apps can start this screen, so only a content:// link is accepted: a file:// path
    // could point at this app's own private files, and anything else isn't a shared document.
    private static Uri incomingPdf(Intent intent) {
        if (intent == null) return null;
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        }
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return null;
        return uri;
    }

    // When another app opened us, finishing would drop the student back in that app;
    // go to the dashboard instead.
    private void leave() {
        if (fromOutside) {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(home);
        }
        finish();
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        pickPdf.launch(intent);
    }

    private void render(ImportViewModel.Stage stage) {
        groupProgress.setVisibility(stage == ImportViewModel.Stage.READING
                || stage == ImportViewModel.Stage.SAVING ? View.VISIBLE : View.GONE);
        groupError.setVisibility(stage == ImportViewModel.Stage.ERROR ? View.VISIBLE : View.GONE);
        groupReview.setVisibility(stage == ImportViewModel.Stage.REVIEW ? View.VISIBLE : View.GONE);
        barButtons.setVisibility(stage == ImportViewModel.Stage.REVIEW ? View.VISIBLE : View.GONE);

        switch (stage) {
            case READING:
                txtProgress.setText(R.string.import_reading);
                break;
            case SAVING:
                txtProgress.setText(R.string.import_saving);
                break;
            case ERROR:
                txtError.setText(model.errorMessage);
                break;
            case REVIEW:
                showReview(model.result, model.preview);
                break;
            case SAVED:
                showSavedDialog(model.summary);
                break;
            default:
                break;
        }
    }

    private void showReview(ParseResult result, AttendanceRepository.ImportPreview preview) {
        StudentProfile p = result.profile;
        StringBuilder report = new StringBuilder();
        appendLine(report, join(p.name, p.rollNo));
        appendLine(report, p.programName);
        appendLine(report, p.academicSession);
        if (p.durationFrom != null) appendLine(report, getString(R.string.import_duration, p.durationFrom, p.durationTo));
        appendLine(report, getString(preview.newSemester ? R.string.import_semester_new : R.string.import_semester,
                preview.semesterLabel));
        appendLine(report, getString(R.string.import_file, model.fileName));
        ((TextView) findViewById(R.id.txt_report)).setText(report.toString().trim());

        int nu = result.countByStatus("NU");
        StringBuilder rows = new StringBuilder();
        rows.append(getResources().getQuantityString(R.plurals.import_rows_read, result.records.size(),
                result.records.size())).append('\n');
        rows.append(getString(R.string.import_status_counts, result.countByStatus("P"), result.countByStatus("A"),
                result.countByStatus("E"), result.countByStatus("L"))).append('\n');
        if (nu > 0) {
            rows.append(getString(R.string.import_not_updated, nu));
        } else {
            rows.append(getString(R.string.import_none_not_updated));
        }
        ((TextView) findViewById(R.id.txt_rows)).setText(rows.toString());

        TextView checks = findViewById(R.id.txt_checks);
        StringBuilder warnings = new StringBuilder();
        if (!result.missingSrNos.isEmpty()) {
            warnings.append(getString(R.string.import_missing_sr, shortList(result.missingSrNos))).append('\n');
        }
        if (!result.duplicateSrNos.isEmpty()) {
            warnings.append(getString(R.string.import_repeated_sr, shortList(result.duplicateSrNos))).append('\n');
        }
        for (Map.Entry<String, Integer> e : result.unknownStatuses.entrySet()) {
            warnings.append(getResources().getQuantityString(R.plurals.import_unknown_status, e.getValue(),
                    e.getValue(), e.getKey())).append('\n');
        }
        if (!result.unmatchedLines.isEmpty()) {
            warnings.append(getResources().getQuantityString(R.plurals.import_unmatched, result.unmatchedLines.size(),
                    result.unmatchedLines.size())).append('\n');
        }
        if (warnings.length() == 0) {
            checks.setText(getString(R.string.import_no_gaps, result.records.size()));
            checks.setTextColor(ContextCompat.getColor(this, R.color.safe));
        } else {
            checks.setText(warnings.toString().trim());
            checks.setTextColor(ContextCompat.getColor(this, R.color.risk));
        }

        List<String> subjects = result.subjectNames();
        ((TextView) findViewById(R.id.txt_subjects_heading)).setText(getString(R.string.import_subjects_heading, subjects.size()));
        LinearLayout list = findViewById(R.id.list_subjects);
        list.removeAllViews();
        for (String name : subjects) {
            int theory = 0, practical = 0;
            for (LectureRecord r : result.recordsFor(name)) {
                if ("T".equals(r.component)) theory++; else practical++;
            }
            TextView row = new TextView(this);
            String detail = practical > 0 ? getString(R.string.import_theory_lab, theory, practical)
                    : getString(R.string.import_theory_only, theory);
            row.setText(getString(R.string.import_subject_row, name, detail));
            row.setPadding(0, 4, 0, 4);
            list.addView(row);
        }

        TextView merges = findViewById(R.id.txt_merges);
        if (result.prefixMerges.isEmpty()) {
            merges.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder(getString(R.string.import_truncated_merged)).append('\n');
            for (Map.Entry<String, String> e : result.prefixMerges.entrySet()) {
                sb.append(e.getKey()).append(" → ").append(e.getValue()).append('\n');
            }
            merges.setText(sb.toString().trim());
            merges.setVisibility(View.VISIBLE);
        }

        StringBuilder effect = new StringBuilder();
        if (preview.changesNothing()) {
            effect.append(getString(R.string.import_already_saved));
        } else {
            effect.append(getString(R.string.import_preview_counts, preview.newLectures, preview.updatedLectures,
                    preview.unchangedLectures));
            if (!preview.newSubjects.isEmpty()) {
                effect.append('\n').append(getResources().getQuantityString(R.plurals.import_new_subjects,
                        preview.newSubjects.size(), preview.newSubjects.size()));
            }
            for (Map.Entry<String, String> e : preview.renamedSubjects.entrySet()) {
                effect.append('\n').append(getString(R.string.import_subject_renamed, e.getKey(), e.getValue()));
            }
        }
        Resources res = getResources();
        if (preview.resolvedByCollege > 0) {
            effect.append('\n').append(res.getQuantityString(R.plurals.import_resolved_by_college,
                    preview.resolvedByCollege, preview.resolvedByCollege));
        }
        if (preview.mergedManual > 0) {
            effect.append('\n').append(res.getQuantityString(R.plurals.import_will_replace_manual,
                    preview.mergedManual, preview.mergedManual));
        }
        if (preview.stillAbsent > 0) {
            effect.append('\n').append(res.getQuantityString(R.plurals.import_still_absent,
                    preview.stillAbsent, preview.stillAbsent));
        }
        if (preview.conflicts > 0) {
            effect.append('\n').append(res.getQuantityString(R.plurals.import_conflicts_preview,
                    preview.conflicts, preview.conflicts));
        }
        ((TextView) findViewById(R.id.txt_effect)).setText(effect.toString());
    }

    private void showSavedDialog(ImportSummary s) {
        StringBuilder msg = new StringBuilder();
        Resources res = getResources();
        msg.append(getString(R.string.import_saved_counts, s.inserted, s.updated, s.unchanged));
        if (!s.createdSubjects.isEmpty()) {
            msg.append('\n').append(res.getQuantityString(R.plurals.import_subjects_added,
                    s.createdSubjects.size(), s.createdSubjects.size()));
        }
        if (s.mergedManual > 0) {
            msg.append('\n').append(res.getQuantityString(R.plurals.import_manual_matched, s.mergedManual, s.mergedManual));
        }
        int reconciled = s.autoApproved + s.stillAbsent;
        if (reconciled > 0) {
            msg.append("\n\n").append(res.getQuantityString(R.plurals.import_reconciled, reconciled,
                    reconciled, s.autoApproved, s.stillAbsent));
        }
        if (!s.conflicts.isEmpty()) {
            msg.append("\n\n").append(res.getQuantityString(R.plurals.import_conflicts_saved,
                    s.conflicts.size(), s.conflicts.size()));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_saved_title)
                .setMessage(msg.toString())
                .setCancelable(false)
                .setPositiveButton(s.conflicts.isEmpty() ? R.string.action_ok : R.string.import_review, (d, w) -> {
                    if (s.conflicts.isEmpty()) {
                        done();
                    } else {
                        showConflictDialog(s.conflicts);
                    }
                })
                .show();
    }

    // Lets the student pick, lecture by lecture, where the PDF is right. Unticked ones keep
    // the student's own record (and will be flagged again on the next import).
    private void showConflictDialog(List<ImportSummary.Conflict> conflicts) {
        String[] items = new String[conflicts.size()];
        boolean[] usePdf = new boolean[conflicts.size()];
        for (int i = 0; i < conflicts.size(); i++) {
            ImportSummary.Conflict c = conflicts.get(i);
            String yours = c.kind == ImportSummary.Conflict.REJECTED_LEAVE
                    ? getString(R.string.import_conflict_leave_rejected) : Formats.statusLabel(this, c.yours);
            items[i] = getString(R.string.import_conflict_item, c.subjectName, Formats.component(this, c.component),
                    Formats.date(c.dateMillis), Formats.time(c.startTime), yours, c.pdf);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_conflict_title)
                .setMultiChoiceItems(items, usePdf, (d, which, checked) -> usePdf[which] = checked)
                .setCancelable(false)
                .setPositiveButton(R.string.import_apply, (d, w) -> {
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.run(() -> {
                        for (int i = 0; i < conflicts.size(); i++) {
                            if (usePdf[i]) repo.acceptPdf(conflicts.get(i));
                        }
                    }, this::done);
                })
                .setNegativeButton(R.string.import_keep_mine, (d, w) -> done())
                .show();
    }

    private void done() {
        setResult(RESULT_OK);
        leave();
    }

    private static String join(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + " · " + b;
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (line != null && !line.isEmpty()) sb.append(line).append('\n');
    }

    private String shortList(List<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbers.size() && i < 10; i++) {
            if (i > 0) sb.append(", ");
            sb.append(numbers.get(i));
        }
        if (numbers.size() > 10) return getString(R.string.import_and_more, sb.toString(), numbers.size() - 10);
        return sb.toString();
    }
}
