package io.github.fortunatechick.attendease;

import android.content.ClipData;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.splashscreen.SplashScreen;
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
        // The launcher activity starts on Theme.AttendEase.Starting; this shows the system
        // splash on every Android version and then switches to the normal theme.
        SplashScreen.installSplashScreen(this);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) acceptDroppedPdfs(findViewById(android.R.id.content));
        findViewById(R.id.btn_add_subject).setOnClickListener(v -> showAddSubjectDialog());
        findViewById(R.id.btn_portal).setOnClickListener(v -> SapPortal.showAndOpen(this));
        findViewById(R.id.btn_exit_guest).setOnClickListener(v -> confirmExitGuest());

        if (savedInstanceState == null) {
            if (!UiPrefs.welcomed(this)) showWelcome(); else CrashLog.offerReport(this);
        }

        boolean guest = GuestMode.isActive(this);
        findViewById(R.id.guest_banner).setVisibility(guest ? View.VISIBLE : View.GONE);
        if (guest) {
            ((TextView) findViewById(R.id.txt_empty_title)).setText(R.string.menu_main_guest_mode);
            ((TextView) findViewById(R.id.txt_empty_body)).setText(R.string.guest_empty_body);
            findViewById(R.id.btn_add_subject).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    // Dragging a PDF from Files (or a browser's downloads) onto the dashboard uploads it.
    @RequiresApi(Build.VERSION_CODES.N)
    private void acceptDroppedPdfs(View target) {
        target.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription() != null
                            && event.getClipDescription().hasMimeType("application/pdf");
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.6f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    v.setAlpha(1f);
                    ClipData clip = event.getClipData();
                    if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getUri() == null) {
                        return false;
                    }
                    // Read access lasts while this activity lives; the import reads it at once.
                    if (requestDragAndDropPermissions(event) == null) return false;
                    Intent intent = new Intent(this, ImportActivity.class)
                            .setAction(Intent.ACTION_VIEW)
                            .setDataAndType(clip.getItemAt(0).getUri(), "application/pdf")
                            .putExtra(ImportActivity.EXTRA_FROM_DROP, true)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                    return true;
                default:
                    return true;
            }
        });
    }

    @Override
    public void onDataChanged() {
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
            if (over > 0) parts.add(getString(R.string.footnote_over_limit, over));
            if (tight > 0) parts.add(getString(R.string.footnote_near_limit, tight));
            if (over == 0 && tight == 0 && !data.rows.isEmpty()) parts.add(getString(R.string.footnote_all_within));
            if (notUpdated > 0) parts.add(getString(R.string.footnote_not_marked, notUpdated));
            if (prefs.dataUpTo(semesterId) != null) parts.add(getString(R.string.footnote_pdf_to, prefs.dataUpTo(semesterId)));
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
        input.setHint(R.string.add_subject_hint);
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_main_add_subject)
                .setMessage(R.string.add_subject_message)
                .setView(box)
                .setPositiveButton(R.string.add_subject_action, (d, w) -> {
                    String name = input.getText().toString().trim().replaceAll("\\s+", " ");
                    if (name.isEmpty()) return;
                    AttendanceRepository repo = new AttendanceRepository(this);
                    Background.load(() -> {
                        Semester semester = repo.ensureCurrentSemester();
                        if (repo.subjects.findByName(semester.id, name) != null) return -1L;
                        return repo.subjects.insert(semester.id, name);
                    }, id -> {
                        if (id < 0) {
                            Toast.makeText(this, getString(R.string.add_subject_exists, name), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent intent = new Intent(this, SubjectDetailActivity.class);
                        intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, id);
                        startActivity(intent);
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // First launch only: what the app does, that it's unofficial, and that it stays offline.
    private void showWelcome() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_message)
                .setCancelable(false)
                .setPositiveButton(R.string.welcome_start, (d, w) -> UiPrefs.setWelcomed(this))
                .setNeutralButton(R.string.settings_about, (d, w) -> {
                    UiPrefs.setWelcomed(this);
                    startActivity(new Intent(this, AboutActivity.class));
                })
                .show();
    }

    private void openImport() {
        startActivity(new Intent(this, ImportActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_guest).setTitle(GuestMode.isActive(this) ? R.string.guest_exit_menu : R.string.menu_main_guest_mode);
        boolean dark = ThemeMode.isDark(this);
        MenuItem theme = menu.findItem(R.id.action_theme);
        theme.setIcon(dark ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
        theme.setTitle(dark ? R.string.theme_light : R.string.menu_main_dark_mode);
        return true;
    }

    // A friend checks their attendance on this phone. Their PDF goes into a separate,
    // blank database; the owner's data is not opened and can't be changed.
    private void confirmEnterGuest() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_main_guest_mode)
                .setMessage(R.string.guest_enter_message)
                .setPositiveButton(R.string.guest_start, (d, w) -> {
                    GuestMode.enter(this);
                    restart();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmExitGuest() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.guest_exit_title)
                .setMessage(R.string.guest_exit_message)
                .setPositiveButton(R.string.guest_exit_action, (d, w) -> {
                    GuestMode.exit(this);
                    restart();
                })
                .setNegativeButton(R.string.guest_stay, null)
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
