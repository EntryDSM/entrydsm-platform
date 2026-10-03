package hs.kr.entrydsm.common.crypto;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public class SnapshotCipherTest {
    private static final byte[] PLAIN = "지원자 개인정보".getBytes(StandardCharsets.UTF_8);
    private static final String OLD = key(32, (byte) 7);
    private static final String NEW = key(32, (byte) 9);

    @Test
    public void roundTripUsesFreshNoncesForAllAesKeySizes() {
        for (int size : new int[] {16, 24, 32}) {
            SnapshotCipher cipher = new SnapshotCipher("new", Map.of("new", key(size, (byte) 3)));
            Set<String> nonces = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                byte[] encrypted = cipher.encrypt(PLAIN);
                assertEquals(2, encrypted[0]);
                assertTrue(nonces.add(Base64.getEncoder().encodeToString(Arrays.copyOfRange(encrypted, 5, 17))));
                assertArrayEquals(PLAIN, cipher.decrypt(encrypted));
            }
            assertArrayEquals(new byte[0], cipher.decrypt(cipher.encrypt(new byte[0])));
        }
    }

    @Test
    public void rotationReadsOldKeysAndWritesOnlyTheActiveKey() {
        SnapshotCipher old = new SnapshotCipher("old", Map.of("old", OLD));
        SnapshotCipher rotated = new SnapshotCipher("new", Map.of("old", OLD, "new", NEW));
        assertArrayEquals(PLAIN, rotated.decrypt(old.encrypt(PLAIN)));
        byte[] encrypted = rotated.encrypt(PLAIN);
        assertArrayEquals(PLAIN, new SnapshotCipher("new", Map.of("new", NEW)).decrypt(encrypted));
        assertThrows(IllegalArgumentException.class, () -> old.decrypt(encrypted));
        assertThrows(IllegalStateException.class,
                () -> new SnapshotCipher("new", Map.of("new", OLD)).decrypt(encrypted));
    }

    @Test
    public void versionOneSnapshotsAreRejected() throws Exception {
        byte[] nonce = new byte[12];
        Arrays.fill(nonce, (byte) 5);
        Cipher original = Cipher.getInstance("AES/GCM/NoPadding");
        original.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(OLD), "AES"),
                new GCMParameterSpec(128, nonce));
        byte[] ciphertext = original.doFinal(PLAIN);
        byte[] versionOne = ByteBuffer.allocate(1 + nonce.length + ciphertext.length)
                .put((byte) 1).put(nonce).put(ciphertext).array();
        assertThrows(IllegalArgumentException.class,
                () -> new SnapshotCipher("old", Map.of("old", OLD)).decrypt(versionOne));
    }

    @Test
    public void authenticationCoversKeyIdNonceAndPayload() {
        // 두 ID가 같은 키를 가리키더라도 헤더 변조를 검출한다.
        SnapshotCipher cipher = new SnapshotCipher("old", Map.of("old", OLD, "new", OLD));
        byte[] encrypted = cipher.encrypt(PLAIN);
        byte[] renamed = encrypted.clone();
        System.arraycopy("new".getBytes(StandardCharsets.US_ASCII), 0, renamed, 2, 3);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(renamed));
        for (int offset : new int[] {5, 17, encrypted.length - 1}) {
            byte[] tampered = encrypted.clone();
            tampered[offset] ^= 1;
            assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
        }
        byte[] changedVersion = encrypted.clone();
        changedVersion[0] = 1;
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(changedVersion));
        byte[] invalidLength = encrypted.clone();
        invalidLength[1] = (byte) 255;
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(invalidLength));
        for (int length : new int[] {0, 1, 28, 29, 30}) {
            assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(Arrays.copyOf(encrypted, length)));
        }
        byte[] unsupported = encrypted.clone();
        unsupported[0] = 3;
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(unsupported));
    }

    @Test
    public void invalidKeyConfigurationIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new SnapshotCipher("new", Map.of("new", key(15, (byte) 1))));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotCipher("new", Map.of("new", "not base64")));
        assertThrows(IllegalArgumentException.class, () -> new SnapshotCipher("new", Map.of("old", OLD)));
        for (String id : new String[] {"", "한글", "a.b", "a".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> new SnapshotCipher(id, Map.of(id, NEW)));
        }
    }

    private static String key(int size, byte value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
