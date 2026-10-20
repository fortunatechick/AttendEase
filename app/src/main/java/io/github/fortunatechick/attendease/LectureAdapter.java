package io.github.fortunatechick.attendease;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.fortunatechick.attendease.data.Lecture;

import java.util.ArrayList;
import java.util.List;

// Subject detail list: one header view (figures and filter chips), then the lectures.
public class LectureAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_LECTURE = 1;

    private final View header;
    private final List<Lecture> lectures = new ArrayList<>();
    private final Callback<Lecture> onLongPress;

    public LectureAdapter(View header, Callback<Lecture> onLongPress) {
        this.header = header;
        this.onLongPress = onLongPress;
    }

    public void setLectures(List<Lecture> list) {
        lectures.clear();
        lectures.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return lectures.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_LECTURE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            if (header.getParent() != null) ((ViewGroup) header.getParent()).removeView(header);
            return new RecyclerView.ViewHolder(header) { };
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lecture, parent, false);
        return new LectureHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LectureHolder) {
            ((LectureHolder) holder).bind(lectures.get(position - 1));
        }
    }

    class LectureHolder extends RecyclerView.ViewHolder {
        final TextView date, detail, status;

        LectureHolder(View v) {
            super(v);
            date = v.findViewById(R.id.txt_date);
            detail = v.findViewById(R.id.txt_detail);
            status = v.findViewById(R.id.chip_status);
        }

        void bind(Lecture l) {
            date.setText(Formats.date(l.dateMillis));

            StringBuilder sb = new StringBuilder();
            sb.append(Formats.time(l.startTime));
            if (l.endTime != null) sb.append(" – ").append(Formats.time(l.endTime));
            Context context = itemView.getContext();
            sb.append(" · ").append(Formats.component(context, l.component));
            if (l.isHandAdded()) sb.append(" · ").append(context.getString(R.string.lecture_added_by_hand));
            else if (l.manualOverride) sb.append(" · ").append(context.getString(R.string.lecture_changed_by_you));
            detail.setText(sb.toString());

            Formats.tintChip(status, Formats.statusLabel(context, l.effectiveStatus),
                    Formats.statusColor(itemView.getContext(), l.effectiveStatus));

            itemView.setOnLongClickListener(v -> {
                onLongPress.accept(l);
                return true;
            });
            // TalkBack says "double-tap and hold to change status or mark medical leave".
            ViewCompat.replaceAccessibilityAction(itemView, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                    context.getString(R.string.a11y_change_status), (view, args) -> {
                        onLongPress.accept(l);
                        return true;
                    });
            // A mouse right-click or two-finger trackpad tap opens the same menu.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                itemView.setOnContextClickListener(v -> {
                    onLongPress.accept(l);
                    return true;
                });
            }
        }
    }
}
