package exh.debug

import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import uy.kohesive.injekt.injectLazy

object DebugFunctions {
    val db: DatabaseHelper by injectLazy()
    val prefs: PreferencesHelper by injectLazy()
    val sourceManager: SourceManager by injectLazy()

    fun addAllMangaInDatabaseToLibrary() {
        db.inTransaction {
            db.lowLevel().executeSQL(RawQuery.builder()
                    .query("""
                            UPDATE ${MangaTable.TABLE}
                                SET ${MangaTable.COL_FAVORITE} = 1
                        """.trimIndent())
                    .affectsTables(MangaTable.TABLE)
                    .build())
        }
    }

    fun countMangaInDatabaseInLibrary() = db.getMangas().executeAsBlocking().count { it.favorite }

    fun countMangaInDatabaseNotInLibrary() = db.getMangas().executeAsBlocking().count { !it.favorite }

    fun countMangaInDatabase() = db.getMangas().executeAsBlocking().size

    fun countMetadataInDatabase() = db.getSearchMetadata().executeAsBlocking().size

    fun countMangaInLibraryWithMissingMetadata() = db.getMangas().executeAsBlocking().count {
        it.favorite && db.getSearchMetadataForManga(it.id!!).executeAsBlocking() == null
    }

    fun clearSavedSearches() = prefs.eh_savedSearches().set(emptySet())

    fun listAllSources() = sourceManager.getCatalogueSources().map {
        "${it.id}: ${it.name} (${it.lang.toUpperCase()})"
    }.joinToString("\n")

    fun convertAllEhentaiGalleriesToExhentai() = convertSources(EH_SOURCE_ID, EXH_SOURCE_ID)

    fun convertAllExhentaiGalleriesToEhentai() = convertSources(EXH_SOURCE_ID, EH_SOURCE_ID)

    private fun convertSources(from: Long, to: Long) {
        db.lowLevel().executeSQL(RawQuery.builder()
                .query("""
                            UPDATE ${MangaTable.TABLE}
                                SET ${MangaTable.COL_SOURCE} = $to
                                WHERE ${MangaTable.COL_SOURCE} = $from
                        """.trimIndent())
                .affectsTables(MangaTable.TABLE)
                .build())
    }
}