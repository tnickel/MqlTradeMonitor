package de.trademonitor.app.api

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var retrofit: Retrofit? = null
    private var apiService: TradeMonitorApi? = null
    private var currentUrl: String = ""
    @Volatile private var csrfToken: String? = null
    @Volatile private var csrfHeader: String = "X-CSRF-TOKEN"

    // In-memory cookie jar that stores and sends cookies automatically
    private val cookieJar = object : CookieJar {
        private val cookieStore = HashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: ArrayList()
        }

        fun clear() {
            cookieStore.clear()
        }
    }

    fun getService(context: Context): TradeMonitorApi {
        val sharedPrefs = context.getSharedPreferences("TradeMonitorPrefs", Context.MODE_PRIVATE)
        val savedUrl = sharedPrefs.getString("server_url", "") ?: ""

        if (savedUrl.isEmpty()) {
            throw IllegalStateException("Server URL not configured")
        }

        // Recreate client if URL has changed or not yet initialized
        if (apiService == null || currentUrl != savedUrl) {
            initialize(savedUrl)
        }

        return apiService!!
    }

    fun getSavedUrl(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("TradeMonitorPrefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("server_url", "") ?: ""
    }

    fun saveServerUrl(context: Context, url: String) {
        val formattedUrl = if (!url.endsWith("/")) "$url/" else url
        context.getSharedPreferences("TradeMonitorPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("server_url", formattedUrl)
            .apply()
        
        cookieJar.clear() // Clear old cookies when switching server
        csrfToken = null
        initialize(formattedUrl)
    }

    fun clearSession() {
        cookieJar.clear()
        csrfToken = null
    }

    fun updateCsrfToken(loginResponse: de.trademonitor.app.model.LoginResponse?) {
        csrfToken = loginResponse?.csrfToken
        csrfHeader = loginResponse?.csrfHeader ?: "X-CSRF-TOKEN"
    }

    private fun initialize(baseUrl: String) {
        currentUrl = baseUrl
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("X-CSRF-TOKEN")
            level = HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                val method = request.method.uppercase()
                val token = csrfToken
                if (token != null && method !in setOf("GET", "HEAD", "OPTIONS", "TRACE")) {
                    chain.proceed(request.newBuilder().header(csrfHeader, token).build())
                } else {
                    chain.proceed(request)
                }
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit!!.create(TradeMonitorApi::class.java)
    }
}
