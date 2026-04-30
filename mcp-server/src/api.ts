import axios, { AxiosInstance } from 'axios';
import dotenv from 'dotenv';

const originalLog = console.log;
console.log = () => {};
dotenv.config();
console.log = originalLog;

export class TradeMonitorApi {
    private client: AxiosInstance;
    private baseUrl: string;
    private jsessionId: string | null = null;
    
    constructor() {
        this.baseUrl = process.env.TRADEMONITOR_URL || 'http://localhost:8080';
        this.client = axios.create({
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

        // Detect expired session (server returns login HTML) and re-authenticate
        this.client.interceptors.response.use(async (response) => {
            const contentType = String(response.headers['content-type'] || '');
            if (contentType.includes('text/html')) {
                this.jsessionId = null;
                await this.authenticate();
                // Retry the original request with the new session
                const config = response.config;
                if (this.jsessionId) {
                    config.headers['Cookie'] = `JSESSIONID=${this.jsessionId}`;
                }
                return this.client.request(config);
            }
            return response;
        });
    }

    async authenticate(): Promise<void> {
        const username = process.env.TRADEMONITOR_USERNAME;
        const password = process.env.TRADEMONITOR_PASSWORD;

        if (username && password) {
            try {
                const response = await this.client.post('/api/login', { username, password });
                this.extractSessionCookie(response);
                console.error('Successfully authenticated using standard login');
                return;
            } catch (error: any) {
                console.error('Standard login failed:', error.message);
                throw new Error('Failed to authenticate with TradeMonitor API');
            }
        }

        try {
            const response = await this.client.post('/api/demo-login', {});
            this.extractSessionCookie(response);
            console.error('Successfully authenticated using demo-login');
        } catch (error: any) {
            console.error('Demo login failed:', error.message);
            throw new Error('Failed to authenticate with TradeMonitor API');
        }
    }

    private extractSessionCookie(response: any) {
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

    async getClosedTrades(accountId: number) {
        await this.ensureAuthenticated();
        const response = await this.client.get(`/api/account/${accountId}/closed-trades`);
        return response.data;
    }

    async getEaLogs(accountId: number) {
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
        } catch (error: any) {
            console.error("Error fetching blocked IPs, ensure you are authenticated as ADMIN:", error.message);
            throw new Error(`Permission Denied: Fetching blocked IPs requires ADMIN privileges. Check your TRADEMONITOR_USERNAME. Error: ${error.message}`);
        }
    }

    async getServerHealth() {
        await this.ensureAuthenticated();
        try {
            const response = await this.client.get('/admin/api/health/data');
            return response.data;
        } catch (error: any) {
            console.error("Error fetching server health, ensure you are authenticated as ADMIN:", error.message);
            throw new Error(`Permission Denied: Fetching server health requires ADMIN privileges. Check your TRADEMONITOR_USERNAME. Error: ${error.message}`);
        }
    }

    async getDailyProfits() {
        // Daily profits are included in the system-status allAccounts payload
        const status = await this.getSystemStatus();
        if (status && status.allAccounts) {
            return status.allAccounts.map((acc: any) => ({
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
