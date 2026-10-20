package io.github.fortunatechick.attendease;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import io.github.fortunatechick.attendease.calc.ApprovalConvention;
import io.github.fortunatechick.attendease.calc.AttendanceCalculator;
import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.calc.CsvExporter;
import io.github.fortunatechick.attendease.calc.SemesterBudget;
import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.GuestMode;
import io.github.fortunatechick.attendease.data.Prefs;
import io.github.fortunatechick.attendease.data.Semester;
import io.github.fortunatechick.attendease.data.Subject;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    // No network access in the app: updates are downloaded from the Releases page in the browser.
    private static final String RELEASES_URL = "https://github.com/fortunatechick/AttendEase/releases/latest";

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("Settings");
        showBackArrow();
        prefs = new Prefs(this);

        TextView txtThreshold = findViewById(R.id.txt_threshold);
        Slider slider = findViewById(R.id.slider_threshold);
        slider.setValue(prefs.threshold());
        txtThreshold.setText(prefs.threshold() + "%");
        slider.addOnChangeListener((s, value, fromUser) -> {
            txtThreshold.setText(Math.round(value) + "%");
            if (fromUser) prefs.setThreshold(Math.round(value));
        });

        TextView txtWeeks = findViewById(R.id.txt_weeks);
        TextView txtWeeksHint = findViewById(R.id.txt_weeks_hint);
        Slider weeks = findViewById(R.id.slider_weeks);
        weeks.setValue(prefs.semesterWeeks());
        showWeeks(txtWeeks, txtWeeksHint, prefs.semesterWeeks(), prefs.threshold());
        weeks.addOnChangeListener((s, value, fromUser) -> {
            int w = Math.round(value);
            showWeeks(txtWeeks, txtWeeksHint, w, prefs.threshold());
            if (fromUser) prefs.setSemesterWeeks(w);
        });
        slider.addOnChangeListener((s, value, fromUser) ->
                showWeeks(txtWeeks, txtWeeksHint, Math.round(weeks.getValue()), Math.round(value)));

        RadioGroup convention = findViewById(R.id.group_convention);
        convention.check(prefs.convention() == ApprovalConvention.EXCLUDED_FROM_TOTAL
                ? R.id.radio_excluded : R.id.radio_counted);
        convention.setOnCheckedChangeListener((group, checkedId) -> prefs.setConvention(
                checkedId == R.id.radio_excluded
                        ? ApprovalConvention.EXCLUDED_FROM_TOTAL : ApprovalConvention.COUNTED_AS_ATTENDED));

        findViewById(R.id.btn_check_updates).setOnClickListener(v -> SapPortal.openInBrowser(this, RELEASES_URL));
        ((TextView) findViewById(R.id.txt_version)).setText("AttendEase " + BuildConfig.VERSION_NAME);

        findViewById(R.id.btn_switch_semester).setOnClickListener(v -> switchSemester());
        findViewById(R.id.btn_new_semester).setOnClickListener(v -> newSemester());
        findViewById(R.id.btn_export).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btn_clear).setOnClickListener(v -> confirmClear());

        StringBuilder about = new StringBuilder("AttendEase works fully offline. Nothing leaves this phone unless you share an export.");
        if (prefs.studentName() != null) {
            about.append("\n\n").append(prefs.studentName());
            if (prefs.rollNo() != null) about.append(" · ").append(prefs.rollNo());
        }
        ((TextView) findViewById(R.id.txt_about)).setText(about.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        showCurrentSemester();
    }

    private void showCurrentSemester() {
        AttendanceRepository repo = new AttendanceRepository(this);
        TextView label = findViewById(R.id.txt_current_semester);
        Background.load(() -> {
            Semester s = repo.currentSemester();
            if (s == null) return "No semester yet: upload a PDF or add a subject";
            int count = repo.semesters.subjectCount(s.id);
            int total = repo.semesters.all().size();
            return s.label + " · " + Formats.plural(count, "subject", "subjects")
                    + (total > 1 ? " · " + total + " semesters stored" : "");
        }, text -> {
            if (!isDestroyed()) label.setText(text);
        });
    }

    private void switchSemester() {
        AttendanceRepository repo = new AttendanceRepository(this);
        Background.load(repo.semesters::all, all -> {
            if (isDestroyed()) return;
            if (all.size() < 2) {
                Toast.makeText(this, "Only one semester so far. Uploading a PDF from another semester adds one.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Semester current = repo.currentSemester();
            String[] labels = new String[all.size()];
            int checked = 0;
            for (int i = 0; i < all.size(); i++) {
                labels[i] = all.get(i).label;
                if (current != null && all.get(i).id == current.id) checked = i;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Show semester")
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        repo.setCurrentSemester(all.get(which).id);
                        d.dismiss();
                        showCurrentSemester();
                    })
                    .show();
        });
    }

    // For starting a semester by hand before its first PDF.
    private void newSemester() {
        EditText input = new EditText(this);
        input.setHint("e.g. 2027-2028, Semester VI");
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle("New semester")
                .setMessage("Use the same wording as the PDF header (Academic Year & Academic Session) "
                        + "so PDFs you upload later land in it.")
                .setView(box)
                .setPositiveButton("Create", (d, w) -> {
                    String label = input.getText().toString().trim();
                    if (label.isEmpty()) return;
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.run(() -> {
                        Semester existing = repo.semesters.byLabel(label);
                        Semester s = existing != null ? existing : repo.semesters.insert(label);
                        repo.setCurrentSemester(s.id);
                    }, this::showCurrentSemester);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Shows what the semester length means for the two common timetables.
    private static void showWeeks(TextView label, TextView hint, int weeks, int threshold) {
        label.setText(weeks + " weeks");
        int four = 4 * weeks, three = 3 * weeks;
        hint.setText("Absences are counted over the whole semester, theory and lab together. "
                + "4 lectures a week = " + four + " lectures, " + four * (100 - threshold) / 100 + " misses allowed; "
                + "3 a week = " + three + ", " + three * (100 - threshold) / 100 + " allowed.");
    }

    private void exportCsv() {
        AttendanceRepository repo = new AttendanceRepository(this);
        AttendanceCalculator calc = prefs.calculator();
        Background.load(() -> {
            List<CsvExporter.Row> rows = new ArrayList<>();
            Semester semester = repo.currentSemester();
            if (semester == null) return null;
            long start = repo.lectures.semesterStart(semester.id);
            for (Subject s : repo.subjects.forSemester(semester.id)) {
                List<Lecture> lectures = repo.lectures.forSubject(s.id);
                AttendanceCalculator.SubjectSummary sum = calc.summarise(s.id, lectures);
                SemesterBudget budget = start < 0 ? null : calc.budget(s, lectures, start);
                if (budget != null && !budget.isKnown()) budget = null;
                rows.add(new CsvExporter.Row(s.displayName(), "Combined", sum.combined, budget));
                if (sum.theory.hasData()) rows.add(new CsvExporter.Row(s.displayName(), "Theory", sum.theory));
                if (sum.practical.hasData()) rows.add(new CsvExporter.Row(s.displayName(), "Practical", sum.practical));
            }
            AttendanceStats overall = calc.stats(repo.lectures.forSemester(semester.id));
            String csv = CsvExporter.build(rows, overall);

            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) return null;
            String stamp = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            File file = new File(dir, "attendance_" + stamp + ".csv");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(csv);
            } catch (IOException e) {
                return null;
            }
            return file;
        }, file -> {
            if (file == null) {
                Toast.makeText(this, "Couldn't write the export file.", Toast.LENGTH_LONG).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/csv");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "Attendance summary");
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share attendance CSV"));
        });
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear all data?")
                .setMessage("Every uploaded lecture, subject and leave request is deleted. "
                        + "Your settings stay. This can't be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.run(() -> {
                        repo.helper.clearAll();
                        prefs.clearProfile();
                    }, () -> {
                        Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
