package de.trademonitor.app.api

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object UpdateManager {
    val isUpdateAvailable = mutableStateOf(false)
    var latestVersionName = ""
    var downloadUrl = ""
    
    val showUpdateDialog = mutableStateOf(false)
    val isDownloading = mutableStateOf(false)
    val downloadProgress = mutableStateOf(0f)

    fun checkForUpdates(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val savedUrl = ApiClient.getSavedUrl(context)
                if (savedUrl.isEmpty()) return@launch

                val service = ApiClient.getService(context)
                val response = service.getLatestVersion()

                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    packageInfo.versionCode
                }

                if (response.versionCode > currentVersionCode) {
                    withContext(Dispatchers.Main) {
                        latestVersionName = response.versionName
                        downloadUrl = response.downloadUrl
                        downloadProgress.value = 0f
                        isDownloading.value = false
                        isUpdateAvailable.value = true
                        showUpdateDialog.value = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isUpdateAvailable.value = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startDownload(context: Context) {
        if (downloadUrl.isEmpty()) return
        isDownloading.value = true
        downloadProgress.value = 0f
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(downloadUrl).build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    val apkFile = File(context.cacheDir, "update.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }

                    body.byteStream().use { inputStream ->
                        FileOutputStream(apkFile).use { outputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    downloadProgress.value = totalBytesRead.toFloat() / contentLength.toFloat()
                                }
                            }
                            outputStream.flush()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        isDownloading.value = false
                        showUpdateDialog.value = false
                        installApk(context, apkFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isDownloading.value = false
                    downloadProgress.value = -1f
                }
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
