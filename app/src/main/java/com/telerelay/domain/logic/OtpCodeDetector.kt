package com.telerelay.domain.logic

/**
 * Detection-only OTP lookup: finds the most likely verification code in an SMS
 * body so Telegram can attach a one-tap copy button. Deliberately NOT a privacy
 * filter — nothing is masked, dropped or rewritten, and there is no rule set to
 * configure.
 *
 * Best-effort by design: leftmost 4–8 digit run wins, with slashes excluded
 * from the boundaries so date fragments (`16/09/2026`) and times (`16:06:39`)
 * are not picked up.
 */
object OtpCodeDetector {

    /** 4–8 digits, not adjacent to another digit or a slash. */
    private val CODE = Regex("(?<![\\d/])\\d{4,8}(?![\\d/])")

    /** First code-like token in [body], or null when nothing looks like a code. */
    fun find(body: String): String? = CODE.find(body)?.value
}
