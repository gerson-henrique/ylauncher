package com.ykatchou.ylauncher.data.net

/**
 * Best-effort offline owner for a remote IP — turns a wall of hex into "Google / Meta / Cloudflare"
 * without a VPN or a network lookup. A curated table of the big consumer clouds and CDNs, which is
 * where the overwhelming majority of app traffic actually goes; anything not in it stays a raw IP.
 *
 * The ranges drift over time and this is deliberately not exhaustive — it is a readability aid, not
 * a routing table. Easy to extend as new offenders show up on the radar.
 */
object IpOwner {

    fun ownerOf(ip: String): String? =
        if (ip.contains(':')) ownerV6(ip) else ownerV4(ip)

    // ---- IPv4 ----

    private fun ownerV4(ip: String): String? {
        val n = ipv4ToLong(ip) ?: return null
        // Private / link-local first — these are the house, not the internet.
        if (inCidr(n, "10.0.0.0", 8) || inCidr(n, "172.16.0.0", 12) || inCidr(n, "192.168.0.0", 16)) {
            return "rede local"
        }
        for ((base, prefix, name) in V4) if (inCidr(n, base, prefix)) return name
        return null
    }

    private fun inCidr(ip: Long, base: String, prefix: Int): Boolean {
        val b = ipv4ToLong(base) ?: return false
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return (ip and mask) == (b and mask)
    }

    private fun ipv4ToLong(ip: String): Long? {
        val p = ip.split('.')
        if (p.size != 4) return null
        var n = 0L
        for (part in p) {
            val v = part.toIntOrNull() ?: return null
            if (v !in 0..255) return null
            n = (n shl 8) or v.toLong()
        }
        return n
    }

    // ---- IPv6 (matched on the /32 prefix — the first two groups) ----

    private fun ownerV6(ip: String): String? {
        val head = ip.substringBefore("::").split(':').filter { it.isNotEmpty() }
        if (head.isEmpty()) return null
        val g0 = head[0].trimStart('0').ifEmpty { "0" }.lowercase()
        // Unique-local / link-local — checked before the size guard, since fe80::1 has one group.
        if (g0.startsWith("fe8") || g0.startsWith("fc") || g0.startsWith("fd")) return "rede local"
        if (head.size < 2) return null
        val g1 = head[1].trimStart('0').ifEmpty { "0" }.lowercase()
        return V6["$g0:$g1"]
    }

    // base, prefix length, owner
    private val V4 = listOf(
        // Google
        Triple("8.8.8.0", 24, "Google"), Triple("8.8.4.0", 24, "Google"),
        Triple("142.250.0.0", 15, "Google"), Triple("172.217.0.0", 16, "Google"),
        Triple("216.58.192.0", 19, "Google"), Triple("74.125.0.0", 16, "Google"),
        Triple("173.194.0.0", 16, "Google"), Triple("64.233.160.0", 19, "Google"),
        Triple("209.85.128.0", 17, "Google"), Triple("172.253.0.0", 16, "Google"),
        Triple("34.0.0.0", 9, "Google Cloud"),
        // Meta / Facebook / WhatsApp / Instagram
        Triple("31.13.24.0", 21, "Meta"), Triple("31.13.64.0", 18, "Meta"),
        Triple("157.240.0.0", 16, "Meta"), Triple("179.60.192.0", 22, "Meta"),
        Triple("129.134.0.0", 16, "Meta"), Triple("66.220.144.0", 20, "Meta"),
        Triple("69.63.176.0", 20, "Meta"), Triple("173.252.64.0", 18, "Meta"),
        Triple("57.144.0.0", 14, "Meta"),
        // Cloudflare
        Triple("104.16.0.0", 13, "Cloudflare"), Triple("172.64.0.0", 13, "Cloudflare"),
        Triple("1.1.1.0", 24, "Cloudflare"), Triple("1.0.0.0", 24, "Cloudflare"),
        Triple("162.158.0.0", 15, "Cloudflare"), Triple("198.41.128.0", 17, "Cloudflare"),
        // Apple owns all of 17/8
        Triple("17.0.0.0", 8, "Apple"),
        // Amazon (CloudFront + some EC2)
        Triple("13.32.0.0", 15, "Amazon"), Triple("13.224.0.0", 14, "Amazon"),
        Triple("99.84.0.0", 16, "Amazon"), Triple("143.204.0.0", 16, "Amazon"),
        Triple("52.0.0.0", 11, "Amazon"), Triple("54.144.0.0", 12, "Amazon"),
        // Microsoft / Azure
        Triple("20.0.0.0", 8, "Microsoft"), Triple("13.64.0.0", 11, "Microsoft"),
        Triple("40.64.0.0", 10, "Microsoft"), Triple("104.40.0.0", 13, "Microsoft"),
        // Akamai
        Triple("23.192.0.0", 11, "Akamai"), Triple("2.16.0.0", 13, "Akamai"),
        Triple("104.64.0.0", 10, "Akamai"),
        // Fastly
        Triple("151.101.0.0", 16, "Fastly"), Triple("199.232.0.0", 16, "Fastly"),
        // Netflix
        Triple("45.57.0.0", 17, "Netflix"), Triple("208.75.76.0", 22, "Netflix"),
        // X / Twitter
        Triple("104.244.42.0", 24, "X"),
    )

    private val V6 = mapOf(
        "2001:4860" to "Google", "2404:6800" to "Google", "2607:f8b0" to "Google",
        "2800:3f0" to "Google", "2a00:1450" to "Google",
        "2a03:2880" to "Meta", "2620:0" to "Meta",
        "2606:4700" to "Cloudflare", "2803:f800" to "Cloudflare",
        "2620:149" to "Apple", "2a01:b740" to "Apple",
        "2600:9000" to "Amazon", "2600:1f00" to "Amazon",
        "2603:1000" to "Microsoft", "2a01:111" to "Microsoft",
        "2a02:26f0" to "Akamai",
        "2a04:4e42" to "Fastly",
    )
}
