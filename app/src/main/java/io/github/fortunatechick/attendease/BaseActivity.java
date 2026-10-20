package io.github.fortunatechick.attendease;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.github.fortunatechick.attendease.data.DataChanges;
import io.github.fortunatechick.attendease.data.GuestMode;
import com.google.android.material.appbar.MaterialToolbar;

// Targeting SDK 35+ forces edge-to-edge drawing, so every screen pads its root view
// by the system bar insets and uses its own toolbar instead of the window action bar.
public abstract class BaseActivity extends AppCompatActivity implements DataChanges.Listener {

    // Wider than this, content is centred instead of stretched edge to edge.
    private static final int MAX_CONTENT_WIDTH_DP = 720;

    @Override
    public void setContentView(int layoutResId) {
        super.setContentView(layoutResId);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (GuestMode.isActive(this)) {
                toolbar.setSubtitle(R.string.guest_subtitle);
            }
        }

        View root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // The on-screen keyboard covers the bottom too: pad by whichever is taller so
            // text fields and Save buttons stay above it.
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int side = sideMargin();
            v.setPadding(left + bars.left + side, top + bars.top, right + bars.right + side,
                    bottom + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // Extra left/right padding that keeps lines a readable length on a tablet or desktop window.
    private int sideMargin() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (widthDp <= MAX_CONTENT_WIDTH_DP) return 0;
        float density = getResources().getDisplayMetrics().density;
        return Math.round((widthDp - MAX_CONTENT_WIDTH_DP) / 2f * density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        DataChanges.add(this);
    }

    @Override
    protected void onStop() {
        DataChanges.remove(this);
        super.onStop();
    }

    // Saved data changed (possibly from the other pane). Screens that show data reload.
    @Override
    public void onDataChanged() {
    }

    // Esc goes back, as on a desktop, for keyboards and Chromebooks.
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    protected void showBackArrow() {
        // As the right-hand pane next to the dashboard, there's nothing to go "up" to.
        if (TwoPane.isEmbedded(this)) return;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
