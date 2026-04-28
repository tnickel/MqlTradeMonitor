"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.TradeMonitorApi = void 0;
const axios_1 = __importDefault(require("axios"));
const dotenv_1 = __importDefault(require("dotenv"));
dotenv_1.default.config();
class TradeMonitorApi {
    client;
    baseUrl;
    jsessionId = null;
    constructor() {
        this.baseUrl = process.env.TRADEMONITOR_URL || 'http://localhost:8080';
        this.client = axios_1.default.create({
            baseURL: this.baseUrl,
            headers: {
                'Content-Type': 'application/json'
            },
            withCredentials: true
        });
        // Add interceptor to attach session cookie
        this.client.interceptors.request.use((config) => {
            if (this.jsessionId) {
                config.headers['Cookie'] = `JSESSIONID=${this.jsessionId}`;
            }
            return config;
        });
    }
    async authenticate() {
        try {
            // First attempt demo login (simpler)
            const response = await this.client.post('/api/demo-login', {});
            this.extractSessionCookie(response);
            console.error('Successfully authenticated using demo-login');
        }
        catch (error) {
            console.error('Demo login failed, attempting standard login...');
            try {
                const username = process.env.TRADEMONITOR_USERNAME;
                const password = process.env.TRADEMONITOR_PASSWORD;
                if (!username || !password) {
                    throw new Error('TRADEMONITOR_USERNAME and TRADEMONITOR_PASSWORD must be provided in environment if demo login is disabled.');
                }
                const response = await this.client.post('/api/login', { username, password });
                this.extractSessionCookie(response);
                console.error('Successfully authenticated using standard login');
            }
            catch (authError) {
                console.error('Authentication failed completely:', authError.message);
                throw new Error('Failed to authenticate with TradeMonitor API');
            }
        }
    }
    extractSessionCookie(response) {
        const setCookieHeaders = response.headers['set-cookie'];
        if (setCookieHeaders && Array.isArray(setCookieHeaders)) {
            for (const cookie of setCookieHeaders) {
                if (cookie.startsWith('JSESSIONID=')) {
                    this.jsessionId = cookie.split(';')[0].split('=')[1];
                    break;
                }
            }
        }
    }
    async ensureAuthenticated() {
        if (!this.jsessionId) {
            await this.authenticate();
        }
    }
    async getAccounts() {
        await this.ensureAuthenticated();
        const response = await this.client.get('/api/accounts');
        return response.data;
    }
    async getSystemStatus() {
        await this.ensureAuthenticated();
        const response = await this.client.get('/api/stats/system-status');
        return response.data;
    }
    async getOpenTrades() {
        await this.ensureAuthenticated();
        const response = await this.client.get('/api/trades/open');
        return response.data;
    }
    async getClosedTrades(accountId) {
        await this.ensureAuthenticated();
        const response = await this.client.get(`/api/account/${accountId}/closed-trades`);
        return response.data;
    }
    async getEaLogs(accountId) {
        await this.ensureAuthenticated();
        const response = await this.client.get(`/api/ea-logs/${accountId}`);
        return response.data;
    }
    async getBlockedIps() {
        await this.ensureAuthenticated();
        try {
            const fail2banRes = await this.client.get('/admin/api/fail2ban/details');
            const dbBansRes = await this.client.get('/admin/api/banned-ips/data');
            return {
                liveFail2Ban: fail2banRes.data,
                databaseBannedIps: dbBansRes.data
            };
        }
        catch (error) {
            console.error("Error fetching blocked IPs, ensure you are authenticated as ADMIN:", error.message);
            throw new Error(`Permission Denied: Fetching blocked IPs requires ADMIN privileges. Check your TRADEMONITOR_USERNAME. Error: ${error.message}`);
        }
    }
    async getServerHealth() {
        await this.ensureAuthenticated();
        try {
            const response = await this.client.get('/admin/api/health/data');
            return response.data;
        }
        catch (error) {
            console.error("Error fetching server health, ensure you are authenticated as ADMIN:", error.message);
            throw new Error(`Permission Denied: Fetching server health requires ADMIN privileges. Check your TRADEMONITOR_USERNAME. Error: ${error.message}`);
        }
    }
    async getDailyProfits() {
        // Daily profits are included in the system-status allAccounts payload
        const status = await this.getSystemStatus();
        if (status && status.allAccounts) {
            return status.allAccounts.map((acc) => ({
                accountId: acc.accountId,
                name: acc.name,
                type: acc.type,
                dailyProfit: acc.dailyProfit,
                currency: acc.currency
            }));
        }
        return [];
    }
}
exports.TradeMonitorApi = TradeMonitorApi;
