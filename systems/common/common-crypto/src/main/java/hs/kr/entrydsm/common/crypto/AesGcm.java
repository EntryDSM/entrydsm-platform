package hs.kr.entrydsm.common.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 스냅샷과 개인정보 형식에서 함께 사용하는 AES-GCM 연산. */
final class AesGcm {
    static final int NONCE_BYTES = 12;
    static final int TAG_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcm() {}

    static SecretKeySpec decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) throw new IllegalArgumentException("AES key must not be blank");
        byte[] bytes = Base64.getDecoder().decode(base64.trim());
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32)
            throw new IllegalArgumentException("AES key must be 128, 192 or 256 bits");
        return new SecretKeySpec(bytes, "AES");
    }

    static byte[] nonce() {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    static byte[] crypt(int mode, SecretKeySpec key, byte[] nonce, byte[] input, byte[] aad)
            throws GeneralSecurityException {
        if (nonce.length != NONCE_BYTES) throw new IllegalArgumentException("AES-GCM nonce must be 12 bytes");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BYTES * 8, nonce));
        if (aad != null) cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }
}
