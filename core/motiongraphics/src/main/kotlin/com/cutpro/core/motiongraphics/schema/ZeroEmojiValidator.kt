package com.cutpro.core.motiongraphics.schema

object ZeroEmojiValidator {

    fun containsEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isEmojiCodePoint(cp)) {
                return true
            }
            i += Character.charCount(cp)
        }
        return false
    }

    fun isEmojiCodePoint(cp: Int): Boolean {
        return when (cp) {
            // Regional Indicator Symbols (National Flags: US, BD, GB, etc.)
            in 0x1F1E6..0x1F1FF -> true

            // Enclosed Alphanumeric Supplement (Squared Latin letters A, B, O, P, AB, CL, COOL, FREE, ID, NEW, NG, OK, SOS, UP, VS)
            in 0x1F100..0x1F1E5 -> true

            // Emoticons & Smileys
            in 0x1F600..0x1F64F -> true

            // Miscellaneous Symbols and Pictographs (includes Fitzpatrick skin tone modifiers 0x1F3FB..0x1F3FF)
            in 0x1F300..0x1F5FF -> true

            // Transport and Map Symbols
            in 0x1F680..0x1F6FF -> true

            // Alchemical Symbols
            in 0x1F700..0x1F77F -> true

            // Geometric Shapes Extended
            in 0x1F780..0x1F7FF -> true

            // Supplemental Arrows-C
            in 0x1F800..0x1F8FF -> true

            // Supplemental Symbols and Pictographs (Unicode 9.0-12.0 emojis)
            in 0x1F900..0x1F9FF -> true

            // Chess Symbols
            in 0x1FA00..0x1FA6F -> true

            // Symbols and Pictographs Extended-A (Unicode 13.0+ modern emojis)
            in 0x1FA70..0x1FAFF -> true

            // Symbols for Legacy Computing (Terminal and retro graphic pictographs)
            in 0x1FB00..0x1FBFF -> true

            // Games & Cards (Mahjong Red Dragon 0x1F004, Playing Card Black Joker 0x1F0CF, Dominoes)
            in 0x1F000..0x1F02F -> true
            in 0x1F030..0x1F09F -> true
            in 0x1F0A0..0x1F0FF -> true

            // Enclosed Ideographic Supplement (Squared CJK emoji buttons)
            in 0x1F200..0x1F2FF -> true

            // Miscellaneous Symbols & Dingbats (Sun, umbrella, warning, skull, checkmarks, scissors, heavy heart)
            in 0x2600..0x27BF -> true

            // Variation Selectors (VS-1 to VS-16, specifically VS-15 0xFE0E and VS-16 0xFE0F)
            in 0xFE00..0xFE0F -> true

            // Miscellaneous Technical (Watch, hourglass, alarm clock, stopwatch, media buttons)
            in 0x2300..0x23FF -> true

            // Miscellaneous Symbols and Arrows (Stars, circles, squares, black/white arrows)
            in 0x2B00..0x2BFF -> true

            // Punctuation used as emojis (Double exclamation 0x203C, exclamation question 0x2049)
            in 0x203C..0x2049 -> true

            // Letterlike Symbols & Navigation Arrows
            0x2122, 0x2139 -> true
            in 0x2194..0x2199 -> true
            in 0x21A9..0x21AA -> true
            in 0x2934..0x2935 -> true

            // Geometric Shapes (Play 0x25B6, reverse 0x25C0, squares)
            in 0x25A0..0x25FF -> true

            // CJK Symbols & Enclosed Characters (Wavy dash 0x3030, part alternation 0x303D, circled ideographs 0x3297, 0x3299)
            0x3030, 0x303D -> true
            0x3297, 0x3299 -> true

            // Combining Diacritical Marks for Symbols (Keycap 0x20E3, enclosing circle/square)
            0x20E3 -> true
            in 0x20D0..0x20FF -> true

            // Tag Characters (Subdivision flag components)
            in 0xE0020..0xE007F -> true

            // Isolated Surrogates (Malformed UTF-16 defense)
            in 0xD800..0xDFFF -> true

            else -> false
        }
    }

    fun validateNoEmoji(text: String, fieldName: String = "text") {
        if (containsEmoji(text)) {
            throw IllegalArgumentException("Field '$fieldName' violates the strict zero-emoji mandate: contains emojis")
        }
    }
}
