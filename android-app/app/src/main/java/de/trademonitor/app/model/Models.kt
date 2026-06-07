package de.trademonitor.app.model

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val message: String
)

data class Account(
    val accountId: Long,
    val broker: String?,
    val currency: String?,
    val balance: Double,
    val equity: Double,
    val profit: Double,
    val trades: Int,
    val online: Boolean,
    val lastSeen: String?,
    val eaLogAcceptedAt: String?,
    val lastSeenMins: Long,
    val name: String?,
    val type: String?, // "DEMO" or "REAL"
    val section: String?,
    val sectionId: Long?,
    val displayOrder: Int,
    val syncWarning: Boolean,
    val errorState: Boolean,
    val lastErrorMsg: String?,
    val openProfitAlarmEnabled: Boolean,
    val openProfitAlarmAbs: Double?,
    val openProfitAlarmPct: Double?,
    val openProfitAlarmTriggered: Boolean,
    val copierError: Boolean,
    val copierErrorMessage: String?,
    val worstCopierStage: Int,
    val profitPct: Double,
    val monthlyProfitPct: Double,
    val m1Pct: Double,
    val m2Pct: Double,
    val m3Pct: Double,
    val mpdd3: Double,
    val maxDrawdownPct: Double,
    val openTradesCount: Int,
    val closedTradesCount: Int,
    val totalHistoryProfit: Double
)

data class Trade(
    val accountId: Long,
    val accountName: String?,
    val accountType: String?,
    val isReal: Boolean,
    val ticket: Long,
    val symbol: String?,
    val type: String?, // BUY or SELL
    val volume: Double,
    val openPrice: Double,
    val openTime: String?,
    val profit: Double,
    val magicNumber: Long,
    val comment: String?,
    val syncStatus: String?,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0
)

data class ClosedTrade(
    val ticket: Long,
    val symbol: String?,
    val type: String?,
    val volume: Double,
    val openPrice: Double,
    val closePrice: Double,
    val openTime: String?,
    val closeTime: String?,
    val profit: Double,
    val swap: Double,
    val commission: Double,
    val magicNumber: Long,
    val comment: String?,
    val sl: Double?
)

data class MagicDrawdownItem(
    val accountId: Long,
    val accountName: String?,
    val accountType: String?,
    val isReal: Boolean,
    val magicNumber: Long,
    val magicName: String?,
    val currentDrawdownEur: Double,
    val currentDrawdownPercent: Double,
    val balanceHigh: Double,
    val currentMagicEquity: Double,
    val lastSeenMins: Long?,
    val lastSeenString: String?
)

data class EquitySnapshot(
    val timestamp: String,
    val equity: Double,
    val balance: Double
)

data class SystemStatusResponse(
    val isAdmin: Boolean
)

data class ServerHealth(
    val osName: String?,
    val cpuLoad: String?,
    val totalMemory: String?,
    val freeMemory: String?,
    val usedMemory: String?,
    val diskTotal: String?,
    val diskFree: String?,
    val diskUsed: String?,
    val dbFileSize: String?,
    val logFileSize: String?,
    val systemTotalMemory: String?,
    val systemFreeMemory: String?,
    val systemUsedMemory: String?,
    val aiTaskManagerWarSize: String?,
    val rootWarSize: String?
)
