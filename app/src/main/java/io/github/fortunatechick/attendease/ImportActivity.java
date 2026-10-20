package io.github.fortunatechick.attendease;

import android.content.Intent;
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
        setTitle("Upload attendance PDF");
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
        fromOutside = incoming != null;
        if (savedInstanceState == null) {
            if (incoming != null) model.read(incoming); else openPicker();
        }
    }

    private static Uri incomingPdf(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) return intent.getData();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        }
        return null;
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
                txtProgress.setText("Reading the PDF…");
                break;
            case SAVING:
                txtProgress.setText("Saving…");
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
        if (p.durationFrom != null) appendLine(report, "From " + p.durationFrom + " to " + p.durationTo);
        appendLine(report, "Semester: " + preview.semesterLabel + (preview.newSemester
                ? " (new; earlier semesters are kept and can be switched to in Settings)" : ""));
        appendLine(report, "File: " + model.fileName);
        ((TextView) findViewById(R.id.txt_report)).setText(report.toString().trim());

        int nu = result.countByStatus("NU");
        StringBuilder rows = new StringBuilder();
        rows.append(result.records.size()).append(" lecture rows read\n");
        rows.append(result.countByStatus("P")).append(" present · ")
                .append(result.countByStatus("A")).append(" absent · ")
                .append(result.countByStatus("E")).append(" exemption · ")
                .append(result.countByStatus("L")).append(" late admission\n");
        if (nu > 0) {
            rows.append(nu).append(" not yet updated by faculty: saved, but not counted until they are marked");
        } else {
            rows.append("No rows awaiting faculty update");
        }
        ((TextView) findViewById(R.id.txt_rows)).setText(rows.toString());

        TextView checks = findViewById(R.id.txt_checks);
        StringBuilder warnings = new StringBuilder();
        if (!result.missingSrNos.isEmpty()) {
            warnings.append("⚠ Missing Sr No.: ").append(shortList(result.missingSrNos))
                    .append(". Some rows could not be read, so totals may be low.\n");
        }
        if (!result.duplicateSrNos.isEmpty()) {
            warnings.append("⚠ Repeated Sr No.: ").append(shortList(result.duplicateSrNos)).append("\n");
        }
        for (Map.Entry<String, Integer> e : result.unknownStatuses.entrySet()) {
            warnings.append("⚠ ").append(e.getValue()).append(" row(s) have the status code \"").append(e.getKey())
                    .append("\", which isn't P/A/E/L/NU. They're saved as not updated (not counted): long-press them to set.\n");
        }
        if (!result.unmatchedLines.isEmpty()) {
            warnings.append("⚠ ").append(result.unmatchedLines.size())
                    .append(" line(s) looked like rows but could not be read\n");
        }
        if (warnings.length() == 0) {
            checks.setText("✓ Sr No. 1–" + result.records.size() + " with no gaps");
            checks.setTextColor(ContextCompat.getColor(this, R.color.safe));
        } else {
            checks.setText(warnings.toString().trim());
            checks.setTextColor(ContextCompat.getColor(this, R.color.risk));
        }

        List<String> subjects = result.subjectNames();
        ((TextView) findViewById(R.id.txt_subjects_heading)).setText("Subjects (" + subjects.size() + ")");
        LinearLayout list = findViewById(R.id.list_subjects);
        list.removeAllViews();
        for (String name : subjects) {
            int theory = 0, practical = 0;
            for (LectureRecord r : result.recordsFor(name)) {
                if ("T".equals(r.component)) theory++; else practical++;
            }
            TextView row = new TextView(this);
            String detail = practical > 0 ? theory + " theory, " + practical + " lab" : theory + " theory";
            row.setText(name + "  ·  " + detail);
            row.setPadding(0, 4, 0, 4);
            list.addView(row);
        }

        TextView merges = findViewById(R.id.txt_merges);
        if (result.prefixMerges.isEmpty()) {
            merges.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder("Truncated names merged:\n");
            for (Map.Entry<String, String> e : result.prefixMerges.entrySet()) {
                sb.append(e.getKey()).append(" → ").append(e.getValue()).append('\n');
            }
            merges.setText(sb.toString().trim());
            merges.setVisibility(View.VISIBLE);
        }

        StringBuilder effect = new StringBuilder();
        if (preview.changesNothing()) {
            effect.append("This report is already saved. Saving again changes nothing.");
        } else {
            effect.append(preview.newLectures).append(" new lectures, ")
                    .append(preview.updatedLectures).append(" updated, ")
                    .append(preview.unchangedLectures).append(" unchanged");
            if (!preview.newSubjects.isEmpty()) {
                effect.append("\n").append(preview.newSubjects.size()).append(" new subjects");
            }
            for (Map.Entry<String, String> e : preview.renamedSubjects.entrySet()) {
                effect.append("\nSubject renamed: ").append(e.getKey()).append(" → ").append(e.getValue());
            }
        }
        if (preview.resolvedByCollege > 0) {
            effect.append("\n").append(preview.resolvedByCollege)
                    .append(" pending-leave lecture(s) now show as attended and will be approved");
        }
        if (preview.mergedManual > 0) {
            effect.append("\n").append(preview.mergedManual)
                    .append(" lecture(s) you added by hand are in this PDF and will be replaced by its rows");
        }
        if (preview.stillAbsent > 0) {
            effect.append("\n").append(preview.stillAbsent)
                    .append(" pending-leave lecture(s) still show as absent and stay pending");
        }
        if (preview.conflicts > 0) {
            effect.append("\n").append(preview.conflicts)
                    .append(" lecture(s) disagree with what you recorded; you'll choose after saving");
        }
        ((TextView) findViewById(R.id.txt_effect)).setText(effect.toString());
    }

    private void showSavedDialog(ImportSummary s) {
        StringBuilder msg = new StringBuilder();
        msg.append(s.inserted).append(" new, ").append(s.updated).append(" updated, ")
                .append(s.unchanged).append(" unchanged lectures.");
        if (!s.createdSubjects.isEmpty()) {
            msg.append("\n").append(s.createdSubjects.size()).append(" subjects added.");
        }
        if (s.mergedManual > 0) {
            msg.append("\n").append(s.mergedManual).append(" hand-added lectures matched to PDF rows.");
        }
        String reconciliation = s.reconciliationText();
        if (reconciliation != null) {
            msg.append("\n\n").append(reconciliation);
        }
        if (!s.conflicts.isEmpty()) {
            msg.append("\n\n").append(s.conflicts.size())
                    .append(" lecture(s) disagree with what you recorded. Your version is kept until you choose.");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Attendance saved")
                .setMessage(msg.toString())
                .setCancelable(false)
                .setPositiveButton(s.conflicts.isEmpty() ? "OK" : "Review", (d, w) -> {
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
                    ? "leave rejected" : Formats.statusLabel(c.yours).toLowerCase();
            items[i] = c.subjectName + " (" + Formats.component(c.component) + ")\n"
                    + Formats.date(c.dateMillis) + ", " + Formats.time(c.startTime)
                    + "\nYou: " + yours + " · PDF: " + c.pdf;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Tick where the PDF is right")
                .setMultiChoiceItems(items, usePdf, (d, which, checked) -> usePdf[which] = checked)
                .setCancelable(false)
                .setPositiveButton("Apply", (d, w) -> {
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.run(() -> {
                        for (int i = 0; i < conflicts.size(); i++) {
                            if (usePdf[i]) repo.acceptPdf(conflicts.get(i));
                        }
                    }, this::done);
                })
                .setNegativeButton("Keep mine", (d, w) -> done())
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

    private static String shortList(List<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numbers.size() && i < 10; i++) {
            if (i > 0) sb.append(", ");
            sb.append(numbers.get(i));
        }
        if (numbers.size() > 10) sb.append(" and ").append(numbers.size() - 10).append(" more");
        return sb.toString();
    }
}
