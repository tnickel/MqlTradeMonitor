import sys

filename = 'Doku/Entwickler.md'
with open(filename, 'r', encoding='utf-8') as f:
    content = f.read()

# Update 1: Directory Tree
old_tree = '''├── mql5/
│   └── TradeMonitorClient.mq5           # MQL5 Expert Advisor
├── mql4/                                # MQL4 EA (Legacy)
└── Doku/                                # Dokumentation'''

new_tree = '''├── mql5/
│   └── TradeMonitorClient.mq5           # MQL5 Expert Advisor
├── mql4/                                # MQL4 EA (Legacy)
├── mcp-server/                          # Model Context Protocol (MCP) Server
│   ├── src/                             # TypeScript Quellcode (API-Bridge)
│   └── tsconfig.json                    # TS Konfiguration
└── Doku/                                # Dokumentation'''

content = content.replace(old_tree, new_tree)

# Update 2: Section 9.5 MCP Server
old_sec = '''### 9.4 Konfiguration via Umgebungsvariablen

Spring Boot erlaubt die Überschreibung aller Properties:
```bash
java -jar target/trade-monitor-server-0.12.0.jar \\
  --server.port=9090 \\
  --app.admin.password=sicheres-passwort
```

---'''

new_sec = '''### 9.4 Konfiguration via Umgebungsvariablen

Spring Boot erlaubt die Überschreibung aller Properties:
```bash
java -jar target/trade-monitor-server-0.12.0.jar \\
  --server.port=9090 \\
  --app.admin.password=sicheres-passwort
```

### 9.5 MCP Server Build & Konfiguration

Der MCP-Server läuft lokal als Sidecar-Prozess in der Claude Desktop App und kommuniziert mit dem TradeMonitor-Server.

**Build:**
```bash
cd mcp-server
npm install
npm run build
```

**Konfiguration (claude_desktop_config.json):**
Die Zugangsdaten und die Server-URL werden in der Claude-Konfiguration oder in der `.env` Datei (`mcp-server/.env`) abgelegt.
```json
"mqltrademonitor": {
  "command": "node",
  "args": ["D:\\\\Pfad\\\\mcp-server\\\\build\\\\index.js"],
  "env": {
    "TRADEMONITOR_URL": "https://monitor.deine-domain.de",
    "TRADEMONITOR_USERNAME": "admin",
    "TRADEMONITOR_PASSWORD": "admin_password"
  }
}
```

---'''

content = content.replace(old_sec, new_sec)

with open(filename, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated Entwickler.md")
