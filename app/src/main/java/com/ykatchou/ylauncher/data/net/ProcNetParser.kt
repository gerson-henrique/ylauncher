package com.ykatchou.ylauncher.data.net

/**
 * Reads the kernel's socket tables (`/proc/net/{tcp,tcp6,udp,udp6}`) into live connections. Pure
 * string work, kept apart from the Shizuku call so the fiddly hex parsing can be tested — the
 * addresses are little-endian hex and easy to get subtly wrong.
 *
 * The caller cats the four files with a `#name` marker before each so one shell round-trip covers
 * all of them and the proto (tcp/udp) survives; the IP version is told apart by address length.
 */
object ProcNetParser {

    data class Conn(
        val proto: String,       // "tcp" | "udp"
        val uid: Int,
        val remoteIp: String,
        val remotePort: Int,
        val state: String,       // hex TCP state, or "" for udp
    ) {
        /** Identity for de-duping and for spotting a *new* connection between two polls. */
        val key: String get() = "$proto:$uid:$remoteIp:$remotePort"
    }

    // TCP states worth showing: a call that is up or being opened. Skip LISTEN/TIME_WAIT/etc.
    private const val ESTABLISHED = "01"
    private const val SYN_SENT = "02"

    fun parse(marked: String): List<Conn> {
        val out = ArrayList<Conn>()
        var proto = "tcp"
        for (raw in marked.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#")) {
                proto = if (line.contains("udp")) "udp" else "tcp"
                continue
            }
            val t = line.split(Regex("\\s+"))
            if (t.size < 10) continue
            val rem = parseAddr(t[2]) ?: continue          // header row fails here and is skipped
            val (ip, port) = rem
            val uid = t[7].toIntOrNull() ?: continue
            val state = t[3]
            // Skip the noise: unbound remotes, loopback, and TCP sockets that are not a live call.
            if (port == 0 || ip.isBlank() || isLocal(ip)) continue
            if (proto == "tcp" && state != ESTABLISHED && state != SYN_SENT) continue
            out += Conn(proto, uid, ip, port, if (proto == "tcp") state else "")
        }
        return out
    }

    /** `0805A8C0:01BB` -> ("192.168.5.8", 443). Null when the token is not an address (the header). */
    private fun parseAddr(token: String): Pair<String, Int>? {
        val parts = token.split(':')
        if (parts.size != 2) return null
        val (addrHex, portHex) = parts
        val port = portHex.toIntOrNull(16) ?: return null
        val ip = when (addrHex.length) {
            8 -> ipv4(addrHex) ?: return null
            32 -> ipv6(addrHex) ?: return null
            else -> return null
        }
        return ip to port
    }

    /** Four little-endian bytes: `0100007F` is stored LSB-first, so the address is 127.0.0.1. */
    private fun ipv4(hex: String): String? {
        val b = bytes(hex) ?: return null
        return "${b[3]}.${b[2]}.${b[1]}.${b[0]}"
    }

    /**
     * Sixteen bytes as four little-endian 32-bit words. Collapses an IPv4-mapped address
     * (`::ffff:a.b.c.d`) back to dotted v4, and compresses the longest zero run to `::`.
     */
    private fun ipv6(hex: String): String? {
        val raw = bytes(hex) ?: return null
        if (raw.size != 16) return null
        val b = IntArray(16)
        for (word in 0 until 4) {
            for (i in 0 until 4) b[word * 4 + i] = raw[word * 4 + (3 - i)]  // reverse each word
        }
        // IPv4-mapped: 10 zero bytes, then 0xffff, then the v4.
        if ((0 until 10).all { b[it] == 0 } && b[10] == 0xff && b[11] == 0xff) {
            return "${b[12]}.${b[13]}.${b[14]}.${b[15]}"
        }
        val groups = IntArray(8) { (b[it * 2] shl 8) or b[it * 2 + 1] }
        return compress(groups)
    }

    private fun compress(groups: IntArray): String {
        var bestStart = -1; var bestLen = 0; var curStart = -1; var curLen = 0
        for (i in groups.indices) {
            if (groups[i] == 0) {
                if (curStart < 0) curStart = i
                curLen++
                if (curLen > bestLen) { bestLen = curLen; bestStart = curStart }
            } else { curStart = -1; curLen = 0 }
        }
        if (bestLen < 2) return groups.joinToString(":") { it.toString(16) }
        val head = (0 until bestStart).joinToString(":") { groups[it].toString(16) }
        val tail = (bestStart + bestLen until 8).joinToString(":") { groups[it].toString(16) }
        return "$head::$tail"
    }

    /** Hex string -> unsigned byte values, in string order. */
    private fun bytes(hex: String): IntArray? {
        if (hex.length % 2 != 0) return null
        return IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toIntOrNull(16) ?: return null }
    }

    private fun isLocal(ip: String): Boolean =
        ip == "0.0.0.0" || ip == "::" || ip.startsWith("127.") || ip == "::1"
}
