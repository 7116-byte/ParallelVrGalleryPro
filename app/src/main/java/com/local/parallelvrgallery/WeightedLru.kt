package com.local.parallelvrgallerypro

internal class WeightedLru<K, V>(val budget: Long, private val weigh: (V) -> Long) {
    private val values = LinkedHashMap<K, V>(16, 0.75f, true)
    var bytes: Long = 0
        private set
    init { require(budget > 0) }
    @Synchronized operator fun get(key: K): V? = values[key]
    @Synchronized fun put(key: K, value: V) {
        values.remove(key)?.let { bytes -= weigh(it) }
        val size = weigh(value).coerceAtLeast(0)
        if (size > budget) return
        values[key] = value
        bytes += size
        while (bytes > budget) {
            val oldest = values.entries.iterator()
            val entry = oldest.next()
            bytes -= weigh(entry.value)
            oldest.remove()
        }
    }
    @Synchronized fun clear() { values.clear(); bytes = 0 }
}
