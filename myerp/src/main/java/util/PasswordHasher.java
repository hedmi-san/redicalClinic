package util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides salted PBKDF2WithHmacSHA256 password hashing and constant-time verification.
 * Formatted as: pbkdf2$<iterations>$<base64Salt>$<base64Hash>
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2$";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * Returns true if the stored password value is already a salted PBKDF2 hash.
     */
    public static boolean isHashed(String storedValue) {
        return storedValue != null && storedValue.startsWith(PREFIX);
    }

    /**
     * Hashes a plaintext password using a fresh 16-byte cryptographic salt.
     * If the input is already hashed, returns it unchanged.
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        if (isHashed(plainPassword)) {
            return plainPassword;
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derived = pbkdf2(plainPassword.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return PREFIX + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    /**
     * Verifies a candidate plaintext password against a stored value (either a salted PBKDF2 hash
     * or a legacy plaintext password prior to migration).
     */
    public static boolean verify(String candidatePassword, String storedValue) {
        if (candidatePassword == null || storedValue == null) {
            return false;
        }
        if (!isHashed(storedValue)) {
            return MessageDigest.isEqual(
                    candidatePassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    storedValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        String[] parts = storedValue.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(candidatePassword.toCharArray(), salt, iterations, expectedHash.length * 8);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            try {
                SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
                return skf.generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 hashing failed", e);
        }
    }
}

