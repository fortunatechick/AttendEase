package io.github.fortunatechick.attendease;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.calc.SemesterBudget;
import io.github.fortunatechick.attendease.data.Subject;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// First item is the overall summary card, the rest are one card per subject.
public class DashboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static class SubjectRow {
        public final Subject subject;
        public final AttendanceStats stats;
        public final SemesterBudget budget;   // null when lectures per week are unknown

        public SubjectRow(Subject subject, AttendanceStats stats, SemesterBudget budget) {
            this.subject = subject;
            this.stats = stats;
            this.budget = budget;
        }
    }

    public static class Header {
        public String student;
        public String program;
        public String footnote;
        public AttendanceStats overall;
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SUBJECT = 1;

    private Header header;
    private final List<SubjectRow> rows = new ArrayList<>();
    private final Consumer<Subject> onSubjectClick;

    public DashboardAdapter(Consumer<Subject> onSubjectClick) {
        this.onSubjectClick = onSubjectClick;
    }

    public void setData(Header header, List<SubjectRow> rows) {
        this.header = header;
        this.rows.clear();
        this.rows.addAll(rows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return header == null ? 0 : rows.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_SUBJECT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_dashboard_header, parent, false));
        }
        return new SubjectHolder(inflater.inflate(R.layout.item_subject, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(header);
        } else {
            ((SubjectHolder) holder).bind(rows.get(position - 1));
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView student, program, overall, chip, caption, held, attended, missed, pending, footnote;
        final LinearProgressIndicator progress;

        HeaderHolder(View v) {
            super(v);
            student = v.findViewById(R.id.txt_student);
            program = v.findViewById(R.id.txt_program);
            overall = v.findViewById(R.id.txt_overall);
            chip = v.findViewById(R.id.chip_overall);
            caption = v.findViewById(R.id.txt_overall_caption);
            progress = v.findViewById(R.id.progress_overall);
            held = v.findViewById(R.id.txt_held);
            attended = v.findViewById(R.id.txt_attended);
            missed = v.findViewById(R.id.txt_missed);
            pending = v.findViewById(R.id.txt_pending);
            footnote = v.findViewById(R.id.txt_footnote);
        }

        void bind(Header h) {
            AttendanceStats s = h.overall;
            int color = Formats.standingColor(itemView.getContext(), s.standing());

            student.setText(h.student);
            student.setVisibility(h.student == null ? View.GONE : View.VISIBLE);
            program.setText(h.program);
            program.setVisibility(h.program == null ? View.GONE : View.VISIBLE);

            overall.setText(s.displayText());
            overall.setTextColor(color);
            Formats.tintChip(chip, Formats.standingLabel(s.standing()), color);

            String text = "Overall attendance · target " + s.thresholdPercent + "%";
            if (s.hasPending()) text += "\n" + s.pendingText();
            caption.setText(text);

            progress.setProgressCompat(progressValue(s), false);
            progress.setIndicatorColor(color);

            held.setText(String.valueOf(s.decidedHeld() + s.pending));
            attended.setText(String.valueOf(s.decidedAttended()));
            missed.setText(String.valueOf(s.missed()));
            pending.setText(String.valueOf(s.pending));

            footnote.setText(h.footnote);
            footnote.setVisibility(h.footnote == null ? View.GONE : View.VISIBLE);
        }
    }

    class SubjectHolder extends RecyclerView.ViewHolder {
        final TextView name, chip, percent, counts, advice, semester, pending;
        final LinearProgressIndicator progress;

        SubjectHolder(View v) {
            super(v);
            name = v.findViewById(R.id.txt_name);
            chip = v.findViewById(R.id.chip_standing);
            percent = v.findViewById(R.id.txt_percent);
            counts = v.findViewById(R.id.txt_counts);
            progress = v.findViewById(R.id.progress);
            advice = v.findViewById(R.id.txt_advice);
            semester = v.findViewById(R.id.txt_semester);
            pending = v.findViewById(R.id.txt_pending);
        }

        void bind(SubjectRow row) {
            AttendanceStats s = row.stats;
            int color = Formats.standingColor(itemView.getContext(), s.standing());

            name.setText(row.subject.displayName());
            Formats.tintChip(chip, Formats.standingLabel(s.standing()), color);
            percent.setText(s.displayText());
            percent.setTextColor(color);
            counts.setText(s.decidedAttended() + " / " + s.decidedHeld() + " · missed " + s.missed());
            progress.setProgressCompat(progressValue(s), false);
            progress.setIndicatorColor(color);

            SemesterBudget b = row.budget;
            if (b != null) {
                advice.setText(b.adviceText());
                advice.setTextColor(Formats.budgetColor(itemView.getContext(), b));
                semester.setText(b.summaryText());
                semester.setVisibility(View.VISIBLE);
            } else {
                // Without a weekly pattern there is no semester total; fall back to the running figure.
                advice.setText(s.hasData() ? s.advice().text() : "No lectures marked yet");
                advice.setTextColor(color);
                semester.setVisibility(View.GONE);
            }
            pending.setText(s.pendingText());
            pending.setVisibility(s.hasPending() ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> onSubjectClick.accept(row.subject));
        }
    }

    static int progressValue(AttendanceStats s) {
        double value = s.hasPending() ? s.rangeLow() : s.confirmedPercent();
        return Double.isNaN(value) ? 0 : (int) Math.round(value);
    }
}
