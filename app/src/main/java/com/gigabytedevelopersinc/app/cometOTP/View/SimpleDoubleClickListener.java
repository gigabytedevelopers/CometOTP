package com.gigabytedevelopersinc.app.cometOTP.View;

import android.view.View;

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Thursday, 07
 * Month: May
 * Year: 2020
 * Date: 07 May, 2020
 * Time: 1:11 AM
 * Desc: SimpleDoubleClickListener
 **/
public abstract class SimpleDoubleClickListener implements View.OnClickListener {

    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // Milliseconds

    private long lastClickTime = 0;
    private boolean firstTap = true;

    @Override
    public void onClick(View v) {
        long clickTime = System.currentTimeMillis();

        if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
            if (firstTap)
                onDoubleClick(v);

            firstTap = false;
        } else {
            firstTap = true;

            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (firstTap)
                        onSingleClick(v);
                }
            }, DOUBLE_CLICK_TIME_DELTA);
        }

        lastClickTime = clickTime;
    }

    public abstract void onSingleClick(View v);
    public abstract void onDoubleClick(View v);
}
