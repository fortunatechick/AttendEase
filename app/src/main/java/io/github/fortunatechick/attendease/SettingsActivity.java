package io.github.fortunatechick.attendease;

import android.view.View;
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

import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
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
        setTitle(R.string.menu_main_settings);
        showBackArrow();
        prefs = new Prefs(this);

        TextView txtThreshold = findViewById(R.id.txt_threshold);
        Slider slider = findViewById(R.id.slider_threshold);
        slider.setValue(prefs.threshold());
        txtThreshold.setText(getString(R.string.percent_value, prefs.threshold()));
        slider.addOnChangeListener((s, value, fromUser) -> {
            txtThreshold.setText(getString(R.string.percent_value, Math.round(value)));
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

        if (DynamicColors.isDynamicColorAvailable()) {
            findViewById(R.id.group_display).setVisibility(View.VISIBLE);
            MaterialSwitch dynamic = findViewById(R.id.switch_dynamic_color);
            dynamic.setChecked(ThemeMode.isDynamic(this));
            dynamic.setOnCheckedChangeListener((b, on) -> {
                ThemeMode.setDynamic(this, on);
                // Colours are applied as screens open, so reopen the app from the dashboard.
                Intent home = new Intent(this, MainActivity.class);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(home);
            });
        }

        findViewById(R.id.btn_check_updates).setOnClickListener(v -> SapPortal.openInBrowser(this, RELEASES_URL));
        findViewById(R.id.btn_about).setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btn_portal_url).setOnClickListener(v -> editPortalUrl());
        ((TextView) findViewById(R.id.txt_version)).setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        findViewById(R.id.btn_switch_semester).setOnClickListener(v -> switchSemester());
        findViewById(R.id.btn_new_semester).setOnClickListener(v -> newSemester());
        findViewById(R.id.btn_export).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btn_clear).setOnClickListener(v -> confirmClear());

        StringBuilder about = new StringBuilder(getString(R.string.settings_offline_note));
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
            if (s == null) return getString(R.string.settings_no_semester);
            int count = repo.semesters.subjectCount(s.id);
            int total = repo.semesters.all().size();
            String text = getString(R.string.dot_join, s.label,
                    getResources().getQuantityString(R.plurals.subjects, count, count));
            if (total > 1) text = getString(R.string.dot_join, text, getString(R.string.settings_semesters_stored, total));
            return text;
        }, text -> {
            if (!isDestroyed()) label.setText(text);
        });
    }

    private void switchSemester() {
        AttendanceRepository repo = new AttendanceRepository(this);
        Background.load(repo.semesters::all, all -> {
            if (isDestroyed()) return;
            if (all.size() < 2) {
                Toast.makeText(this, R.string.settings_only_one_semester, Toast.LENGTH_LONG).show();
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
                    .setTitle(R.string.settings_show_semester)
                    .setSingleChoiceItems(labels, checked, (d, which) -> {
                        repo.setCurrentSemester(all.get(which).id);
                        d.dismiss();
                        showCurrentSemester();
                    })
                    .show();
        });
    }

    // Where "SAP portal" in the ⋮ menu goes. Blank restores the default.
    private void editPortalUrl() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(UiPrefs.portalUrl(this));
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_portal_address)
                .setMessage(R.string.settings_portal_message)
                .setView(box)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    if (!UiPrefs.setPortalUrl(this, input.getText().toString())) {
                        Toast.makeText(this, R.string.settings_portal_invalid, Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton(R.string.settings_portal_default, (d, w) -> UiPrefs.setPortalUrl(this, ""))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // For starting a semester by hand before its first PDF.
    private void newSemester() {
        EditText input = new EditText(this);
        input.setHint(R.string.settings_new_semester_hint);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_new_semester)
                .setMessage(R.string.settings_new_semester_message)
                .setView(box)
                .setPositiveButton(R.string.settings_create, (d, w) -> {
                    String label = input.getText().toString().trim();
                    if (label.isEmpty()) return;
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.run(() -> {
                        Semester existing = repo.semesters.byLabel(label);
                        Semester s = existing != null ? existing : repo.semesters.insert(label);
                        repo.setCurrentSemester(s.id);
                    }, this::showCurrentSemester);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // Shows what the semester length means for the two common timetables.
    private static void showWeeks(TextView label, TextView hint, int weeks, int threshold) {
        android.content.res.Resources r = label.getResources();
        label.setText(r.getQuantityString(R.plurals.weeks, weeks, weeks));
        int four = 4 * weeks, three = 3 * weeks;
        hint.setText(r.getString(R.string.settings_weeks_hint, four, four * (100 - threshold) / 100,
                three, three * (100 - threshold) / 100));
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
                Toast.makeText(this, R.string.settings_export_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/csv");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_export_subject));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.settings_export_chooser)));
        });
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_clear_title)
                .setMessage(R.string.settings_clear_message)
                .setPositiveButton(R.string.settings_clear_action, (d, w) -> {
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.run(() -> {
                        repo.helper.clearAll();
                        prefs.clearProfile();
                    }, () -> {
                        Toast.makeText(this, R.string.settings_cleared, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
