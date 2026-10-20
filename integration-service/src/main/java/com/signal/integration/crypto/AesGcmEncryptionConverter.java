package com.signal.integration.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class AesGcmEncryptionConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEncryptionConverter.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final String PREFIX = "ENC:";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionConverter() {
        this(resolveKeyFromEnv());
    }

    public AesGcmEncryptionConverter(String keyString) {
        if (keyString == null || keyString.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be null or blank");
        }
        this.secretKey = deriveKey(keyString);
    }

    private static String resolveKeyFromEnv() {
        String key = System.getenv("SIGNAL_ENCRYPTION_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("signal.encryption.key");
        }
        if (key == null || key.isBlank()) {
            key = System.getenv("SIGNAL_JWT_SECRET");
        }
        if (key == null || key.isBlank()) {
            key = "signal_development_default_aes_256_encryption_key_change_in_prod";
            log.warn("SIGNAL_ENCRYPTION_KEY not set. Using development fallback key. DO NOT USE IN PRODUCTION.");
        }
        return key;
    }

    private SecretKey deriveKey(String rawKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        if (attribute.isBlank()) {
            return attribute;
        }
        // If already encrypted, do not re-encrypt
        if (attribute.startsWith(PREFIX)) {
            return attribute;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt OAuth token at rest", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (dbData.isBlank()) {
            return dbData;
        }
        // Legacy plaintext compatibility: return as-is if unencrypted
        if (!dbData.startsWith(PREFIX)) {
            return dbData;
        }

        try {
            String base64Payload = dbData.substring(PREFIX.length());
            byte[] decoded = Base64.getDecoder().decode(base64Payload);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt OAuth token. Invalid key or corrupted ciphertext.", e);
        }
    }
}
