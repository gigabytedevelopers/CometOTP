package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;

import java.util.ArrayList;

import javax.crypto.SecretKey;

/**
 * Re-encrypts the database with a new key, restoring the internal backup if anything fails.
 * Shared by the settings screen and the security screen.
 */
public final class EncryptionChangeHelper {

    public enum Status { SUCCESS, BACKUP_FAILED, NO_KEY, SAVE_FAILED }

    public static final class Result {
        public final Status status;
        @Nullable public final SecretKey newKey;

        Result(Status status, @Nullable SecretKey newKey) {
            this.status = status;
            this.newKey = newKey;
        }
    }

    private EncryptionChangeHelper() {
    }

    /**
     * @param currentKey  key the database is currently encrypted with (may be null when empty)
     * @param newType     encryption type to switch to
     * @param newKeyBytes key material for {@link Constants.EncryptionType#PASSWORD}; ignored for the KeyStore
     */
    @NonNull
    public static Result changeEncryption(@NonNull Context context, @Nullable SecretKey currentKey,
                                          @NonNull Constants.EncryptionType newType, @Nullable byte[] newKeyBytes) {
        if (!DatabaseHelper.backupDatabase(context))
            return new Result(Status.BACKUP_FAILED, null);

        ArrayList<Entry> entries;
        if (currentKey != null)
            entries = DatabaseHelper.loadDatabase(context, currentKey);
        else
            entries = new ArrayList<>();

        SecretKey newEncryptionKey;
        if (newType == Constants.EncryptionType.KEYSTORE) {
            newEncryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, true);
        } else if (newKeyBytes != null && newKeyBytes.length > 0) {
            newEncryptionKey = EncryptionHelper.generateSymmetricKey(newKeyBytes);
        } else {
            DatabaseHelper.restoreDatabaseBackup(context);
            return new Result(Status.NO_KEY, null);
        }

        if (DatabaseHelper.saveDatabase(context, entries, newEncryptionKey))
            return new Result(Status.SUCCESS, newEncryptionKey);

        DatabaseHelper.restoreDatabaseBackup(context);
        return new Result(Status.SAVE_FAILED, null);
    }
}
