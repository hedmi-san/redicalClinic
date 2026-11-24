package util;

import model.Patient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shared patient search utilities supporting word-order-independent name matching,
 * space-insensitive phone matching, SQL LIKE escaping, and match highlighting.
 */
public final class PatientSearchHelper {

    private PatientSearchHelper() {
    }

    /**
     * Splits a search query on whitespace into non-empty tokens.
     */
    public static List<String> tokenize(String searchTerm) {
        if (searchTerm == null) {
            return Collections.emptyList();
        }
        String trimmed = searchTerm.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = trimmed.split("\\s+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /**
     * Returns true if the patient matches all whitespace-separated query tokens in any order
     * against their name, or matches the query against their space-stripped phone number.
     */
    public static boolean matches(Patient patient, String searchTerm) {
        if (patient == null) {
            return false;
        }
        List<String> tokens = tokenize(searchTerm);
        if (tokens.isEmpty()) {
            return true;
        }

        String name = patient.getName() != null ? patient.getName().toLowerCase(Locale.ROOT) : "";
        String nameNoSpaces = name.replaceAll("\\s+", "");
        String phoneNoSpaces = patient.getPhone() != null
                ? patient.getPhone().replaceAll("\\s+", "").toLowerCase(Locale.ROOT)
                : "";

        String queryNoSpaces = searchTerm.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (!queryNoSpaces.isEmpty() && phoneNoSpaces.contains(queryNoSpaces)) {
            return true;
        }

        for (String token : tokens) {
            String lowerToken = token.toLowerCase(Locale.ROOT);
            if (!name.contains(lowerToken) && !nameNoSpaces.contains(lowerToken)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Escapes SQL LIKE wildcards using '!' as the escape character.
     */
    public static String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * Finds sorted, merged [start, end) character index ranges in {@code name}
     * corresponding to occurrences of any token in {@code query}.
     */
    public static List<int[]> findHighlightRanges(String name, String query) {
        if (name == null || name.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.length() != name.length()) {
            return Collections.emptyList();
        }

        List<int[]> rawRanges = new ArrayList<>();
        for (String token : tokens) {
            String lowerToken = token.toLowerCase(Locale.ROOT);
            if (lowerToken.isEmpty() || lowerToken.length() != token.length()) {
                continue;
            }
            int searchFrom = 0;
            while (searchFrom < lowerName.length()) {
                int idx = lowerName.indexOf(lowerToken, searchFrom);
                if (idx < 0) {
                    break;
                }
                int end = Math.min(idx + lowerToken.length(), name.length());
                rawRanges.add(new int[]{idx, end});
                searchFrom = idx + lowerToken.length();
            }
        }

        if (rawRanges.isEmpty()) {
            return Collections.emptyList();
        }

        rawRanges.sort(Comparator.comparingInt((int[] r) -> r[0]).thenComparingInt(r -> r[1]));
        List<int[]> merged = new ArrayList<>(rawRanges.size());
        int[] current = rawRanges.get(0);
        for (int i = 1; i < rawRanges.size(); i++) {
            int[] next = rawRanges.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }
}
