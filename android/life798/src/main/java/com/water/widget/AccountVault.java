package com.water.widget;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 慧生活账户与 Token 的本地加密容器。
 *
 * 密文格式：v1:<base64(ivLength | iv | AES-GCM ciphertext)>。
 * 密钥保存在 Android Keystore，不进入 SharedPreferences、备份或源码。
 */
final class AccountVault {
    private static final String PREFIX = "v1:";
    private static final String KEY_ALIAS = "stmall.life798.accounts.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private AccountVault() {}

    static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    static String seal(Context context, String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer packed = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
            packed.put((byte) iv.length);
            packed.put(iv);
            packed.put(ciphertext);
            return PREFIX + Base64.encodeToString(packed.array(), Base64.NO_WRAP);
        } catch (Exception error) {
            throw new IllegalStateException("无法加密慧生活账户数据", error);
        }
    }

    static String open(Context context, String stored) {
        if (!isEncrypted(stored)) return stored;
        try {
            byte[] packed = Base64.decode(stored.substring(PREFIX.length()), Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            int ivLength = buffer.get() & 0xFF;
            if (ivLength < 12 || ivLength > 32 || buffer.remaining() <= ivLength) {
                throw new IllegalArgumentException("Invalid encrypted account payload");
            }
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("无法解密慧生活账户数据，请重新登录", error);
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }

        synchronized (AccountVault.class) {
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
            );
            generator.init(
                    new KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                    )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .setRandomizedEncryptionRequired(true)
                            .build()
            );
            return generator.generateKey();
        }
    }
}
