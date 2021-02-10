package com.gigabytedevelopersinc.app.cometOTP.Tasks;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.StorageAccessHelper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.SecretKey;

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Wednesday, 10
 * Month: February
 * Year: 2021
 * Date: 10 Feb, 2021
 * Time: 12:02 AM
 * Desc: EncryptedRestoreTask
 **/
public class EncryptedRestoreTask extends GenericRestoreTask {
    private final String password;
    private final boolean oldFormat;

    public EncryptedRestoreTask(Context context, Uri uri, String password, boolean oldFormat) {
        super(context, uri);
        this.password = password;
        this.oldFormat = oldFormat;
    }

    @Override
    @NonNull
    protected RestoreTaskResult doInBackground() {
        boolean success = true;
        String decryptedString = "";

        try {
            byte[] data = StorageAccessHelper.loadFile(applicationContext, uri);

            if (oldFormat) {
                SecretKey key = EncryptionHelper.generateSymmetricKeyFromPassword(password);
                byte[] decrypted = EncryptionHelper.decrypt(key, data);

                decryptedString = new String(decrypted, StandardCharsets.UTF_8);
            } else {
                byte[] iterBytes = Arrays.copyOfRange(data, 0, Constants.INT_LENGTH);
                byte[] salt = Arrays.copyOfRange(data, Constants.INT_LENGTH, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH);
                byte[] encrypted = Arrays.copyOfRange(data, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH, data.length);

                int iter = ByteBuffer.wrap(iterBytes).getInt();

                SecretKey key = EncryptionHelper.generateSymmetricKeyPBKDF2(password, iter, salt);

                byte[] decrypted = EncryptionHelper.decrypt(key, encrypted);
                decryptedString = new String(decrypted, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            success = false;
            e.printStackTrace();
        }

        if (success) {
            return RestoreTaskResult.success(decryptedString);
        } else {
            return RestoreTaskResult.failure(R.string.backup_toast_import_decryption_failed);
        }
    }
}
