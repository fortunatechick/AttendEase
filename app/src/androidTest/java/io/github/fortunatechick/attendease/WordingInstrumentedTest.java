package io.github.fortunatechick.attendease;

import android.content.Context;
import android.content.res.Configuration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import io.github.fortunatechick.attendease.calc.ApprovalConvention;
import io.github.fortunatechick.attendease.calc.AttendanceStats;
import io.github.fortunatechick.attendease.calc.BunkAdvice;
import io.github.fortunatechick.attendease.calc.SemesterBudget;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

// The screens now build their sentences from string resources (so they can be translated),
// while calc keeps its own English text for its unit tests. In English the two must read the
// same. The only intended differences are singulars ("1 lecture", "1 miss allowed",
// "the 1 remaining lecture"), which calc writes in the plural.
@RunWith(AndroidJUnit4.class)
public class WordingInstrumentedTest {

    private static Context english() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.ENGLISH);
        return base.createConfigurationContext(config);
    }

    @Test
    public void adviceMatchesCalc() {
        Context c = english();
        for (int threshold : new int[]{0, 50, 75, 80, 100}) {
            for (int held = 0; held <= 40; held++) {
                for (int attended = 0; attended <= held; attended++) {
                    BunkAdvice a = BunkAdvice.of(attended, held, threshold);
                    assertEquals(a.text(), Formats.advice(c, a));
                }
            }
        }
    }

    @Test
    public void budgetMatchesCalc() {
        Context c = english();
        for (ApprovalConvention convention : ApprovalConvention.values()) {
            for (int perWeek : new int[]{1, 3, 4}) {
                for (int absent = 0; absent <= 14; absent++) {
                    for (int pending = 0; pending <= 3; pending++) {
                        for (int present = 0; present <= 40; present += 5) {
                            AttendanceStats s = new AttendanceStats(present, absent, pending, 1, 0, convention, 80);
                            SemesterBudget b = new SemesterBudget(15, perWeek, s);
                            if (b.total == 1 || b.maxMisses == 1 || b.lecturesLeft == 1) continue;  // singulars
                            assertEquals(b.summaryText(), Formats.budgetSummary(c, b));
                            assertEquals(b.adviceText(), Formats.budgetAdvice(c, b));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void pendingMatchesCalc() {
        Context c = english();
        for (int pending = 0; pending <= 5; pending++) {
            AttendanceStats s = new AttendanceStats(3, 1, pending, 0, 0, ApprovalConvention.COUNTED_AS_ATTENDED, 80);
            assertEquals(s.pendingText(), Formats.pending(c, s));
        }
    }
}
