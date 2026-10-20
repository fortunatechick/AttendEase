package io.github.fortunatechick.attendease;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.LeaveRepository;
import io.github.fortunatechick.attendease.data.LeaveRequest;
import io.github.fortunatechick.attendease.data.LeaveRules;
import io.github.fortunatechick.attendease.data.Status;
import io.github.fortunatechick.attendease.data.Subject;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

// Two steps: the request details, then the lectures it covers.
public class NewLeaveActivity extends BaseActivity {

    private static final String KEY_FROM = "from";
    private static final String KEY_TO = "to";
    private static final String KEY_SUBMITTED = "submitted";
    private static final String KEY_STEP = "step";
    private static final String KEY_SELECTED = "selected";

    private long fromDate, toDate, submittedOn;

    private View stepDetails, stepLectures;
    private Button btnFrom, btnTo, btnSubmittedOn;
    private EditText editReason;
    private RadioGroup groupSubmittedTo;
    private TextView txtSelection;

    // Step 2 state
    private final List<Object> items = new ArrayList<>();   // String date headers and Lecture rows
    private final List<Lecture> rangeLectures = new ArrayList<>();
    private final Map<Long, String> subjectNames = new HashMap<>();
    private final Set<Long> selected = new HashSet<>();
    private SelectionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_leave);
        setTitle("New leave request");
        showBackArrow();

        stepDetails = findViewById(R.id.step_details);
        stepLectures = findViewById(R.id.step_lectures);
        btnFrom = findViewById(R.id.btn_from);
        btnTo = findViewById(R.id.btn_to);
        btnSubmittedOn = findViewById(R.id.btn_submitted_on);
        editReason = findViewById(R.id.edit_reason);
        groupSubmittedTo = findViewById(R.id.group_submitted_to);
        txtSelection = findViewById(R.id.txt_selection);

        btnFrom.setOnClickListener(v -> pickDate("From", fromDate, d -> {
            fromDate = d;
            if (toDate < fromDate) toDate = fromDate;
            showDates();
        }));
        btnTo.setOnClickListener(v -> pickDate("To", toDate, d -> {
            toDate = d;
            if (fromDate > toDate) fromDate = toDate;
            showDates();
        }));
        btnSubmittedOn.setOnClickListener(v -> pickDate("Submitted on", submittedOn, d -> {
            submittedOn = d;
            showDates();
        }));
        findViewById(R.id.btn_next).setOnClickListener(v -> loadLecturesInRange(null));

        RecyclerView list = findViewById(R.id.list_lectures);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SelectionAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.btn_select_all).setOnClickListener(v -> {
            for (Lecture l : rangeLectures) if (LeaveRules.canBeAddedToLeave(l)) selected.add(l.id);
            selectionChanged();
        });
        findViewById(R.id.btn_select_none).setOnClickListener(v -> {
            selected.clear();
            selectionChanged();
        });
        findViewById(R.id.btn_by_subject).setOnClickListener(v -> selectBySubject());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (stepLectures.getVisibility() == View.VISIBLE) {
                    showStep(1);
                } else {
                    finish();
                }
            }
        });

        if (savedInstanceState != null) {
            fromDate = savedInstanceState.getLong(KEY_FROM);
            toDate = savedInstanceState.getLong(KEY_TO);
            submittedOn = savedInstanceState.getLong(KEY_SUBMITTED);
            showDates();
            if (savedInstanceState.getInt(KEY_STEP) == 2) {
                long[] ids = savedInstanceState.getLongArray(KEY_SELECTED);
                Set<Long> restored = new HashSet<>();
                if (ids != null) for (long id : ids) restored.add(id);
                loadLecturesInRange(restored);
            }
        } else {
            submittedOn = todayUtc();
            fromDate = toDate = todayUtc();
            showDates();
            suggestLatestAbsence();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong(KEY_FROM, fromDate);
        out.putLong(KEY_TO, toDate);
        out.putLong(KEY_SUBMITTED, submittedOn);
        out.putInt(KEY_STEP, stepLectures.getVisibility() == View.VISIBLE ? 2 : 1);
        long[] ids = new long[selected.size()];
        int i = 0;
        for (long id : selected) ids[i++] = id;
        out.putLongArray(KEY_SELECTED, ids);
    }

    // Start the range on the most recent absence, the likeliest reason to be here.
    private void suggestLatestAbsence() {
        AttendanceRepository repo = new AttendanceRepository(this);
        Background.load(() -> {
            long latest = -1;
            for (Lecture l : repo.lectures.all()) {
                if (Status.ABSENT.equals(l.effectiveStatus)) latest = Math.max(latest, l.dateMillis);
            }
            return latest;
        }, latest -> {
            if (isDestroyed() || latest < 0) return;
            fromDate = toDate = latest;
            showDates();
        });
    }

    private void showDates() {
        btnFrom.setText("From  " + Formats.shortDate(fromDate));
        btnTo.setText("To  " + Formats.shortDate(toDate));
        btnSubmittedOn.setText(Formats.date(submittedOn));
    }

    private void pickDate(String title, long current, LongCallback onPicked) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_date_picker, null);
        DatePicker picker = view.findViewById(R.id.date_picker);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(current);
        picker.updateDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton("OK", (d, w) ->
                        onPicked.accept(utcMidnight(picker.getYear(), picker.getMonth(), picker.getDayOfMonth())))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadLecturesInRange(Set<Long> restoreSelection) {
        AttendanceRepository repo = new AttendanceRepository(this);
        long from = fromDate, to = toDate;
        Background.load(() -> {
            RangeData d = new RangeData();
            for (Subject s : repo.subjects.all()) d.names.put(s.id, s.displayName());
            d.lectures = repo.lectures.inDateRange(from, to);
            return d;
        }, data -> {
            if (isDestroyed()) return;
            List<Lecture> lectures = data.lectures;
            if (lectures.isEmpty()) {
                Toast.makeText(this, "No lectures recorded between those dates. "
                        + "Upload a newer PDF or add the lectures by hand first.", Toast.LENGTH_LONG).show();
                return;
            }
            subjectNames.clear();
            subjectNames.putAll(data.names);
            rangeLectures.clear();
            rangeLectures.addAll(lectures);

            items.clear();
            long lastDate = -1;
            for (Lecture l : lectures) {
                if (l.dateMillis != lastDate) {
                    items.add(Formats.date(l.dateMillis));
                    lastDate = l.dateMillis;
                }
                items.add(l);
            }

            selected.clear();
            if (restoreSelection != null) {
                selected.addAll(restoreSelection);
            } else {
                // Every lecture in the range starts ticked; the student unticks what the leave doesn't cover.
                for (Lecture l : lectures) if (LeaveRules.canBeAddedToLeave(l)) selected.add(l.id);
            }
            showStep(2);
            selectionChanged();
        });
    }

    private static class RangeData {
        final Map<Long, String> names = new HashMap<>();
        List<Lecture> lectures;
    }

    private void selectBySubject() {
        Map<Long, String> inRange = new LinkedHashMap<>();
        for (Lecture l : rangeLectures) {
            if (LeaveRules.canBeAddedToLeave(l)) inRange.put(l.subjectId, subjectNames.get(l.subjectId));
        }
        if (inRange.isEmpty()) return;
        List<Long> ids = new ArrayList<>(inRange.keySet());
        String[] labels = new String[ids.size()];
        boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            labels[i] = inRange.get(ids.get(i));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select lectures of")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Select", (d, w) -> {
                    Set<Long> chosenSubjects = new HashSet<>();
                    for (int i = 0; i < ids.size(); i++) if (checked[i]) chosenSubjects.add(ids.get(i));
                    selected.clear();
                    for (Lecture l : rangeLectures) {
                        if (chosenSubjects.contains(l.subjectId) && LeaveRules.canBeAddedToLeave(l)) {
                            selected.add(l.id);
                        }
                    }
                    selectionChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void selectionChanged() {
        int absent = 0;
        for (Lecture l : rangeLectures) {
            if (selected.contains(l.id) && Status.ABSENT.equals(l.effectiveStatus)) absent++;
        }
        txtSelection.setText(selected.size() + " of " + rangeLectures.size() + " lectures selected"
                + (absent > 0 ? " · " + absent + " marked absent" : ""));
        adapter.notifyDataSetChanged();
    }

    private void showStep(int step) {
        stepDetails.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        stepLectures.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        setTitle(step == 1 ? "New leave request" : "Lectures covered");
    }

    private void save() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Tick at least one lecture the leave covers.", Toast.LENGTH_SHORT).show();
            return;
        }
        LeaveRequest r = new LeaveRequest();
        r.fromDate = fromDate;
        r.toDate = toDate;
        r.reason = editReason.getText().toString().trim();
        r.submittedTo = groupSubmittedTo.getCheckedRadioButtonId() == R.id.radio_coordinator
                ? LeaveRequest.TO_COORDINATOR : LeaveRequest.TO_HOD;
        r.submittedOn = submittedOn;

        List<Long> ids = new ArrayList<>();
        for (Lecture l : rangeLectures) if (selected.contains(l.id)) ids.add(l.id);

        LeaveRepository repo = new LeaveRepository(this);
        findViewById(R.id.btn_save).setEnabled(false);
        Background.run(() -> {
            repo.create(r, ids);
        }, () -> {
            Toast.makeText(this, Formats.plural(ids.size(), "lecture is", "lectures are")
                    + " now awaiting a decision", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private static long todayUtc() {
        Calendar local = Calendar.getInstance();
        return utcMidnight(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
    }

    private static long utcMidnight(int year, int month, int day) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month, day);
        return c.getTimeInMillis();
    }

    private class SelectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? 0 : 1;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 0) {
                return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_date_header, parent, false)) { };
            }
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_select_lecture, parent, false)) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);
            if (item instanceof String) {
                ((TextView) holder.itemView).setText((String) item);
                return;
            }
            Lecture l = (Lecture) item;
            View v = holder.itemView;
            boolean eligible = LeaveRules.canBeAddedToLeave(l);

            CheckBox check = v.findViewById(R.id.check);
            check.setChecked(selected.contains(l.id));
            check.setEnabled(eligible);
            ((TextView) v.findViewById(R.id.txt_subject)).setText(subjectNames.get(l.subjectId));
            String detail = Formats.component(l.component) + " · " + Formats.time(l.startTime)
                    + (eligible ? "" : " · already in a leave request");
            ((TextView) v.findViewById(R.id.txt_detail)).setText(detail);
            Formats.tintChip(v.findViewById(R.id.chip_status), Formats.statusLabel(l.effectiveStatus),
                    Formats.statusColor(v.getContext(), l.effectiveStatus));
            v.setAlpha(eligible ? 1f : 0.5f);
            v.setOnClickListener(eligible ? x -> {
                if (!selected.remove(l.id)) selected.add(l.id);
                selectionChanged();
            } : null);
        }
    }
}
