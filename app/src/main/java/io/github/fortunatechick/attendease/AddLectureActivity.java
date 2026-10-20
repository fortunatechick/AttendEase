package io.github.fortunatechick.attendease;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;

import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.Lecture;
import io.github.fortunatechick.attendease.data.Status;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

// Records one lecture by hand, so the app is usable between PDF releases or with no PDF at all.
public class AddLectureActivity extends BaseActivity {

    public static final String EXTRA_SUBJECT_ID = "subject_id";

    private AttendanceRepository repo;
    private long subjectId;
    private long dateMillis;
    private int startMinutes = 9 * 60;

    private Button btnDate, btnTime;
    private RadioGroup groupComponent, groupStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_lecture);
        setTitle(R.string.menu_subject_detail_add_lecture);
        showBackArrow();

        repo = new AttendanceRepository(this);
        subjectId = getIntent().getLongExtra(EXTRA_SUBJECT_ID, -1);

        btnDate = findViewById(R.id.btn_date);
        btnTime = findViewById(R.id.btn_time);
        groupComponent = findViewById(R.id.group_component);
        groupStatus = findViewById(R.id.group_status);

        if (savedInstanceState != null) {
            dateMillis = savedInstanceState.getLong("date");
            startMinutes = savedInstanceState.getInt("start");
        } else {
            Calendar now = Calendar.getInstance();
            dateMillis = utcMidnight(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        }
        showWhen();

        btnDate.setOnClickListener(v -> pickDate());
        btnTime.setOnClickListener(v -> pickTime());
        findViewById(R.id.btn_save).setOnClickListener(v -> save(false));
        findViewById(R.id.btn_save_another).setOnClickListener(v -> save(true));

        Background.load(() -> repo.subjects.byId(subjectId), subject -> {
            if (isDestroyed()) return;
            if (subject == null) {
                finish();
                return;
            }
            ((TextView) findViewById(R.id.txt_subject)).setText(subject.displayName());
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("date", dateMillis);
        out.putInt("start", startMinutes);
    }

    private void showWhen() {
        btnDate.setText(Formats.date(dateMillis));
        btnTime.setText(Formats.time(sapTime(startMinutes)));
    }

    private void pickDate() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_date_picker, null);
        DatePicker picker = view.findViewById(R.id.date_picker);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(dateMillis);
        picker.updateDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_lecture_date)
                .setView(view)
                .setPositiveButton(R.string.action_ok, (d, w) -> {
                    dateMillis = utcMidnight(picker.getYear(), picker.getMonth(), picker.getDayOfMonth());
                    showWhen();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void pickTime() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_time_picker, null);
        TimePicker picker = view.findViewById(R.id.time_picker);
        setTime(picker, startMinutes / 60, startMinutes % 60);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_lecture_start_time)
                .setView(view)
                .setPositiveButton(R.string.action_ok, (d, w) -> {
                    startMinutes = minutesOf(picker);
                    showWhen();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // TimePicker's getHour/setHour arrived in API 23; older versions only have the
    // getCurrentHour/setCurrentHour pair, which does the same thing.
    @SuppressWarnings("deprecation")
    private static void setTime(TimePicker picker, int hour, int minute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            picker.setHour(hour);
            picker.setMinute(minute);
        } else {
            picker.setCurrentHour(hour);
            picker.setCurrentMinute(minute);
        }
    }

    @SuppressWarnings("deprecation")
    private static int minutesOf(TimePicker picker) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return picker.getHour() * 60 + picker.getMinute();
        }
        return picker.getCurrentHour() * 60 + picker.getCurrentMinute();
    }

    private void save(boolean another) {
        Lecture l = new Lecture();
        l.subjectId = subjectId;
        l.component = groupComponent.getCheckedRadioButtonId() == R.id.radio_practical ? "P" : "T";
        l.dateMillis = dateMillis;
        l.startMinutes = startMinutes;
        l.startTime = sapTime(startMinutes);
        l.endTime = sapTime((startMinutes + 60) % (24 * 60));
        l.rawCourseName = Lecture.manualCourseName(subjectId, l.component);
        l.srNo = 0;
        int status = groupStatus.getCheckedRadioButtonId();
        l.effectiveStatus = status == R.id.radio_absent ? Status.ABSENT
                : status == R.id.radio_not_updated ? Status.NOT_UPDATED : Status.PRESENT;
        l.manualOverride = true;

        Background.load(() -> {
            try {
                repo.lectures.insert(l);
            } catch (SQLiteConstraintException e) {
                return false;   // same subject, type, day and time already recorded
            }
            return true;
        }, saved -> {
            if (isDestroyed()) return;
            if (!saved) {
                Toast.makeText(this, "P".equals(l.component) ? R.string.add_lecture_duplicate_practical
                        : R.string.add_lecture_duplicate_theory, Toast.LENGTH_LONG).show();
                return;
            }
            if (another) {
                startMinutes = (startMinutes + 60) % (24 * 60);
                showWhen();
                Toast.makeText(this, R.string.add_lecture_saved, Toast.LENGTH_SHORT).show();
            } else {
                finish();
            }
        });
    }

    // Same text shape the SAP report uses: "9:00:00 AM".
    static String sapTime(int minutes) {
        int hour = minutes / 60;
        int minute = minutes % 60;
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return String.format(Locale.US, "%d:%02d:00 %s", h12, minute, hour < 12 ? "AM" : "PM");
    }

    private static long utcMidnight(int year, int month, int day) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month, day);
        return c.getTimeInMillis();
    }
}
