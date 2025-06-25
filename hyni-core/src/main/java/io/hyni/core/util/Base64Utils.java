package io.hyni.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Utility class for Base64 encoding operations
 */
public class Base64Utils {

    private static final String BASE64_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Encode byte array to Base64 string
     */
    public static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Encode image file to Base64 string
     */
    public static String encodeImageToBase64(String imagePath) throws IOException {
        Path path = Paths.get(imagePath);

        if (!Files.exists(path)) {
            throw new IOException("Image file does not exist: " + imagePath);
        }

        long fileSize = Files.size(path);
        if (fileSize > MAX_IMAGE_SIZE) {
            throw new IOException("Image file too large: " + fileSize + " bytes");
        }

        byte[] imageBytes = Files.readAllBytes(path);
        return encode(imageBytes);
    }

    /**
     * Check if a string is Base64 encoded
     */
    public static boolean isBase64Encoded(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        // Check for data URI scheme (e.g., "data:image/png;base64,...")
        if (data.startsWith("data:") && data.contains(";base64,")) {
            return true;
        }

        // Check for valid Base64 characters
        int padding = 0;
        int dataLen = 0;

        for (char c : data.toCharArray()) {
            if (Character.isWhitespace(c)) continue;

            if (BASE64_CHARS.indexOf(c) == -1) {
                return false;
            }

            if (c == '=') {
                if (++padding > 2) return false;
            }
            dataLen++;
        }

        // Validate length and padding
        return (dataLen % 4 == 0) && (padding != 1);
    }
}
