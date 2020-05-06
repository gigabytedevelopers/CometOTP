package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.ColorFilter;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.cardview.widget.CardView;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.aakira.expandablelayout.ExpandableLayoutListenerAdapter;
import com.github.aakira.expandablelayout.ExpandableLinearLayout;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

import java.util.Objects;

import saschpe.android.customtabs.CustomTabsHelper;
import saschpe.android.customtabs.WebViewFallback;

public class AboutActivity extends BaseActivity {
    private static final String GITHUB_URI = "https://github.com/gigabytedevelopers/CometOTP";
    private static final String WHATSAPP_URI = "https://chat.whatsapp.com/HvUjQXAkMne3hjSiy1sUpc";
    // private static final String CHANGELOG_URI = GITHUB_URI + "/blob/master/CHANGELOG.md";
    private static final String MIT_URI = GITHUB_URI + "/blob/master/LICENSE.txt";

    private static final String AUTHOR1_GITHUB = "https://github.com/gigabytedevelopers";
    private static final String AUTHOR1_PAYPAL = "https://paypal.me/gigabtedevelopers";

    // private static final String AUTHOR2_GITHUB = "https://github.com";
    private static final String AUTHOR2_APP = "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2";
    private static final String changeLogUrl = "https://gigabytedevelopersinc.com/apps/changelog/cometotp";

    // private static final String BUGREPORT_URI = GITHUB_URI + "/issues";

    static final int[] imageResources = {
            R.id.aboutImgVersion, R.id.aboutImgLicense, R.id.aboutImgChangelog, R.id.aboutImgSource,
            R.id.aboutImgOpenSource, R.id.aboutImgAuthor1, R.id.aboutImgAuthor2, R.id.aboutImgBugs
    };

    static long lastTap = 0;
    static int taps = 0;
    static Toast currentToast = null;

    private BottomSheetDialog mBottomSheetDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.about_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_about);
        View v = stub.inflate();

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setShowTitle(true);
        CustomTabsIntent customTabsIntent = builder.build();
        builder.setToolbarColor(getResources().getColor(R.color.colorPrimary));
        CustomTabsHelper.addKeepAliveExtra(this, customTabsIntent.intent);

        ColorFilter filter = Tools.getThemeColorFilter(this, android.R.attr.textColorSecondary);
        for (int i : imageResources) {
            ImageView imgView = v.findViewById(i);
            imgView.getDrawable().setColorFilter(filter);
        }

        String versionName = "";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        LinearLayout versionLayout = v.findViewById(R.id.about_layout_version);

        versionLayout.setOnClickListener(view -> {
            long thisTap = System.currentTimeMillis();

            if (thisTap - lastTap < 500) {
                taps = taps + 1;

                if (currentToast != null && taps <= 7)
                    currentToast.cancel();

                if (taps >= 3 && taps <= 7)
                    currentToast = Toast.makeText(getBaseContext(), String.valueOf(taps), Toast.LENGTH_SHORT);

                if (taps == 7) {
                    if (settings.getSpecialFeatures())
                        Snackbar.make(findViewById(R.id.about), R.string.about_toast_special_features_enabled, Snackbar.LENGTH_LONG).show();
                    else
                        enableSpecialFeatures();
                }

                if (currentToast != null)
                    currentToast.show();
            } else {
                taps = 0;
            }

            lastTap = thisTap;
        });

        TextView version = v.findViewById(R.id.about_text_version);
        version.setText(versionName);

        LinearLayout license = v.findViewById(R.id.about_layout_license);
        LinearLayout changelog = v.findViewById(R.id.about_layout_changelog);
        LinearLayout source = v.findViewById(R.id.about_layout_source);
        LinearLayout licenses = v.findViewById(R.id.about_layout_licenses);
        license.setOnClickListener(view -> openURI(MIT_URI));
        changelog.setOnClickListener(view -> CustomTabsHelper.openCustomTab(this, customTabsIntent,
                Uri.parse(changeLogUrl),
                new WebViewFallback()));
        source.setOnClickListener(view -> openURI(WHATSAPP_URI));
        licenses.setOnClickListener(view -> showLicenses());

        TextView author1GitHub = v.findViewById(R.id.about_author1_github);
        TextView author1Paypal = v.findViewById(R.id.about_author1_paypal);

        author1GitHub.setOnClickListener(view -> {
            try {
                openURI(AUTHOR1_GITHUB);
            } catch(Exception ignored) {
                copyToClipboard(AUTHOR1_GITHUB);
            }
        });
        author1Paypal.setOnClickListener(view -> {
            try {
                openURI(AUTHOR1_PAYPAL);
            } catch(Exception ignored) {
                copyToClipboard(AUTHOR1_PAYPAL);
            }
        });

        /*TextView author2GitHub = v.findViewById(R.id.about_author2_github);*/
        TextView author2App = v.findViewById(R.id.about_author2_app);
        /*author2GitHub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openURI(AUTHOR2_GITHUB);
            }
        });*/
        author2App.setOnClickListener(view -> openURI(AUTHOR2_APP));

        LinearLayout bugReport = v.findViewById(R.id.about_layout_bugs);
        bugReport.setOnClickListener(view -> {
            final Intent feedback = new Intent(Intent.ACTION_SENDTO);
            feedback.setType("text/html");
            feedback.setData(Uri.parse("mailto:"));
            feedback.putExtra(Intent.EXTRA_EMAIL, new String[]{ getString(R.string.feedback_email_address)});
            feedback.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject));
            feedback.putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_email_message));
            startActivity(Intent.createChooser(feedback, getString(R.string.feedback_email_title)));
        });

        final Button expandButton = v.findViewById(R.id.thumb_expand_button);
        final CardView expand = v.findViewById(R.id.thumb_expand);
        final ExpandableLinearLayout expandLayout = v.findViewById(R.id.thumb_disclaimer);
        final LinearLayout privacypolicy = v.findViewById(R.id.privacy_policy);

        privacypolicy.setOnClickListener(view -> {
            Intent privacyPolicyIntent = new Intent(AboutActivity.this, PrivacyPolicyActivity.class);
            startActivityForResult(privacyPolicyIntent, Constants.INTENT_MAIN_PRIVACYPOLICY);
        });

        expand.setOnClickListener(view -> expandLayout.toggle());

        expandButton.setOnClickListener(view -> expandLayout.toggle());

        expandLayout.setListener(new ExpandableLayoutListenerAdapter() {
            @Override
            public void onOpened() {
                super.onOpened();
                expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
            }

            @Override
            public void onClosed() {
                super.onClosed();
                expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
            }
        });
    }

    private void enableSpecialFeatures() {
        View bottomSheet = findViewById(R.id.framelayout_bottom_sheet);
        final View bottomSheetLayout = getLayoutInflater().inflate(R.layout.bottom_sheet_special_features, null);
        (bottomSheetLayout.findViewById(R.id.button_no)).setOnClickListener(v -> mBottomSheetDialog.dismiss());
        (bottomSheetLayout.findViewById(R.id.button_yes)).setOnClickListener(v -> {
            mBottomSheetDialog.dismiss();
            settings.setSpecialFeatures(true);
            Snackbar.make(findViewById(R.id.about), R.string.about_toast_special_features,
                    Snackbar.LENGTH_LONG).show();
        });
        mBottomSheetDialog = new BottomSheetDialog(this);
        mBottomSheetDialog.setContentView(bottomSheetLayout);
        mBottomSheetDialog.setCancelable(false);
        mBottomSheetDialog.show();
    }

    // Go back to the main activity
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    public void openURI(String uri) {
        Intent openURI = new Intent(Intent.ACTION_VIEW);
        openURI.setData(Uri.parse(uri));
        startActivity(openURI);
    }

    public void copyToClipboard(String uri) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("CometOTP", uri);
        clipboard.setPrimaryClip(clip);
        Snackbar.make(findViewById(R.id.about), getString(R.string.about_toast_copied_to_clipboard), Snackbar.LENGTH_LONG).show();
        //Toast.makeText(this, getString(R.string.about_toast_copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }


    public void showLicenses() {
        Intent licensesIntent = new Intent(this, LicensesActivity.class);
        startActivityForResult(licensesIntent, Constants.INTENT_MAIN_LICENSES);
        /*String backgroundColor = Tools.getCSSRGBAString(Tools.getThemeColor(this, R.attr.colorBackgroundFloating));
        String textColor = Tools.getCSSRGBAString(Tools.getThemeColor(this, android.R.attr.textColorPrimary));
        String textColorSecondary = Tools.getCSSRGBAString(Tools.getThemeColor(this, android.R.attr.textColorSecondary));
        String cssFormat = getString(R.string.custom_notices_style, backgroundColor, textColor, textColorSecondary);
        LicensesDialog dialog = new LicensesDialog.Builder(this)
                .setNotices(R.raw.licenses)
                .setTitle(R.string.about_label_licenses)
                .setShowFullLicenseText(false)
                .setIncludeOwnLicense(true)
                .setNoticesCssStyle(cssFormat)
                .build();

        dialog.show();*/
    }
}
