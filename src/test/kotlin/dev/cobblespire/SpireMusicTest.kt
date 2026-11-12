package dev.cobblespire

import com.google.gson.JsonParser
import dev.cobblespire.client.SpireMusic
import dev.cobblespire.client.SpireMusic.Jingle
import dev.cobblespire.client.SpireMusic.Track
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpireMusicTest {
    @Test
    fun `encounter ids map to expected act elite and boss music tracks`() {
        assertEquals(Track.BATTLE_ACT1, SpireMusic.battleTrack("ACT1-R01"))
        assertEquals(Track.BATTLE_ACT1, SpireMusic.battleTrack("ACT1-R03"))
        assertEquals(Track.BATTLE_ELITE, SpireMusic.battleTrack("ACT1-R04"))
        assertEquals(Track.BATTLE_ELITE, SpireMusic.battleTrack("ACT1-R05"))
        assertEquals(Track.BOSS_ACT1, SpireMusic.battleTrack("ACT1-R06"))
        assertEquals(Track.BOSS_ACT1, SpireMusic.battleTrack("ACT1-R07"))

        assertEquals(Track.BATTLE_ACT2_MAGMA, SpireMusic.battleTrack("ACT2-M01"))
        assertEquals(Track.BATTLE_ELITE, SpireMusic.battleTrack("ACT2-M03"))
        assertEquals(Track.BOSS_ACT2_MAGMA, SpireMusic.battleTrack("ACT2-M04"))

        assertEquals(Track.BATTLE_ACT2_AQUA, SpireMusic.battleTrack("ACT2-A01"))
        assertEquals(Track.BATTLE_ELITE, SpireMusic.battleTrack("ACT2-A03"))
        assertEquals(Track.BOSS_ACT2_AQUA, SpireMusic.battleTrack("ACT2-A04"))

        assertEquals(Track.BATTLE_ACT3, SpireMusic.battleTrack("ACT3-G01"))
        assertEquals(Track.BATTLE_ELITE, SpireMusic.battleTrack("ACT3-G03"))
        assertEquals(Track.BATTLE_ELITE, SpireMusic.battleTrack("ACT3-G04"))
        assertEquals(Track.BOSS_ACT3, SpireMusic.battleTrack("ACT3-G05"))
        assertEquals(Track.BOSS_ACT3, SpireMusic.battleTrack("ACT3-G06"))
    }

    @Test
    fun `every track and jingle resolves in sounds json to a bundled ogg resource`() {
        val stream = javaClass.getResourceAsStream("/assets/cobblespire/sounds.json")
        assertNotNull(stream, "Missing /assets/cobblespire/sounds.json")
        val root = stream!!.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }

        val allIds = Track.entries.map { it.id } + Jingle.entries.map { it.id }
        for (id in allIds) {
            assertTrue(root.has(id), "sounds.json missing sound event '$id'")
            val sounds = root.getAsJsonObject(id).getAsJsonArray("sounds")
            assertTrue(sounds.size() > 0, "Sound event '$id' has empty sounds list")
            val name = sounds[0].asJsonObject.get("name").asString
            assertTrue(name.startsWith("cobblespire:"), "Expected bundled cobblespire resource for '$id', got '$name'")
            val relPath = "/assets/cobblespire/sounds/${name.removePrefix("cobblespire:")}.ogg"
            assertNotNull(javaClass.getResource(relPath), "Missing bundled OGG resource '$relPath' for '$id'")
        }
    }
}
