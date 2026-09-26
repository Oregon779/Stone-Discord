package dev.stonediscord.plugin.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public int tryUse(UUID player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }

        // Bugfix: System.currentTimeMillis() folgt der Systemuhr und kann bei
        // NTP-Korrekturen ruckartig springen (auch rueckwaerts) - ein
        // Rueckwaertssprung liess Spieler zuvor eine viel zu lange Cooldown-Zeit
        // angezeigt bekommen (cooldownSeconds - negative elapsedSeconds). Reine
        // Zeitspannen (nicht persistiert, nur In-Memory waehrend der Laufzeit)
        // gehoeren auf System.nanoTime(), das fuer Dauer-Messungen innerhalb
        // derselben JVM garantiert monoton ist.
        long now = System.nanoTime();
        Long last = lastUse.get(player);
        if (last != null) {
            long elapsedSeconds = (now - last) / 1_000_000_000L;
            if (elapsedSeconds < cooldownSeconds) {
                return (int) (cooldownSeconds - elapsedSeconds);
            }
        }

        lastUse.put(player, now);
        return 0;
    }

    public void remove(UUID player) {
        // Ohne diesen Aufruf (siehe PlayerQuitListener) waechst die Map ueber die
        // Serverlaufzeit unbegrenzt mit jedem JEMALS eingeloggten Spieler - bei
        // 250+ CCU und hoher Spielerfluktuation ueber Wochen/Monate ein echtes
        // Memory-Leak. Nach dem Fix ist die Groesse durch die aktuell online
        // befindliche Spielerzahl gedeckelt.
        lastUse.remove(player);
    }

    public void clear() {
        lastUse.clear();
    }
}
