package io.github.fortunatechick.attendease;

import android.os.Bundle;
import android.widget.TextView;

// Version, what the app is (and isn't), privacy in brief, and open-source notices.
public class AboutActivity extends BaseActivity {

    static final String REPO_URL = "https://github.com/fortunatechick/AttendEase";
    static final String PRIVACY_URL = "https://fortunatechick.github.io/AttendEase/privacy.html";
    static final String ISSUES_URL = REPO_URL + "/issues";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle(R.string.about_title);
        showBackArrow();

        ((TextView) findViewById(R.id.txt_about_version)).setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        findViewById(R.id.btn_privacy_policy).setOnClickListener(v -> SapPortal.openInBrowser(this, PRIVACY_URL));
        findViewById(R.id.btn_source).setOnClickListener(v -> SapPortal.openInBrowser(this, REPO_URL));
        findViewById(R.id.btn_report_problem).setOnClickListener(v -> SapPortal.openInBrowser(this, ISSUES_URL));
    }
}
