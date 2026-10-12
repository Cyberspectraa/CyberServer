package com.cyberspectraa.cyberserver.voice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional explicit text substitutions for known recurring recognition errors.
 * Exact whole phrase replacement only: no AI, no transcript persistence,
 * and never a guess about the speaker's meaning.
 */
final class CaptionVocabulary {
    static String correct(String text) {
        if (text == null || text.isBlank()) return "";
        String result = text;
        int count = 0;
        for (String entry : VoiceCaptionConfig.WORD_REPLACEMENTS.get()) {
            if (++count > 16) break;
            int arrow = entry.indexOf("=>");
            if (arrow < 1 || arrow != entry.lastIndexOf("=>")) continue;
            String heard = entry.substring(0, arrow).trim();
            String intended = entry.substring(arrow + 2).trim();
            if (heard.isEmpty() || intended.isEmpty()
                    || heard.length() > 48 || intended.length() > 48) continue;
            Pattern pattern = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])"
                    + Pattern.quote(heard) + "(?![\\p{L}\\p{N}])");
            result = pattern.matcher(result).replaceAll(Matcher.quoteReplacement(intended));
        }
        return result;
    }

    private CaptionVocabulary() {}
}
