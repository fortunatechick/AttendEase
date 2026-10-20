package io.github.fortunatechick.attendease;

import android.os.Bundle;

// The empty right-hand pane on a tablet or unfolded phone, until a subject is picked.
public class PlaceholderActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeholder);
    }
}
