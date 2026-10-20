package io.github.fortunatechick.attendease;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.fortunatechick.attendease.data.LeaveRepository;
import io.github.fortunatechick.attendease.data.LeaveRequest;

import java.util.ArrayList;
import java.util.List;

public class LeavesActivity extends BaseActivity {

    private static class Row {
        LeaveRequest request;
        int lectureCount;
        List<String> subjects;
    }

    private final List<Row> rows = new ArrayList<>();
    private RecyclerView.Adapter<RowHolder> adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaves);
        setTitle(R.string.menu_main_medical_leave);
        showBackArrow();

        empty = findViewById(R.id.txt_empty);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<RowHolder>() {
            @NonNull
            @Override
            public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RowHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_leave, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull RowHolder holder, int position) {
                holder.bind(rows.get(position));
            }

            @Override
            public int getItemCount() {
                return rows.size();
            }
        };
        list.setAdapter(adapter);

        findViewById(R.id.fab_new).setOnClickListener(v ->
                startActivity(new Intent(this, NewLeaveActivity.class)));
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
        LeaveRepository repo = new LeaveRepository(this);
        Background.load(() -> {
            List<Row> loaded = new ArrayList<>();
            for (LeaveRequest r : repo.leaves.all()) {
                Row row = new Row();
                row.request = r;
                row.lectureCount = repo.leaves.links(r.id).size();
                row.subjects = repo.leaves.subjectNames(r.id);
                loaded.add(row);
            }
            return loaded;
        }, loaded -> {
            if (isDestroyed()) return;
            rows.clear();
            rows.addAll(loaded);
            adapter.notifyDataSetChanged();
            empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    class RowHolder extends RecyclerView.ViewHolder {
        final TextView range, status, lectures, subjects;

        RowHolder(View v) {
            super(v);
            range = v.findViewById(R.id.txt_range);
            status = v.findViewById(R.id.chip_status);
            lectures = v.findViewById(R.id.txt_lectures);
            subjects = v.findViewById(R.id.txt_subjects);
        }

        void bind(Row row) {
            LeaveRequest r = row.request;
            Context context = itemView.getContext();
            range.setText(LeaveFormats.range(context, r));
            Formats.tintChip(status, LeaveFormats.statusLabel(context, r.status), LeaveFormats.statusColor(context, r.status));
            lectures.setText(context.getString(R.string.leaves_row_detail,
                    Formats.lectures(context, row.lectureCount), LeaveFormats.submittedTo(context, r)));
            subjects.setText(TextUtils.join(", ", row.subjects));
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(LeavesActivity.this, LeaveDetailActivity.class);
                intent.putExtra(LeaveDetailActivity.EXTRA_LEAVE_ID, r.id);
                startActivity(intent);
            });
        }
    }
}
