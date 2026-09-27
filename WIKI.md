# Stone Discord — Wiki

Lässt Spieler mit einem einzigen Befehl einen anklickbaren, voll konfigurierbaren Discord-Einladungslink öffnen. Gebaut für Paper-Server.

> Diese Datei ist die zentrale Referenz für dieses Plugin — Features, Befehle, Berechtigungen, Konfiguration und Entwicklungs-Setup. Sie liegt im Repo-Root und wird bei jeder relevanten Änderung mitgepflegt.

## Inhaltsverzeichnis

- [Voraussetzungen](#voraussetzungen)
- [Installation](#installation)
- [Befehle](#befehle)
- [Berechtigungen](#berechtigungen)
- [Konfiguration (`config.yml`)](#konfiguration-configyml)
- [Sprachen (`messages.yml`)](#sprachen-messagesyml)
- [Update-Checker](#update-checker)
- [Architektur / Quellcode-Überblick](#architektur--quellcode-überblick)
- [Entwicklung & Build](#entwicklung--build)
- [Tests](#tests)
- [Bekannte Einschränkungen](#bekannte-einschränkungen)
- [Versionshistorie](#versionshistorie)

## Voraussetzungen

| | |
|---|---|
| **Server** | Paper (oder ein Paper-Fork) |
| **Paper API** | `26.2.build.124-stable` (siehe `pom.xml`) |
| **Java** | 25 |
| **Abhängigkeiten** | Keine externen Plugin-Abhängigkeiten (kein Vault, kein PlaceholderAPI o.ä. im Code referenziert) |

Das Plugin deklariert `api-version: '26.2'` in `plugin.yml` und `maven.compiler.release=25` in `pom.xml` — beides muss zueinander und zur tatsächlichen Server-Version passen, siehe [Versionshistorie](#versionshistorie).

## Installation

1. `StoneDiscord-1.0.0.jar` (aus `mvn clean package`, liegt danach unter `target/`) in den `plugins`-Ordner des Servers legen.
2. Server starten/neustarten. Beim ersten Start werden automatisch angelegt:
   - `plugins/StoneDiscord/config.yml`
   - `plugins/StoneDiscord/languages/en/messages.yml`
   - `plugins/StoneDiscord/languages/de/messages.yml`
3. `config.yml` anpassen (mindestens `discord.link` auf die eigene Einladung setzen).
4. `/stonediscord reload` — kein Neustart nötig.

## Befehle

### `/discord` (Alias: `/dc`)

Sendet dem ausführenden Spieler eine anklickbare Chat-Nachricht mit dem konfigurierten Discord-Link.

- Benötigt Berechtigung `stonediscord.use`.
- Kann über `discord.enabled` in `config.yml` komplett deaktiviert werden.
- Unterliegt einem Cooldown (`discord.cooldown-seconds`, Standard 5 s) — **nicht** für Spieler mit `stonediscord.admin`.
- Nachrichtentext, Farben/Formatierung und Platzhalter (`{player}`, `{link}`) sind in `config.yml` unter `discord.message` frei konfigurierbar.

### `/stonediscord <reload|help|checkupdate>` (Aliase: `/sdc`, `/stonedc`)

Admin-Befehl, benötigt durchgehend `stonediscord.admin`.

| Unterbefehl | Wirkung |
|---|---|
| `reload` | Lädt `config.yml` und alle Sprachdateien neu (asynchron, kein Server-Lag). Setzt außerdem alle aktiven Cooldowns zurück und startet den Update-Checker neu. Läuft bereits ein Reload, wird ein zweiter Aufruf mit einer Hinweismeldung abgelehnt statt einen zweiten parallelen Schreibzugriff auf dieselben Dateien auszulösen. |
| `help` | Zeigt die Liste der Unterbefehle. |
| `checkupdate` | Stößt sofort eine Prüfung auf eine neue Version bei Modrinth an (statt auf das nächste automatische Intervall zu warten). Ergebnis erscheint in der Konsole. |

Ohne Argument oder bei einem unbekannten Unterbefehl wird automatisch die Hilfe angezeigt.

## Berechtigungen

| Knoten | Standard | Wirkung |
|---|---|---|
| `stonediscord.use` | `true` (alle Spieler) | Erlaubt `/discord` bzw. `/dc`. |
| `stonediscord.admin` | `op` | Erlaubt alle `/stonediscord`-Unterbefehle, befreit vom `/discord`-Cooldown und schaltet die "neue Version verfügbar"-Erinnerung beim Login frei. |

## Konfiguration (`config.yml`)

```yaml
language: en                 # "en" oder "de" (weitere Sprachen: eigenen Ordner unter languages/ anlegen)

discord:
  enabled: true               # /discord komplett an/aus
  link: "https://discord.gg/your-invite"
  message: "<gradient:#5865F2:#7289DA>...</gradient> ..."   # unterstützt &-Codes, &#RRGGBB-Hex und MiniMessage gleichzeitig
  cooldown-seconds: 5          # 0 = kein Cooldown; gilt nicht für stonediscord.admin

update-checker:
  enabled: true
  check-interval-minutes: 60   # wird intern auf min. 5 begrenzt
```

Fehlende Schlüssel werden bei jedem Laden automatisch aus den Standardwerten ergänzt (z. B. nach einem Plugin-Update) — bereits angepasste, bestehende Werte werden dabei **nie** überschrieben, auch nicht innerhalb verschachtelter Abschnitte. Falsch typisierte oder fehlende Werte fallen sicher auf den jeweiligen Default zurück.

## Sprachen (`messages.yml`)

Alle Chat-Texte außer der `/discord`-Nachricht selbst (die bleibt sprachunabhängig in `config.yml`) liegen pro Sprache unter `plugins/StoneDiscord/languages/<sprache>/messages.yml`. Mitgeliefert: `en` (Standard) und `de`.

| Schlüssel | Bedeutung |
|---|---|
| `prefix` | Wird vor jede `sendChat`-Nachricht gesetzt (nicht vor `help.*`). |
| `general.no-permission` | Fehlende Berechtigung. |
| `general.reload-triggered` | Reload wurde gestartet. |
| `general.reload-success` | Reload abgeschlossen. |
| `general.reload-in-progress` | Reload läuft bereits, zweiter Aufruf wurde abgelehnt. |
| `help.*` | Hilfetext für `/stonediscord help`. |
| `discord.disabled` | `/discord` ist per Config deaktiviert. |
| `discord.cooldown` | Cooldown aktiv, `{seconds}` = Restzeit. |
| `update.available` | Neue Version verfügbar (`{version}`, `{current}`, `{behind}`). |
| `update.versions-behind` / `-unknown` | Baustein für `{behind}` oben. |
| `update.check-triggered` | Rückmeldung auf `/stonediscord checkupdate`. |

Fehlt ein Schlüssel in der aktiven Sprache, greift automatisch der englische Text als Fallback. Fehlende Sprachdateien/Schlüssel werden wie bei `config.yml` automatisch ergänzt, ohne bestehende Übersetzungen zu überschreiben.

## Update-Checker

Fragt periodisch `https://api.modrinth.com/v2/project/stone-discord/version` ab (asynchron, kein Einfluss auf Server-Performance):

- Sofortige Prüfung beim Serverstart bzw. nach jedem `reload`, danach im konfigurierten Intervall.
- Bei neuer Version: einmalige Konsolenmeldung, danach Erinnerung für jeden Spieler mit OP-Status oder `stonediscord.admin` beim Login, bis aktualisiert wird.
- Komplett abschaltbar über `update-checker.enabled: false`.

## Architektur / Quellcode-Überblick

```
src/main/java/dev/stonediscord/plugin/
├── StoneDiscord.java              # Hauptklasse: onEnable/onDisable, Command-/Listener-Registrierung, reloadAsync
├── command/
│   ├── DiscordCommand.java        # /discord
│   └── StoneDiscordCommand.java   # /stonediscord reload|help|checkupdate
├── manager/
│   ├── ConfigManager.java         # config.yml lesen/cachen (thread-sicherer Snapshot, siehe unten)
│   ├── MessageManager.java        # messages.yml lesen, Legacy/Hex → MiniMessage-Konvertierung, Caching
│   ├── CooldownManager.java       # Cooldowns pro Spieler (In-Memory, ConcurrentHashMap)
│   └── UpdateChecker.java         # Modrinth-Abfrage, Join-Erinnerung
├── config/
│   └── ConfigUpdater.java         # generischer Merge-Algorithmus für Config-Migration (fehlende Keys ergänzen)
└── listener/
    └── PlayerQuitListener.java    # räumt Cooldown-Eintrag beim Verlassen auf (verhindert Memory-Leak)
```

**Thread-Safety-Design:** `/stonediscord reload` lädt `config.yml`/`messages.yml` asynchron (Datei-I/O blockiert nicht den Main-Thread). `ConfigManager` und `MessageManager` publizieren den neu geladenen Zustand danach atomar über eine einzelne `volatile` Record-Referenz (Snapshot-Swap), damit ein zeitgleich auf dem Main-Thread laufender `/discord`-Befehl niemals einen halb aktualisierten Zustand sieht. Zwei überlappende `/stonediscord reload`-Aufrufe werden über ein `AtomicBoolean`-Flag serialisiert, damit nie zwei Tasks gleichzeitig dieselbe Datei beschreiben.

## Entwicklung & Build

```bash
# Java 25 + die paper-api-26.2-Artefakte müssen im lokalen Maven-Cache liegen
# (repo.papermc.io ist in manchen Sandboxes blockiert — ggf. Artefakte manuell
# per `mvn install:install-file` installieren)

mvn clean package
```

Ergebnis: `target/StoneDiscord-1.0.0.jar` (enthält `plugin.yml`, alle Klassen, `config.yml` und beide Sprachdateien als eingebettete Default-Ressourcen).

## Tests

```bash
mvn test
```

23 JUnit-5-Tests unter `src/test/java`, ohne Abhängigkeit von einem laufenden/gemockten Bukkit-Server:

- `CooldownManagerTest` — Cooldown-Grenzfälle, Isolation zwischen Spielern, Cleanup.
- `ConfigUpdaterTest` — Merge-Algorithmus der Config-Migration (fehlende Keys, verschachtelte Sections, keine Überschreibung bestehender Werte).
- `MessageManagerTest` — Legacy-/Hex-/MiniMessage-Konvertierung inkl. Grenzfälle, Platzhalter-Ersetzung, Caching-Korrektheit.

`MockBukkit` wurde als Abhängigkeit für echte Integrationstests evaluiert, scheitert aber beim Start gegen `paper-api 26.2` (Registry-Mock ist auf reale MC-1.21-Daten gebaut). Tests, die einen laufenden Mock-/Live-Server brauchen (z. B. Ende-zu-Ende-Test des Reload-Race-Fixes), sind aktuell nicht automatisiert — siehe [Bekannte Einschränkungen](#bekannte-einschränkungen).

## Bekannte Einschränkungen

- Keine automatisierten Integrationstests gegen einen echten/gemockten Server möglich (s. o.) — der Reload-Concurrency-Fix ist nur durch Code-Argumentation (unveränderliche Records + `volatile`-Referenz sind laut JLS atomar publiziert), nicht durch einen laufenden Test abgesichert.
- `ConfigUpdater` migriert nur *fehlende* Schlüssel, keine Typ-Änderungen an bestehenden Schlüsseln (z. B. Skalar → Section) — mit dem aktuellen Schema nicht relevant, nur bei einem künftigen Config-Umbau zu beachten.
- Kein Lasttest/Profiling-Report für 300+ gleichzeitige Spieler vorhanden; der Code enthält aber keine Tick-Loops oder O(n²)-Muster, die das erwarten ließen.

## Versionshistorie

| Datum | Änderung |
|---|---|
| 2026-09 | Portierung von Paper API `1.21.4-R0.1-SNAPSHOT` / Java 21 auf Paper API `26.2.build.124-stable` / Java 25. Keine Compile-Fixes am Plugin-Code nötig. |
| 2026-09 | Bugfixes: Race Conditions in `ConfigManager`/`MessageManager` bei gleichzeitigem `/stonediscord reload` behoben (atomarer Snapshot-Swap), Schutz gegen zwei parallele Reloads (`AtomicBoolean` + neue Meldung `general.reload-in-progress`), `CooldownManager` auf `System.nanoTime()` umgestellt (robust gegen Systemuhr-Sprünge). 23 JUnit-Tests ergänzt. |
