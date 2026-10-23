
package com.familyconnect.modules.ai.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ContentHashService {

    public String hash(String content) {
        if (content == null) {
            throw new IllegalArgumentException(
                    "Content must not be null");
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] result = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available", e);
        }
    }
}
