package com.gigabytedevelopersinc.app.cometOTP.View;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity;
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Database.EntryList;
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ManualEntryDialog;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper;
import com.gigabytedevelopersinc.app.cometOTP.View.ItemTouchHelper.ItemTouchHelperAdapter;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.crypto.SecretKey;

import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SortMode;

public class EntriesCardAdapter extends RecyclerView.Adapter<EntryViewHolder>
    implements ItemTouchHelperAdapter, Filterable {
    private final Context context;
    private final Handler taskHandler;
    private EntryFilter filter;
    private final EntryList entries;
    private ArrayList<Entry> displayedEntries;
    private Callback callback;
    private List<String> tagsFilter = new ArrayList<>();

    private static SecretKey encryptionKey = null;

    private SortMode sortMode = SortMode.UNSORTED;
    private final TagsAdapter tagsFilterAdapter;
    private final Settings settings;

    private static final int ESTABLISH_PIN_MENU_INDEX = 4;

    public EntriesCardAdapter(Context context, TagsAdapter tagsFilterAdapter) {
        this.context = context;
        this.tagsFilterAdapter = tagsFilterAdapter;
        this.settings = new Settings(context);
        this.taskHandler = new Handler(Looper.getMainLooper());
        this.entries = new EntryList();

        setHasStableIds(true);
    }

    public void setEncryptionKey(SecretKey key) {
        encryptionKey = key;
    }

    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }

    @Override
    public int getItemCount() {
        return displayedEntries.size();
    }

    @Override
    public long getItemId(int position) {
        return displayedEntries.get(position).getListId();
    }

    public ArrayList<Entry> getEntries() {
        return entries.getEntries();
    }

    public void saveAndRefresh(boolean auto_backup) {
        saveAndRefresh(auto_backup, RecyclerView.NO_POSITION);
    }

    public void saveAndRefresh(boolean auto_backup, int itemPos) {
        updateTagsFilter();
        entriesChanged(itemPos);
        saveEntries(auto_backup);
    }

    public void addEntry(Entry e) {
        if (entries.addEntry(e)) {
            saveAndRefresh(settings.getAutoBackupEncryptedPasswordsEnabled());
        } else {
            Snackbar.make((((MainActivity) context).findViewById(R.id.main_content)), R.string.toast_entry_exists, Snackbar.LENGTH_LONG).show();
        }
    }

    private int getRealIndex(int displayPosition) {
        return entries.indexOf(displayedEntries.get(displayPosition));
    }

    private void entriesChanged(int itemPos) {
        displayedEntries = entries.getEntriesSorted(sortMode);
        filterByTags(tagsFilter);

        if (itemPos == RecyclerView.NO_POSITION)
            notifyDataSetChanged();
        else
            notifyItemChanged(itemPos);
    }

    public void updateTagsFilter() {
        List<String> inUseTags = getTags();

        HashMap<String, Boolean> tagsHashMap = new HashMap<>();
        for(String tag: tagsFilterAdapter.getTags()) {
            if(inUseTags.contains(tag))
                tagsHashMap.put(tag, false);
        }
        for(String tag: tagsFilterAdapter.getActiveTags()) {
            if(inUseTags.contains(tag))
                tagsHashMap.put(tag, true);
        }
        for(String tag: getTags()) {
            if(inUseTags.contains(tag))
                if(!tagsHashMap.containsKey(tag))
                    tagsHashMap.put(tag, true);
        }

        tagsFilterAdapter.setTags(tagsHashMap);
        tagsFilter = tagsFilterAdapter.getActiveTags();
    }

    public void saveEntries(boolean auto_backup) {
        DatabaseHelper.saveDatabase(context, entries.getEntries(), encryptionKey);

        if (auto_backup) {
            Constants.BackupType backupType = BackupHelper.autoBackupType(context);
            if (backupType == Constants.BackupType.ENCRYPTED) {
                BackupHelper.BackupFile cryptBackupFile = BackupHelper.backupFile(context, settings.getBackupLocation(), Constants.BackupType.ENCRYPTED);

                if (cryptBackupFile.file != null) {
                    byte[] keyMaterial = encryptionKey.getEncoded();
                    SecretKey encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial);

                    boolean success = BackupHelper.backupToFile(context, cryptBackupFile.file.getUri(), settings.getBackupPasswordEnc(), encryptionKey);
                    if (success) {
                        Snackbar.make((((MainActivity) context).findViewById(R.id.main_content)),
                                R.string.backup_toast_export_success,
                                Snackbar.LENGTH_LONG)
                                .show();
                    } else {
                        Snackbar.make((((MainActivity) context).findViewById(R.id.main_content)),
                                R.string.backup_toast_export_failed,
                                Snackbar.LENGTH_LONG)
                                .show();
                    }
                } else {
                    Snackbar.make((((MainActivity) context).findViewById(R.id.main_content)),
                            cryptBackupFile.errorMessage,
                            Snackbar.LENGTH_LONG)
                            .show();
                }
            }
        }
    }

    public void loadEntries() {
        if (encryptionKey != null) {
            ArrayList<Entry> newEntries = DatabaseHelper.loadDatabase(context, encryptionKey);

            entries.updateEntries(newEntries, true);
            entriesChanged(RecyclerView.NO_POSITION);
        }
    }

    public void filterByTags(List<String> tags) {
        displayedEntries = entries.getEntriesFilteredByTags(tags, settings.getNoTagsToggle(), settings.getTagFunctionality(), sortMode);
        tagsFilter = tags;

        notifyDataSetChanged();
    }

    public void updateTimeBasedTokens() {
        boolean change = false;

        for (Entry e : entries.getEntries()) {
            if (e.isTimeBased()) {
                boolean cardVisible = !settings.getTapToReveal() || e.isVisible();

                boolean item_changed = e.updateOTP(false);
                boolean color_changed = false;

                // Check color change only if highlighting token feature is enabled and the entry is visible
                if (settings.isHighlightTokenOptionEnabled()) {
                    color_changed = cardVisible && e.hasColorChanged();
                }


                change = change || item_changed || color_changed ||
                        (cardVisible && (e.hasNonDefaultPeriod() || settings.isShowIndividualTimeoutsEnabled()));
            }
        }

        if (change)
            notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder entryViewHolder, int i) {
        Entry entry = displayedEntries.get(i);

        if (!entry.isTimeBased())
            entry.updateOTP(false);

        if(settings.isHighlightTokenOptionEnabled())
            entryViewHolder.updateColor(entry.getColor());
        entryViewHolder.updateValues(entry);

        entryViewHolder.setLabelSize(settings.getLabelSize());
        entryViewHolder.setLabelScroll(settings.getLabelDisplay());

        if(settings.getThumbnailVisible())
            entryViewHolder.setThumbnailSize(settings.getThumbnailSize());
    }

    @Override @NonNull
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.component_card, viewGroup, false);

        EntryViewHolder viewHolder = new EntryViewHolder(context, itemView, settings.getTapToReveal());
        viewHolder.setCallback(new EntryViewHolder.Callback() {
            @Override
            public void onMoveEventStart() {
                if (callback != null)
                    callback.onMoveEventStart();
            }

            @Override
            public void onMoveEventStop() {
                if (callback != null)
                    callback.onMoveEventStop();
            }

            @Override
            public void onMenuButtonClicked(View parentView, int position) {
                showPopupMenu(parentView, position);
            }

            @Override
            public void onCopyButtonClicked(String text, int position) {
                copyHandler(position, text, settings.isMinimizeAppOnCopyEnabled());
            }

            @Override
            public void onCardSingleClicked(final int position, final String text) {
                switch (settings.getTapSingle()) {
                    case REVEAL:
                        establishPinIfNeeded(position);
                        cardTapToRevealHandler(position);
                        break;
                    case COPY:
                        establishPinIfNeeded(position);
                        copyHandler(position, text, false);
                        break;
                    case COPY_BACKGROUND:
                        establishPinIfNeeded(position);
                        copyHandler(position, text, true);
                        break;
                    case SEND_KEYSTROKES:
                        establishPinIfNeeded(position);
                        sendKeystrokes(position);
                        break;
                    default:
                        // If tap-to-reveal is disabled a single tab still needs to establish the PIN
                        if (!settings.getTapToReveal())
                            establishPinIfNeeded(position);
                        break;
                }
            }


            @Override
            public void onCardDoubleClicked(final int position, final String text) {
                switch (settings.getTapDouble()) {
                    case REVEAL:
                        establishPinIfNeeded(position);
                        cardTapToRevealHandler(position);
                        break;
                    case COPY:
                        establishPinIfNeeded(position);
                        copyHandler(position, text, false);
                        break;
                    case COPY_BACKGROUND:
                        establishPinIfNeeded(position);
                        copyHandler(position, text, true);
                        break;
                    case SEND_KEYSTROKES:
                        establishPinIfNeeded(position);
                        sendKeystrokes(position);
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onCounterClicked(int position) {
                updateEntry(
                        displayedEntries.get(position),
                        entries.getEntry(getRealIndex(position)),
                        position
                );
            }

            @Override
            public void onCounterLongPressed(int position) {
                setCounter(position);
            }
        });

        return viewHolder;
    }

    private void establishPinIfNeeded(int position) {
        final Entry entry = displayedEntries.get(position);

        if (entry.getType() == Entry.OTPType.MOTP && entry.getPin().isEmpty())
            establishPIN(position);
    }

    private void copyHandler(final int position, final String text, final boolean dropToBackground) {
        Tools.copyToClipboard(context, text);
        updateLastUsedAndFrequency(position, getRealIndex(position));
        if (context != null && dropToBackground) {
            ((MainActivity)context).moveTaskToBack(true);
        }
    }

    private void cardTapToRevealHandler(final int position) {
        final Entry entry = displayedEntries.get(position);
        final int realIndex = entries.indexOf(entry);

        if (entry.isVisible()) {
            hideEntry(entry);
        } else {
            entries.getEntry(realIndex).setHideTask(() -> hideEntry(entry));
            taskHandler.postDelayed(entries.getEntry(realIndex).getHideTask(), settings.getTapToRevealTimeout() * 1000);

            if (entry.isCounterBased()) {
                updateEntry(entry, entries.getEntry(realIndex), position);
            }
            entry.setVisible(true);
            notifyItemChanged(position);
        }
    }

    private void updateEntry(Entry entry, Entry realEntry, final int position) {
        long counter = entry.getCounter() + 1;

        entry.setCounter(counter);
        entry.updateOTP(false);
        notifyItemChanged(position);

        realEntry.setCounter(counter);
        realEntry.updateOTP(false);
        saveEntries(settings.getAutoBackupEncryptedFullEnabled());
    }

    private void hideEntry(Entry entry) {
        int pos = displayedEntries.indexOf(entry);
        int realIndex = entries.indexOf(entry);

        if (realIndex >= 0) {
            entries.getEntry(realIndex).setVisible(false);
            taskHandler.removeCallbacks(entries.getEntry(realIndex).getHideTask());
            entries.getEntry(realIndex).setHideTask(null);
        }

        boolean updateNeeded = updateLastUsedAndFrequency(pos, realIndex);

        if (pos >= 0) {
            displayedEntries.get(pos).setVisible(false);

            if (updateNeeded)
                notifyItemChanged(pos);
        }
    }

    private void setCounter(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        int marginSmall = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_small);
        int marginMedium = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_medium);

        final EditText input = new EditText(context);
        input.setLayoutParams(new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setText(String.format(Locale.ENGLISH, "%d", displayedEntries.get(pos).getCounter()));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine();

        FrameLayout container = new FrameLayout(context);
        container.setPaddingRelative(marginMedium, marginSmall, marginMedium, 0);
        container.addView(input);

        AlertDialog dialog = builder.setTitle(R.string.dialog_title_counter)
                .setView(container)
                .setPositiveButton(R.string.button_save, (dialogInterface, i) -> {
                    int realIndex = getRealIndex(pos);
                    long newCounter = Long.parseLong(input.getEditableText().toString());

                    displayedEntries.get(pos).setCounter(newCounter);
                    notifyItemChanged(pos);

                    Entry e = entries.getEntry(realIndex);
                    e.setCounter(newCounter);

                    saveEntries(settings.getAutoBackupEncryptedFullEnabled());
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {

                })
                .setCancelable(false)
                .create();
        addCounterValidationWatcher(input, dialog);
        dialog.show();
    }

    private void addCounterValidationWatcher(EditText input, AlertDialog dialog) {
        TextWatcher counterWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable input) {
                Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (positive != null) {
                    positive.setEnabled(isZeroOrPositiveLongInput(input));
                }
            }

            private boolean isZeroOrPositiveLongInput(Editable input) {
                try {
                    return !TextUtils.isEmpty(input) && (Long.parseLong(input.toString()) >= 0);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        };
        input.addTextChangedListener(counterWatcher);
    }

    private boolean updateLastUsedAndFrequency(int position, int realIndex) {
        long timeStamp = System.currentTimeMillis();
        long entryUsedFrequency = entries.getEntry(realIndex).getUsedFrequency();

        if (position >= 0) {
            long displayEntryUsedFrequency = displayedEntries.get(position).getUsedFrequency();
            displayedEntries.get(position).setLastUsed(timeStamp);
            displayedEntries.get(position).setUsedFrequency(displayEntryUsedFrequency + 1);
        }

        entries.getEntry(realIndex).setLastUsed(timeStamp);
        entries.getEntry(realIndex).setUsedFrequency(entryUsedFrequency + 1);
        saveEntries(false);

        if (sortMode == SortMode.LAST_USED) {
            displayedEntries = EntryList.sortEntries(displayedEntries, sortMode);
            notifyDataSetChanged();
            return false;
        } else if (sortMode == SortMode.MOST_USED) {
            displayedEntries = EntryList.sortEntries(displayedEntries, sortMode);
            notifyDataSetChanged();
            return false;
        }

        return true;
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        if (sortMode == SortMode.UNSORTED && entries.isEqual(displayedEntries)) {
            entries.swapEntries(fromPosition, toPosition);

            displayedEntries = entries.getEntries();
            notifyItemMoved(fromPosition, toPosition);

            saveEntries(false);
        }

        return true;
    }

    public void changeThumbnail(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        int marginSmall = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_small);
        int marginMedium = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_medium);

        int realIndex = getRealIndex(pos);
        final ThumbnailSelectionAdapter thumbnailAdapter = new ThumbnailSelectionAdapter(context, entries.getEntry(realIndex).getIssuer(), entries.getEntry(realIndex).getLabel());

        final EditText input = new EditText(context);
        input.setLayoutParams(new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setSingleLine();

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                thumbnailAdapter.filter(editable.toString());
            }
        });

        int gridPadding = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_small);
        int gridBackground = Tools.getThemeColor(context, R.attr.thumbnailBackground);

        GridView grid = new GridView(context);
        grid.setAdapter(thumbnailAdapter);
        grid.setBackgroundColor(gridBackground);
        grid.setPadding(gridPadding, gridPadding, gridPadding, gridPadding);
        grid.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int thumbnailSize = settings.getThumbnailSize();
        grid.setColumnWidth(thumbnailSize);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setVerticalSpacing(context.getResources().getDimensionPixelSize(R.dimen.activity_margin_medium));
        grid.setHorizontalSpacing(context.getResources().getDimensionPixelSize(R.dimen.activity_margin_medium));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(input);
        layout.addView(grid);

        FrameLayout container = new FrameLayout(context);
        container.setPaddingRelative(marginMedium, marginSmall, marginMedium, 0);
        container.addView(layout);

        final AlertDialog alert = builder.setTitle(R.string.menu_popup_change_image)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {

                })
                .create();

        grid.setOnItemClickListener((parent, view, position, id) -> {
            int realIndex1 = getRealIndex(pos);
            EntryThumbnail.EntryThumbnails thumbnail = EntryThumbnail.EntryThumbnails.Default;
            try {
                int realPos = thumbnailAdapter.getRealIndex(position);
                thumbnail = EntryThumbnail.EntryThumbnails.values()[realPos];
            } catch (Exception e) {
                e.printStackTrace();
            }

            Entry e = entries.getEntry(realIndex1);
            e.setThumbnail(thumbnail);

            saveEntries(settings.getAutoBackupEncryptedFullEnabled());
            notifyItemChanged(pos);
            alert.cancel();
        });

        alert.show();
    }

    public void establishPIN(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        int marginSmall = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_small);
        int marginMedium = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_medium);

        final EditText input = new EditText(context);
        input.setLayoutParams(new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setRawInputType(InputType.TYPE_NUMBER_VARIATION_PASSWORD  | InputType.TYPE_CLASS_NUMBER);
        input.setText(displayedEntries.get(pos).getPin());
        input.setSingleLine();
        input.requestFocus();
        input.setTransformationMethod(new PasswordTransformationMethod());
        UIHelper.showKeyboard(context, input, true);

        FrameLayout container = new FrameLayout(context);
        container.setPaddingRelative(marginMedium, marginSmall, marginMedium, 0);
        container.addView(input);

        builder.setTitle(R.string.dialog_title_pin)
                .setCancelable(false)
                .setView(container)
                .setPositiveButton(R.string.button_accept, (dialogInterface, i) -> {
                    int realIndex = getRealIndex(pos);
                    String newPin = input.getEditableText().toString();

                    displayedEntries.get(pos).setPin(newPin);
                    Entry e = entries.getEntry(realIndex);
                    e.setPin(newPin);
                    e.updateOTP(true);
                    notifyItemChanged(pos);
                    UIHelper.hideKeyboard(context, input);
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> UIHelper.hideKeyboard(context, input))
                .create()
                .show();
    }

    @SuppressLint("StringFormatInvalid")
    public void removeItem(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        String label = displayedEntries.get(pos).getLabel();
        String message = context.getString(R.string.dialog_msg_confirm_delete, label);

        builder.setTitle(R.string.dialog_title_remove)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    int realIndex = getRealIndex(pos);

                    displayedEntries.remove(pos);
                    notifyItemRemoved(pos);

                    entries.removeEntry(realIndex);
                    saveEntries(settings.getAutoBackupEncryptedFullEnabled());
                })
                .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {

                })
                .setCancelable(false)
                .show();
    }

    // sends the current OTP code via a "Send Action" with the MIME type "text/x-keystrokes"
    // other apps (eg. https://github.com/KDE/kdeconnect-android ) can listen for this and handle
    // the current code on their own (eg. sending it to a connected device/browser/...)
    private void sendKeystrokes(final int pos) {
        String otp = displayedEntries.get(pos).getCurrentOTP();
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/x-keystrokes");
        sendIntent.putExtra(Intent.EXTRA_TEXT, otp);
        if (sendIntent.resolveActivity(this.context.getPackageManager()) != null) {
            this.context.startActivity(sendIntent);
        }
    }


    private void showQRCode(final int pos) {
        Uri uri = displayedEntries.get(pos).toUri();
        if (uri != null) {
            Bitmap bitmap;
            try {
                bitmap = new BarcodeEncoder().encodeBitmap(uri.toString(), BarcodeFormat.QR_CODE, 0, 0);
            } catch(Exception ignored) {
                Snackbar.make((((MainActivity) context).findViewById(R.id.main_content)),
                        R.string.toast_qr_failed_to_generate,
                        Snackbar.LENGTH_LONG)
                        .show();
                return;
            }
            BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            drawable.setFilterBitmap(false);

            ImageView image = new ImageView(context);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageDrawable(drawable);

            new AlertDialog.Builder(context)
                    .setTitle(R.string.dialog_title_qr_code)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {})
                    .setView(image)
                    .create()
                    .show();
        } else {
            Snackbar.make((((MainActivity) context).findViewById(R.id.main_content)),
                    R.string.toast_qr_unsupported,
                    Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void showPopupMenu(View view, final int pos) {
        View menuItemView = view.findViewById(R.id.menuButton);
        PopupMenu popup = new PopupMenu(view.getContext(), menuItemView);
        MenuInflater inflate = popup.getMenuInflater();
        inflate.inflate(R.menu.menu_popup, popup.getMenu());

        if (displayedEntries.get(pos).getType() == Entry.OTPType.MOTP){
            MenuItem item = popup.getMenu().getItem(ESTABLISH_PIN_MENU_INDEX);
            item.setVisible(true);
        }


        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_popup_edit) {
                ManualEntryDialog.show((MainActivity) context, settings, EntriesCardAdapter.this, entries.getEntry(getRealIndex(pos)), () -> saveAndRefresh(settings.getAutoBackupEncryptedFullEnabled(), pos));
                return true;
            } else if(id == R.id.menu_popup_changeImage) {
                changeThumbnail(pos);
                return true;
            } else if (id == R.id.menu_popup_establishPin) {
                establishPIN(pos);
                return true;
            } else if (id == R.id.menu_popup_show_qr_code) {
                showQRCode(pos);
                return true;
            }  else if (id == R.id.menu_popup_remove) {
                removeItem(pos);
                return true;
            } else if (id == R.id.menu_send_keystrokes) {
                sendKeystrokes(pos);
                return true;
            } else {
                return false;
            }
        });
        popup.show();
    }

    public void setSortMode(SortMode mode) {
        this.sortMode = mode;
        entriesChanged(RecyclerView.NO_POSITION);
    }

    public SortMode getSortMode() {
        return this.sortMode;
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    public EntryFilter getFilter() {
        if (filter == null)
            filter = new EntryFilter();

        return filter;
    }

    public void clearFilter() {
        if (filter != null)
            filter = null;
    }

    public List<String> getTags() {
        return entries.getAllTags();
    }

    public class EntryFilter extends Filter {
        private final List<Constants.SearchIncludes> filterValues = settings.getSearchValues();

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            ArrayList<Entry> filtered = entries.getFilteredEntries(constraint, filterValues, sortMode);

            final FilterResults filterResults = new FilterResults();
            filterResults.count = filtered.size();
            filterResults.values = filtered;

            return filterResults;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, @NonNull FilterResults results) {
            displayedEntries = (ArrayList<Entry>) results.values;
            notifyDataSetChanged();
        }
    }

    public interface Callback {
        void onMoveEventStart();
        void onMoveEventStop();
    }
}
