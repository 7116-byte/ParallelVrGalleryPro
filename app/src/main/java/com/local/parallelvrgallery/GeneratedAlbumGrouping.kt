package com.local.parallelvrgallerypro

// Image copies have always used the first eight lowercase hex digits of a UUID.
// Match the writer's trailing parameters and encoder so other version fields stay intact.
private val imageCopySuffix = Regex(
    """_force[01](?:_du1_m[0-9]+_i[nlc]_o(?:sr|rs))?(?:_eq1050)?(?:_di1)?(_take[0-9a-f]{8})_eyesJpegQ100V1\z""",
)

/** Presentation key only. The complete version continues to identify each saved result. */
internal fun generatedAlbumKey(version: String): String {
    val copyToken = imageCopySuffix.find(version)?.groups?.get(1) ?: return version
    return version.removeRange(copyToken.range)
}
