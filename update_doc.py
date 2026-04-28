import sys

filename = 'Doku/Projektbeschreibung.md'
with open(filename, 'r', encoding='utf-8') as f:
    content = f.read()

# Update 1: Architecture ASCII
old_art = '''                                     |  - Responsive Dark UI     |
                                     +---------------------------+
```'''
new_art = '''                                     |  - Responsive Dark UI     |
                                     +---------------------------+
                                               |
                                     +---------------------------+
                                     |  MCP Server (Node.js)     |
                                     |  - AI Agent Interface     |
                                     |  - Tools für Claude App   |
                                     +---------------------------+
```'''
content = content.replace(old_art, new_art)

# Update 2: Section 4.6
old_sec = '''Sämtliche Dokumentation, Prompts und Beschreibungen für die Replit App befinden sich im separaten Verzeichnis `replit/` (z.B. `Replit_Erweiterung.md`)

---'''

new_sec = '''Sämtliche Dokumentation, Prompts und Beschreibungen für die Replit App befinden sich im separaten Verzeichnis `replit/` (z.B. `Replit_Erweiterung.md`)

### 4.6 MCP Server (AI-Sidecar)

Der TradeMonitor bringt einen eigenen Model Context Protocol (MCP) Server mit, um sich nahtlos in KI-Agenten wie die Claude Desktop App zu integrieren. Der MCP-Server läuft lokal als Node.js/TypeScript-Anwendung und spricht über REST mit dem TradeMonitor-Backend.

**Verfügbare Tools (Schnittstellen) für die KI:**
- `get_accounts`, `get_open_trades`, `get_closed_trades`: Trading-Daten lesen
- `get_system_status`, `get_daily_profits`: Dashboard-Metriken aggregieren
- `get_ea_logs`: Logs der Expert Advisors auslesen
- `get_blocked_ips`, `get_server_health`: Fail2Ban und System-Ressourcen (erfordert Admin-Rechte)

---'''
content = content.replace(old_sec, new_sec)

with open(filename, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated Projektbeschreibung.md")
