package org.techbd.csv.service.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import org.techbd.csv.model.FileDetail;
import org.techbd.csv.model.FileType;
import java.util.LinkedHashMap;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
public class FileProcessor {
    
    /**
     * Configuration for content validation rules
     */
    public static class ValidationConfig {
        public boolean checkBOM = true;
        public boolean checkNullBytes = true;
        public boolean checkControlCharacters = true;
        public boolean checkProblematicWhitespace = true;
                public boolean checkNonCharacters = true;
        public boolean checkPrivateUseArea = false; // Usually safe, but can be enabled
        
        // Whitelist for allowed control characters (like tab, newline, carriage return)
        public boolean allowTabsAndNewlines = true;
        public boolean checkSurrogates = true;
    }
    
    private static final ValidationConfig DEFAULT_CONFIG = new ValidationConfig();
    
    public static Map<String, List<FileDetail>> processAndGroupFiles(final List<String> filePaths) throws IOException {
        return processAndGroupFiles(filePaths, DEFAULT_CONFIG);
    }
    
    public static Map<String, List<FileDetail>> processAndGroupFiles(
            final List<String> filePaths, 
            final ValidationConfig config) throws IOException {
        
        final Map<String, List<FileDetail>> groupedFiles = new HashMap<>();
        final List<FileDetail> filesNotProcessed = new ArrayList<>();
        final Map<String, String> groupHasInvalidContent = new HashMap<>();
        
        for (final String filePath : filePaths) {
            final Path path = Path.of(filePath);
            final String fileName = path.getFileName().toString();
            
            try {
                final FileType fileType = FileType.fromFilename(fileName);
                String groupKey = fileName.substring(fileType.name().length(), fileName.lastIndexOf(".csv"));
                String content = null;
                Boolean isValid = true;
                String reason = null;
                
                // Step 1: Read file bytes
                byte[] fileBytes = Files.readAllBytes(path);
                
                // Step 2: Check for BOM at start and strip it if present
                boolean hasBOMAtStart = hasBOMAtStart(fileBytes);
                if (hasBOMAtStart) {
                    // Strip BOM from the beginning for content reading
                    byte[] contentBytes = new byte[fileBytes.length - 3];
                    System.arraycopy(fileBytes, 3, contentBytes, 0, contentBytes.length);
                    fileBytes = contentBytes;
                }
                
                // Step 3: Try to read as UTF-8
                try {
                   content = decodeUtf8Strict(fileBytes);
                } catch (Exception e) {
                    isValid = false;
                    reason = "File is not valid UTF-8 encoded: " + e.getMessage();
                    groupHasInvalidContent.put(groupKey, "utf8");
                }
                
                // Step 4: Validate content using Unicode character properties
                // This will detect BOM in the middle of content (U+FEFF)
                if (isValid && content != null) {
                    ContentValidationResult validation = validateContentDynamic(content, config);
                    if (!validation.isValid) {
                        isValid = false;
                        reason = validation.reason;
                        groupHasInvalidContent.put(groupKey, "content");
                    }
                }
                
                FileDetail fileDetail = new FileDetail(fileName, fileType, content, filePath, isValid, reason);
                groupedFiles.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(fileDetail);
                
            } catch (IllegalArgumentException e) {
                String reason = "Invalid file prefix: " + e.getMessage();
                filesNotProcessed.add(new FileDetail(fileName, null, null, filePath, false, reason));
            } catch (IOException e) {
                String reason = "IOException during processing: " + e.getMessage();
                filesNotProcessed.add(new FileDetail(fileName, null, null, filePath, false, reason));
            }
        }
        
        // Remove entire groups that have any invalid files
        for (String invalidGroupKey : groupHasInvalidContent.keySet()) {
            List<FileDetail> group = groupedFiles.remove(invalidGroupKey);
            if (group != null) {
                List<String> invalidFiles = group.stream()
                        .filter(fd -> !fd.utf8Encoded())
                        .map(fd -> fd.filename() + " (" + fd.reason() + ")")
                        .toList();
                        String failureType = groupHasInvalidContent.get(invalidGroupKey);
                        String blockReason;
                    if ("utf8".equals(failureType)) {
                        blockReason = "Not processed as other files in the group were not UTF-8 encoded. Group blocked by: "
                                + String.join("; ", invalidFiles);
                    } else {
                        blockReason = "Not processed as other files in the group have content validation errors. Group blocked by: "
                                + String.join("; ", invalidFiles);
                    }
                
                    for (FileDetail fd : group) {
                      FileDetail failed = new FileDetail(
                            fd.filename(),
                            fd.fileType(),
                            null,
                            fd.filePath(),
                            fd.utf8Encoded(),
                            !fd.utf8Encoded() ? fd.reason() : blockReason);
                    filesNotProcessed.add(failed);
                }
            }
        }
        
        groupedFiles.put("filesNotProcessed", filesNotProcessed);
        return groupedFiles;
    }
    
    /**
     * Check if file starts with UTF-8 BOM
     */
    private static boolean hasBOMAtStart(byte[] bytes) {
        return bytes.length >= 3 && 
               bytes[0] == (byte) 0xEF && 
               bytes[1] == (byte) 0xBB && 
               bytes[2] == (byte) 0xBF;
    }
    
    /**
     * Dynamic content validation using Java's Character class properties
     * This automatically detects all problematic Unicode characters without hardcoding
     */
    private static ContentValidationResult validateContentDynamic(String content, ValidationConfig config) {
        Map<String, List<CharacterInfo>> issues = new LinkedHashMap<>();
        int maxSamples = 5; // Limit samples per issue type
        
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            int codePoint = content.codePointAt(i);
            
            // Skip high surrogates, we'll check them separately
            if (Character.isHighSurrogate(ch)) {
                continue;
            }
            
            // 1. Check for null bytes (0x00) - Critical for databases
            if (config.checkNullBytes && ch == '\u0000') {
                addIssue(issues, "Null bytes (0x00)", ch, codePoint, i, maxSamples);
                continue;
            }
            
            // 2. Check control characters using Character.isISOControl()
            if (config.checkControlCharacters && Character.isISOControl(ch)) {
                // Allow common whitespace if configured
                if (config.allowTabsAndNewlines && (ch == '\t' || ch == '\n' || ch == '\r')) {
                    continue;
                }
                addIssue(issues, "Control characters", ch, codePoint, i, maxSamples);
                continue;
            }
            
            // 3. Check for Unicode surrogates (U+D800 to U+DFFF)
            if (config.checkSurrogates && Character.isSurrogate(ch)) {
                addIssue(issues, "Invalid surrogate characters", ch, codePoint, i, maxSamples);
                continue;
            }
            
            // 4. Check for non-characters (U+FDD0..U+FDEF and U+FFFE, U+FFFF, etc.)
            if (config.checkNonCharacters && isNonCharacter(codePoint)) {
                addIssue(issues, "Unicode non-characters", ch, codePoint, i, maxSamples);
                continue;
            }
            
            // 5. Check for problematic whitespace using Character.getType()
            if (config.checkProblematicWhitespace) {
                int type = Character.getType(ch);
                // SPACE_SEPARATOR includes non-breaking spaces and other problematic whitespace
                if (type == Character.SPACE_SEPARATOR && ch != ' ') {
                    addIssue(issues, "Problematic whitespace", ch, codePoint, i, maxSamples);
                    continue;
                }
                
                // Check for format characters (invisible formatting)
                if (type == Character.FORMAT) {
                    addIssue(issues, "Invisible format characters", ch, codePoint, i, maxSamples);
                    continue;
                }
                
                // Check for zero-width characters (including BOM/ZWNBSP when in content)
                if (isZeroWidthCharacter(codePoint)) {
                    // U+FEFF in the middle of content is a zero-width no-break space (BOM misplaced)
                    if (codePoint == 0xFEFF && config.checkBOM) {
                        addIssue(issues, "BOM character in middle of content", ch, codePoint, i, maxSamples);
                    } else {
                        addIssue(issues, "Zero-width characters", ch, codePoint, i, maxSamples);
                    }
                    continue;
                }
            }
            
            // 6. Check for private use area characters (optional)
            if (config.checkPrivateUseArea) {
                int type = Character.getType(ch);
                if (type == Character.PRIVATE_USE) {
                    addIssue(issues, "Private use area characters", ch, codePoint, i, maxSamples);
                    continue;
                }
            }
        }
        
        // Build error message if issues found
        if (!issues.isEmpty()) {
            StringBuilder reason = new StringBuilder("File contains invalid characters:\n");
            for (Map.Entry<String, List<CharacterInfo>> entry : issues.entrySet()) {
                reason.append("  - ").append(entry.getKey()).append(": ");
                List<CharacterInfo> samples = entry.getValue();
                reason.append(samples.stream()
                    .map(CharacterInfo::toString)
                    .limit(maxSamples)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
                if (samples.size() > maxSamples) {
                    reason.append(" (and ").append(samples.size() - maxSamples).append(" more)");
                }
                reason.append("\n");
            }
            return new ContentValidationResult(false, reason.toString().trim());
        }
        
        return new ContentValidationResult(true, null);
    }
    
    /**
     * Check if codepoint is a Unicode non-character
     */
    private static boolean isNonCharacter(int codePoint) {
        // U+FDD0..U+FDEF
        if (codePoint >= 0xFDD0 && codePoint <= 0xFDEF) {
            return true;
        }
        // U+FFFE, U+FFFF and their equivalents in all planes
        if ((codePoint & 0xFFFF) >= 0xFFFE) {
            return true;
        }
        return false;
    }
    
    /**
     * Check if codepoint is a zero-width character
     */
    private static boolean isZeroWidthCharacter(int codePoint) {
        return codePoint == 0x200B || // ZERO WIDTH SPACE
               codePoint == 0x200C || // ZERO WIDTH NON-JOINER
               codePoint == 0x200D || // ZERO WIDTH JOINER
               codePoint == 0xFEFF;   // ZERO WIDTH NO-BREAK SPACE (BOM when in content)
    }
    
    /**
     * Add an issue to the map with character information
     */
    private static void addIssue(Map<String, List<CharacterInfo>> issues, String category, 
                                  char ch, int codePoint, int position, int maxSamples) {
        issues.computeIfAbsent(category, k -> new ArrayList<>());
        List<CharacterInfo> list = issues.get(category);
        if (list.size() < maxSamples * 2) { // Keep more samples than we display
            list.add(new CharacterInfo(ch, codePoint, position));
        }
    }
    
    /**
     * Information about a problematic character
     */
    private static class CharacterInfo {
        final char ch;
        final int codePoint;
        final int position;
        
        CharacterInfo(char ch, int codePoint, int position) {
            this.ch = ch;
            this.codePoint = codePoint;
            this.position = position;
        }
        
        @Override
        public String toString() {
            String name = getCharacterName(codePoint);
            return String.format("U+%04X (%s) at position %d", codePoint, name, position);
        }
        
        private String getCharacterName(int codePoint) {
            if (codePoint == 0x0000) return "NULL";
            if (codePoint == 0x0008) return "BACKSPACE";
            if (codePoint == 0x000B) return "VERTICAL TAB";
            if (codePoint == 0x001A) return "SUBSTITUTE";
            if (codePoint == 0x007F) return "DELETE";
            if (codePoint == 0x00A0) return "NON-BREAKING SPACE";
            if (codePoint == 0x200B) return "ZERO WIDTH SPACE";
            if (codePoint == 0x200C) return "ZERO WIDTH NON-JOINER";
            if (codePoint == 0x200D) return "ZERO WIDTH JOINER";
            if (codePoint == 0xFEFF) return "ZERO WIDTH NO-BREAK SPACE / BOM";
            
            // Fallback to Unicode character type
            int type = Character.getType(codePoint);
            return switch (type) {
                case Character.CONTROL -> "CONTROL";
                case Character.FORMAT -> "FORMAT";
                case Character.PRIVATE_USE -> "PRIVATE USE";
                case Character.SURROGATE -> "SURROGATE";
                case Character.SPACE_SEPARATOR -> "SPACE SEPARATOR";
                default -> "CHAR";
            };
        }
    }
    
    /**
     * Result object for content validation
     */
    private static class ContentValidationResult {
        final boolean isValid;
        final String reason;
        
        ContentValidationResult(boolean isValid, String reason) {
            this.isValid = isValid;
            this.reason = reason;
        }
    }
    private static String decodeUtf8Strict(byte[] bytes) throws IOException {
    CharsetDecoder decoder = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    try {
        CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
        return buffer.toString();
    } catch (Exception e) {
        throw new IOException("Invalid UTF-8 encoding: " + e.getMessage(), e);
    }
}
}