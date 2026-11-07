package tachiyomi.domain.chapter.service

import kotlin.math.floor
import kotlin.math.round

/**
 * Pure Kotlin Multiplatform implementation of Mihon's [ChapterRecognition] without `java.text.DecimalFormat`.
 */
object IosChapterRecognition {

    private const val NUMBER_PATTERN = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""
    private val basic = Regex("""(?<=ch\.) *$NUMBER_PATTERN""")
    private val number = Regex(NUMBER_PATTERN)
    private val unwanted = Regex("""\b(?:ver|version)[^a-z]?[0-9]+""")
    private val unwantedWhiteSpace = Regex("""\s(?=extra|special|omake)""")
    private val volume = Regex("""\b(?:(?:vol(?:ume)?|season)[^a-z0-9]? *|[vs]\.?)([0-9]+(?:\.[0-9]+)?)\b""")
    private val part = Regex("""\bpart\.? *([0-9]+|i{1,3}|iv|vi{0,3}|ix|x)\b""")
    private val romanNumerals = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")

    fun formatChapterNumber(value: Double): String {
        val rounded = round(value * 1000.0) / 1000.0
        val longPart = rounded.toLong()
        if (rounded == longPart.toDouble()) {
            return longPart.toString()
        }
        return rounded.toString().trimEnd('0').trimEnd('.')
    }

    private data class RecognizedNumber(val value: Double, val letter: Char? = null) {
        override fun toString(): String = formatChapterNumber(value) + (letter?.toString() ?: "")
    }

    fun parseChapterNumber(
        mangaTitle: String,
        chapterName: String,
        chapterNumber: Double? = null,
    ): Double {
        if (chapterNumber != null && (chapterNumber == -2.0 || chapterNumber > -1.0)) {
            return chapterNumber
        }
        val clean = chapterName.lowercase().replace(mangaTitle.lowercase(), "")
        return parseName(clean)?.let { toDouble(it.toString()) } ?: chapterNumber ?: -1.0
    }

    fun toDouble(numberStr: String): Double {
        val letter = numberStr.lastOrNull()?.takeIf { it.isLetter() }
        val value = numberStr.removeSuffix(letter?.toString() ?: "").toDoubleOrNull() ?: -1.0
        val tenths = letter?.let { it - 'a' + 1 }
        return if (tenths != null && tenths < 10 && '.' !in numberStr) value + tenths / 10.0 else value
    }

    private fun parseName(name: String): RecognizedNumber? {
        val withoutVolume = volume.replace(name, " ")
        val partMatch = part.find(withoutVolume)
        val partLetter = partMatch?.groupValues?.get(1)?.let(::parsePart)?.takeIf { it in 1..26 }?.let { 'a' + it - 1 }
        if (partMatch != null && partLetter != null) {
            parseNumber(withoutVolume.removeRange(partMatch.range))?.let { num ->
                return if (num.letter == null) num.copy(letter = partLetter) else num
            }
        }
        return parseNumber(withoutVolume)
    }

    private fun parsePart(partStr: String): Int = partStr.toIntOrNull() ?: (romanNumerals.indexOf(partStr) + 1)

    private fun parseNumber(name: String): RecognizedNumber? {
        val cleanName = name
            .trim()
            .replace(',', '.')
            .replace('-', '.')
            .replace(unwantedWhiteSpace, "")

        val numbers = number.findAll(cleanName).toList()
        if (numbers.size > 1) {
            val withoutTags = unwanted.replace(cleanName, "")
            (basic.find(withoutTags) ?: number.find(withoutTags))?.let { return it.toRecognizedNumber() }
        }
        return numbers.firstOrNull()?.toRecognizedNumber()
    }

    private fun MatchResult.toRecognizedNumber(): RecognizedNumber {
        val (whole, decimal, alpha) = destructured
        val value = whole.toDouble() + (decimal.toDoubleOrNull() ?: 0.0)
        return when {
            decimal.isEmpty() && "extra" in alpha -> RecognizedNumber(value + 0.99)
            decimal.isEmpty() && "omake" in alpha -> RecognizedNumber(value + 0.98)
            decimal.isEmpty() && "special" in alpha -> RecognizedNumber(value + 0.97)
            else -> RecognizedNumber(value, alpha.trimStart('.').singleOrNull()?.takeIf { it in 'a'..'i' })
        }
    }
}

