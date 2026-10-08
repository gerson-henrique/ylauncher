package com.ykatchou.ylauncher.data.qr

/**
 * What a scanned QR code turns out to be. The reader decides the action from this, so each kind
 * maps to the one thing a person wants from it: a link opens, a Wi‑Fi code connects, a Pix code
 * and plain text are copied, and a ponte code pairs the phone with the Mac.
 */
sealed interface QrContent {
    val raw: String

    data class Link(override val raw: String) : QrContent

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String?,
        val security: String?,   // WPA / WEP / SAE / nopass, as the code states it
        val hidden: Boolean,
    ) : QrContent

    /** Pix "copia e cola" — an EMV payload, always starting with the format indicator 000201. */
    data class Pix(override val raw: String) : QrContent

    /** Pairing code shown by `hashi parear` on the Mac: hashi://host:port?t=<token>&n=<name>. */
    data class Ponte(
        override val raw: String,
        val host: String,
        val port: Int,
        val token: String,
        val name: String,
    ) : QrContent

    data class Text(override val raw: String) : QrContent

    companion object {
        fun parse(raw: String): QrContent {
            val text = raw.trim()
            return when {
                text.startsWith("http://", ignoreCase = true) ||
                    text.startsWith("https://", ignoreCase = true) -> Link(text)
                text.startsWith("WIFI:", ignoreCase = true) -> parseWifi(text) ?: Text(text)
                text.startsWith("hashi://", ignoreCase = true) -> parsePonte(text) ?: Text(text)
                text.startsWith("000201") -> Pix(text)
                else -> Text(text)
            }
        }

        /**
         * WIFI:T:WPA;S:name;P:secret;H:false;; — fields in any order, with `\` escaping the
         * separators (`;` `,` `:` `"` and itself) inside values.
         */
        private fun parseWifi(text: String): Wifi? {
            val fields = mutableMapOf<String, String>()
            val body = text.substring(5)
            var key: String? = null
            val cur = StringBuilder()
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    c == '\\' && i + 1 < body.length -> { cur.append(body[i + 1]); i++ }
                    c == ':' && key == null -> { key = cur.toString().uppercase(); cur.clear() }
                    c == ';' -> {
                        key?.let { fields[it] = cur.toString() }
                        key = null
                        cur.clear()
                    }
                    else -> cur.append(c)
                }
                i++
            }
            val ssid = fields["S"]?.takeIf { it.isNotEmpty() } ?: return null
            return Wifi(
                raw = text,
                ssid = ssid,
                password = fields["P"]?.takeIf { it.isNotEmpty() },
                security = fields["T"]?.takeIf { it.isNotEmpty() },
                hidden = fields["H"].equals("true", ignoreCase = true),
            )
        }

        private fun parsePonte(text: String): Ponte? {
            val rest = text.substring("hashi://".length)
            val authority = rest.substringBefore('?').trimEnd('/')
            val query = rest.substringAfter('?', "")
                .split('&')
                .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .toMap()
            val host = authority.substringBeforeLast(':').takeIf { it.isNotEmpty() } ?: return null
            val port = authority.substringAfterLast(':', "").toIntOrNull() ?: return null
            val token = query["t"]?.takeIf { it.isNotEmpty() } ?: return null
            val name = query["n"]?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: "Mac"
            return Ponte(text, host, port, token, name)
        }
    }
}
