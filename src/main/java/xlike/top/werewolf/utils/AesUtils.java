package xlike.top.werewolf.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author xlike
 */
public class AesUtils {
    // 密钥（必须是 16、24 或 32 字节长度，这里使用 16 字节）
    private static final String SECRET_KEY = "xLikeSecKey12345";
    private static final String ALGORITHM = "AES";
    // 用于拼接和拆分 url 和 key 的分隔符
    private static final String DELIMITER = "|";

    /**
     * 加密方法：将 url 和 key 一起加密
     * @param url 需要加密的 URL
     * @param key 需要加密的 Key
     * @return 加密后的 Base64 字符串，发生异常时返回 null
     */
    public static String encryptTogether(String url, String key) {
        try {
            // 将 url 和 key 拼接成一个字符串
            String combinedData = url + DELIMITER + key;
            // 创建密钥
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            // 初始化加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            // 加密数据
            byte[] encryptedData = cipher.doFinal(combinedData.getBytes(StandardCharsets.UTF_8));
            // 将加密后的字节数组转为 Base64 字符串
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解密方法：将加密后的字符串解密回 url 和 key
     * @param encryptedData 加密后的 Base64 字符串
     * @return 包含 url 和 key 的字符串数组，索引 0 为 url，索引 1 为 key，发生异常时返回 null
     */
    public static String[] decryptTogether(String encryptedData) {
        try {
            // 创建密钥
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            // 将 Base64 字符串解码为字节数组
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            // 解密数据
            byte[] decryptedData = cipher.doFinal(decodedData);
            // 将解密后的字节数组转为字符串
            String combinedData = new String(decryptedData, StandardCharsets.UTF_8);
            // 按分隔符拆分为 url 和 key
            return combinedData.split("\\" + DELIMITER);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 测试加密和解密
     */
    public static void main(String[] args) {
        // 测试数据
        String url = "https://api.openai.com/v1/models";
        String key = "sk-123456";
        // 加密
        String encryptedData = encryptTogether(url, key);
        if (encryptedData != null) {
            System.out.println("Encrypted Data: " + encryptedData);
        } else {
            System.out.println("Encryption failed.");
        }
        // 解密
        if (encryptedData != null) {
            String[] decryptedData = decryptTogether(encryptedData);
            if (decryptedData != null && decryptedData.length == 2) {
                System.out.println("Decrypted URL: " + decryptedData[0]);
                System.out.println("Decrypted Key: " + decryptedData[1]);
            } else {
                System.out.println("Decryption failed: Invalid data format or error occurred.");
            }
        }
    }
}
