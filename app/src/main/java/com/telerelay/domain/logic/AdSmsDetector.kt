package com.telerelay.domain.logic

/**
 * Recognizes advertising SMS so the user can choose not to receive them on
 * Telegram. One fixed rule set, nothing to configure — deliberately not the
 * removed regex/number filter.
 *
 * Two signal families, both taken from real traffic:
 * - **Opt-out wording.** Turkish law (İYS) requires every commercial SMS to
 *   carry a rejection instruction and the MERSIS number: "SMSRET->3639",
 *   "Ret icin 3338'e RET TURKNET yaz", "JJK IPTAL yazip 4946'ya", "MERSIS: ...".
 *   Transactional messages (OTPs, bank transfers, bills) carry none of these.
 * - **Illegal betting spam.** Never has opt-out wording, but always pushes
 *   bonuses / free spins or "üye ol" + "yatırım".
 *
 * Bodies are folded to ASCII lowercase first: senders mix "IPTAL", "İPTAL" and
 * "iptal", and `İ` does not lowercase to `i` in a locale-independent way.
 *
 * Callers must still let OTP-bearing SMS and contacts through — this detector
 * only classifies text.
 */
object AdSmsDetector {

    private val OPT_OUT = Regex(
        listOf(
            """sms\s*-?\s*ret""",         // "SMSRET->3639", "SMS RET icin"
            """\bret\s+icin""",           // "Ret icin 3338'e"
            """\bret\s+\w+\s+yaz""",      // "RET TURKNET yaz"
            """sms\s+iptal""",            // "SMS IPTAL", "SMS iptali icin"
            """iptal\w*\s+icin""",        // "iptal icin H yazin"
            """iptal\s+yaz""",            // "JJK IPTAL yazip 4946'ya"
            """\bmersis\b""",             // mandatory in commercial e-messages
        ).joinToString("|"),
    )

    private val BETTING = Regex("""freebet|free\s*spin|freespin|casino|bonus""")

    private val BETTING_SIGNUP = Regex("""uye\s+ol|hosgeldin|nakit\s+kazan|cevrim""")

    fun isAd(body: String): Boolean {
        val text = fold(body)
        return OPT_OUT.containsMatchIn(text) ||
            BETTING.containsMatchIn(text) ||
            ("yatirim" in text && BETTING_SIGNUP.containsMatchIn(text))
    }

    /** Turkish-aware ASCII fold: "İPTAL" / "ŞİFRE" → "iptal" / "sifre". */
    private fun fold(value: String): String = buildString(value.length) {
        for (c in value) {
            append(
                when (c) {
                    'İ', 'I', 'ı' -> 'i'
                    'Ş', 'ş' -> 's'
                    'Ğ', 'ğ' -> 'g'
                    'Ü', 'ü' -> 'u'
                    'Ö', 'ö' -> 'o'
                    'Ç', 'ç' -> 'c'
                    else -> c.lowercaseChar()
                },
            )
        }
    }
}
