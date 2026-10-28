package de.crunchymate.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.crunchymate.R

/**
 * Wählbare App-Sprachen. Boykisser nutzt den Locale-Tag en-Qaau: Qaaa bis Qabx ist der für private Zwecke
 * reservierte Schriftbereich (ISO 15924), das Ressourcenverzeichnis heißt daher values-b+en+Qaau.
 * Datums- und Zahlenformate folgen dem Sprachanteil und bleiben damit englisch.
 */
enum class AppLanguage(
    val languageTag: String?,
    @StringRes val nameRes: Int,
    @DrawableRes val flagRes: Int?,
) {
    SYSTEM(null, R.string.settings_language_system, null),
    GERMAN("de", R.string.language_name_de, R.drawable.ic_flag_de),
    ENGLISH("en", R.string.language_name_en, R.drawable.ic_flag_us),
    BOYKISSER("en-Qaau", R.string.language_name_boykisser, R.drawable.ic_flag_boykisser),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.languageTag == tag } ?: SYSTEM
    }
}

/** Die Wahl hält das System selbst fest (LocaleManager, auch im Backup); die App speichert sie nicht zusätzlich. */
fun Context.currentAppLanguage(): AppLanguage {
    val locales = getSystemService(LocaleManager::class.java).applicationLocales
    return AppLanguage.fromTag(if (locales.isEmpty) null else locales[0].toLanguageTag())
}

fun Context.applyAppLanguage(language: AppLanguage) {
    val locales = language.languageTag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
    getSystemService(LocaleManager::class.java).applicationLocales = locales
}
