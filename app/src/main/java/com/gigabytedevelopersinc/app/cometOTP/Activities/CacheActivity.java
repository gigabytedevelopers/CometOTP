package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;

import com.gigabytedevelopersinc.app.cometOTP.R;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Objects;

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 6/21/2018
 **/
public class CacheActivity extends BaseActivity {

    private BottomSheetDialog mBottomSheetDialog;

    @Override
    @SuppressLint("MissingInflatedId")   // the ids live in the layout inflated through the ViewStub
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.clear_cache_button);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.clear_cache_activity);
        stub.inflate();

        Button clearCache = findViewById(R.id.clear_cache_button);
        clearCache.setOnClickListener(v -> {
            final View bottomSheetLayout = getLayoutInflater().inflate(R.layout.bottom_sheet_cache_dialog, null);
            (bottomSheetLayout.findViewById(R.id.button_no)).setOnClickListener(v1 -> mBottomSheetDialog.dismiss());
            (bottomSheetLayout.findViewById(R.id.button_yes)).setOnClickListener(v2 -> {
                deleteCache(getBaseContext());
                mBottomSheetDialog.dismiss();
                Snackbar.make(findViewById(R.id.clear_cache_layout),
                        "CometOTP Cache Storage has been Cleared!",
                        Snackbar.LENGTH_LONG).show();
            });
            mBottomSheetDialog = new BottomSheetDialog(CacheActivity.this);
            mBottomSheetDialog.setContentView(bottomSheetLayout);
            mBottomSheetDialog.setCancelable(false);
            mBottomSheetDialog.show();
        });
    }

    public static void deleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
        } catch (Exception e) {
            //
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            assert children != null;
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else
            return dir != null && dir.isFile() && dir.delete();
    }

    private void initializeCache(Context context) {
        long size = 0;
        size += getDirSize(context.getCacheDir());
        size += getDirSize(Objects.requireNonNull(context.getExternalCacheDir()));
        /*cache.append(readableFileSize(size));
            Preference cacheSize = findPreference(getString(R.string.settings_key_clear_cache));
        cacheSize.setSummary(R.string.settings_desc_clear_cache + readableFileSize(size));*/
    }

    public long getDirSize(File dir) {
        long size = 0;
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file != null && file.isDirectory()) {
                size += getDirSize(file);
            } else if (file != null && file.isFile()) {
                size += file.length();
            }
        }
        return size;
    }

    public static String readableFileSize(long size) {
        if (size <= 0) return "0 Bytes";
        final String[] units = new String[]{"Bytes", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
