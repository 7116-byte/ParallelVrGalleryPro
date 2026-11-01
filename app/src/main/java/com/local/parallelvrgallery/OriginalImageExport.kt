package com.local.parallelvrgallerypro

import android.content.Context
import android.provider.MediaStore
import java.io.File
import org.json.JSONObject

internal fun readOriginalExportSpec(entry: VrCacheEntry): OriginalImageExportSpec {
    val file = File(entry.paramsPath)
    require(file.isFile && file.length() <= 2L * 1024 * 1024) { "结果参数不存在或损坏 / Recorded parameters missing or corrupt" }
    val json = JSONObject(file.readText(Charsets.UTF_8))
    require(!json.has("depthUpscale") || json.optJSONObject("depthUpscale") != null) { "Invalid recorded depth upscale settings" }
    fun fields(value: JSONObject) = value.keys().asSequence().associateWith { value.get(it).toString() }
    val spec = OriginalImageExportSpec.restore(fields(json), json.optJSONObject("depthUpscale")?.let(::fields),
        entry.photoKey, entry.version, entry.width, entry.height)
    val model = AvailableModels.firstOrNull { it.id == spec.params.depthModel || it.fileName == spec.params.depthModel }
        ?: error("原结果的模型不受支持 / Recorded model unavailable")
    json.optString("modelSha256").takeIf { it.isNotBlank() }?.let {
        require(it.equals(model.sha256, true)) { "Recorded model checksum differs" }
    }
    return spec.copy(params = spec.params.copy(depthModel = model.id))
}

internal fun validateOriginalExportSource(context: Context, photo: PhotoItem) {
    require(photo.kind == MediaKind.IMAGE) { "Only original images can be exported as PNG" }
    if (photo.uri.scheme == "file") {
        val source = File(requireNotNull(photo.uri.path)).canonicalFile
        val cacheRoot = File(context.getExternalFilesDir(null), "vr_cache").canonicalFile
        require(!source.toPath().startsWith(cacheRoot.toPath())) { "原图不可用，不能用预览缓存替代 / Original unavailable; preview cache cannot substitute" }
        require(source.isFile) { "原图不存在 / Original image missing" }
        require(photo.size <= 0 || source.length() == photo.size) { "原图已变更 / Original image changed" }
        require(photo.modifiedTime <= 0 || source.lastModified() / 1000 == photo.modifiedTime) { "原图已变更 / Original image changed" }
    } else {
        context.contentResolver.query(photo.uri, arrayOf(MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_MODIFIED), null, null, null)?.use { cursor ->
            require(cursor.moveToFirst()) { "原图不可访问 / Original image unavailable" }
            require(photo.size <= 0 || cursor.getLong(0) == photo.size) { "原图已变更 / Original image changed" }
            require(photo.modifiedTime <= 0 || cursor.getLong(1) == photo.modifiedTime) { "原图已变更 / Original image changed" }
        } ?: error("原图不可访问 / Original image unavailable")
    }
}
