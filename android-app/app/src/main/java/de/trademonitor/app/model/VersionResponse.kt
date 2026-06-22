package de.trademonitor.app.model

data class VersionResponse(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)
