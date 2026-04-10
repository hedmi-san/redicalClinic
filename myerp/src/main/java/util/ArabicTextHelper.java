package util;

import java.text.Bidi;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles Arabic text reshaping and RTL reordering for PDFBox rendering.
 * PDFBox does not perform OpenType shaping, so we must manually:
 * 1. Map Arabic letters to their contextual presentation forms (initial/medial/final/isolated)
 * 2. Handle lam-alef mandatory ligatures
 * 3. Reorder text to visual order (RTL → LTR) since PDFBox renders left-to-right
 */
public class ArabicTextHelper {

    // Arabic Presentation Forms-B mapping: base char → [isolated, final, initial, medial]
    // 2-form entries (right-only connectors): [isolated, final]
    // 4-form entries (dual connectors): [isolated, final, initial, medial]
    private static final Map<Character, char[]> FORMS = new HashMap<>();

    static {
        // === 2-form letters (connect from right only) ===
        FORMS.put('\u0621', new char[]{'\uFE80', '\uFE80'});           // hamza
        FORMS.put('\u0622', new char[]{'\uFE81', '\uFE82'});           // alef madda
        FORMS.put('\u0623', new char[]{'\uFE83', '\uFE84'});           // alef hamza above
        FORMS.put('\u0624', new char[]{'\uFE85', '\uFE86'});           // waw hamza
        FORMS.put('\u0625', new char[]{'\uFE87', '\uFE88'});           // alef hamza below
        FORMS.put('\u0627', new char[]{'\uFE8D', '\uFE8E'});           // alef
        FORMS.put('\u0629', new char[]{'\uFE93', '\uFE94'});           // taa marbuta
        FORMS.put('\u062F', new char[]{'\uFEA9', '\uFEAA'});           // dal
        FORMS.put('\u0630', new char[]{'\uFEAB', '\uFEAC'});           // thal
        FORMS.put('\u0631', new char[]{'\uFEAD', '\uFEAE'});           // raa
        FORMS.put('\u0632', new char[]{'\uFEAF', '\uFEB0'});           // zayn
        FORMS.put('\u0648', new char[]{'\uFEED', '\uFEEE'});           // waw
        FORMS.put('\u0649', new char[]{'\uFEEF', '\uFEF0'});           // alef maksura

        // === 4-form letters (connect both sides) ===
        FORMS.put('\u0626', new char[]{'\uFE89', '\uFE8A', '\uFE8B', '\uFE8C'}); // yeh hamza
        FORMS.put('\u0628', new char[]{'\uFE8F', '\uFE90', '\uFE91', '\uFE92'}); // baa
        FORMS.put('\u062A', new char[]{'\uFE95', '\uFE96', '\uFE97', '\uFE98'}); // taa
        FORMS.put('\u062B', new char[]{'\uFE99', '\uFE9A', '\uFE9B', '\uFE9C'}); // thaa
        FORMS.put('\u062C', new char[]{'\uFE9D', '\uFE9E', '\uFE9F', '\uFEA0'}); // jeem
        FORMS.put('\u062D', new char[]{'\uFEA1', '\uFEA2', '\uFEA3', '\uFEA4'}); // haa
        FORMS.put('\u062E', new char[]{'\uFEA5', '\uFEA6', '\uFEA7', '\uFEA8'}); // khaa
        FORMS.put('\u0633', new char[]{'\uFEB1', '\uFEB2', '\uFEB3', '\uFEB4'}); // seen
        FORMS.put('\u0634', new char[]{'\uFEB5', '\uFEB6', '\uFEB7', '\uFEB8'}); // sheen
        FORMS.put('\u0635', new char[]{'\uFEB9', '\uFEBA', '\uFEBB', '\uFEBC'}); // sad
        FORMS.put('\u0636', new char[]{'\uFEBD', '\uFEBE', '\uFEBF', '\uFEC0'}); // dad
        FORMS.put('\u0637', new char[]{'\uFEC1', '\uFEC2', '\uFEC3', '\uFEC4'}); // tah
        FORMS.put('\u0638', new char[]{'\uFEC5', '\uFEC6', '\uFEC7', '\uFEC8'}); // zah
        FORMS.put('\u0639', new char[]{'\uFEC9', '\uFECA', '\uFECB', '\uFECC'}); // ayn
        FORMS.put('\u063A', new char[]{'\uFECD', '\uFECE', '\uFECF', '\uFED0'}); // ghayn
        FORMS.put('\u0641', new char[]{'\uFED1', '\uFED2', '\uFED3', '\uFED4'}); // faa
        FORMS.put('\u0642', new char[]{'\uFED5', '\uFED6', '\uFED7', '\uFED8'}); // qaf
        FORMS.put('\u0643', new char[]{'\uFED9', '\uFEDA', '\uFEDB', '\uFEDC'}); // kaf
        FORMS.put('\u0644', new char[]{'\uFEDD', '\uFEDE', '\uFEDF', '\uFEE0'}); // lam
        FORMS.put('\u0645', new char[]{'\uFEE1', '\uFEE2', '\uFEE3', '\uFEE4'}); // meem
        FORMS.put('\u0646', new char[]{'\uFEE5', '\uFEE6', '\uFEE7', '\uFEE8'}); // noon
        FORMS.put('\u0647', new char[]{'\uFEE9', '\uFEEA', '\uFEEB', '\uFEEC'}); // heh
        FORMS.put('\u064A', new char[]{'\uFEF1', '\uFEF2', '\uFEF3', '\uFEF4'}); // yaa
    }

    /**
     * Process text for PDF rendering: reshape Arabic letters and convert to visual (LTR) order.
     * Non-Arabic text passes through unchanged.
     */
    public static String processForPdf(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!containsArabic(text)) return text;

        String reshaped = reshape(text);
        return toVisualOrder(reshaped);
    }

    public static boolean containsArabic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (isArabicLetter(text.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isArabicLetter(char c) {
        return FORMS.containsKey(c);
    }

    private static boolean isDiacritic(char c) {
        return c >= '\u064B' && c <= '\u065F';
    }

    /** Returns true if the letter has 4 forms (can connect to the left / next letter). */
    private static boolean canConnectAfter(char c) {
        char[] forms = FORMS.get(c);
        return forms != null && forms.length == 4;
    }

    private static boolean isAlef(char c) {
        return c == '\u0627' || c == '\u0622' || c == '\u0623' || c == '\u0625';
    }

    // ======================== RESHAPING ========================

    private static String reshape(String text) {
        char[] chars = text.toCharArray();
        int len = chars.length;
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < len; i++) {
            char c = chars[i];

            // Skip diacritics (tashkeel) — they complicate rendering in PDFBox
            if (isDiacritic(c)) continue;

            // Keep tatweel (kashida) as-is
            if (c == '\u0640') {
                result.append(c);
                continue;
            }

            // --- Lam-Alef ligatures (mandatory in Arabic) ---
            if (c == '\u0644') { // lam
                int nextIdx = findNextNonDiacriticIndex(chars, i);
                if (nextIdx != -1 && isAlef(chars[nextIdx])) {
                    char alef = chars[nextIdx];
                    char prev = findPrevLetter(chars, i);
                    boolean prevConnects = prev != 0 && canConnectAfter(prev);

                    char ligature;
                    switch (alef) {
                        case '\u0622': ligature = prevConnects ? '\uFEF6' : '\uFEF5'; break; // lam + alef madda
                        case '\u0623': ligature = prevConnects ? '\uFEF8' : '\uFEF7'; break; // lam + alef hamza above
                        case '\u0625': ligature = prevConnects ? '\uFEFA' : '\uFEF9'; break; // lam + alef hamza below
                        default:       ligature = prevConnects ? '\uFEFC' : '\uFEFB'; break; // lam + alef
                    }
                    result.append(ligature);
                    i = nextIdx; // skip the alef (loop i++ moves past it)
                    continue;
                }
            }

            // --- Standard letter shaping ---
            char[] forms = FORMS.get(c);
            if (forms == null) {
                result.append(c); // non-Arabic character → pass through
                continue;
            }

            char prev = findPrevLetter(chars, i);
            char next = findNextLetter(chars, i);

            boolean prevConnects = prev != 0 && canConnectAfter(prev);
            boolean nextIsArabic = next != 0 && isArabicLetter(next);
            boolean thisCanConnectAfter = forms.length == 4;

            if (prevConnects && thisCanConnectAfter && nextIsArabic) {
                result.append(forms[3]); // medial
            } else if (prevConnects) {
                result.append(forms[1]); // final
            } else if (thisCanConnectAfter && nextIsArabic) {
                result.append(forms[2]); // initial
            } else {
                result.append(forms[0]); // isolated
            }
        }

        return result.toString();
    }

    /** Find the previous Arabic letter, skipping diacritics. Returns 0 if none found. */
    private static char findPrevLetter(char[] chars, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (isDiacritic(chars[i])) continue;
            if (isArabicLetter(chars[i])) return chars[i];
            return 0; // non-Arabic char breaks the chain
        }
        return 0;
    }

    /** Find the next Arabic letter, skipping diacritics. Returns 0 if none found. */
    private static char findNextLetter(char[] chars, int index) {
        for (int i = index + 1; i < chars.length; i++) {
            if (isDiacritic(chars[i])) continue;
            if (isArabicLetter(chars[i])) return chars[i];
            return 0; // non-Arabic char breaks the chain
        }
        return 0;
    }

    /** Find the index of the next non-diacritic character. Returns -1 if none. */
    private static int findNextNonDiacriticIndex(char[] chars, int index) {
        for (int i = index + 1; i < chars.length; i++) {
            if (!isDiacritic(chars[i])) return i;
        }
        return -1;
    }

    // ======================== BIDI REORDERING ========================

    /**
     * Converts reshaped text from logical order to visual order using java.text.Bidi.
     * This ensures Arabic text is reversed for LTR rendering while keeping
     * embedded LTR segments (numbers, Latin text) in the correct position.
     */
    private static String toVisualOrder(String text) {
        Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);

        if (bidi.isLeftToRight()) return text;

        // Build per-character embedding levels
        int length = text.length();
        int runCount = bidi.getRunCount();
        byte[] levels = new byte[length];

        for (int i = 0; i < runCount; i++) {
            int start = bidi.getRunStart(i);
            int limit = bidi.getRunLimit(i);
            byte level = (byte) bidi.getRunLevel(i);
            for (int j = start; j < limit; j++) {
                levels[j] = level;
            }
        }

        // Wrap chars as objects for reorderVisually
        Character[] charObjects = new Character[length];
        for (int i = 0; i < length; i++) {
            charObjects[i] = text.charAt(i);
        }

        Bidi.reorderVisually(levels, 0, charObjects, 0, length);

        StringBuilder sb = new StringBuilder(length);
        for (Character c : charObjects) {
            sb.append(c);
        }
        return sb.toString();
    }
}
