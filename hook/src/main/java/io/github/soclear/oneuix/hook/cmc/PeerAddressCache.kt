package io.github.soclear.oneuix.hook.cmc

internal class PeerAddressCache(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val ttlMillis: Long = 300_000,
    private val capacity: Int = 64,
) {
    private data class Peer(val address: String, val lastSeen: Long)
    private val peers = linkedMapOf<String, Peer>()

    @Synchronized
    fun remember(deviceId: String?, address: String) {
        val ip = validAddress(address) ?: return
        prune()
        val id = deviceId?.trim()?.removePrefix("urn:duid:")?.takeIf { it.isNotEmpty() }
        val key = id?.let { "device:$it" } ?: "address:$ip"
        if (id != null) peers.remove("address:$ip")
        peers[key] = Peer(ip, nowMillis())
        while (peers.size > capacity) {
            peers.remove(peers.minBy { it.value.lastSeen }.key)
        }
    }

    @Synchronized
    fun resolve(
        manualAddress: String = "",
        deviceId: String? = null,
        localAddress: String? = null,
        currentAddress: String? = null,
    ): String? {
        prune()
        val local = validAddress(localAddress)
        if (manualAddress.isNotBlank()) return validAddress(manualAddress)?.takeUnless { it == local }
        val id = deviceId?.trim()?.removePrefix("urn:duid:")?.takeIf { it.isNotEmpty() }
        if (id != null) return peers["device:$id"]?.address?.takeUnless { it == local }
        val addresses = addresses(local)
        val current = validAddress(currentAddress)
        return current?.takeIf { it in addresses } ?: addresses.singleOrNull()
    }

    @Synchronized
    fun addresses(localAddress: String? = null): List<String> {
        prune()
        val local = validAddress(localAddress)
        return peers.values.map { it.address }.filterNot { it == local }.distinct().sorted()
    }

    private fun prune() {
        val now = nowMillis()
        peers.entries.removeAll { now - it.value.lastSeen >= ttlMillis }
    }

    companion object {
        fun validAddress(value: String?): String? {
            val parts = value?.trim()?.split('.') ?: return null
            if (parts.size != 4 || parts.any { it.isEmpty() || it.any { char -> char !in '0'..'9' } }) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            if (numbers.any { it !in 0..255 } || numbers[0] == 0 || numbers[0] == 127 || numbers[0] >= 224) return null
            return numbers.joinToString(".")
        }
    }
}
