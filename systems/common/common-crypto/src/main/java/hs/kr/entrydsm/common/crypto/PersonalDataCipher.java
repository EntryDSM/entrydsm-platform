package hs.kr.entrydsm.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** identity/application의 기존 v1.nonce.ciphertext 개인정보 형식을 유지한다. */
public final class PersonalDataCipher {
    private final SecretKeySpec key;

    public PersonalDataCipher(String base64Key) {
        key = AesGcm.decodeKey(base64Key);
    }

    public String encrypt(String plaintext) throws GeneralSecurityException {
        byte[] nonce = AesGcm.nonce();
        byte[] ciphertext = AesGcm.crypt(Cipher.ENCRYPT_MODE, key, nonce,
                plaintext.getBytes(StandardCharsets.UTF_8), null);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "v1." + encoder.encodeToString(nonce) + "." + encoder.encodeToString(ciphertext);
    }

    public String decrypt(String encrypted) throws GeneralSecurityException {
        if (!isEncrypted(encrypted)) throw new IllegalArgumentException("Plaintext personal data is not supported");
        String[] parts = encrypted.split("\\.", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid encrypted personal data format");
        byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
        byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
        return new String(AesGcm.crypt(Cipher.DECRYPT_MODE, key, nonce, ciphertext, null), StandardCharsets.UTF_8);
    }

    public boolean isEncrypted(String value) {
        return value.startsWith("v1.");
    }
}
