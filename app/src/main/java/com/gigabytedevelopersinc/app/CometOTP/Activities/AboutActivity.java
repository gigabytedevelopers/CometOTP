/*
 * Copyright (C) 2017-2018 Jakob Nixdorf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.gigabytedevelopersinc.app.CometOTP.Activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.ColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.aakira.expandablelayout.ExpandableLayoutListenerAdapter;
import com.github.aakira.expandablelayout.ExpandableLinearLayout;

import com.gigabytedevelopersinc.app.CometOTP.R;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Tools;

public class AboutActivity extends BaseActivity {
    private static final String GITHUB_URI = "https://github.com/gigabytedevelopers/CometOTP";
    private static final String WHATSAPP_URI = "https://chat.whatsapp.com/HvUjQXAkMne3hjSiy1sUpc";
    private static final String CHANGELOG_URI = GITHUB_URI + "/blob/master/CHANGELOG.md";
    private static final String MIT_URI = GITHUB_URI + "/blob/master/LICENSE.txt";

    private static final String AUTHOR1_GITHUB = "https://github.com/gigabytedevelopers";
    private static final String AUTHOR1_PAYPAL = "https://paypal.me/gigabtedevelopers";

    /*private static final String AUTHOR2_GITHUB = "https://github.com";*/
    private static final String AUTHOR2_APP = "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2";

    private static final String CONTRIBUTORS_URI = GITHUB_URI + "/graphs/contributors";
    private static final String TRANSLATORS_URI = GITHUB_URI + "/blob/master/README.md#translators";

    private static final String BUGREPORT_URI = GITHUB_URI + "/issues";
    private static final String TRANSLATE_URI = "http://gigabytedevelopers.oneskyapp.com/collaboration/project/296138";

    static final int[] imageResources = {
            R.id.aboutImgVersion, R.id.aboutImgLicense, R.id.aboutImgChangelog, R.id.aboutImgSource,
            R.id.aboutImgOpenSource, R.id.aboutImgAuthor1, R.id.aboutImgAuthor2, R.id.aboutImgContributors,
            R.id.aboutImgTranslators, R.id.aboutImgBugs, R.id.aboutImgTranslate
    };

    static long lastTap = 0;
    static int taps = 0;
    static Toast currentToast = null;

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

        versionLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
                            //currentToast = Toast.makeText(getBaseContext(), R.string.about_toast_special_features_enabled, Toast.LENGTH_LONG);
                        else
                            enableSpecialFeatures();
                    }

                    if (currentToast != null)
                        currentToast.show();
                } else {
                    taps = 0;
                }

                lastTap = thisTap;
            }
        });

        TextView version = v.findViewById(R.id.about_text_version);
        version.setText(versionName);

        LinearLayout license = v.findViewById(R.id.about_layout_license);
        LinearLayout changelog = v.findViewById(R.id.about_layout_changelog);
        LinearLayout source = v.findViewById(R.id.about_layout_source);
        LinearLayout licenses = v.findViewById(R.id.about_layout_licenses);
        license.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openURI(MIT_URI);
            }
        });
        changelog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(AboutActivity.this);
                builder.setTitle("ChangeLog");
                builder.setMessage(R.string.chagelog);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });
                builder.create().show();
            }
        });
        source.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openURI(WHATSAPP_URI);
            }
        });
        licenses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showLicenses();
            }
        });

        TextView author1GitHub = v.findViewById(R.id.about_author1_github);
        TextView author1Paypal = v.findViewById(R.id.about_author1_paypal);

        author1GitHub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    openURI(AUTHOR1_GITHUB);
                } catch(Exception ignored) {
                    copyToClipboard(AUTHOR1_GITHUB);
                }
            }
        });
        author1Paypal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    openURI(AUTHOR1_PAYPAL);
                } catch(Exception ignored) {
                    copyToClipboard(AUTHOR1_PAYPAL);
                }
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
        author2App.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openURI(AUTHOR2_APP);
            }
        });

        LinearLayout contributors = v.findViewById(R.id.about_layout_contributors);
        LinearLayout translators = v.findViewById(R.id.about_layout_translators);
        contributors.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openURI(CONTRIBUTORS_URI);
            }
        });
        translators.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openURI(TRANSLATORS_URI);
            }
        });

        LinearLayout bugReport = v.findViewById(R.id.about_layout_bugs);
        LinearLayout translate = v.findViewById(R.id.about_layout_translate);
        bugReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent feedback = new Intent(Intent.ACTION_SEND);
                feedback.setType("text/html");
                feedback.putExtra(Intent.EXTRA_EMAIL, new String[]{ getString(R.string.feedback_email_address)});
                feedback.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject));
                feedback.putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_email_message));
                startActivity(Intent.createChooser(feedback, getString(R.string.feedback_email_title)));
            }
        });
        translate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openURI(TRANSLATE_URI);
            }
        });

        final Button expandButton = v.findViewById(R.id.thumb_expand_button);
        final CardView expand = v.findViewById(R.id.thumb_expand);
        final ExpandableLinearLayout expandLayout = v.findViewById(R.id.thumb_disclaimer);

        expand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                expandLayout.toggle();
            }
        });

        expandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                expandLayout.toggle();
            }
        });

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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.about_title_special_features)
                .setMessage(R.string.about_dialog_special_features)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        settings.setSpecialFeatures(true);

                        Snackbar.make(findViewById(R.id.about), R.string.about_toast_special_features, Snackbar.LENGTH_LONG).show();
                        //Toast.makeText(getBaseContext(), R.string.about_toast_special_features, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {}
                })
                .create()
                .show();
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
