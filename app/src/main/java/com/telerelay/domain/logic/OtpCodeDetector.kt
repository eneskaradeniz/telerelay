package com.telerelay.domain.logic

/**
 * Detection-only OTP lookup: finds the most likely verification code in an SMS
 * body so Telegram can attach a one-tap copy button. Deliberately NOT a privacy
 * filter — nothing is masked, dropped or rewritten, and there is no rule set to
 * configure.
 *
 * A bare digit run is not enough: marketing SMS are full of 4–8 digit numbers
 * ("4000 TL'ye varan ParaPuan"), so the number must sit next to OTP-context
 * wording (kod / code / doğrulama / otp / pin / şifre) — in either order, since
 * real bodies read both "dogrulama kodu 123456" and "123456 dogrulama kodu".
 * Best-effort by design; slashes are excluded from the number boundaries so
 * dates (`16/09/2026`) are never picked up.
 */
object OtpCodeDetector {

    private const val KEYWORDS = "dogrulama|doğrulama|kod|code|passcode|otp|şifre|sifre|pin"

    /** Wording first, then the number: "Dogrulama kodunuz : 814067". */
    private const val KEYWORD_FIRST =
        "(?<![a-zçğıöşü])(?:$KEYWORDS)[a-zçğıöşü]*[^0-9]{0,12}(?<![\\d/])(\\d{4,8})(?![\\d/])"

    /** Number first, then the wording: "461751 dogrulama kodu ile giriş". */
    private const val NUMBER_FIRST =
        "(?<![\\d/])(\\d{4,8})(?![\\d/])[^0-9]{0,12}(?<![a-zçğıöşü])(?:$KEYWORDS)"

    private val CODE = Regex("(?iu)(?:$KEYWORD_FIRST|$NUMBER_FIRST)")

    /** First code-like token near OTP wording in [body], or null when there is none. */
    fun find(body: String): String? {
        val match = CODE.find(body) ?: return null
        return match.groupValues[1].ifBlank { null } ?: match.groupValues[2].ifBlank { null }
    }
}
