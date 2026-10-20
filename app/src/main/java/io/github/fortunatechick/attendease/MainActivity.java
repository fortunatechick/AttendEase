package io.github.fortunatechick.attendease;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.fortunatechick.attendease.calc.AttendanceCalculator;
import io.github.fortunatechick.attendease.calc.SemesterBudget;
import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.GuestMode;
import io.github.fortunatechick.attendease.data.Prefs;
import io.github.fortunatechick.attendease.data.Semester;
import io.github.fortunatechick.attendease.data.Subject;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {

    private static class DashboardData {
        DashboardAdapter.Header header;
        List<DashboardAdapter.SubjectRow> rows;
    }

    private DashboardAdapter adapter;
    private RecyclerView list;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        list = findViewById(R.id.list);
        emptyState = findViewById(R.id.empty_state);

        adapter = new DashboardAdapter(subject -> {
            Intent intent = new Intent(this, SubjectDetailActivity.class);
            intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, subject.id);
            startActivity(intent);
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.btn_import).setOnClickListener(v -> openImport());
        findViewById(R.id.btn_add_subject).setOnClickListener(v -> showAddSubjectDialog());
        findViewById(R.id.btn_portal).setOnClickListener(v -> SapPortal.showAndOpen(this));
        findViewById(R.id.btn_exit_guest).setOnClickListener(v -> confirmExitGuest());

        boolean guest = GuestMode.isActive(this);
        findViewById(R.id.guest_banner).setVisibility(guest ? View.VISIBLE : View.GONE);
        if (guest) {
            ((TextView) findViewById(R.id.txt_empty_title)).setText("Guest mode");
            ((TextView) findViewById(R.id.txt_empty_body)).setText(
                    "Upload your friend's attendance PDF to see their figures. It's kept apart from "
                            + "this phone owner's data and erased when guest mode ends.");
            findViewById(R.id.btn_add_subject).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        AttendanceRepository repo = new AttendanceRepository(this);
        Prefs prefs = new Prefs(this);
        Background.load(() -> {
            AttendanceCalculator calc = prefs.calculator();
            Semester semester = repo.currentSemester();
            List<Lecture> all = semester == null ? new ArrayList<Lecture>() : repo.lectures.forSemester(semester.id);

            DashboardData data = new DashboardData();
            data.header = new DashboardAdapter.Header();
            data.header.overall = calc.stats(all);
            long semesterId = semester == null ? -1 : semester.id;
            String name = prefs.studentName(semesterId);
            String roll = prefs.rollNo(semesterId);
            data.header.student = name == null ? roll : (roll == null ? name : name + " · " + roll);
            String program = prefs.program(semesterId);
            String label = semester == null ? null : semester.label;
            data.header.program = program == null ? label : (label == null ? program : program + " · " + label);
            int notUpdated = data.header.overall.notUpdated;

            data.rows = new ArrayList<>();
            long start = semester == null ? -1 : repo.lectures.semesterStart(semester.id);
            int over = 0, tight = 0;
            List<Subject> subjectList = semester == null ? new ArrayList<Subject>() : repo.subjects.forSemester(semester.id);
            for (Subject s : subjectList) {
                List<Lecture> lectures = repo.lectures.forSubject(s.id);
                SemesterBudget budget = start < 0 ? null : calc.budget(s, lectures, start);
                if (budget != null && !budget.isKnown()) budget = null;
                if (budget != null && budget.isOverLimit()) over++;
                else if (budget != null && budget.canStillMiss() <= 2) tight++;
                data.rows.add(new DashboardAdapter.SubjectRow(s, calc.stats(lectures), budget));
            }
            // One short line: "1 over limit · 2 near limit · PDF to 11.10.2026"
            List<String> parts = new ArrayList<>();
            if (over > 0) parts.add(over + " over limit");
            if (tight > 0) parts.add(tight + " near limit");
            if (over == 0 && tight == 0 && !data.rows.isEmpty()) parts.add("All within limit");
            if (notUpdated > 0) parts.add(notUpdated + " not marked yet");
            if (prefs.dataUpTo(semesterId) != null) parts.add("PDF to " + prefs.dataUpTo(semesterId));
            data.header.footnote = parts.isEmpty() ? null : android.text.TextUtils.join(" · ", parts);
            return data;
        }, data -> {
            if (isDestroyed()) return;
            boolean empty = data.rows.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
            adapter.setData(data.header, data.rows);
        });
    }

    // A subject typed in by hand. If its name matches the PDF's later (or is a truncated
    // form of it), imported lectures join it instead of creating a second subject.
    private void showAddSubjectDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("e.g. Operating Systems");
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add subject")
                .setMessage("Use the name as it appears on the SAP portal so a PDF you upload later joins up with it.")
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    String name = input.getText().toString().trim().replaceAll("\\s+", " ");
                    if (name.isEmpty()) return;
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.load(() -> {
                        Semester semester = repo.ensureCurrentSemester();
                        if (repo.subjects.findByName(semester.id, name) != null) return -1L;
                        return repo.subjects.insert(semester.id, name);
                    }, id -> {
                        if (id < 0) {
                            Toast.makeText(this, name + " already exists", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent intent = new Intent(this, SubjectDetailActivity.class);
                        intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, id);
                        startActivity(intent);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openImport() {
        startActivity(new Intent(this, ImportActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_guest).setTitle(GuestMode.isActive(this) ? "Exit guest mode" : "Guest mode");
        boolean dark = ThemeMode.isDark(this);
        MenuItem theme = menu.findItem(R.id.action_theme);
        theme.setIcon(dark ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
        theme.setTitle(dark ? "Light mode" : "Dark mode");
        return true;
    }

    // A friend checks their attendance on this phone. Their PDF goes into a separate,
    // blank database; the owner's data is not opened and can't be changed.
    private void confirmEnterGuest() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Guest mode")
                .setMessage("For a friend to check their own attendance on this phone.\n\n"
                        + "It starts blank: they upload their PDF and see their figures. Your attendance, "
                        + "leaves and settings stay hidden and untouched. Everything they upload is erased "
                        + "when guest mode ends.")
                .setPositiveButton("Start guest mode", (d, w) -> {
                    GuestMode.enter(this);
                    restart();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmExitGuest() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit guest mode?")
                .setMessage("Your friend's uploaded attendance is erased and your own data comes back.")
                .setPositiveButton("Exit and erase", (d, w) -> {
                    GuestMode.exit(this);
                    restart();
                })
                .setNegativeButton("Stay", null)
                .show();
    }

    // Reopens the dashboard so every screen picks up the switched data.
    private void restart() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_theme) {
            ThemeMode.toggle(this);
            return true;
        } else if (item.getItemId() == R.id.action_portal) {
            SapPortal.showAndOpen(this);
            return true;
        } else if (item.getItemId() == R.id.action_guest) {
            if (GuestMode.isActive(this)) confirmExitGuest(); else confirmEnterGuest();
            return true;
        } else if (item.getItemId() == R.id.action_import) {
            openImport();
            return true;
        } else if (item.getItemId() == R.id.action_leaves) {
            startActivity(new Intent(this, LeavesActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_add_subject) {
            showAddSubjectDialog();
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
