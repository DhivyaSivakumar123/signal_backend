package com.signal.integration.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionConverterTest {

    private final String testKey = "test_encryption_secret_key_1234567890123456";
    private final AesGcmEncryptionConverter converter = new AesGcmEncryptionConverter(testKey);

    @Test
    @DisplayName("Encrypt and decrypt round trip succeeds")
    void testRoundTripEncryptionDecryption() {
        String originalToken = "ya29.a0AfH6SMD_test_refresh_token_xyz987654321";

        String encrypted = converter.convertToDatabaseColumn(originalToken);
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("ENC:"), "Encrypted token must start with ENC: prefix");
        assertNotEquals(originalToken, encrypted, "Ciphertext must not equal plaintext");

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(originalToken, decrypted, "Decrypted token must match original plaintext");
    }

    @Test
    @DisplayName("Dynamic IV produces different ciphertexts for the same plaintext")
    void testRandomIvProducesDifferentCiphertexts() {
        String token = "sample_oauth_refresh_token_value";

        String encrypted1 = converter.convertToDatabaseColumn(token);
        String encrypted2 = converter.convertToDatabaseColumn(token);

        assertNotEquals(encrypted1, encrypted2, "Two encryptions of the same token must produce distinct ciphertexts due to dynamic IV");
        assertEquals(token, converter.convertToEntityAttribute(encrypted1));
        assertEquals(token, converter.convertToEntityAttribute(encrypted2));
    }

    @Test
    @DisplayName("Null and blank tokens are safely handled")
    void testNullAndBlankHandling() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertEquals("", converter.convertToEntityAttribute(""));
    }

    @Test
    @DisplayName("Legacy unencrypted tokens pass through decrypt transparently")
    void testLegacyPlaintextCompatibility() {
        String legacyToken = "legacy_unencrypted_google_refresh_token_123";

        String entityValue = converter.convertToEntityAttribute(legacyToken);
        assertEquals(legacyToken, entityValue, "Tokens without ENC: prefix should be returned as-is for backward compatibility");
    }

    @Test
    @DisplayName("Decrypting with an incorrect key fails with exception")
    void testWrongKeyFailsDecryption() {
        String token = "secret_google_oauth_token";
        String encrypted = converter.convertToDatabaseColumn(token);

        AesGcmEncryptionConverter wrongKeyConverter = new AesGcmEncryptionConverter("completely_different_secret_key_99999");

        assertThrows(IllegalStateException.class, () -> wrongKeyConverter.convertToEntityAttribute(encrypted));
    }

    @Test
    @DisplayName("Corrupted or tampered ciphertext fails authentication check")
    void testCorruptedCiphertextThrows() {
        String corrupted = "ENC:invalid_base64_or_tampered_payload_$$$";
        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute(corrupted));
    }

    @Test
    @DisplayName("Blank key in constructor throws IllegalArgumentException")
    void testBlankKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmEncryptionConverter(""));
        assertThrows(IllegalArgumentException.class, () -> new AesGcmEncryptionConverter(null));
    }
}
