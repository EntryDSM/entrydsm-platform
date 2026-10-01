package hs.kr.entrydsm.contracts;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 원서 스냅샷을 이벤트 저장소와 projection에서 동일한 AES-GCM 형식으로 보관한다. */
public final class SnapshotCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public SnapshotCipher(String base64Key) {
        byte[] bytes = Base64.getDecoder().decode(base64Key.trim());
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32)
            throw new IllegalArgumentException("Snapshot key must be 128, 192 or 256 bits");
        key = new SecretKeySpec(bytes, "AES");
    }
    public byte[] encrypt(byte[] plaintext) {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + iv.length + encrypted.length).put((byte) 1).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Snapshot encryption failed", e); }
    }
    public byte[] decrypt(byte[] encrypted) {
        if (encrypted.length < 29 || encrypted[0] != 1) throw new IllegalArgumentException("Invalid snapshot envelope");
        ByteBuffer buffer = ByteBuffer.wrap(encrypted);
        buffer.get();
        byte[] iv = new byte[12]; buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()]; buffer.get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Snapshot decryption failed", e); }
    }
}
