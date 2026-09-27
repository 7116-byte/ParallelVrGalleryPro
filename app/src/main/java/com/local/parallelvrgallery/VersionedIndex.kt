package com.local.parallelvrgallerypro

internal class VersionedIndex<T>(
    private val keyOf: (T) -> String,
    private val versionOf: (T) -> String,
    private val timestampOf: (T) -> Long,
) {
    private val entries = linkedMapOf<Pair<String, String>, T>()
    private val sizes = mutableMapOf<Pair<String, String>, Long>()
    private val latest = mutableMapOf<String, T>()
    private val buckets = mutableMapOf<String, MutableMap<String, T>>()
    private val changes = mutableMapOf<Pair<String, String>, Long>()
    private var revision = 0L
    @Synchronized fun scanRevision(): Long = revision

    @Synchronized fun put(entry: T, bytes: Long) {
        val key = keyOf(entry) to versionOf(entry)
        entries[key] = entry
        sizes[key] = bytes
        changes[key] = ++revision
        val bucket = buckets.getOrPut(key.first) { mutableMapOf() }
        bucket[key.second] = entry
        latest[key.first] = bucket.values.maxBy(timestampOf)
    }
    @Synchronized fun get(key: String, version: String? = null): T? =
        if (version != null) entries[key to version]
        else latest[key]

    @Synchronized fun all(): List<T> = entries.values.sortedByDescending(timestampOf)
    @Synchronized fun removeWhere(predicate: (T) -> Boolean) {
        val keys = entries.filterValues(predicate).keys
        keys.forEach {
            entries.remove(it); sizes.remove(it)
            buckets[it.first]?.remove(it.second)
            changes[it] = ++revision
        }
        keys.map { it.first }.distinct().forEach { key ->
            val newest = buckets[key]?.values?.maxByOrNull(timestampOf)
            if (newest == null) latest.remove(key) else latest[key] = newest
        }
    }
    @Synchronized fun clear() { removeWhere { true }; buckets.clear(); latest.clear() }
    @Synchronized fun replaceFromScan(scanned: List<Pair<T, Long>>, since: Long) {
        val changed = changes.filterValues { it > since }.keys
        val preserved = entries.filterKeys { it in changed }.map { (key, value) -> value to (sizes[key] ?: 0L) }
        entries.clear(); sizes.clear(); buckets.clear(); latest.clear()
        val merged = scanned.filter { (entry, _) -> (keyOf(entry) to versionOf(entry)) !in changed } + preserved
        merged.forEach { (entry, bytes) ->
            val key = keyOf(entry) to versionOf(entry)
            entries[key] = entry
            sizes[key] = bytes
            buckets.getOrPut(key.first) { mutableMapOf() }[key.second] = entry
        }
        buckets.forEach { (key, values) -> latest[key] = values.values.maxBy(timestampOf) }
        changes.entries.removeAll { it.value <= since }
    }
    @Synchronized fun summaries(): Map<String, Pair<Int, Long>> = entries.entries
        .groupBy { it.key.second }
        .mapValues { (_, group) -> group.size to group.sumOf { sizes[it.key] ?: 0L } }
}
