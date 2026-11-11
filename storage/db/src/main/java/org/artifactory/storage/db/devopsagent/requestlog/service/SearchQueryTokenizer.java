package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side tokenizer for the {@code search} query parameter. Produces a
 * {@code to_tsquery('simple', ?)}-safe string by stripping the special tsquery characters and
 * joining surviving tokens with the documented operator. Pure function — no I/O, no state.
 * <p>
 * Algorithm (mirrors the API documentation §8.1 byte-for-byte):
 * <ol>
 *   <li>Split on whitespace and the characters {@code , | : ; /}.</li>
 *   <li>Strip the special tsquery characters {@code & | ! ( )} from each token.</li>
 *   <li>Drop empty tokens.</li>
 *   <li>Join the survivors with {@code |} (OR mode) or {@code &} (AND mode).</li>
 * </ol>
 * Returns an empty string when no tokens survive — callers should not pass an empty result to
 * {@code to_tsquery} (it would raise).
 */
public final class SearchQueryTokenizer {

    private SearchQueryTokenizer() {
        // utility
    }

    public static String tokenize(String raw, boolean matchAll) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        // Split: whitespace OR any of , | : ; /  (/ handles URL path components)
        String[] split = raw.split("[\\s,|:;/]+");
        List<String> kept = new ArrayList<>(split.length);
        for (String token : split) {
            String stripped = token.replaceAll("[&|!()]", "");
            if (!stripped.isEmpty()) {
                kept.add(stripped);
            }
        }
        if (kept.isEmpty()) {
            return "";
        }
        String op = matchAll ? "&" : "|";
        return String.join(op, kept);
    }
}
