package tcpserver.utils;

import java.security.SecureRandom;
import java.util.Base64;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

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

    public static String hashPassword(String password, String salt){
        Argon2 argon2 = Argon2Factory.create();
        return argon2.hash(3,65536,2,(password+salt).toCharArray());
    }

    public static boolean verifyPassword(String password, String salt, String hash){
        Argon2 argon2 =Argon2Factory.create();
        return argon2.verify(hash,(password+salt).toCharArray());
    }
}
