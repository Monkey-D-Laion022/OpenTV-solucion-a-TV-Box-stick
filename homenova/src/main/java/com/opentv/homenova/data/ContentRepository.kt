package com.opentv.homenova.data

import com.opentv.homenova.core.LauncherNotification
import com.opentv.homenova.core.MediaEntry
import com.opentv.homenova.core.NotificationKind

/**
 * Offline demo catalogue with original titles and artwork generated at runtime.
 *
 * The video URLs point to Creative Commons sample films. They are demo sources
 * only: replace [MediaEntry.videoUrl] with your own catalogue or a media backend.
 */
class ContentRepository {

    val catalogue: List<MediaEntry> = buildList {
        addAll(
            listOf(
                MediaEntry(
                    id = "neon-horizon",
                    title = "Neon Horizon",
                    subtitle = "Temporada 2 · Episodio 4",
                    description = "Una cartógrafa recorre una ciudad costera que cambia de forma cada noche. " +
                        "Cada mapa que dibuja la acerca a la verdad sobre la señal que brilla en el mar.",
                    year = 2025,
                    rating = "16+",
                    durationMinutes = 48,
                    genres = listOf("Ciencia ficción", "Misterio"),
                    paletteSeed = 7,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    progress = 0.42f,
                    badge = "NUEVO",
                ),
                MediaEntry(
                    id = "last-signal",
                    title = "The Last Signal",
                    subtitle = "Película · 2024",
                    description = "Cuando todas las radios del planeta emiten la misma voz, un técnico de una " +
                        "estación remota decide responder. Lo que contesta no es humano.",
                    year = 2024,
                    rating = "13+",
                    durationMinutes = 112,
                    genres = listOf("Thriller", "Ciencia ficción"),
                    paletteSeed = 21,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    badge = "DESTACADO",
                ),
                MediaEntry(
                    id = "orbital-drift",
                    title = "Orbital Drift",
                    subtitle = "Serie · Temporada 1",
                    description = "La tripulación de una estación de mantenimiento descubre que su órbita " +
                        "no es natural. Reparar el presente exige entender un mensaje del futuro.",
                    year = 2025,
                    rating = "16+",
                    durationMinutes = 52,
                    genres = listOf("Ciencia ficción", "Drama"),
                    paletteSeed = 34,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    progress = 0.18f,
                ),
                MediaEntry(
                    id = "paper-cities",
                    title = "Paper Cities",
                    subtitle = "Documental · 2023",
                    description = "Arquitectos, ilustradores y vecinos imaginan ciudades que caben en una " +
                        "libreta y, sin embargo, pueden cambiar la vida de un barrio entero.",
                    year = 2023,
                    rating = "TP",
                    durationMinutes = 74,
                    genres = listOf("Documental"),
                    paletteSeed = 48,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                ),
                MediaEntry(
                    id = "night-circuit",
                    title = "Night Circuit",
                    subtitle = "Película · 2025",
                    description = "Una piloto clandestina acepta una última carrera por las azoteas de " +
                        "una megalópolis donde la noche nunca se apaga.",
                    year = 2025,
                    rating = "13+",
                    durationMinutes = 98,
                    genres = listOf("Acción", "Aventura"),
                    paletteSeed = 63,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    badge = "4K",
                ),
                MediaEntry(
                    id = "deep-field",
                    title = "Deep Field",
                    subtitle = "Serie · Temporada 1",
                    description = "Un telescopio espacial captura una galaxia que no debería existir. " +
                        "El equipo que la estudia empieza a recordar vidas que nunca vivió.",
                    year = 2024,
                    rating = "16+",
                    durationMinutes = 45,
                    genres = listOf("Misterio", "Drama"),
                    paletteSeed = 78,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    progress = 0.67f,
                ),
                MediaEntry(
                    id = "glass-garden",
                    title = "Glass Garden",
                    subtitle = "Película · 2022",
                    description = "Dos hermanas heredan un invernadero que solo florece con las historias " +
                        "que su familia lleva décadas evitando contar.",
                    year = 2022,
                    rating = "7+",
                    durationMinutes = 101,
                    genres = listOf("Drama", "Fantasía"),
                    paletteSeed = 92,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                ),
                MediaEntry(
                    id = "quiet-machines",
                    title = "Quiet Machines",
                    subtitle = "Serie · Temporada 2",
                    description = "En un pueblo donde los electrodomésticos tienen memoria, una reparadora " +
                        "descubre que la lavadora de su madre guarda una conversación imposible.",
                    year = 2025,
                    rating = "13+",
                    durationMinutes = 38,
                    genres = listOf("Comedia", "Ciencia ficción"),
                    paletteSeed = 105,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                ),
                MediaEntry(
                    id = "solar-archive",
                    title = "Solar Archive",
                    subtitle = "Documental · 2024",
                    description = "Una biblioteca solar recorre el desierto conservando libros que el calor " +
                        "está borrando lentamente. Su guardián tiene una idea peligrosa: copiarlos todos.",
                    year = 2024,
                    rating = "TP",
                    durationMinutes = 66,
                    genres = listOf("Documental", "Aventura"),
                    paletteSeed = 118,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
                ),
                MediaEntry(
                    id = "echoes-of-north",
                    title = "Echoes of North",
                    subtitle = "Película · 2021",
                    description = "Un farero retirado vuelve a la isla donde creció para desmontar la " +
                        "estación. Cada noche, la luz sigue encendiéndose sola.",
                    year = 2021,
                    rating = "13+",
                    durationMinutes = 109,
                    genres = listOf("Drama", "Misterio"),
                    paletteSeed = 131,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                ),
                MediaEntry(
                    id = "after-the-rain",
                    title = "After the Rain",
                    subtitle = "Serie · Temporada 1",
                    description = "Cinco desconocidos comparten un refugio durante una tormenta que dura " +
                        "semanas. Cuando escampa, uno de ellos no recuerda quién era.",
                    year = 2023,
                    rating = "16+",
                    durationMinutes = 41,
                    genres = listOf("Drama", "Suspense"),
                    paletteSeed = 146,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                ),
                MediaEntry(
                    id = "small-worlds",
                    title = "Small Worlds",
                    subtitle = "Infantil · 2024",
                    description = "Una familia de exploradores diminutos vive dentro de una maqueta de " +
                        "tren que, todas las mañanas, amanece en un lugar distinto.",
                    year = 2024,
                    rating = "TP",
                    durationMinutes = 24,
                    genres = listOf("Infantil", "Aventura"),
                    paletteSeed = 160,
                    videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                ),
            ),
        )
    }

    val featured: List<MediaEntry>
        get() = catalogue.filter { it.id in setOf("last-signal", "neon-horizon", "night-circuit") }

    val continueWatching: List<MediaEntry>
        get() = catalogue.filter { it.progress > 0f }

    val trending: List<MediaEntry>
        get() = catalogue.filter { it.progress == 0f }.take(8)

    val recommendations: List<MediaEntry>
        get() = catalogue.sortedByDescending { it.year }.take(8)

    val liveChannels: List<MediaEntry> = listOf(
        live("nova-news", "OpenTV Noticias 24", "Última hora", "Informativo continuo con corresponsales en directo.", 171),
        live("nova-sports", "OpenTV Deportes", "Fútbol · En directo", "Cobertura de la jornada y análisis posterior.", 184),
        live("nova-cinema", "OpenTV Cine", "Ciclo: futuros posibles", "Una selección de ciencia ficción contemporánea.", 197),
        live("nova-kids", "OpenTV Kids", "Aventuras", "Programación familiar sin interrupciones.", 210),
        live("nova-docs", "OpenTV Docs", "Planeta azul", "Documentales de naturaleza y exploración.", 223),
        live("nova-music", "OpenTV Música", "Sesión acústica", "Conciertos íntimos y descubrimientos musicales.", 236),
    )

    val notifications: List<LauncherNotification> = listOf(
        LauncherNotification(
            id = 1,
            title = "Nuevo episodio disponible",
            body = "Neon Horizon · T2 E5 ya está listo para ver.",
            timestamp = System.currentTimeMillis() - 12 * 60_000,
            kind = NotificationKind.RECOMMENDATION,
        ),
        LauncherNotification(
            id = 2,
            title = "Actualización completada",
            body = "OpenTV se ha actualizado con mejoras de navegación.",
            timestamp = System.currentTimeMillis() - 3 * 60 * 60_000,
            kind = NotificationKind.UPDATE,
        ),
        LauncherNotification(
            id = 3,
            title = "Continúa donde lo dejaste",
            body = "Deep Field · te quedan 14 minutos del episodio 3.",
            timestamp = System.currentTimeMillis() - 26 * 60 * 60_000,
            kind = NotificationKind.REMINDER,
        ),
        LauncherNotification(
            id = 4,
            title = "Entrada HDMI 2 conectada",
            body = "Se ha detectado una nueva fuente de vídeo.",
            timestamp = System.currentTimeMillis() - 2 * 24 * 60 * 60_000,
            kind = NotificationKind.SYSTEM,
        ),
    )

    fun findMedia(id: String): MediaEntry? = (catalogue + liveChannels).firstOrNull { it.id == id }

    private fun live(
        id: String,
        title: String,
        program: String,
        description: String,
        seed: Int,
    ) = MediaEntry(
        id = id,
        title = title,
        subtitle = "En directo",
        description = description,
        year = 2026,
        rating = "TP",
        durationMinutes = 0,
        genres = listOf("En directo"),
        paletteSeed = seed,
        videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        badge = "EN VIVO",
        isLive = true,
        liveProgram = program,
    )
}
