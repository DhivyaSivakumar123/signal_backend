package com.signal.integration.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionConverterTest {

    // Exactly 32 bytes (256-bit key) in ASCII
    private final String testKey32Bytes = "12345678901234567890123456789012";
    // 64 hex characters (32 bytes)
    private final String testHexKey64Chars = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final AesGcmEncryptionConverter converter = new AesGcmEncryptionConverter(testKey32Bytes);

    @Test
    @DisplayName("Encrypt outputs versioned prefix v1:ENC: and round trip matches plaintext")
    void testVersionedRoundTripEncryptionDecryption() {
        String originalToken = "ya29.a0AfH6SMD_test_refresh_token_xyz987654321";

        String encrypted = converter.convertToDatabaseColumn(originalToken);
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("v1:ENC:"), "Encrypted token must start with versioned v1:ENC: prefix");
        assertNotEquals(originalToken, encrypted, "Ciphertext must not equal plaintext");

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(originalToken, decrypted, "Decrypted token must match original plaintext");
    }

    @Test
    @DisplayName("64-character hex key parses successfully into 32-byte AES-256 key")
    void testHexKeySupport() {
        AesGcmEncryptionConverter hexConverter = new AesGcmEncryptionConverter(testHexKey64Chars);
        String token = "sample_hex_key_token";
        String encrypted = hexConverter.convertToDatabaseColumn(token);
        assertTrue(encrypted.startsWith("v1:ENC:"));
        assertEquals(token, hexConverter.convertToEntityAttribute(encrypted));
    }

    @Test
    @DisplayName("Key that is not 32 bytes throws IllegalArgumentException")
    void testInvalidKeyLengthThrows() {
        // 16 bytes (AES-128, rejected)
        String shortKey = "1234567890123456";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AesGcmEncryptionConverter(shortKey));
        assertTrue(ex.getMessage().contains("must resolve to exactly 32 bytes"));

        // 10 bytes
        assertThrows(IllegalArgumentException.class,
                () -> new AesGcmEncryptionConverter("short_key!"));
    }

    @Test
    @DisplayName("Dynamic IV produces different ciphertexts for the same plaintext")
    void testRandomIvProducesDifferentCiphertexts() {
        String token = "sample_oauth_refresh_token_value";

        String encrypted1 = converter.convertToDatabaseColumn(token);
        String encrypted2 = converter.convertToDatabaseColumn(token);

        assertNotEquals(encrypted1, encrypted2, "Two encryptions of the same token must produce distinct ciphertexts due to fresh dynamic IV");
        assertEquals(token, converter.convertToEntityAttribute(encrypted1));
        assertEquals(token, converter.convertToEntityAttribute(encrypted2));
    }

    @Test
    @DisplayName("Legacy unencrypted tokens pass through decrypt transparently (decrypt-with-fallback)")
    void testLegacyPlaintextCompatibility() {
        String legacyToken = "legacy_unencrypted_google_refresh_token_123";

        String entityValue = converter.convertToEntityAttribute(legacyToken);
        assertEquals(legacyToken, entityValue, "Tokens without encryption prefix should be returned as-is for backward compatibility");
    }

    @Test
    @DisplayName("Legacy plaintext gets re-encrypted on write to v1:ENC: format")
    void testPlaintextReEncryptionOnSave() {
        String legacyPlaintextToken = "1//old_plaintext_unencrypted_token";

        // 1. Reading from DB returns original plaintext through fallback
        String loadedEntityToken = converter.convertToEntityAttribute(legacyPlaintextToken);
        assertEquals(legacyPlaintextToken, loadedEntityToken);

        // 2. Writing entity back to DB converts and encrypts it
        String reEncryptedDbColumn = converter.convertToDatabaseColumn(loadedEntityToken);
        assertTrue(reEncryptedDbColumn.startsWith("v1:ENC:"));
        assertNotEquals(legacyPlaintextToken, reEncryptedDbColumn);

        // 3. Subsequent read decodes properly
        String decryptedAgain = converter.convertToEntityAttribute(reEncryptedDbColumn);
        assertEquals(legacyPlaintextToken, decryptedAgain);
    }

    @Test
    @DisplayName("Legacy unversioned ENC: prefix is still decryptable and upgrades on save")
    void testLegacyEncPrefixCompatibility() {
        // Construct a legacy token format: ENC:<base64>
        AesGcmEncryptionConverter legacyKeyConverter = new AesGcmEncryptionConverter(testKey32Bytes);
        String token = "legacy_token_to_upgrade";
        String v1Encrypted = legacyKeyConverter.convertToDatabaseColumn(token);
        String legacyEncrypted = "ENC:" + v1Encrypted.substring("v1:ENC:".length());

        // Decrypts via legacy prefix support
        String decrypted = converter.convertToEntityAttribute(legacyEncrypted);
        assertEquals(token, decrypted);

        // Converting to DB column upgrades it to v1:ENC:
        String upgraded = converter.convertToDatabaseColumn(legacyEncrypted);
        assertTrue(upgraded.startsWith("v1:ENC:"));
        assertEquals(token, converter.convertToEntityAttribute(upgraded));
    }

    @Test
    @DisplayName("Null and blank tokens are safely handled without encryption")
    void testNullAndBlankHandling() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertEquals("", converter.convertToEntityAttribute(""));
    }

    @Test
    @DisplayName("Decrypting with an incorrect key fails with exception")
    void testWrongKeyFailsDecryption() {
        String token = "secret_google_oauth_token";
        String encrypted = converter.convertToDatabaseColumn(token);

        AesGcmEncryptionConverter wrongKeyConverter = new AesGcmEncryptionConverter("99999999901234567890123456789012");

        assertThrows(IllegalStateException.class, () -> wrongKeyConverter.convertToEntityAttribute(encrypted));
    }

    @Test
    @DisplayName("Corrupted or tampered ciphertext fails authentication check")
    void testCorruptedCiphertextThrows() {
        String corrupted = "v1:ENC:invalid_base64_or_tampered_payload_$$$";
        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute(corrupted));
    }

    @Test
    @DisplayName("Blank key in constructor throws IllegalArgumentException")
    void testBlankKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmEncryptionConverter(""));
        assertThrows(IllegalArgumentException.class, () -> new AesGcmEncryptionConverter(null));
    }

    @Test
    @DisplayName("Missing SIGNAL_ENCRYPTION_KEY environment variable throws IllegalStateException")
    void testMissingEnvVarThrows() {
        // When no env var and no system property are set, resolveKeyFromEnv throws IllegalStateException
        // Ensure system property is cleared for this test
        String prev = System.getProperty("signal.encryption.key");
        try {
            System.clearProperty("signal.encryption.key");
            // If the machine does not have SIGNAL_ENCRYPTION_KEY set in OS env:
            if (System.getenv("SIGNAL_ENCRYPTION_KEY") == null) {
                assertThrows(IllegalStateException.class, AesGcmEncryptionConverter::resolveKeyFromEnv);
            }
        } finally {
            if (prev != null) {
                System.setProperty("signal.encryption.key", prev);
            }
        }
    }
}
