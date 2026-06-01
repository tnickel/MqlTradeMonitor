# Dokumentation: TradeMonitor Android App (Kotlin / Compose)

Diese Dokumentation beschreibt die Architektur, Struktur, Benutzeroberfläche und den Build-Prozess der native Android-Begleit-App für den **MqlTradeMonitor**.

---

## 1. Architektur-Übersicht

Die Android-App ist als schlanker REST-Client konzipiert, der sich mit dem Spring Boot Backend verbindet und die Kontodaten in Echtzeit darstellt.

```
+------------------------+
|   Android Mobilgerät   |
+-----------+------------+
            |
            | HTTPS / JSON (Session-basiert via JSESSIONID Cookie)
            v
+------------------------+
|  Spring Boot Backend   | <======== REST (X-User-Key Header) <======== MetaTrader EA
+------------------------+
```

### Technische Kernkomponenten:
- **Sprache & UI:** Kotlin, vollständig deklarativ entwickelt mit **Jetpack Compose** und **Material 3**.
- **Netzwerkschicht:** **Retrofit 2** & **OkHttp 3** für die HTTP-Kommunikation.
- **Session-Handling:** Ein benutzerdefinierter `CookieJar` speichert das vom Server zurückgegebene `JSESSIONID`-Session-Cookie nach dem Login und schickt es bei allen Folgeanfragen automatisch mit.
- **Navigation:** **Navigation Compose** steuert den Bildschirmwechsel ohne klassische Activities (Single-Activity-Pattern).
- **Grafische Darstellung:** Die Performance-Renditekurve (Equity/Balance) wird direkt über ein Jetpack Compose `Canvas` gezeichnet, um Ladezeiten zu minimieren und Abhängigkeiten von schweren Drittanbieter-Bibliotheken zu vermeiden.

---

## 2. Projektstruktur (`/android-app`)

Das Projekt befindet sich vollständig im Unterordner `/android-app` des Repositories:

```
android-app/
├── build.gradle.kts          # Projekt-Level Build-Konfiguration
├── settings.gradle.kts       # Gradle Repositories & Modul-Deklaration
├── gradle.properties         # JVM- & AndroidX-Einstellungen
├── gradle/wrapper/           # Gradle Wrapper (konfiguriert auf Version 8.13)
└── app/                      # Hauptmodul der App
    ├── build.gradle.kts      # Modul-Build-Datei (Compose, Retrofit-Abhängigkeiten)
    └── src/main/
        ├── AndroidManifest.xml  # Deklariert App-Permissions (Internet, Cleartext Traffic)
        ├── res/                 # Layout-Ressourcen (Strings, XML-Themes)
        └── java/de/trademonitor/app/
            ├── MainActivity.kt  # App-Einstieg & Navigationsgraph (Navigation-Host)
            ├── api/
            │   ├── ApiClient.kt        # HTTP-Client & dynamische Server-URL Verwaltung
            │   └── TradeMonitorApi.kt  # Retrofit API-Schnittstellendefinitionen
            ├── model/
            │   └── Models.kt           # Kotlin-Datenklassen (Account, Trade, ClosedTrade)
            └── ui/
                ├── theme/              # Color, Type & Theme Spezifikation (Neon-Darktheme)
                ├── login/
                │   └── LoginScreen.kt        # Login-Bildschirm (mit Server-URL Vorbelegung)
                ├── dashboard/
                │   └── DashboardScreen.kt    # Gesamtübersicht & Kontenliste
                ├── detail/
                │   └── AccountDetailScreen.kt# Detailseite mit Canvas-Equitychart & Trade-Tabs
                └── stats/
                    └── DrawdownScreen.kt     # Globale Magic Number Drawdowns
```

---

## 3. Benutzeroberfläche & Features

### 3.1 Login & Verbindung (`LoginScreen`)
- Fragt nach **Server-URL**, **Benutzername** und **Passwort**.
- **Vorbelegung:** Die Server-URL ist standardmäßig mit `https://monitor.tnickel-ki.de` vorbelegt, kann aber jederzeit überschrieben werden.
- **Auto-Login:** Wurden Zugangsdaten einmal erfolgreich eingegeben, meldet die App den Benutzer beim nächsten Start automatisch an.
- **Demo-Zugang:** Ermöglicht den schnellen Zugriff als Demo-User ohne Zugangsdaten.

### 3.2 Dashboard (`DashboardScreen`)
- **Gesamtstatistik-Karte:** Zeigt die summierten Werte über alle freigegebenen Konten (Gesamt-Balance, Gesamt-Equity, offener Gewinn/Verlust, Anzahl offener Trades).
- **Kontenliste:** Listet alle Handelskonten einzeln auf mit:
  - Broker-Name und Kontonummer.
  - Aktueller Kontotyp-Kennzeichnung (z. B. `REAL` in Orange).
  - Online-Status (Grüner/Roter Punkt basierend auf dem Heartbeat).
  - Balance, Equity und offener Profit (farbkodiert: Grün für Profit, Rot für Verlust).
  - Aktueller Drawdown in % und Anzahl offener Trades.
- **Aktualisierung:** Unterstützt Pull-to-Refresh und aktualisiert sich im Hintergrund alle 30 Sekunden automatisch.

### 3.3 Konto-Details (`AccountDetailScreen`)
- **Interactive Canvas-Chart:** Zeichnet die Equity-Kurve als blaue Linie mit einem transparenten Farbverlauf darunter sowie die Balance-Kurve als graue Linie.
- **Tabs (Reiter) für tiefere Analysen:**
  1. **Offen:** Zeigt alle aktuell laufenden Positionen (Typ, Volumen, Symbol, offener Profit, Einstiegspreis, SL/TP, Kommentar und den EA-Synchronisationsstatus).
  2. **Historie:** Zeigt die Liste der geschlossenen Trades (Profit, Swap, Kommission, Schließungszeitpunkt).
  3. **Info:** Listet detaillierte Kontoparameter und Alarmgrenzen (Drawdown-Alarme) auf.

### 3.4 Magic Drawdowns (`DrawdownScreen`)
- Zeigt eine aggregierte Live-Übersicht aller Strategien (Magic Numbers), die sich aktuell im Drawdown befinden, sortiert nach der Höhe des Drawdowns in %.

---

## 4. Build- & Deployment-Anleitung

### Voraussetzungen:
- Installiertes **Android Studio** (Version *Jellyfish* oder neuer empfohlen).
- Ein Android-Smartphone mit aktiviertem **USB-Debugging** (in den Entwickleroptionen des Telefons).

### App bauen und starten:
1. Öffne Android Studio.
2. Wähle **Open** und navigiere zum Verzeichnis `d:\AntiGravitySoftware\GitWorkspace\MqlTradeMonitor\android-app`.
3. Warte, bis der Gradle-Sync abgeschlossen ist (lädt alle Compiler-Tools herunter).
4. Schließe dein Handy per USB-Kabel an deinen PC an.
5. Wähle dein Handy im oberen Gerätemenü in Android Studio aus.
6. Drücke den **grünen Play-Button (Run)** in der Symbolleiste.
7. Android Studio übersetzt das Projekt, erzeugt die `.apk`-Datei und installiert & startet die App direkt auf deinem Smartphone.
