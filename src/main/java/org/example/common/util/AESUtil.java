package org.example.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AESUtil {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    
    // khóa dự phòng 256 bit
    private static final String DEFAULT_KEY = "12345678901234567890123456789012"; 
    private static final String KEY_ENV_VAR = "CHAT_APP_AES_KEY";
    private static final String PREFIX = "enc:";

    private static SecretKeySpec getSecretKey() {
        String keyStr = System.getenv(KEY_ENV_VAR);
        if (keyStr == null || keyStr.length() != 32) {
            keyStr = DEFAULT_KEY;
        }
        return new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "AES");
    }

    public static String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), ivSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // ghép iv và dữ liệu đã mã hóa
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting text", e);
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        // trả về dữ liệu cũ nếu không có tiền tố mã hóa
        if (!encryptedText.startsWith(PREFIX)) {
            return encryptedText;
        }
        try {
            String base64Content = encryptedText.substring(PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Content);

            if (combined.length < 16) {
                return encryptedText; // trả về chuỗi gốc khi sai định dạng
            }

            // tách iv từ 16 byte đầu
            byte[] iv = new byte[16];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // tách phần byte đã mã hóa còn lại
            int encryptedLength = combined.length - 16;
            byte[] encryptedBytes = new byte[encryptedLength];
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // trả về dữ liệu gốc khi giải mã lỗi
            System.err.println("AESUtil Decryption failed, returning raw string. Error: " + e.getMessage());
            return encryptedText;
        }
    }
}
