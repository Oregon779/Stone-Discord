# CLAUDE.md

Projektspezifische Anweisungen für Claude Code bei der Arbeit an Stone Discord.

## Rückfragen

Stelle möglichst viele klärende Fragen — aber nur, wenn es wirklich sinnvoll ist. Bei mehrdeutigen oder folgenreichen Entscheidungen (Versionierung, Spielverhalten, Config-Schema etc.) lieber einmal zu viel nachfragen statt auf Verdacht zu raten. Keine Fragen stellen, wenn die Antwort eindeutig aus dem Kontext, dem Code oder vorherigen Anweisungen hervorgeht.

## Regeln für die Versionierung & Build-Erstellung

Für dieses Minecraft-Plugin trennen wir strikt zwischen Entwicklungs-Builds (Dev-Builds) und Release-Builds für Modrinth. Halte dich bei jeder Änderung an der Codebase zwingend an folgenden Workflow:

1. **Stabile Hauptversion:** Die aktuelle Hauptversionsnummer (z. B. `1.1`) darf von dir NIEMALS selbstständig geändert oder erhöht werden.
2. **Das Dev-Suffix:** Für alle laufenden Änderungen, Fehlerbehebungen und Code-Tests nutzt du ein Entwickler-Suffix. Das Format ist `[Hauptversion]-dev[Nummer]` (Beispiel: `1.1-dev1`, `1.1-dev2`).
3. **Automatisches Hochzählen:** Bei jedem neuen Build, den du generierst, zählst du *ausschließlich* die Nummer des Dev-Suffixes um +1 hoch. Aktualisiere diese Version in allen relevanten Dateien (z. B. `plugin.yml` und `build.gradle` bzw. `pom.xml`).
4. **Der Release-Befehl:** Erst wenn der Nutzer ausdrücklich den Befehl **"Baue das Release"** gibt, entfernst du das `-dev`-Suffix vollständig. Du trägst dann die reine Hauptversion (z. B. `1.1`) in die Dateien ein und erstellst die finale, saubere `.jar`-Datei.
5. **Nächster Zyklus:** Nachdem das Release gebaut wurde und neue Änderungen anstehen, fragst du den Nutzer, wie die nächste Hauptversion lauten soll, und startest wieder bei `-dev1`.
