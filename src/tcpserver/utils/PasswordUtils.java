package tcpserver.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;


public class PasswordUtils {
    public static SecureRandom secureRandom =  new SecureRandom();

    private PasswordUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String generateSalt(int length){
        byte[] salt = new byte[length];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] combined = (password + salt).getBytes(StandardCharsets.UTF_8);
            byte[] hashed = md.digest(combined);
            String hashedInput = Base64.getEncoder().encodeToString(hashed);
            return BCrypt.withDefaults().hashToString(12, hashedInput.toCharArray());
        } catch (Exception e) {
            System.out.println("[BCRYPT] ERROR HASHING PASSWORD");
            return null;
        }
    }

    public static boolean verifyPassword(String password, String salt, String hash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] combined = (password + salt).getBytes(StandardCharsets.UTF_8);
            byte[] hashed = md.digest(combined);
            String hashedInput = Base64.getEncoder().encodeToString(hashed);
            BCrypt.Result result = BCrypt.verifyer().verify(hashedInput.toCharArray(), hash);
            return result.verified;
        } catch (Exception e) {
            return false;
        }
    }
}
