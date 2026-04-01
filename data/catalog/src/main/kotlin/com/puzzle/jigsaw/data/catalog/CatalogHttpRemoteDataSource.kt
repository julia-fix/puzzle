package com.puzzle.jigsaw.data.catalog

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 20_000

private val remoteAssetsBaseUrl = BuildConfig.PUZZLE_ASSETS_BASE_URL.trimEnd('/')
private val remoteManifestUrl = "$remoteAssetsBaseUrl/manifest.json"
private val remoteImagesBaseUrl = "$remoteAssetsBaseUrl/images/"

internal class CatalogHttpRemoteDataSource : CatalogRemoteDataSource {
    override fun fetchManifest(): RemoteCatalogManifest? {
        val manifestJson = downloadText(remoteManifestUrl) ?: return null
        val manifest = parseCatalogManifest(manifestJson) ?: return null
        return RemoteCatalogManifest(
            manifestJson = manifestJson,
            manifest = manifest,
        )
    }

    fun downloadImage(relativePath: String, target: File): Boolean {
        val connection = openConnection(remoteImagesBaseUrl + relativePath) ?: return false
        target.parentFile?.mkdirs()

        return try {
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target.isFile && target.length() > 0L
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadText(url: String): String? {
        val connection = openConnection(url) ?: return null
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.requestMethod = "GET"

        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            null
        } else {
            connection
        }
    }.getOrNull()
}
