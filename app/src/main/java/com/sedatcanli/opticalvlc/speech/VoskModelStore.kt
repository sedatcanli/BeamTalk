package com.sedatcanli.opticalvlc.speech

import android.content.Context
import com.sedatcanli.opticalvlc.core.Language
import org.vosk.Model
import org.vosk.android.StorageService
import java.io.File

/**
 * Owns the single, very large Vosk [Model] instance.
 *
 * Models are bundled in `assets/models/<lang>` and unpacked on first use to
 * the app's external files directory. An externally downloaded model placed
 * in `files/vosk-models/<lang-lowercase>/` overrides the bundled one, which
 * makes it possible to swap in a bigger model without rebuilding the APK.
 *
 * Only one language is kept in memory at a time: the small Turkish and
 * English models are ~50 MB of RAM each once loaded.
 */
class VoskModelStore(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var model: Model? = null

    @Volatile
    private var loadedLanguage: Language? = null

    @Synchronized
    fun getModel(language: Language): Model {
        val existing = model
        if (existing != null && loadedLanguage == language) {
            return existing
        }
        existing?.close()
        model = null
        loadedLanguage = null

        val assetDir = language.sttAssetDir
            ?: throw IllegalStateException("${language.displayName} için STT modeli yok")

        val external = findExternalModel(language)
        val loaded = if (external != null) {
            Model(external.absolutePath)
        } else {
            val targetDir = "vosk-${language.name.lowercase()}"
            val path = StorageService.sync(appContext, assetDir, targetDir)
            Model(path)
        }

        model = loaded
        loadedLanguage = language
        return loaded
    }

    @Synchronized
    fun close() {
        model?.close()
        model = null
        loadedLanguage = null
    }

    private fun findExternalModel(language: Language): File? {
        val dir = File(appContext.filesDir, "vosk-models/${language.name.lowercase()}")
        if (!dir.isDirectory) return null
        return when {
            File(dir, "final.mdl").isFile -> dir
            File(dir, "am/final.mdl").isFile -> dir
            else -> null
        }
    }
}
