package org.artifactory.repo.db.importexport;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.CodecPolicy;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.io.FilenameUtils;

import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class SafePathUtils {

    private static final String RT_SAFE_RENAME_PREFIX = "RT";
    private static final Pattern RESERVED_FILENAMES = Pattern.compile("(?i:con|prn|aux|nul|com[1-9]|lpt[1-9])$");
    private static final Pattern VALID_FILENAME = Pattern.compile("^[^<>:\"\\\\/|?*]+$");
    private static final byte PADDING = (byte) 61; // '=' padding symbol
    private static final Base32 BASE_32 = new Base32(0, null, false, PADDING, CodecPolicy.STRICT);
    private static final String RT_SAFE_RENAME_SUFFIX = "-";
    private static final int MAX_RENAME_TRIES_COUNT = 100;

    public static String convertToSafeName(String name, Set<String> siblings, boolean safeExport) {
        log.debug("Converting to safe name: '{}'", name);
        String result = "";
        if (!name.isEmpty()) {
            boolean sameDiffCaseSiblingNameExists = getNameCount(name, siblings) > 1;
            boolean needToBeRenamed = name.toUpperCase().startsWith(RT_SAFE_RENAME_PREFIX)
                    || sameDiffCaseSiblingNameExists
                    || (safeExport && !isSafe(name));
            result = needToBeRenamed ? createSafeName(name, siblings) : name;
        }
        log.debug("Converted to safe name: '{}' to '{}'", name, result);
        return result;
    }

    private static long getNameCount(String name, Set<String> siblings) {
        return siblings.stream().filter(name::equalsIgnoreCase).count();
    }

    private static String createSafeName(String name, Set<String> siblings) {
        String base = RT_SAFE_RENAME_PREFIX + encode(name);
        String result = base;
        int tryCount = 0;
        while (getNameCount(result, siblings) > 0 && tryCount++ <= MAX_RENAME_TRIES_COUNT) {
            if (tryCount >= MAX_RENAME_TRIES_COUNT) {
                throw new IllegalStateException("Unable to create a safe name for " + name);
            }
            result = base + RT_SAFE_RENAME_SUFFIX + tryCount;
            tryCount++;
        }
        return result;
    }

    public static String convertFromSafeName(String name) {
        log.debug("Converting from safe name: '{}'", name);
        String result = name;
        if (name.startsWith(RT_SAFE_RENAME_PREFIX)) {
            int suffixIndex = name.lastIndexOf(RT_SAFE_RENAME_SUFFIX);
            int endIndex = suffixIndex == -1 ? name.length() : suffixIndex;
            result = decode(name.substring(RT_SAFE_RENAME_PREFIX.length(), endIndex));
        }
        log.debug("Converted from safe name: '{}', '{}'", name, result);
        return result;
    }

    private static String decode(String encoded) {
        return new String(BASE_32.decode(encoded));
    }

    private static String encode(String element) {
        return new String(BASE_32.encode(element.getBytes()));
    }

    private static boolean isSafe(String element) {
        try {
            if (element.endsWith(" ") || element.endsWith(".")) {
                return false;
            }
            if (!VALID_FILENAME.matcher(element).matches()) {
                return false;
            }
            String name = FilenameUtils.removeExtension(element);
            return !RESERVED_FILENAMES.matcher(name).matches();
        } catch (Exception e) {
            log.error("Failed to check if element is safe: '{}'", element, e);
            return false;
        }
    }

}
