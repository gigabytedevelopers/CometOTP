package com.gigabytedevelopersinc.app.cometOTP.Preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.LauncherIcon;

/**
 * Picks the launcher icon by showing each icon rather than only naming it.
 *
 * <p>The previews come straight from the mipmaps the aliases use, so what the dialog shows is what
 * the launcher will show. Choosing a row commits immediately and closes the dialog, which is why
 * there is no positive button.</p>
 */
public class LauncherIconPreference extends DialogPreference {

    private static final String[] VALUES = {
            LauncherIcon.BLUE, LauncherIcon.MONO, LauncherIcon.WHITE, LauncherIcon.CLASSIC};

    private static final int[] LABELS = {
            R.string.settings_entry_launcher_icon_blue,
            R.string.settings_entry_launcher_icon_mono,
            R.string.settings_entry_launcher_icon_white,
            R.string.settings_entry_launcher_icon_classic};

    private static final int[] PREVIEWS = {
            R.mipmap.ic_launcher_blue, R.mipmap.ic_launcher_mono,
            R.mipmap.ic_launcher_white, R.mipmap.ic_launcher_classic};

    private String value = LauncherIcon.DEFAULT;

    @SuppressWarnings("unused")
    public LauncherIconPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public LauncherIconPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Choosing a row is the commit, so the dialog only needs a way out.
        setPositiveButtonText(null);
    }

    private CharSequence labelFor(String variant) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(variant))
                return getContext().getString(LABELS[i]);
        }
        return getContext().getString(LABELS[0]);
    }

    @Override
    public CharSequence getSummary() {
        return labelFor(value);
    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        LinearLayout root = (LinearLayout) inflater.inflate(R.layout.preference_launcher_icon, null);

        for (int i = 0; i < VALUES.length; i++) {
            final String variant = VALUES[i];
            View row = inflater.inflate(R.layout.item_launcher_icon, root, false);

            ((ImageView) row.findViewById(R.id.icon_preview)).setImageResource(PREVIEWS[i]);
            ((TextView) row.findViewById(R.id.icon_label)).setText(LABELS[i]);

            boolean selected = variant.equals(value);
            row.setSelected(selected);
            row.findViewById(R.id.icon_check)
                    .setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            row.setContentDescription(getContext().getString(LABELS[i]));

            row.setOnClickListener(v -> choose(variant));
            root.addView(row);
        }
        return root;
    }

    private void choose(String variant) {
        if (callChangeListener(variant)) {
            value = variant;
            persistString(variant);
            notifyChanged();
        }
        getDialog().dismiss();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        String fromXml = a.getString(index);
        return fromXml != null ? fromXml : LauncherIcon.DEFAULT;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String fallback = defaultValue instanceof String ? (String) defaultValue : LauncherIcon.DEFAULT;
        value = restorePersistedValue ? getPersistedString(fallback) : fallback;
        if (!restorePersistedValue)
            persistString(value);
    }

    /** Only used by the framework to size the dialog; the rows do the work. */
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        ViewGroup root = (ViewGroup) view;
        root.setMinimumWidth(0);
    }
}
