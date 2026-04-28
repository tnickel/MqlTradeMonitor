# Maven Build-Anleitung (WAR-Erzeugung)

Diese Dokumentation beschreibt, wie das Backend-Projekt des MQL Trade Monitors gebaut wird und wo Maven auf diesem System installiert ist.

## 1. Maven Installation

Auf diesem Rechner ist Maven an folgendem Ort installiert:

**Pfad:** `C:\Users\tnickel\apache-maven-3.9.6`

Die ausführbare Datei für die Windows PowerShell oder CMD befindet sich unter:
`C:\Users\tnickel\apache-maven-3.9.6\bin\mvn.cmd`

## 2. WAR-File erzeugen

Um das Projekt zu bauen und das `ROOT.war` Archive zu erzeugen, führen Sie folgende Schritte aus:

1.  Öffnen Sie ein Terminal (PowerShell).
2.  Navigieren Sie in das `server` Verzeichnis des Projekts:
    ```powershell
    cd d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\server
    ```
3.  Führen Sie den Maven-Build-Befehl aus:
    ```powershell
    C:\Users\tnickel\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests
    ```

### Erläuterung der Parameter:
- `clean`: Löscht das vorhandene `target` Verzeichnis (alter Build-Stand).
- `package`: Kompiliert den Code und verpackt ihn als WAR-File.
- `-DskipTests`: (Optional) Überspringt die Unit-Tests für einen schnelleren Build.

## 3. Build-Ergebnis

Nach erfolgreichem Build finden Sie das fertige WAR-File unter:
`d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\server\target\ROOT.war`

Dieses File kann direkt auf den WildFly-Server in das Verzeichnis `/opt/wildfly/standalone/deployments/` kopiert werden.

## 4. Deployment zum Contabo-Server

Der Trade Monitor läuft auf einem **Contabo VPS**:

| | Details |
|---|---|
| **Server** | Contabo VPS |
| **IP** | `84.46.247.222` |
| **SSH User** | `root` |
| **SSH Key** | `C:\Users\tnickel\.ssh\contabo_key` |
| **SSH Config Alias** | `contabo` |
| **WildFly Pfad** | `/opt/wildfly/standalone/deployments/` |
| **Lokaler Server** | `192.168.178.57` (kein SSH) |

### WAR-File übertragen (PowerShell):

```powershell
C:\WINDOWS\System32\OpenSSH\scp.exe -i C:\Users\tnickel\.ssh\contabo_key d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\server\target\ROOT.war root@84.46.247.222:/opt/wildfly/standalone/deployments/ROOT.war
```

> **Hinweis:** In manchen PowerShell-Sessions ist `scp` nicht im PATH. Dann den vollen Pfad `C:\WINDOWS\System32\OpenSSH\scp.exe` verwenden. Der SSH-Key (`contabo_key`) wird per `-i` Flag angegeben.

### Kompletter Build + Deploy in einem Schritt:

```powershell
mvn clean package -f d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\server\pom.xml -q; C:\WINDOWS\System32\OpenSSH\scp.exe -i C:\Users\tnickel\.ssh\contabo_key d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\server\target\ROOT.war root@84.46.247.222:/opt/wildfly/standalone/deployments/ROOT.war
```
