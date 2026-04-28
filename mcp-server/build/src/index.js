"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const index_js_1 = require("@modelcontextprotocol/sdk/server/index.js");
const stdio_js_1 = require("@modelcontextprotocol/sdk/server/stdio.js");
const types_js_1 = require("@modelcontextprotocol/sdk/types.js");
const api_1 = require("./api");
const server = new index_js_1.Server({
    name: "mqltrademonitor-mcp",
    version: "1.0.0",
}, {
    capabilities: {
        tools: {},
    },
});
const api = new api_1.TradeMonitorApi();
server.setRequestHandler(types_js_1.ListToolsRequestSchema, async () => {
    return {
        tools: [
            {
                name: "get_accounts",
                description: "Liefert eine Liste aller angebundenen MetaTrader-Konten (inkl. Balance, Equity, Online-Status).",
                inputSchema: {
                    type: "object",
                    properties: {},
                },
            },
            {
                name: "get_open_trades",
                description: "Liefert alle derzeit offenen Trades aller Konten (oder gefiltert nach Berechtigung).",
                inputSchema: {
                    type: "object",
                    properties: {},
                },
            },
            {
                name: "get_closed_trades",
                description: "Liefert die Historie der geschlossenen Trades für ein spezifisches Konto.",
                inputSchema: {
                    type: "object",
                    properties: {
                        accountId: {
                            type: "number",
                            description: "Die ID des Kontos",
                        },
                    },
                    required: ["accountId"],
                },
            },
            {
                name: "get_system_status",
                description: "Gibt einen Überblick über die Systemgesundheit (Online EAs, offene Trades, Alarme).",
                inputSchema: {
                    type: "object",
                    properties: {},
                },
            },
            {
                name: "get_ea_logs",
                description: "Liefert die letzten EA-Logs (Expert Advisor Log-Einträge) für ein MetaTrader-Konto.",
                inputSchema: {
                    type: "object",
                    properties: {
                        accountId: {
                            type: "number",
                            description: "Die ID des Kontos",
                        },
                    },
                    required: ["accountId"],
                },
            },
            {
                name: "get_daily_profits",
                description: "Liefert die aggregierten Tagesgewinne (Daily Profit) aller verbundenen Konten von heute.",
                inputSchema: {
                    type: "object",
                    properties: {},
                },
            },
            {
                name: "get_blocked_ips",
                description: "Liefert Angriffsversuche, Fail2Ban-Details und blockierte IPs. (Erfordert ADMIN-Rechte im TradeMonitor).",
                inputSchema: {
                    type: "object",
                    properties: {},
                },
            },
            {
                name: "get_server_health",
                description: "Liefert detaillierten Speicherverbrauch (RAM), Festplattenplatz und CPU-Auslastung des TradeMonitor-Servers. (Erfordert ADMIN-Rechte).",
                inputSchema: {
                    type: "object",
                    properties: {},
                },
            },
        ],
    };
});
server.setRequestHandler(types_js_1.CallToolRequestSchema, async (request) => {
    try {
        switch (request.params.name) {
            case "get_accounts": {
                const accounts = await api.getAccounts();
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(accounts, null, 2),
                        },
                    ],
                };
            }
            case "get_open_trades": {
                const trades = await api.getOpenTrades();
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(trades, null, 2),
                        },
                    ],
                };
            }
            case "get_closed_trades": {
                const accountId = Number(request.params.arguments?.accountId);
                if (isNaN(accountId)) {
                    throw new types_js_1.McpError(types_js_1.ErrorCode.InvalidParams, "Invalid accountId");
                }
                const trades = await api.getClosedTrades(accountId);
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(trades, null, 2),
                        },
                    ],
                };
            }
            case "get_system_status": {
                const status = await api.getSystemStatus();
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(status, null, 2),
                        },
                    ],
                };
            }
            case "get_ea_logs": {
                const accountId = Number(request.params.arguments?.accountId);
                if (isNaN(accountId)) {
                    throw new types_js_1.McpError(types_js_1.ErrorCode.InvalidParams, "Invalid accountId");
                }
                const logs = await api.getEaLogs(accountId);
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(logs, null, 2),
                        },
                    ],
                };
            }
            case "get_daily_profits": {
                const profits = await api.getDailyProfits();
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(profits, null, 2),
                        },
                    ],
                };
            }
            case "get_blocked_ips": {
                const ips = await api.getBlockedIps();
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(ips, null, 2),
                        },
                    ],
                };
            }
            case "get_server_health": {
                const health = await api.getServerHealth();
                return {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify(health, null, 2),
                        },
                    ],
                };
            }
            default:
                throw new types_js_1.McpError(types_js_1.ErrorCode.MethodNotFound, `Unknown tool: ${request.params.name}`);
        }
    }
    catch (error) {
        console.error(`Error executing tool ${request.params.name}:`, error.message);
        return {
            content: [
                {
                    type: "text",
                    text: `Error executing tool: ${error.message}`,
                },
            ],
            isError: true,
        };
    }
});
async function main() {
    const transport = new stdio_js_1.StdioServerTransport();
    await server.connect(transport);
    console.error("MqlTradeMonitor MCP Server running on stdio");
}
main().catch((error) => {
    console.error("Fatal error in main():", error);
    process.exit(1);
});
