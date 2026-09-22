package com.gigabytedevelopersinc.app.cometOTP;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests that need a real device or emulator: Entry parses otpauth:// URLs through android.net.Uri,
 * and the database work goes through the Android keystore.
 *
 * EncryptionHelper is here for a subtler reason: it passes an IvParameterSpec to AES/GCM, which
 * Android's security provider accepts and the JDK's does not, so it can only be checked against a
 * real provider.
 *
 * The parts that need neither — TokenCalculator and Tools — live in app/src/test instead, so they
 * run on every pull request without waiting for an emulator.
 */
public class ApplicationTest {

    @Test
    public void testEntry() throws Exception {
        byte[] secret = "Das System ist sicher".getBytes();
        String label = "5 von 5 Sterne";
        int period = 30;

        String s = "{\"secret\":\"" + new String(new Base32().encode(secret)) + "\"," +
                "\"issuer\":\"\"," +
                "\"label\":\"" + label + "\"," +
                "\"digits\":6," +
                "\"type\":\"TOTP\"," +
                "\"algorithm\":\"SHA1\"," +
                "\"thumbnail\":\"Default\"," +
                "\"last_used\":0," +
                "\"used_frequency\":0," +
                "\"period\":" + period + "," +
                "\"tags\":[\"test1\",\"test2\"]}";

        Entry e = new Entry(new JSONObject(s));
        assertArrayEquals(secret, e.getSecret());
        assertEquals(label, e.getLabel());

        String[] tags = new String[]{"test1", "test2"};
        assertEquals(tags.length, e.getTags().size());
        assertArrayEquals(tags, e.getTags().toArray(new String[0]));

        assertEquals(s, e.toJSON().toString());
    }


    @Test
    public void testEntryURL() throws Exception {
        try {
            new Entry("DON'T CARE");
            fail();
        } catch (Exception ignored) {
        }

        try {
            new Entry("https://github.com/0xbb/");
            fail();
        } catch (Exception ignored) {
        }

        try {
            new Entry("otpauth://hotp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ");
            fail();
        }
        catch (Exception ignored){
        }

        try {
            new Entry("otpauth://totp/ACME");
            fail();
        }
        catch (Exception ignored){
        }

        Entry entry = new Entry("otpauth://totp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&ALGORITHM=SHA1&digits=6&period=30");
        assertEquals("john.doe@email.com", entry.getLabel());

        Entry entry2 = new Entry("otpauth://totp/ :john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&ALGORITHM=SHA1&digits=6&period=30");
        assertEquals(":john.doe@email.com", entry2.getLabel());

        Entry entry3 = new Entry("otpauth://totp/ :john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=%20&ALGORITHM=SHA1&digits=6&period=30");
        assertEquals("john.doe@email.com", entry3.getLabel());

        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", new String(new Base32().encode(entry.getSecret())));


        entry = new Entry("otpauth://totp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&ALGORITHM=SHA1&digits=6&period=30&tags=test1&tags=test2");
        assertEquals("john.doe@email.com", entry.getLabel());

        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", new String(new Base32().encode(entry.getSecret())));
        String[] tags = new String[]{"test1", "test2"};
        assertEquals(tags.length, entry.getTags().size());
        assertArrayEquals(tags, entry.getTags().toArray(new String[0]));
    }

    @Test
    public void testSettingsHelper() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry("settings");

        new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE).delete();
        new File(context.getFilesDir() + "/" + Constants.FILENAME_ENCRYPTED_KEY).delete();

        SecretKey encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, false);
        ArrayList<Entry> b = DatabaseHelper.loadDatabase(context, encryptionKey);
        assertEquals(0, b.size());

        ArrayList<Entry> a = new ArrayList<>();
        Entry e = new Entry();
        e.setLabel("label");
        e.setSecret("secret".getBytes());
        a.add(e);

        e = new Entry();
        e.setLabel("label2");
        e.setSecret("secret2".getBytes());
        a.add(e);

        DatabaseHelper.saveDatabase(context, a, encryptionKey);
        b = DatabaseHelper.loadDatabase(context, encryptionKey);

        assertEquals(a, b);

        new File(context.getFilesDir() + "/" + Constants.FILENAME_DATABASE).delete();
        new File(context.getFilesDir() + "/" + Constants.FILENAME_ENCRYPTED_KEY).delete();
    }

    @Test
    public void testEncryptionHelper() throws NoSuchPaddingException, BadPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, UnsupportedEncodingException, InvalidAlgorithmParameterException, DecoderException {


        // https://golang.org/src/crypto/cipher/gcm_test.go
        String[][] testCases =  new String[][]{
                new String []{"11754cd72aec309bf52f7687212e8957","3c819d9a9bed087615030b65","", "250327c674aaf477aef2675748cf6971" },
                new String []{"ca47248ac0b6f8372a97ac43508308ed","ffd2b598feabc9019262d2be","", "60d20404af527d248d893ae495707d1a" },
                new String []{"7fddb57453c241d03efbed3ac44e371c","ee283a3fc75575e33efd4887","d5de42b461646c255c87bd2962d3b9a2", "2ccda4a5415cb91e135c2a0f78c9b2fdb36d1df9b9d5e596f83e8b7f52971cb3" },
                new String []{"ab72c77b97cb5fe9a382d9fe81ffdbed","54cc7dc2c37ec006bcc6d1da","007c5e5b3e59df24a7c355584fc1518d", "0e1bde206a07a9c2c1b65300f8c649972b4401346697138c7a4891ee59867d0c" },
                new String []{"feffe9928665731c6d6a8f9467308308","cafebabefacedbaddecaf888","d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255", "42831ec2217774244b7221b784d0d49ce3aa212f2c02a4e035c17e2329aca12e21d514b25466931c7d8f6a5aac84aa051ba30b396a0aac973d58e091473f59854d5c2af327cd64a62cf35abd2ba6fab4" },

        };

        for(String[] testCase: testCases){

            SecretKeySpec k = new SecretKeySpec(new Hex().decode(testCase[0].getBytes()), "AES");
            IvParameterSpec iv = new IvParameterSpec(new Hex().decode(testCase[1].getBytes()));

            byte[] cipherTExt = EncryptionHelper.encrypt(k,iv,new Hex().decode(testCase[2].getBytes()));
            String cipher = new String(new Hex().encode(cipherTExt));

            assertEquals(cipher, testCase[3]);

            assertEquals(testCase[2], new String(new Hex().encode(EncryptionHelper.decrypt(k, iv, cipherTExt))));

        }
    }
}
