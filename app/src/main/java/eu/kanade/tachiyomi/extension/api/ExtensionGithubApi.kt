package eu.kanade.tachiyomi.extension.api

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.source.BlacklistedSources
import kotlinx.serialization.Serializable
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.concurrent.TimeUnit

internal class ExtensionGithubApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            val extensions = networkService.client
                .newCall(GET("${REPO_URL_PREFIX}index.min.json"))
                .await()
                .parseAs<List<ExtensionJsonObject>>()
                .toExtensions() /* SY --> */ + preferences.extensionRepos()
                    .get()
                    .flatMap { repoPath ->
			            val url = "$BASE_URL$repoPath/repo/"
			            networkService.client
			                .newCall(GET("${url}index.min.json"))
			                .await()
			                .parseAs<List<ExtensionJsonObject>>()
			                .toExtensions(url)
			        }
			        // SY <--

            // Sanity check - a small number of extensions probably means something broke
            // with the repo generator
            if (extensions.size < 100) {
                throw Exception()
            }

            extensions
        }
    }

    suspend fun checkForUpdates(context: Context): List<Extension.Installed> {
        // Limit checks to once a day at most
        if (Date().time < preferences.lastExtCheck().get() + TimeUnit.DAYS.toMillis(1)) {
            return emptyList()
        }

        val extensions = findExtensions()

        preferences.lastExtCheck().set(Date().time)

        // SY -->
        val blacklistEnabled = preferences.enableSourceBlacklist().get()
        // SY <--

        val installedExtensions = ExtensionLoader.loadExtensions(context)
            .filterIsInstance<LoadResult.Success>()
            .map { it.extension }
            // SY -->
            .filterNot { it.isBlacklisted(blacklistEnabled) }
        // SY <--

        val extensionsWithUpdate = mutableListOf<Extension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdate = availableExt.versionCode > installedExt.versionCode
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(/* SY --> */ repoUrl: String = REPO_URL_PREFIX /* SY <-- */): List<Extension.Available> {
        return this
            .filter {
                val libVersion = it.version.substringBeforeLast('.').toDouble()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    apkName = it.apk,
                    iconUrl = "${/* SY --> */ repoUrl /* SY <-- */}icon/${it.apk.replace(".apk", ".png")}",
                    // SY -->
                    repoUrl = repoUrl
                    // SY <--
                )
            }
    }

    fun getApkUrl(extension: Extension.Available): String {
        return /* SY --> */ "${extension.repoUrl}/apk/${extension.apkName}" /* SY <-- */
    }

    // SY -->
    private fun Extension.isBlacklisted(
        blacklistEnabled: Boolean = preferences.enableSourceBlacklist().get()
    ): Boolean {
        return pkgName in BlacklistedSources.BLACKLISTED_EXTENSIONS && blacklistEnabled
    }
    // SY <--
}

const val BASE_URL = "https://raw.githubusercontent.com/"
const val REPO_URL_PREFIX = "${BASE_URL}tachiyomiorg/tachiyomi-extensions/repo/"

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val version: String,
    val code: Long,
    val lang: String,
    val nsfw: Int,
)
