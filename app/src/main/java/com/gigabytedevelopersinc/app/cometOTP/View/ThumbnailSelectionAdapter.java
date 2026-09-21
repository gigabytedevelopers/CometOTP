package com.gigabytedevelopersinc.app.cometOTP.View;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThumbnailSelectionAdapter extends BaseAdapter {
    private Context context;
    private List<EntryThumbnail.EntryThumbnails> items;
    private String issuer = "Example";
    private String label = "Example";
    private Settings settings;

    public ThumbnailSelectionAdapter(Context context, String issuer, String label) {
        items = new ArrayList<>(EntryThumbnail.EntryThumbnails.values().length);
        Collections.addAll(items, EntryThumbnail.EntryThumbnails.values());
        this.issuer = issuer;
        this.label = label;
        this.context = context;
        settings = new Settings(context);
    }

    public void filter(String filter) {
        items.clear();
        for (EntryThumbnail.EntryThumbnails thumb : EntryThumbnail.EntryThumbnails.values()) {
            if(thumb.name().toLowerCase().contains(filter.toLowerCase())) {
                items.add(thumb);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int i) {
        if(i < getCount())
            return items.get(i);
        else
            return EntryThumbnail.EntryThumbnails.Default;
    }

    public int getRealIndex(int displayPosition) {
        return ((EntryThumbnail.EntryThumbnails)getItem(displayPosition)).ordinal();
    }

    @Override
    public long getItemId(int i) {
        return ((EntryThumbnail.EntryThumbnails) getItem(i)).ordinal();
    }

    @NonNull
    @Override
    public View getView(int i, View view, @NonNull ViewGroup viewGroup) {
        int thumbnailSize = settings.getThumbnailSize();
        ImageView imageView;
        if (view == null) {
            imageView = new ImageView(context);
            imageView.setLayoutParams(new GridView.LayoutParams(thumbnailSize, thumbnailSize));
            // Many issuer logos are black, which vanishes against a dark background.
            imageView.setBackgroundResource(R.drawable.bg_thumbnail_tile);
        } else {
            imageView = (ImageView) view;
        }

        EntryThumbnail.EntryThumbnails thumb = (EntryThumbnail.EntryThumbnails)getItem(i);

        imageView.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context, issuer, label, thumbnailSize, thumb));
        imageView.setContentDescription(thumb.name());

        return imageView;
    }
}
