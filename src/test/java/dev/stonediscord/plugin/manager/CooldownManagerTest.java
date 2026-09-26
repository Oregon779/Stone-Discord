package dev.stonediscord.plugin.manager;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownManagerTest {

    @Test
    void firstUseIsAlwaysAllowed() {
        CooldownManager manager = new CooldownManager();
        assertEquals(0, manager.tryUse(UUID.randomUUID(), 5));
    }

    @Test
    void secondImmediateUseIsBlockedAndReportsRemainingSeconds() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();

        assertEquals(0, manager.tryUse(player, 5));
        int remaining = manager.tryUse(player, 5);

        assertTrue(remaining > 0 && remaining <= 5, "remaining should be within (0, 5], was " + remaining);
    }

    @Test
    void cooldownOfZeroOrNegativeAlwaysAllowsUse() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();

        assertEquals(0, manager.tryUse(player, 0));
        assertEquals(0, manager.tryUse(player, 0));
        assertEquals(0, manager.tryUse(player, -5));
    }

    @Test
    void differentPlayersHaveIndependentCooldowns() {
        CooldownManager manager = new CooldownManager();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        assertEquals(0, manager.tryUse(playerA, 10));
        // playerB must not be affected by playerA's cooldown
        assertEquals(0, manager.tryUse(playerB, 10));
    }

    @Test
    void removeClearsOnlyThatPlayersCooldown() {
        CooldownManager manager = new CooldownManager();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        manager.tryUse(playerA, 30);
        manager.tryUse(playerB, 30);

        manager.remove(playerA);

        // playerA was cleaned up (e.g. PlayerQuitEvent) -> allowed again immediately
        assertEquals(0, manager.tryUse(playerA, 30));
        // playerB is untouched and still on cooldown
        assertTrue(manager.tryUse(playerB, 30) > 0);
    }

    @Test
    void clearResetsEveryPlayer() {
        CooldownManager manager = new CooldownManager();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        manager.tryUse(playerA, 30);
        manager.tryUse(playerB, 30);

        manager.clear();

        assertEquals(0, manager.tryUse(playerA, 30));
        assertEquals(0, manager.tryUse(playerB, 30));
    }
}
