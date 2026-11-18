package com.wavelength.player.data.stats

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.wavelength.player.data.db.EventRow
import com.wavelength.player.data.db.Outcome
import com.wavelength.player.data.db.PlayEventEntity
import com.wavelength.player.data.db.SongEntity
import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.db.WavelengthDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class StatsRepository(private val db: WavelengthDatabase) {
    private val dao = db.statsDao()

    val allStats: Flow<List<SongStatsEntity>> = dao.observeAllStats()

    val favoriteIds: Flow<Set<Long>> = allStats.map { list -> list.filter { it.favorite }.sortedByDescending { it.favoritedAt }.mapTo(LinkedHashSet()) { it.songId } }

    fun recent(limit: Int = 300) = dao.observeRecent(limit)
    fun eventsForSong(songId: Long) = dao.observeForSong(songId, 200)

    /** Saves one listen and updates the song's totals and affinity. */
    suspend fun record(songId: Long, startedAt: Long, endedAt: Long, listenedMs: Long, durationMs: Long, endedNaturally: Boolean, replay: Boolean) {
        val outcome = ListeningRules.classify(listenedMs, durationMs, endedNaturally) ?: return
        db.withTransaction {
            dao.insertEvent(PlayEventEntity(0, songId, startedAt, endedAt, listenedMs, durationMs, outcome, replay))
            val s = dao.stats(songId) ?: SongStatsEntity(songId)
            val delta = Affinity.delta(outcome, listenedMs, durationMs, replay)
            dao.upsertStats(
                s.copy(
                    plays = s.plays + if (outcome == Outcome.PLAYED) 1 else 0,
                    skips = s.skips + if (outcome == Outcome.SKIPPED) 1 else 0,
                    partials = s.partials + if (outcome == Outcome.PARTIAL) 1 else 0,
                    replays = s.replays + if (replay && outcome == Outcome.PLAYED) 1 else 0,
                    listenMs = s.listenMs + listenedMs,
                    firstPlayedAt = s.firstPlayedAt ?: startedAt,
                    lastPlayedAt = startedAt,
                    affinityRaw = Affinity.update(s.affinityRaw, s.affinityUpdatedAt, endedAt, delta),
                    affinityUpdatedAt = endedAt,
                ),
            )
        }
    }

    suspend fun setFavorite(songId: Long, favorite: Boolean) {
        db.withTransaction {
            val s = dao.stats(songId) ?: SongStatsEntity(songId)
            dao.upsertStats(s.copy(favorite = favorite, favoritedAt = if (favorite) System.currentTimeMillis() else null))
        }
    }

    /** Deletes listening history (favourites are kept). */
    suspend fun resetHistory() = db.withTransaction {
        dao.deleteEvents()
        dao.resetTotals()
    }

    /** Writes every listen as a CSV spreadsheet. */
    suspend fun exportCsv(resolver: ContentResolver, target: Uri, songsById: Map<Long, SongEntity>) = withContext(Dispatchers.IO) {
        val events = dao.allEvents()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        resolver.openOutputStream(target, "wt")?.bufferedWriter()?.use { w ->
            w.write("started,title,artist,album,outcome,listened_seconds,song_seconds,completion_percent,replay\n")
            events.forEach { e ->
                val song = songsById[e.songId]
                val outcome = when (e.outcome) { Outcome.PLAYED -> "played"; Outcome.SKIPPED -> "skipped"; else -> "partial" }
                w.write(
                    listOf(
                        Instant.ofEpochMilli(e.startedAt).atZone(ZoneId.systemDefault()).toLocalDateTime().format(fmt),
                        csv(song?.title ?: "(deleted song ${e.songId})"), csv(song?.artist.orEmpty()), csv(song?.album.orEmpty()),
                        outcome, (e.listenedMs / 1000).toString(), (e.durationMs / 1000).toString(),
                        (e.completion * 100).toInt().toString(), e.replay.toString(),
                    ).joinToString(",") + "\n",
                )
            }
        } ?: error("Couldn't write the file")
        events.size
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun snapshot(rangeFlow: Flow<StatsRange>, library: Flow<List<SongEntity>>): Flow<StatsSnapshot> =
        rangeFlow.flatMapLatest { range ->
            val from = rangeStart(range, System.currentTimeMillis())
            combine(dao.observeEventsSince(from), allStats, library, dao.observeFirstEventAt()) { events, stats, songs, first ->
                compute(range, from, events, stats, songs, first)
            }
        }.flowOn(Dispatchers.Default)

    companion object {
        private val zone: ZoneId get() = ZoneId.systemDefault()
        private const val DAY = 86_400_000L

        fun rangeStart(range: StatsRange, now: Long): Long {
            val today = LocalDate.now(zone)
            val start = when (range) {
                StatsRange.Week -> today.minusDays(6)
                StatsRange.Month -> today.minusDays(29)
                StatsRange.Year -> today.withDayOfMonth(1).minusMonths(11)
                StatsRange.All -> return 0
            }
            return start.atStartOfDay(zone).toInstant().toEpochMilli().coerceAtMost(now)
        }

        fun compute(
            range: StatsRange,
            from: Long,
            events: List<EventRow>,
            stats: List<SongStatsEntity>,
            songs: List<SongEntity>,
            firstEventAt: Long?,
            now: Long = System.currentTimeMillis(),
        ): StatsSnapshot {
            val songsById = songs.associateBy { it.id }
            val statsById = stats.associateBy { it.songId }
            fun affinityOf(id: Long): Int? = statsById[id]?.takeIf { it.listens > 0 || it.favorite }
                ?.let { Affinity.score(it.affinityRaw, it.affinityUpdatedAt, it.favorite, now) }

            // ---- per song, in range ----
            class Acc { var plays = 0; var skips = 0; var partials = 0; var listen = 0L; var completionSum = 0.0; var last = 0L }
            val perSong = HashMap<Long, Acc>()
            val hourly = LongArray(24)
            val days = HashSet<LocalDate>()
            events.forEach { e ->
                val a = perSong.getOrPut(e.songId) { Acc() }
                when (e.outcome) { Outcome.PLAYED -> a.plays++; Outcome.SKIPPED -> a.skips++; else -> a.partials++ }
                a.listen += e.listenedMs
                a.completionSum += if (e.durationMs > 0) (e.listenedMs.toDouble() / e.durationMs).coerceIn(0.0, 1.0) else 0.0
                a.last = maxOf(a.last, e.startedAt)
                val t = Instant.ofEpochMilli(e.startedAt).atZone(zone)
                hourly[t.hour] += e.listenedMs
                days += t.toLocalDate()
            }
            fun songStat(id: Long, a: Acc?): SongStat? {
                val song = songsById[id] ?: return null
                val s = statsById[id]
                val n = a?.let { it.plays + it.skips + it.partials } ?: 0
                return SongStat(
                    song, a?.plays ?: 0, a?.skips ?: 0, a?.partials ?: 0, a?.listen ?: 0,
                    if (n > 0) (a!!.completionSum / n).toFloat() else 0f,
                    affinityOf(id), s?.favorite == true, s?.lastPlayedAt,
                )
            }
            val rangeSongs = perSong.mapNotNull { (id, a) -> songStat(id, a) }

            // ---- all-time per song (Songs table, affinity lists) ----
            val allTime = songs.map { song ->
                val s = statsById[song.id]
                val n = s?.listens ?: 0
                SongStat(
                    song, s?.plays ?: 0, s?.skips ?: 0, s?.partials ?: 0, s?.listenMs ?: 0,
                    if (n > 0 && song.durationMs > 0) ((s!!.listenMs.toDouble() / n) / song.durationMs).coerceIn(0.0, 1.0).toFloat() else 0f,
                    affinityOf(song.id), s?.favorite == true, s?.lastPlayedAt,
                )
            }

            // ---- groups ----
            fun group(key: (SongEntity) -> String?, subtitle: (SongEntity) -> String): List<GroupStat> =
                rangeSongs.filter { key(it.song) != null }.groupBy { key(it.song)!! }.map { (name, list) ->
                    val cover = list.maxBy { it.listenMs }.song
                    GroupStat(name, subtitle(cover), list.sumOf { it.listenMs }, list.sumOf { it.plays }, cover)
                }.sortedByDescending { it.listenMs }.take(10)

            // ---- activity buckets ----
            val (buckets, unit) = buckets(range, from, firstEventAt, events)

            // ---- streak: consecutive days with listening, ending today or yesterday ----
            var streak = 0
            var day = LocalDate.now(zone)
            if (day !in days) day = day.minusDays(1)
            while (day in days) { streak++; day = day.minusDays(1) }

            val totalListens = events.size
            val thirtyDaysAgo = now - 30 * DAY
            return StatsSnapshot(
                range = range,
                listenMs = events.sumOf { it.listenedMs },
                plays = events.count { it.outcome == Outcome.PLAYED },
                skips = events.count { it.outcome == Outcome.SKIPPED },
                partials = events.count { it.outcome == Outcome.PARTIAL },
                distinctSongs = perSong.size,
                avgCompletion = if (totalListens == 0) 0f else (perSong.values.sumOf { it.completionSum } / totalListens).toFloat(),
                activeDays = days.size,
                currentStreakDays = streak,
                buckets = buckets,
                bucketUnit = unit,
                hourly = hourly,
                topSongs = rangeSongs.filter { it.plays > 0 }.sortedWith(compareByDescending<SongStat> { it.plays }.thenByDescending { it.listenMs }).take(25),
                topArtists = group({ it.artist }, { "Artist" }),
                topAlbums = group({ "${it.album}\u0000${it.albumId}" }, { it.artist }).map { it.copy(name = it.name.substringBefore('\u0000')) },
                topGenres = group({ it.genre }, { "Genre" }),
                mostSkipped = rangeSongs.filter { it.skips > 0 }.sortedWith(compareByDescending<SongStat> { it.skips }.thenByDescending { it.skipRate }).take(25),
                onYourWavelength = allTime.filter { it.affinity != null && it.affinity > 50 }.sortedByDescending { it.affinity }.take(25),
                forgottenFavorites = allTime.filter { (it.affinity ?: 0) >= 60 && (it.lastPlayedAt ?: 0) < thirtyDaysAgo }
                    .sortedByDescending { it.affinity }.take(25),
                allSongs = allTime,
                neverPlayed = allTime.count { it.listens == 0 },
                librarySize = songs.size,
            )
        }

        private fun buckets(range: StatsRange, from: Long, firstEventAt: Long?, events: List<EventRow>): Pair<List<Bucket>, String> {
            val today = LocalDate.now(zone)
            val byDay = events.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            fun dayStart(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
            return when (range) {
                StatsRange.Week, StatsRange.Month -> {
                    val n = if (range == StatsRange.Week) 7 else 30
                    List(n) { i ->
                        val d = today.minusDays((n - 1 - i).toLong())
                        val ev = byDay[d].orEmpty()
                        val label = if (range == StatsRange.Week) d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        else "${d.monthValue}/${d.dayOfMonth}"
                        Bucket(label, dayStart(d), ev.sumOf { it.listenedMs }, ev.count { it.outcome == Outcome.PLAYED })
                    } to "day"
                }
                StatsRange.Year, StatsRange.All -> {
                    val start = if (range == StatsRange.Year) Instant.ofEpochMilli(from).atZone(zone).toLocalDate().withDayOfMonth(1)
                    else (firstEventAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today).withDayOfMonth(1)
                    val months = ChronoUnit.MONTHS.between(start, today.withDayOfMonth(1)).toInt().coerceIn(0, 59) + 1
                    val first = today.withDayOfMonth(1).minusMonths((months - 1).toLong())
                    val byMonth = events.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().withDayOfMonth(1) }
                    List(months) { i ->
                        val m = first.plusMonths(i.toLong())
                        val ev = byMonth[m].orEmpty()
                        val label = m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + if (m.monthValue == 1 || i == 0) " ${m.year % 100}" else ""
                        Bucket(label, dayStart(m), ev.sumOf { it.listenedMs }, ev.count { it.outcome == Outcome.PLAYED })
                    } to "month"
                }
            }
        }
    }
}
