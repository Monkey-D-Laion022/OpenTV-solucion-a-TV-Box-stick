package com.opentv.app.iptv

/** Resultado medido de un perfil: cuadros que se pidieron y cuadros que el codificador realmente entregó. */
data class PerfResult(val preset: IptvPreset, val seconds: Double, val encodedFps: Double, val drawnFps: Double) {
    /** Sostiene el perfil si entregó al menos 90 % de los FPS pedidos. */
    val sustained: Boolean get() = encodedFps >= preset.fps * 0.9
}

/**
 * Elige el mejor perfil según MEDICIONES (no según lo que declara el códec). Orden de preferencia obligatorio:
 * 1080p60, 720p60 y recién después perfiles menores. Lógica pura.
 */
object PerfAdvisor {
    private val order = listOf(
        IptvPreset.FULL_HD_60, IptvPreset.HD_60, IptvPreset.FULL_HD, IptvPreset.BALANCED,
        IptvPreset.COMPAT_480, IptvPreset.SAFE, IptvPreset.COMPAT_360,
    )

    /** Mejor perfil medido como sostenido; `null` si no hay mediciones (no se inventa nada). */
    fun best(results: Collection<PerfResult>): IptvPreset? =
        order.firstOrNull { p -> results.any { it.preset == p && it.sustained } }

    /** Explicación para la pantalla; separa pedido, dibujado y codificado. */
    fun describe(r: PerfResult): String =
        "${r.preset.shortLabel}: pedidos ${r.preset.fps}, dibujados ${"%.0f".format(r.drawnFps)}, codificados ${"%.0f".format(r.encodedFps)} " +
            "en ${"%.0f".format(r.seconds)} s → " + if (r.sustained) "lo sostiene" else "NO lo sostiene"

    /** Perfiles a medir, en orden, hasta encontrar el primero sostenido (ahorra tiempo al usuario). */
    val sequence: List<IptvPreset> get() = listOf(IptvPreset.FULL_HD_60, IptvPreset.HD_60, IptvPreset.FULL_HD, IptvPreset.BALANCED)
}
