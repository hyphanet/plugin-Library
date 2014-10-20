package plugins.Library.search;

import java.lang.Character.UnicodeBlock;

import java.util.Arrays;
import java.util.List;

public class SearchUtil {
    public static boolean isCJK(int codePoint) {
        UnicodeBlock block = Character.UnicodeBlock.of(codePoint);

        return (block == UnicodeBlock.CJK_COMPATIBILITY    // CJK
            ) || (block == UnicodeBlock.CJK_COMPATIBILITY_FORMS    //
                ) || (block == UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS    //
                    ) || (block == UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT    //
                        ) || (block == UnicodeBlock.CJK_RADICALS_SUPPLEMENT    //
                            ) || (block == UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION    //
                                ) || (block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS    //
                                    ) || (block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A    //
                                    ) || (block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B    //
                                    ) || (block == UnicodeBlock.BOPOMOFO                     // Chinese
                                    ) || (block == UnicodeBlock.BOPOMOFO_EXTENDED            //
                                    ) || (block == UnicodeBlock.HANGUL_COMPATIBILITY_JAMO    // Korean
                                    ) || (block == UnicodeBlock.HANGUL_JAMO                  //
                                    ) || (block == UnicodeBlock.HANGUL_SYLLABLES             //
                                    ) || (block == UnicodeBlock.KANBUN                       // Japanese
                                    ) || (block == UnicodeBlock.HIRAGANA                     //
                                    ) || (block == UnicodeBlock.KANGXI_RADICALS              //
                                    ) || (block == UnicodeBlock.KANNADA                      //
                                    ) || (block == UnicodeBlock.KATAKANA                     //
                                    ) || (block == UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS);
    }

    private static List<String> stopWords = Arrays.asList(new String[] {
        "the", "and", "that", "have",
        "for"    // English stop words
    });

    public static boolean isStopWord(String word) {
        if (stopWords.contains(word)) {
            return true;
        }

        int len = word.codePointCount(0, word.length());

        if (len < 3) {

            // too short, is this CJK?
            int cp1 = word.codePointAt(0);

            if (isCJK(cp1)) {
                return false;
            }

            if (len == 2) {

                // maybe digit+CJK, check the second char
                int cp2 = word.codePointAt(Character.charCount(cp1));

                return !isCJK(cp2);
            }

            return true;
        }

        return false;
    }
}
