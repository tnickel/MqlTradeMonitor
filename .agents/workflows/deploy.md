---
description: Build WAR file and deploy to Contabo server with health check
---

## Deploy TradeMonitor to Contabo

// turbo-all

1. Build the WAR file (clean + package, skip tests for speed):
```powershell
mvn clean package -q
```
Working directory: `d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\server`
Verify: exit code 0 and `ROOT.war` exists in `server\target\`.

2. Upload ROOT.war to Contabo via SCP (to `/tmp/` first to prevent corrupted hot-deploy!):
```powershell
C:\WINDOWS\System32\OpenSSH\scp.exe -i C:\Users\tnickel\.ssh\contabo_key d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\server\target\ROOT.war root@84.46.247.222:/tmp/ROOT.war
```
Verify: exit code 0.

// turbo-all
2.1 Move WAR and trigger deploy:
```powershell
C:\WINDOWS\System32\OpenSSH\ssh.exe -i C:\Users\tnickel\.ssh\contabo_key root@84.46.247.222 "mv /tmp/ROOT.war /opt/wildfly/standalone/deployments/ROOT.war; touch /opt/wildfly/standalone/deployments/ROOT.war.dodeploy"
```

3. Wait 30 seconds for WildFly to hot-deploy the new WAR (it needs time for cache init):
```powershell
Start-Sleep -Seconds 30
```

4. Health check — verify the server is responding on port 80 (public-facing, behind Caddy reverse proxy):
```powershell
try { $r = Invoke-WebRequest -Uri "http://84.46.247.222/" -UseBasicParsing -TimeoutSec 15; Write-Host "OK - Status: $($r.StatusCode)" } catch { Write-Host "FAILED - $($_.Exception.Message)"; exit 1 }
```
Note: Port 8080 is NOT externally accessible. Always use port 80 (Caddy proxy).
If this fails, wait another 30 seconds and retry once.

5. Report the result: Tell the user whether the deployment succeeded or failed, including the HTTP status code.
