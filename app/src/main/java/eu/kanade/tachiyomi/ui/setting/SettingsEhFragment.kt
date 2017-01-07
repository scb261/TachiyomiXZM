package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.os.Bundle
import android.support.v7.preference.XpPreferenceFragment
import android.view.View
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.plusAssign
import exh.ui.migration.MetadataFetchDialog
import exh.ui.login.LoginActivity
import net.xpece.android.support.preference.Preference
import net.xpece.android.support.preference.SwitchPreference
import uy.kohesive.injekt.injectLazy

/**
 * EH Settings fragment
 */

class SettingsEhFragment : SettingsFragment() {
    companion object {
        fun newInstance(rootKey: String): SettingsEhFragment {
            val args = Bundle()
            args.putString(XpPreferenceFragment.ARG_PREFERENCE_ROOT, rootKey)
            return SettingsEhFragment().apply { arguments = args }
        }
    }

    private val preferences: PreferencesHelper by injectLazy()

    val enableExhentaiPref by lazy {
        findPreference("enable_exhentai") as SwitchPreference
    }

    val migrateLibraryPref by lazy {
        findPreference("ex_migrate_library") as Preference
    }

    val useJpTitlePref by lazy {
        findPreference("use_jp_title") as SwitchPreference
    }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        super.onViewCreated(view, savedState)

        subscriptions += preferences
                .enableExhentai()
                .asObservable().subscribe {
            enableExhentaiPref.isChecked = it
        }

        enableExhentaiPref.setOnPreferenceChangeListener { preference, newVal ->
            newVal as Boolean
            (activity as SettingsActivity).parentFlags = SettingsActivity.FLAG_EH_RECREATE
            if(!newVal) {
                preferences.enableExhentai().set(false)
                true
            } else {
                startActivity(Intent(context, LoginActivity::class.java))
                false
            }
        }

        migrateLibraryPref.setOnPreferenceClickListener {
            MetadataFetchDialog().askMigration(activity)
            true
        }

        useJpTitlePref.setOnPreferenceChangeListener { preference, any ->
            (activity as SettingsActivity).parentFlags = SettingsActivity.FLAG_EH_RECREATE
            true
        }
    }
}
