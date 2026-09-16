package com.telerelay.domain.model

/**
 * Default privacy-guard patterns, seeded for first launch and freely editable by the user.
 *
 * Convention: a pattern MAY expose a named group `code` — that group is what
 * gets masked (e.g. in "kodunuz 482913" only "482913" turns into bullets).
 * Without the group, the whole match is masked.
 *
 * Design notes:
 * - OTP patterns require an in-context keyword so ordinary numbers
 *   ("Toplam: 250 TL", "Sipariş #12345") are never caught.
 * - Bank patterns match transaction verbs used by major TR/EN bank senders,
 *   in both "verb … amount" and "amount … verb" word orders.
 */
object DefaultFilterRules {

    val PATTERNS: List<String> = listOf(
        // --- OTP / 2FA codes ---
        """(?i)\b(?:verification|security|confirmation)\s*code\b[^0-9]{0,20}(?<code>[0-9]{4,8})\b""",
        """(?i)\bkodunuz\b[^0-9]{0,20}(?<code>[0-9]{4,8})\b""",
        """(?i)\bcode(?:\sis)?[^0-9]{0,10}(?<code>[0-9]{4,8})\b""",
        """(?i)\b[A-Z]{1,2}-(?<code>[0-9]{4,8})\b""", // Google-style: G-482913
        """(?i)\b(?:otp|şifre|sifre|pin)\b[^0-9]{0,20}(?<code>[0-9]{4,8})\b""",
        """(?i)\bkodunuz\b[^0-9]{0,10}(?<code>[0-9]{3}\s[0-9]{3,4})\b""", // "482 913"
        """(?i)\b(?<code>[0-9]{4,8})\s+is your\b""", // "123456 is your Google verification code"

        // --- Bank / card transactions: verb before amount ---
        // Lazy gap + greedy amount so the FULL number ("25.00", "1.250,75") is captured.
        """(?i)\b(?:harcama|harcandı|çıkış|çekiliş|kullanım|charged|spent|payment|purchase|withdrawal)\b.{0,60}?(?<code>[0-9]{1,12}(?:[.,][0-9]{1,3})*)\s?(?:TL|USD|EUR|GBP|TRY|\$)?""",
        """(?i)\b(?:yatırma|transfer|giriş)\b.{0,60}?(?<code>[0-9]{1,12}(?:[.,][0-9]{1,3})*)\s?(?:TL|USD|EUR|GBP|TRY)?""",

        // --- Bank / card transactions: amount before verb (common in TR SMS) ---
        """(?i)(?<code>[0-9]{1,12}(?:[.,][0-9]{1,3})*)\s?(?:TL|USD|EUR|GBP|TRY)\b.{0,30}?\b(?:harcama|harcandı|yatırma|yatırıldı|yatirildi|çekildi|ödeme|payment|charged)""",

        // --- Card references with amount ---
        """(?i)\bkartınız(?:dan)?\b.{0,40}?(?<code>[0-9]{1,12}(?:[.,][0-9]{1,3})*)""",
    )
}
