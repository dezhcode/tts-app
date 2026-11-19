package com.example.tts

/**
 * Kotlin port of `normalize_fa.py` and sentence chunking from `speak_fa.py`
 * in `mehdi-hf/cilla-mini`.
 *
 * - Normalizes Arabic/Persian character variants (ي->ی, ك->ک, ة->ه, etc.)
 * - Strips diacritics (harakat/tashdid) and tatweel while preserving ZWNJ (\u200C)
 * - Converts Persian, Arabic-Indic, and ASCII numbers (integers & decimals) into Persian words
 * - Splits long paragraphs into sentence-level chunks for stable 18M Flow-LM synthesis
 */
object PersianTextNormalizer {

    private val ones = arrayOf(
        "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه"
    )
    private val teens = arrayOf(
        "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده",
        "شانزده", "هفده", "هجده", "نوزده"
    )
    private val tens = arrayOf(
        "", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"
    )
    private val hundreds = arrayOf(
        "", "صد", "دویست", "سیصد", "چهارصد", "پانصد",
        "ششصد", "هفتصد", "هشتصد", "نهصد"
    )
    private val scales = arrayOf(
        "", "هزار", "میلیون", "میلیارد", "تریلیون"
    )

    private val numberRegex = Regex("""-?(?:\d+(?:[./٫,]\d+)?|\d+)""")

    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        val sb = StringBuilder(rawText.length)
        for (ch in rawText) {
            when (ch) {
                // Unify Arabic forms to standard Persian
                'ي', 'ى', 'ئ' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة', 'ۀ' -> sb.append('ه')
                'أ', 'إ' -> sb.append('ا')
                'ؤ' -> sb.append('و')
                // Convert Persian and Arabic-Indic digits to ASCII for number expansion
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                // Unify punctuation
                '?' -> sb.append('؟')
                ',' -> sb.append('،')
                ';' -> sb.append('؛')
                // Strip harakat, tashdid, and tatweel
                '\u0640' -> Unit
                in '\u064B'..'\u065F', '\u0670' -> Unit
                // Keep ZWNJ (\u200C), Persian letters, spaces, and prosody punctuation
                '\u200C' -> sb.append('\u200C')
                '\n', '\r', '\t' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }

        // Expand numbers to spoken Persian words
        val expanded = numberRegex.replace(sb.toString()) { match ->
            convertNumberStringToPersianWords(match.value)
        }

        // Filter unsupported symbols while keeping Persian script, Latin letters (if any), and prosody marks
        val cleaned = StringBuilder(expanded.length)
        for (ch in expanded) {
            if (isAllowedChar(ch)) {
                cleaned.append(ch)
            } else if (ch.isWhitespace()) {
                cleaned.append(' ')
            }
        }

        return cleaned.toString()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s+([،؛؟!.])"""), "$1")
            .trim()
    }

    /**
     * Splits normalized Persian text into sentence/clause segments (<= maxChunkChars)
     * matching `speak_fa.py` so the small 18.2M flow model maintains prosody without repetition.
     */
    fun splitSentences(normalizedText: String, maxChunkChars: Int = 140): List<String> {
        if (normalizedText.isBlank()) return emptyList()

        val rawSentences = normalizedText
            .split(Regex("""(?<=[.؟!؛…])\s+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<String>()
        for (sentence in rawSentences) {
            if (sentence.length <= maxChunkChars) {
                result.add(sentence)
            } else {
                // Split further at Persian commas or colons
                val clauses = sentence.split(Regex("""(?<=[،:])\s+"""))
                val current = StringBuilder()
                for (clause in clauses) {
                    if (current.isEmpty()) {
                        current.append(clause)
                    } else if (current.length + 1 + clause.length <= maxChunkChars) {
                        current.append(' ').append(clause)
                    } else {
                        result.add(current.toString().trim())
                        current.clear()
                        current.append(clause)
                    }
                }
                if (current.isNotEmpty()) {
                    val remaining = current.toString().trim()
                    if (remaining.length <= maxChunkChars) {
                        result.add(remaining)
                    } else {
                        // Word-level fallback split
                        val words = remaining.split(' ')
                        val wordBuf = StringBuilder()
                        for (w in words) {
                            if (wordBuf.isEmpty()) {
                                wordBuf.append(w)
                            } else if (wordBuf.length + 1 + w.length <= maxChunkChars) {
                                wordBuf.append(' ').append(w)
                            } else {
                                result.add(wordBuf.toString())
                                wordBuf.clear()
                                wordBuf.append(w)
                            }
                        }
                        if (wordBuf.isNotEmpty()) {
                            result.add(wordBuf.toString())
                        }
                    }
                }
            }
        }
        return result
    }

    private fun isAllowedChar(ch: Char): Boolean {
        return ch in '\u0600'..'\u06FF' ||
            ch == '\u200C' ||
            ch in 'a'..'z' ||
            ch in 'A'..'Z' ||
            ch == ' ' ||
            ch in charArrayOf('.', '،', '؛', '؟', '!', ':', '…', '-', '«', '»')
    }

    fun convertNumberStringToPersianWords(raw: String): String {
        if (raw.isEmpty()) return ""
        var s = raw
        val isNegative = s.startsWith("-")
        if (isNegative) s = s.substring(1)

        // Handle thousand separators vs decimal separator
        val decimalSplit = when {
            s.contains('٫') -> s.split('٫', limit = 2)
            s.contains('.') && s.count { it == '.' } == 1 -> s.split('.', limit = 2)
            else -> listOf(s.replace(",", ""))
        }

        val intPartStr = decimalSplit[0].replace(",", "").trim()
        val intVal = intPartStr.toLongOrNull() ?: return raw
        val intWords = integerToWords(intVal)

        val full = if (decimalSplit.size == 2 && decimalSplit[1].isNotBlank()) {
            val fracStr = decimalSplit[1].trim()
            val fracVal = fracStr.toLongOrNull()
            if (fracVal != null) {
                val leadingZeros = fracStr.takeWhile { it == '0' }.length
                val zeroPrefix = if (leadingZeros > 0) "صفر ".repeat(leadingZeros) else ""
                val fracWords = if (fracVal == 0L) "" else integerToWords(fracVal)
                "$intWords ممیز $zeroPrefix$fracWords".trim()
            } else {
                intWords
            }
        } else {
            intWords
        }

        return if (isNegative) "منفی $full" else full
    }

    fun integerToWords(number: Long): String {
        if (number == 0L) return "صفر"
        if (number < 0L) return "منفی ${integerToWords(-number)}"

        var n = number
        var scaleIdx = 0
        val parts = mutableListOf<String>()

        while (n > 0L) {
            val chunk = (n % 1000L).toInt()
            if (chunk != 0) {
                val chunkText = threeDigitToWords(chunk)
                val scaleWord = scales.getOrElse(scaleIdx) { "" }
                val combined = if (scaleWord.isEmpty()) chunkText else "$chunkText $scaleWord"
                parts.add(0, combined)
            }
            n /= 1000L
            scaleIdx++
        }

        return parts.joinToString(" و ")
    }

    private fun threeDigitToWords(n: Int): String {
        val h = n / 100
        val rem = n % 100
        val t = rem / 10
        val o = rem % 10

        val segments = mutableListOf<String>()
        if (h > 0) segments.add(hundreds[h])
        when {
            rem in 10..19 -> segments.add(teens[rem - 10])
            else -> {
                if (t > 0) segments.add(tens[t])
                if (o > 0) segments.add(ones[o])
            }
        }
        return segments.joinToString(" و ")
    }
}
