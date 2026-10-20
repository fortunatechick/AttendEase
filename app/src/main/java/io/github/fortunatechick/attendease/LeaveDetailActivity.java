package io.github.fortunatechick.attendease;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.fortunatechick.attendease.calc.AttendanceCalculator;
import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.LeaveRepository;
import io.github.fortunatechick.attendease.data.LeaveRequest;
import io.github.fortunatechick.attendease.data.LeaveRules;
import io.github.fortunatechick.attendease.data.Prefs;
import io.github.fortunatechick.attendease.data.Subject;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LeaveDetailActivity extends BaseActivity {

    public static final String EXTRA_LEAVE_ID = "leave_id";

    private static class DetailData {
        LeaveRequest request;
        List<Lecture> lectures = new ArrayList<>();
        Map<Long, String> decisions = new HashMap<>();
        Map<Long, String> subjectNames = new HashMap<>();
        Map<String, AttendanceStats> effect = new LinkedHashMap<>();
    }

    private long leaveId;
    private LeaveRepository leaves;
    private DetailData data;
    private View header;
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private boolean noteLoaded;
    private static final String KEY_NOTE = "note";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_detail);   // toolbar + list
        setTitle(R.string.leave_request_title);
        showBackArrow();

        leaveId = getIntent().getLongExtra(EXTRA_LEAVE_ID, -1);
        leaves = new LeaveRepository(this);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        header = LayoutInflater.from(this).inflate(R.layout.item_leave_header, list, false);
        if (savedInstanceState != null && savedInstanceState.getString(KEY_NOTE) != null) {
            ((EditText) header.findViewById(R.id.edit_note)).setText(savedInstanceState.getString(KEY_NOTE));
            noteLoaded = true;
        }

        header.findViewById(R.id.btn_approve_all).setOnClickListener(v ->
                Background.run(() -> {
                    leaves.decideAll(leaveId, LeaveRules.APPROVED);
                }, this::load));
        header.findViewById(R.id.btn_reject_all).setOnClickListener(v ->
                Background.run(() -> {
                    leaves.decideAll(leaveId, LeaveRules.REJECTED);
                }, this::load));
        header.findViewById(R.id.btn_save_note).setOnClickListener(v -> {
            String note = ((EditText) header.findViewById(R.id.edit_note)).getText().toString().trim();
            Background.run(() -> leaves.saveNote(leaveId, note),
                    () -> Toast.makeText(this, R.string.leave_note_saved, Toast.LENGTH_SHORT).show());
        });

        adapter = new LeaveLectureAdapter();
        list.setAdapter(adapter);
    }

    // The note box sits in the list header, outside the saved view tree: keep what was typed.
    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY_NOTE, ((EditText) header.findViewById(R.id.edit_note)).getText().toString());
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
        AttendanceRepository repo = new AttendanceRepository(this);
        AttendanceCalculator calc = new Prefs(this).calculator();
        Background.load(() -> {
            DetailData d = new DetailData();
            d.request = leaves.leaves.byId(leaveId);
            if (d.request == null) return d;
            d.lectures = leaves.lecturesFor(leaveId);
            for (LeaveRequest.Link link : leaves.leaves.links(leaveId)) {
                d.decisions.put(link.lectureId, link.decision);
            }
            for (Subject s : repo.subjects.all()) d.subjectNames.put(s.id, s.displayName());
            for (Lecture l : d.lectures) {
                String name = d.subjectNames.get(l.subjectId);
                if (!d.effect.containsKey(name)) {
                    d.effect.put(name, calc.stats(repo.lectures.forSubject(l.subjectId)));
                }
            }
            return d;
        }, d -> {
            if (isDestroyed()) return;
            if (d.request == null) {
                finish();
                return;
            }
            data = d;
            bindHeader();
            adapter.notifyDataSetChanged();
        });
    }

    private void bindHeader() {
        LeaveRequest r = data.request;
        ((TextView) header.findViewById(R.id.txt_range)).setText(LeaveFormats.range(this, r));
        Formats.tintChip(header.findViewById(R.id.chip_status), LeaveFormats.statusLabel(this, r.status),
                LeaveFormats.statusColor(this, r.status));

        StringBuilder info = new StringBuilder();
        if (r.reason != null && !r.reason.isEmpty()) info.append(r.reason).append('\n');
        info.append(getString(R.string.leave_submitted_to_on, LeaveFormats.submittedTo(this, r),
                Formats.date(r.submittedOn))).append('\n');
        int pending = 0, approved = 0, rejected = 0;
        for (String d : data.decisions.values()) {
            if (LeaveRules.APPROVED.equals(d)) approved++;
            else if (LeaveRules.REJECTED.equals(d)) rejected++;
            else pending++;
        }
        info.append(getString(R.string.leave_decision_counts, Formats.lectures(this, data.lectures.size()),
                pending, approved, rejected));
        ((TextView) header.findViewById(R.id.txt_info)).setText(info.toString());

        StringBuilder effect = new StringBuilder();
        for (Map.Entry<String, AttendanceStats> e : data.effect.entrySet()) {
            AttendanceStats s = e.getValue();
            effect.append(e.getKey()).append(": ").append(s.displayText());
            if (s.hasPending()) effect.append(" (").append(Formats.pending(this, s)).append(")");
            effect.append('\n');
        }
        ((TextView) header.findViewById(R.id.txt_effect)).setText(effect.toString().trim());

        // Only fill the note once so a reload doesn't wipe what the student is typing.
        if (!noteLoaded) {
            ((EditText) header.findViewById(R.id.edit_note)).setText(r.note);
            noteLoaded = true;
        }
    }

    private class LeaveLectureAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            return data == null ? 0 : data.lectures.size() + 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                if (header.getParent() != null) ((ViewGroup) header.getParent()).removeView(header);
                return new RecyclerView.ViewHolder(header) { };
            }
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_leave_lecture, parent, false)) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) return;
            Lecture l = data.lectures.get(position - 1);
            View v = holder.itemView;
            ((TextView) v.findViewById(R.id.txt_subject)).setText(getString(R.string.dot_join,
                    data.subjectNames.get(l.subjectId), Formats.component(v.getContext(), l.component)));
            ((TextView) v.findViewById(R.id.txt_detail)).setText(getString(R.string.leave_lecture_detail,
                    Formats.date(l.dateMillis), Formats.time(l.startTime),
                    l.sourceStatus == null ? "–" : l.sourceStatus));

            MaterialButtonToggleGroup toggle = v.findViewById(R.id.toggle_decision);
            toggle.clearOnButtonCheckedListeners();
            String decision = data.decisions.get(l.id);
            if (LeaveRules.APPROVED.equals(decision)) toggle.check(R.id.btn_approved);
            else if (LeaveRules.REJECTED.equals(decision)) toggle.check(R.id.btn_rejected);
            else toggle.check(R.id.btn_pending);

            toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                String chosen = checkedId == R.id.btn_approved ? LeaveRules.APPROVED
                        : checkedId == R.id.btn_rejected ? LeaveRules.REJECTED : LeaveRules.PENDING;
                if (chosen.equals(data.decisions.get(l.id))) return;
                Background.run(() -> {
                    leaves.decide(leaveId, l.id, chosen);
                }, LeaveDetailActivity.this::load);
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.leave_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_withdraw) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.leave_withdraw_title)
                    .setMessage(R.string.leave_withdraw_message)
                    .setPositiveButton(R.string.leave_withdraw_action, (d, w) ->
                            Background.run(() -> {
                                leaves.withdraw(leaveId);
                            }, this::finish))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
