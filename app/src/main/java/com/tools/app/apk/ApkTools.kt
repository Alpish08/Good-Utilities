package com.tools.app.apk

import com.tools.app.R
import com.tools.app.ui.t
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Environment
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.arsc.chunk.xml.ResXmlElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val base: File,
    val splits: List<File>,
    val sizeBytes: Long,
    val updatedAt: Long,
) {
    val isSplit get() = splits.isNotEmpty()
}

/**
 * APK extractor. A single-APK app is copied as is. An app installed from an App Bundle is a base
 * APK plus config splits (split_config.arm64_v8a.apk, split_config.en.apk, split_config.xxhdpi.apk…);
 * those are merged into one installable APK the way AntiSplit-M does it, with ARSCLib, then signed.
 * All file work runs on Dispatchers.IO.
 */
object ApkTools {
    private const val KEY_ALIAS = "good-tools-apk-signer"
    val FOLDER = com.tools.app.Storage.label(com.tools.app.Storage.Folder.APKs)

    suspend fun listApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        @Suppress("DEPRECATION") pm.getInstalledApplications(0)
            // Apps you installed; system apps (and their Play updates) can't be replaced anyway.
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                runCatching {
                    val pkg = pm.getPackageInfo(info.packageName, 0)
                    val base = File(info.sourceDir)
                    val splits = info.splitSourceDirs.orEmpty().map(::File)
                    InstalledApp(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        versionName = pkg.versionName.orEmpty(),
                        base = base,
                        splits = splits,
                        sizeBytes = base.length() + splits.sumOf { it.length() },
                        updatedAt = pkg.lastUpdateTime,
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Copies a single-APK app to Good Utilities/APKs. Returns the saved file name. */
    suspend fun extract(context: Context, app: InstalledApp): String = withContext(Dispatchers.IO) {
        save(context, app.base, fileName(app, "apk"))
    }

    /** Zips base + splits as .apks (installable with SAI and similar). Keeps Play's signature. */
    suspend fun exportApks(context: Context, app: InstalledApp, onStep: (String) -> Unit): String = withContext(Dispatchers.IO) {
        onStep(t(R.string.apk_packing_parts, app.splits.size + 1))
        val out = File(work(context), "bundle.apks")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.setLevel(0) // APKs are already compressed
            for (f in listOf(app.base) + app.splits) {
                zip.putNextEntry(ZipEntry(if (f == app.base) "base.apk" else f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        onStep(t(R.string.video_saving))
        save(context, out, fileName(app, "apks")).also { out.delete() }
    }

    /**
     * Merges base + splits into one APK (AntiSplit-M logic):
     * 1. load every part as a module and merge resources, native libs and assets into the base,
     * 2. strip the manifest flags that make Android demand the splits,
     * 3. sign with Good Utilities' own key (kept in the Android Keystore).
     * The result has a different signature than Play's, so the original must be uninstalled first.
     */
    suspend fun merge(context: Context, app: InstalledApp, onStep: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val parts = freshParts(context)
        try {
            onStep(t(R.string.apk_copying_parts, app.splits.size + 1))
            app.base.copyTo(File(parts, "base.apk"), overwrite = true)
            app.splits.forEach { it.copyTo(File(parts, it.name), overwrite = true) }
            mergeParts(context, parts, onStep) { fileName(app, "apk") }
        } finally {
            parts.deleteRecursively()
        }
    }

    /**
     * Merges a downloaded split bundle (.apks from SAI/SplitAPKs, .xapk from APKPure, .apkm from
     * APKMirror, or any zip of split APKs) into one installable APK.
     */
    suspend fun mergeFile(context: Context, uri: android.net.Uri, onStep: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val parts = freshParts(context)
        try {
            onStep(t(R.string.apk_unpacking))
            var count = 0
            val input = context.contentResolver.openInputStream(uri) ?: error(t(R.string.audio_couldn_t_open_that_file))
            java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || !e.name.endsWith(".apk", ignoreCase = true)) continue
                    // Flatten folders (XAPK keeps splits at the root, some .apks nest them).
                    val name = e.name.substringAfterLast('/').ifBlank { "part$count.apk" }
                    File(parts, "${count++}_$name").outputStream().use { zip.copyTo(it) }
                }
            }
            when (count) {
                0 -> error(if (bundleName(context, uri).endsWith(".apkm", true))
                    t(R.string.apk_this_apkm_is_encrypted_by_apkmirror_op)
                    else t(R.string.apk_no_apk_files_inside_pick_a_apks_xapk_o))
                1 -> onStep(t(R.string.apk_only_one_apk_inside_re_saving_it))
            }
            mergeParts(context, parts, onStep) { pkg, version ->
                val label = bundleName(context, uri).substringBeforeLast('.').ifBlank { pkg }
                "$label${if (version.isNotBlank() && version !in label) " $version" else ""}".safe() + ".apk"
            }
        } finally {
            parts.deleteRecursively()
        }
    }

    private fun freshParts(context: Context) = File(work(context), "parts").apply { deleteRecursively(); mkdirs() }

    private fun bundleName(context: Context, uri: android.net.Uri): String =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }.orEmpty()

    /** ARSCLib merge → sanitize → sign → save. [name] gets the package name and version. */
    private fun mergeParts(context: Context, parts: File, onStep: (String) -> Unit, name: (String, String) -> String): String {
        val dir = work(context)
        onStep(t(R.string.apk_merging_splits))
        val unsigned = File(dir, "merged-unsigned.apk")
        var pkg = ""; var version = ""
        val bundle = ApkBundle()
        try {
            bundle.loadApkDirectory(parts)
            val merged: ApkModule = bundle.mergeModules()
            try {
                merged.androidManifest?.let { m -> pkg = m.packageName.orEmpty(); version = m.versionName.orEmpty() }
                sanitizeManifest(merged)
                merged.refreshTable()
                merged.refreshManifest()
                merged.setExtractNativeLibs(merged.androidManifest?.isExtractNativeLibs)
                merged.writeApk(unsigned)
            } finally { merged.close() }
        } finally { bundle.close() }
        parts.deleteRecursively()

        onStep(t(R.string.apk_signing))
        val signed = File(dir, "merged.apk")
        sign(unsigned, signed)
        unsigned.delete()

        onStep(t(R.string.video_saving))
        return save(context, signed, name(pkg, version)).also { signed.delete() }
    }

    private fun mergeParts(context: Context, parts: File, onStep: (String) -> Unit, name: () -> String) =
        mergeParts(context, parts, onStep) { _, _ -> name() }

    private fun sanitizeManifest(module: ApkModule) {
        val manifest = module.androidManifest ?: return
        val root = manifest.manifestElement ?: return
        val application = manifest.applicationElement
        for (id in listOf(AndroidManifest.ID_isSplitRequired, AndroidManifest.ID_requiredSplitTypes, AndroidManifest.ID_splitTypes)) {
            root.removeAttributesWithId(id)
            application?.removeAttributesWithId(id)
        }
        for (name in listOf(AndroidManifest.NAME_requiredSplitTypes, AndroidManifest.NAME_splitTypes, AndroidManifest.NAME_isSplitRequired)) {
            root.removeAttributesWithName(name)
        }
        // Play metadata that names the splits (and the stamp of the original signature).
        application?.removeElementsIf { e ->
            e is ResXmlElement && e.equalsName("meta-data") && com.reandroid.arsc.chunk.xml.AndroidManifestBlock.getAndroidNameValue(e).orEmpty().let {
                it.startsWith("com.android.vending.") || it.startsWith("com.android.stamp.") ||
                    it == "com.android.dynamic.apk.fused.modules"
            }
        }
        manifest.refresh()
    }

    // --- Signing

    private fun signingKey(): Pair<PrivateKey, X509Certificate> {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setCertificateSubject(X500Principal("CN=Good Utilities APK Merger"))
                        .setCertificateSerialNumber(BigInteger.ONE)
                        .setCertificateNotBefore(java.util.Date(0))
                        .setCertificateNotAfter(java.util.Date(4102444800000L)) // 2100
                        .build(),
                )
                generateKeyPair()
            }
        }
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey to (entry.certificate as X509Certificate)
    }

    private fun sign(input: File, output: File) {
        val (key, cert) = signingKey()
        val signer = ApkSigner.SignerConfig.Builder("GoodTools", key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    // --- Files

    private fun work(context: Context) = File(context.cacheDir, "apk").apply { mkdirs() }

    fun clearCache(context: Context) { File(context.cacheDir, "apk").deleteRecursively() }

    private fun fileName(app: InstalledApp, ext: String) = "${app.label} ${app.versionName}".safe() + ".$ext"

    private fun String.safe() = trim().replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ")

    private fun save(context: Context, file: File, displayName: String): String =
        com.tools.app.Storage.saveFile(context, com.tools.app.Storage.Folder.APKs, null, displayName, file)
}
