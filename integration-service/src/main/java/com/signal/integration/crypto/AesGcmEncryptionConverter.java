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
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * JPA AttributeConverter for OAuth refresh tokens using AES-256-GCM.
 *
 * Security specifications:
 * 1. Key: Strictly 32 bytes (256-bit) loaded from the SIGNAL_ENCRYPTION_KEY environment variable.
 *    Fails startup clearly with IllegalStateException if missing.
 * 2. Cipher: AES/GCM/NoPadding with 128-bit authentication tag.
 * 3. Fresh IV: 12-byte cryptographically secure random IV generated per encryption.
 * 4. Versioned format: Outputs "v1:ENC:<base64(12-byte IV + ciphertext + 16-byte GCM tag)>".
 * 5. Backward compatibility: Transparent decrypt-with-fallback for legacy plaintext rows
 *    and automatic re-encryption to v1:ENC: on save.
 */
@Converter
public class AesGcmEncryptionConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEncryptionConverter.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    public static final String PREFIX_V1 = "v1:ENC:";
    public static final String PREFIX_LEGACY = "ENC:";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionConverter() {
        this(resolveKeyFromEnv());
    }

    public AesGcmEncryptionConverter(String keyString) {
        if (keyString == null || keyString.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be null or blank");
        }
        this.secretKey = parseAndValidate32ByteKey(keyString);
    }

    static String resolveKeyFromEnv() {
        String key = System.getenv("SIGNAL_ENCRYPTION_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("signal.encryption.key");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable SIGNAL_ENCRYPTION_KEY for AES-256 token encryption at rest. Application startup failed."
            );
        }
        return key;
    }

    private SecretKey parseAndValidate32ByteKey(String rawKey) {
        byte[] keyBytes;
        String trimmed = rawKey.trim();

        // 1. Try 64-char Hex (32 bytes)
        if (trimmed.length() == 64 && trimmed.matches("^[0-9a-fA-F]{64}$")) {
            keyBytes = HexFormat.of().parseHex(trimmed);
        }
        // 2. Try Base64 encoding of 32 bytes (typically 44 chars)
        else if (trimmed.length() == 44 && trimmed.endsWith("=")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(trimmed);
                if (decoded.length == 32) {
                    keyBytes = decoded;
                } else {
                    keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
                }
            } catch (IllegalArgumentException e) {
                keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
            }
        }
        // 3. Raw UTF-8 bytes
        else {
            keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "SIGNAL_ENCRYPTION_KEY must resolve to exactly 32 bytes (256-bit) for AES-256. Found: "
                            + keyBytes.length + " bytes. Provided format must be 32 ASCII characters, 64 hex characters, or 32-byte Base64."
            );
        }

        return new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        if (attribute.isBlank()) {
            return attribute;
        }
        // If already encrypted with current v1 version, do not re-encrypt
        if (attribute.startsWith(PREFIX_V1)) {
            return attribute;
        }
        // If encrypted with legacy prefix, decrypt first so it gets upgraded to v1
        String plainTextToEncrypt = attribute;
        if (attribute.startsWith(PREFIX_LEGACY)) {
            plainTextToEncrypt = convertToEntityAttribute(attribute);
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainTextToEncrypt.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return PREFIX_V1 + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt OAuth token at rest with AES-256-GCM", e);
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

        // Decrypt-with-fallback: if not prefixed, it's a legacy plaintext token; return as-is
        boolean isV1 = dbData.startsWith(PREFIX_V1);
        boolean isLegacy = dbData.startsWith(PREFIX_LEGACY);

        if (!isV1 && !isLegacy) {
            // Transparent fallback for existing plaintext rows
            return dbData;
        }

        try {
            String base64Payload = isV1
                    ? dbData.substring(PREFIX_V1.length())
                    : dbData.substring(PREFIX_LEGACY.length());

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
