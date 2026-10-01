package hs.kr.entrydsm.common.crypto;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public class PersonalDataCipherTest {
    private static final String KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    public void existingFormatIsCompatibleInBothDirections() throws Exception {
        PersonalDataCipher common = new PersonalDataCipher(KEY);
        byte[] nonce = new byte[12];
        Arrays.fill(nonce, (byte) 5);
        Cipher old = original(Cipher.ENCRYPT_MODE, nonce);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String existing = "v1." + encoder.encodeToString(nonce) + "."
                + encoder.encodeToString(old.doFinal("홍길동".getBytes(StandardCharsets.UTF_8)));
        assertEquals("홍길동", common.decrypt(existing));

        String encrypted = common.encrypt("홍길동");
        String[] parts = encrypted.split("\\.");
        assertEquals("v1", parts[0]);
        assertEquals("홍길동", new String(original(Cipher.DECRYPT_MODE, Base64.getUrlDecoder().decode(parts[1]))
                .doFinal(Base64.getUrlDecoder().decode(parts[2])), StandardCharsets.UTF_8));
        assertNotEquals(encrypted, common.encrypt("홍길동"));
        assertEquals("", common.decrypt(common.encrypt("")));
    }

    @Test
    public void tamperingAndIncorrectKeysFailAuthentication() throws Exception {
        PersonalDataCipher cipher = new PersonalDataCipher(KEY);
        String encrypted = cipher.encrypt("01012345678");
        String[] parts = encrypted.split("\\.");
        byte[] tampered = Base64.getUrlDecoder().decode(parts[2]);
        tampered[0] ^= 1;
        String changed = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(tampered);
        assertThrows(AEADBadTagException.class, () -> cipher.decrypt(changed));
        PersonalDataCipher other = new PersonalDataCipher(Base64.getEncoder().encodeToString(new byte[32]));
        assertThrows(AEADBadTagException.class, () -> other.decrypt(encrypted));
    }

    @Test
    public void plaintextMalformedPayloadsAndInvalidKeysAreRejected() {
        PersonalDataCipher cipher = new PersonalDataCipher(KEY);
        assertFalse(cipher.isEncrypted("홍길동"));
        assertTrue(cipher.isEncrypted("v1.nonce.ciphertext"));
        for (String malformed : new String[] {"홍길동", "v2.a.b", "v1.a", "v1.a.b.c", "v1.AA.AA", "v1.*.*"}) {
            assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(malformed));
        }
        for (String invalid : new String[] {"", "  ", "not-base64", Base64.getEncoder().encodeToString(new byte[15])}) {
            assertThrows(IllegalArgumentException.class, () -> new PersonalDataCipher(invalid));
        }
    }

    private static Cipher original(int mode, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(Base64.getDecoder().decode(KEY), "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }
}
