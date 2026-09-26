package dev.stonediscord.plugin.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MessageManager's format()/getRaw() and the private legacy->MiniMessage
 * converter don't touch the StoneDiscord plugin reference at all, so they
 * can be tested directly with `new MessageManager(null)` - no server/plugin
 * instance needed. load()/loadLanguage() (real file I/O via plugin.getResource())
 * are not covered here, see report.
 */
class MessageManagerTest {

    private static String convert(MessageManager mm, String input) throws Exception {
        Method method = MessageManager.class.getDeclaredMethod("convertLegacyToMiniMessage", String.class);
        method.setAccessible(true);
        return (String) method.invoke(mm, input);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void convertsLegacyColorCodeToMiniMessageTag() throws Exception {
        MessageManager mm = new MessageManager(null);
        assertEquals("<green>Hello", convert(mm, "&aHello"));
    }

    @Test
    void convertsSectionSignColorCodeAsWellAsAmpersand() throws Exception {
        MessageManager mm = new MessageManager(null);
        assertEquals("<red>Hi", convert(mm, "§cHi"));
    }

    @Test
    void convertsValidHexColorCode() throws Exception {
        MessageManager mm = new MessageManager(null);
        assertEquals("<#5865F2>Discord", convert(mm, "&#5865F2Discord"));
    }

    @Test
    void doesNotMisreadHexCodeTruncatedAtEndOfString() throws Exception {
        MessageManager mm = new MessageManager(null);
        // Only 4 hex digits before the string ends - must not throw or read
        // past the end (safeSub/isHex boundary check).
        String result = convert(mm, "&#5865");
        assertEquals("&#5865", result);
    }

    @Test
    void unknownLegacyCodeIsLeftAsPlainText() throws Exception {
        MessageManager mm = new MessageManager(null);
        // 'z' is not a valid legacy color/format code
        assertEquals("&zHello", convert(mm, "&zHello"));
    }

    @Test
    void textWithoutAnyColorCodeIsReturnedUnchanged() throws Exception {
        MessageManager mm = new MessageManager(null);
        assertEquals("Plain text, no colors", convert(mm, "Plain text, no colors"));
    }

    @Test
    void formatAppliesPlaceholdersAndRendersMiniMessage() {
        MessageManager mm = new MessageManager(null);
        Component result = mm.format("&aHi {player}!", Map.of("player", "Steve"));
        assertEquals("Hi Steve!", plain(result));
    }

    @Test
    void formatWithNullPlaceholdersLeavesTemplateAsIs() {
        MessageManager mm = new MessageManager(null);
        Component result = mm.format("Hello there", null);
        assertEquals("Hello there", plain(result));
    }

    @Test
    void getRawFallsBackToEmptyStringWhenNoLanguageDataLoaded() {
        MessageManager mm = new MessageManager(null);
        // Fresh instance, load() was never called (no plugin available in this
        // test) -> languageCache is empty. Must not throw / return null.
        assertEquals("", mm.getRaw("general.no-permission"));
    }

    @Test
    void placeholderSubstitutionHandlesMultipleOccurrencesOfSamePlaceholder() {
        MessageManager mm = new MessageManager(null);
        Component result = mm.format("{link} and again {link}", Map.of("link", "https://discord.gg/x"));
        assertEquals("https://discord.gg/x and again https://discord.gg/x", plain(result));
    }

    @Test
    void repeatedFormatCallsWithSameRawTextUseCacheButStayCorrect() {
        MessageManager mm = new MessageManager(null);
        // Same raw template, different placeholder values each call - this is
        // exactly the /discord hot path with 300 players joining/using the
        // command; the conversion cache must not leak stale placeholder values.
        Component first = mm.format("Hi {player}!", Map.of("player", "Alice"));
        Component second = mm.format("Hi {player}!", Map.of("player", "Bob"));

        assertEquals("Hi Alice!", plain(first));
        assertEquals("Hi Bob!", plain(second));
    }

    @Test
    void mixingLegacyHexAndPlaceholdersInOneStringWorks() {
        MessageManager mm = new MessageManager(null);
        Component result = mm.format("&#5865F2{player}&r done", Map.of("player", "Alice"));
        assertTrue(plain(result).contains("Alice"));
        assertTrue(plain(result).contains("done"));
    }
}
