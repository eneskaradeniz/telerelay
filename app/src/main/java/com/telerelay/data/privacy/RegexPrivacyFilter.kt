package com.telerelay.data.privacy

import com.telerelay.domain.model.FilterDecision
import com.telerelay.domain.model.PrivacyMode
import com.telerelay.domain.port.PrivacyFilter
import com.telerelay.domain.port.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Regex-based privacy guard.
 *
 * Evaluation order: sender exclude list first (fast, exact), then patterns.
 * In EXCLUDE mode the first match drops the message; in MASK mode every
 * pattern's `code` group (or the whole match) is replaced with same-length
 * bullets.
 *
 * Safety posture, documented trade-offs:
 * - user-supplied patterns that fail to compile are ignored, never thrown;
 * - pattern length and body length are capped, which bounds the practical
 *   blast radius of catastrophic backtracking without pulling in a RE2-style
 *   engine (kept dependency-free on purpose).
 */
@Singleton
class RegexPrivacyFilter @Inject constructor(
    private val settings: SettingsRepository,
) : PrivacyFilter {

    private class CompiledPattern(val regex: Regex, val hasCodeGroup: Boolean)

    private var cacheKey: List<String>? = null
    private var cache: List<CompiledPattern> = emptyList()

    override fun evaluate(sender: String, body: String): FilterDecision {
        val s = settings.current()

        if (excludedByNumber(sender, s.excludedNumbers)) return FilterDecision.Exclude
        if (body.isEmpty()) return FilterDecision.Allow
        if (body.length > MAX_BODY_LENGTH) return FilterDecision.Allow

        val patterns = compiledPatterns(s.filterPatterns)
        if (patterns.isEmpty()) return FilterDecision.Allow

        var matched = false
        var text = body
        for (pattern in patterns) {
            if (!pattern.regex.containsMatchIn(text)) continue
            if (s.privacyMode == PrivacyMode.EXCLUDE) return FilterDecision.Exclude
            text = mask(pattern, text)
            matched = true
        }
        return if (matched) FilterDecision.Masked(text) else FilterDecision.Allow
    }

    /** True when the sender equals an excluded number, ignoring formatting and country prefix. */
    private fun excludedByNumber(sender: String, excluded: List<String>): Boolean {
        if (excluded.isEmpty()) return false
        val senderKey = normalizeNumber(sender)
        return excluded.any { candidate ->
            val candidateKey = normalizeNumber(candidate)
            candidateKey.isNotEmpty() && (senderKey == candidateKey || senderKey.endsWith(candidateKey))
        }
    }

    private fun normalizeNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > SUFFIX_LENGTH) digits.takeLast(SUFFIX_LENGTH) else digits
    }

    private fun compiledPatterns(patterns: List<String>): List<CompiledPattern> {
        if (patterns != cacheKey) {
            cache = patterns.mapNotNull(::compile)
            cacheKey = patterns
        }
        return cache
    }

    private fun compile(pattern: String): CompiledPattern? {
        if (pattern.length > MAX_PATTERN_LENGTH) return null
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return null
        val hasCodeGroup = runCatching { regex.pattern.contains("(?<code>") }.getOrDefault(false)
        return CompiledPattern(regex, hasCodeGroup)
    }

    private fun mask(pattern: CompiledPattern, text: String): String {
        val matcher = pattern.regex.toPattern().matcher(text)
        val result = StringBuilder(text.length)
        var last = 0
        while (matcher.find()) {
            // Mask ONLY the code group's span, keeping the surrounding prose
            // ("Kodunuz: 482913" → "Kodunuz: ••••••"). Without a named group,
            // the whole match is the secret.
            val span = runCatching {
                if (pattern.hasCodeGroup) matcher.start(CODE_GROUP)..matcher.end(CODE_GROUP)
                else matcher.start()..matcher.end()
            }.getOrElse { matcher.start()..matcher.end() }

            if (span.isEmpty()) continue
            result.append(text, last, span.first)
            result.append(MASK_CHAR.repeat(span.last - span.first))
            last = span.last
        }
        result.append(text, last, text.length)
        return result.toString()
    }

    companion object {
        private const val MASK_CHAR = "•"
        private const val CODE_GROUP = "code"
        private const val MAX_PATTERN_LENGTH = 240
        private const val MAX_BODY_LENGTH = 2_048

        /** Digits compared as a suffix, so "+90555…", "90555…" and "0555…" all match. */
        private const val SUFFIX_LENGTH = 10
    }
}
