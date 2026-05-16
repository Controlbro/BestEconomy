package com.controlbro.besteconomy.shop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ShopSignatureUtil {
    private ShopSignatureUtil() {
    }

    public static String sign(String orderId, String uuid, String username, String command, String apiKey) {
        String payload = String.join("|", orderId == null ? "" : orderId, uuid, username, command);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create webshop command signature", ex);
        }
    }

    public static boolean isValid(String expected, String orderId, String uuid, String username, String command, String apiKey) {
        if (expected == null || expected.isBlank() || apiKey == null || apiKey.isBlank()) {
            return false;
        }
        String actual = sign(orderId, uuid, username, command, apiKey);
        try {
            return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) throws NoSuchAlgorithmException {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
