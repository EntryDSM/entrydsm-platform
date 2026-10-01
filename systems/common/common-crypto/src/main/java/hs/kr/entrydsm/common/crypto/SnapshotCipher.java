package hs.kr.entrydsm.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** v2: version | key ID length | ASCII key ID | 12-byte nonce | ciphertext + 16-byte tag. */
public final class SnapshotCipher {
    private static final int NONCE_BYTES = AesGcm.NONCE_BYTES;
    private static final int TAG_BYTES = AesGcm.TAG_BYTES;
    private final Map<String, SecretKeySpec> keys;
    private final SecretKeySpec activeKey;
    private final SecretKeySpec legacyKey;
    private final byte[] header;

    public SnapshotCipher(String activeKeyId, Map<String, String> base64Keys) {
        this(activeKeyId, base64Keys, null);
    }

    public SnapshotCipher(String activeKeyId, Map<String, String> base64Keys, String legacyBase64Key) {
        validateKeyId(activeKeyId);
        Map<String, SecretKeySpec> decoded = new HashMap<>();
        base64Keys.forEach((id, value) -> {
            validateKeyId(id);
            decoded.put(id, AesGcm.decodeKey(value));
        });
        keys = Map.copyOf(decoded);
        activeKey = keys.get(activeKeyId);
        if (activeKey == null) throw new IllegalArgumentException("Active snapshot key is missing");
        legacyKey = legacyBase64Key == null || legacyBase64Key.isBlank() ? null : AesGcm.decodeKey(legacyBase64Key);
        byte[] id = activeKeyId.getBytes(StandardCharsets.US_ASCII);
        header = ByteBuffer.allocate(2 + id.length).put((byte) 2).put((byte) id.length).put(id).array();
    }

    public byte[] encrypt(byte[] plaintext) {
        byte[] nonce = AesGcm.nonce();
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, activeKey, nonce, plaintext, header);
        return ByteBuffer.allocate(header.length + nonce.length + ciphertext.length)
                .put(header).put(nonce).put(ciphertext).array();
    }

    public byte[] decrypt(byte[] encrypted) {
        if (encrypted.length < 1 + NONCE_BYTES + TAG_BYTES)
            throw new IllegalArgumentException("Invalid snapshot envelope");
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        int version = Byte.toUnsignedInt(buffer.get());
        SecretKeySpec key;
        byte[] aad = null;
        if (version == 1) {
            if (legacyKey == null) throw new IllegalArgumentException("Legacy snapshot key is not configured");
            key = legacyKey;
        } else if (version == 2) {
            int idLength = Byte.toUnsignedInt(buffer.get());
            if (idLength < 1 || idLength > 64 || buffer.remaining() < idLength + NONCE_BYTES + TAG_BYTES)
                throw new IllegalArgumentException("Invalid snapshot envelope");
            byte[] id = new byte[idLength];
            buffer.get(id);
            String keyId = new String(id, StandardCharsets.US_ASCII);
            validateKeyId(keyId);
            key = keys.get(keyId);
            if (key == null) throw new IllegalArgumentException("Unknown snapshot key ID");
            aad = Arrays.copyOf(encrypted, buffer.position());
        } else {
            throw new IllegalArgumentException("Unsupported snapshot version");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        return crypt(Cipher.DECRYPT_MODE, key, nonce, ciphertext, aad);
    }

    private static byte[] crypt(int mode, SecretKeySpec key, byte[] nonce, byte[] input, byte[] aad) {
        try {
            return AesGcm.crypt(mode, key, nonce, input, aad);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Snapshot encryption/authentication failed", exception);
        }
    }

    private static void validateKeyId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Snapshot key ID must be 1-64 ASCII letters, digits, underscores or hyphens");
    }
}
