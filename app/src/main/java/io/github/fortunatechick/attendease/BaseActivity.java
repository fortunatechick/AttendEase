package io.github.fortunatechick.attendease;

import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.github.fortunatechick.attendease.data.GuestMode;
import com.google.android.material.appbar.MaterialToolbar;

// Targeting SDK 35+ forces edge-to-edge drawing, so every screen pads its root view
// by the system bar insets and uses its own toolbar instead of the window action bar.
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    public void setContentView(int layoutResId) {
        super.setContentView(layoutResId);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (GuestMode.isActive(this)) {
                toolbar.setSubtitle("Guest mode · your own data is untouched");
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
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    protected void showBackArrow() {
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
