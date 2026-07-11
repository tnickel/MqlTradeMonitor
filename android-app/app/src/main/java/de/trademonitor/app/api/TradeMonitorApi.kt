package de.trademonitor.app.api

import de.trademonitor.app.model.*
import retrofit2.Response
import retrofit2.http.*

interface TradeMonitorApi {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/demo-login")
    suspend fun demoLogin(): Response<LoginResponse>

    @POST("api/logout")
    suspend fun logout(): Response<Map<String, String>>

    @GET("api/accounts")
    suspend fun getAccounts(): List<Account>

    @GET("api/trades/open")
    suspend fun getOpenTrades(): List<Trade>

    @GET("api/stats/magic-drawdowns")
    suspend fun getMagicDrawdowns(): List<MagicDrawdownItem>

    @GET("api/equity-history/{accountId}")
    suspend fun getEquityHistory(@Path("accountId") accountId: Long): List<EquitySnapshot>

    @GET("api/account/{accountId}/closed-trades")
    suspend fun getClosedTrades(@Path("accountId") accountId: Long): List<ClosedTrade>

    @GET("api/stats/system-status")
    suspend fun getSystemStatus(): SystemStatusResponse

    @GET("admin/api/health/data")
    suspend fun getServerHealth(): ServerHealth

    @GET("admin/api/security-audit/data")
    suspend fun getSecurityAudit(): SecurityAudit

    @POST("admin/api/security-audit/run")
    suspend fun runSecurityAudit(): SecurityAudit

    @POST("admin/api/fail2ban/unban")
    suspend fun unbanIp(@Query("ipAddress") ipAddress: String): Response<Map<String, String>>

    @GET("api/latest-version")
    suspend fun getLatestVersion(): VersionResponse

}
