package com.telerelay.domain.logic

/**
 * Detection-only OTP lookup: finds the most likely verification code in an SMS
 * body so Telegram can attach a one-tap copy button. Deliberately NOT a privacy
 * filter — nothing is masked, dropped or rewritten, and there is no rule set to
 * configure.
 *
 * A bare digit run is not enough: marketing SMS are full of 4–8 digit numbers
 * ("4000 TL'ye varan ParaPuan"), so the number must be preceded by an
 * OTP-context keyword (kod / code / doğrulama / otp / pin / şifre) within a
 * few characters. Best-effort by design; slashes are excluded from the number
 * boundaries so dates (`16/09/2026`) are never picked up.
 */
object OtpCodeDetector {

    private val CODE = Regex(
        "(?iu)(?<![a-zçğıöşü])(?:dogrulama|doğrulama|kod|code|passcode|otp|şifre|sifre|pin)" +
            "[a-zçğıöşü]*[^0-9]{0,12}(?<![\\d/])(\\d{4,8})(?![\\d/])",
    )

    /** First code-like token near OTP wording in [body], or null when there is none. */
    fun find(body: String): String? = CODE.find(body)?.groupValues?.get(1)
}
