
package com.familyconnect.modules.ai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
// import java.util.regex.Pattern;

@Service
public class TextChunkingService {

    // private static final int MIN_WORDS = 300;
    private static final int MAX_WORDS = 500;

    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalized = content
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = normalized.split(" ");

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int count = 0;

        for (String word : words) {
            if (count >= MAX_WORDS) {
                chunks.add(current.toString().trim());
                current.setLength(0);
                count = 0;
            }

            if (current.length() > 0) {
                current.append(' ');
            }

            current.append(word);
            count++;
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }
}
