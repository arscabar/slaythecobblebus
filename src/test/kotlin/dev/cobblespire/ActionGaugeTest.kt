package dev.cobblespire

import dev.cobblespire.client.SpireMusic
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class ActionGaugeTest {
    private val pid = UUID(10, 20)
    private fun mon(uid: String, hp: Int = 200, move: SpireMove = SpireMove("tackle", 1, 10)) =
        SpireCombatant(uid, uid, setOf("normal"), hp, BattleDeck((1L..10L).map { MoveInstance(it, move) }, Random(uid.hashCode())))

    @Test
    fun `faster player laps slower enemy in HSR action gauge mode`() {
        val speeds = mutableMapOf("player:0" to 150, "enemy:0" to 50)
        val p = mon("player:0")
        val e = mon("enemy:0", hp = 999)
        var enemyActs = 0
        var playerStarts = 0
        val b = SpireBattle(
            listOf(SpireBattlePlayer(pid, listOf(p))),
            listOf(e),
            42L,
            hook = SpireBattleHook { ev, battle ->
                if (ev.name == "REFRESH_INTENTS") {
                    battle.intents = listOf(EnemyIntent(0, SpireMove("scratch", 1, 5)))
                } else if (ev.name == "ENEMY_ACTION_RESOLVED") {
                    enemyActs++
                } else if (ev.name == "ROUND_START") {
                    playerStarts++
                }
            },
            speedOf = { m -> speeds[m.uid] ?: 100 }
        )
        b.start()
        assertEquals(1, playerStarts)
        assertEquals(0, enemyActs)
        // Turn 1 ends -> at 150 vs 50 (3:1), player acts 2nd time before enemy reaches 100%
        b.endTurn(pid, "end1", b.revision)
        assertEquals(2, playerStarts)
        assertEquals(0, enemyActs)
        // Turn 2 ends -> player acts 3rd time while enemy is still charging (166.7 < 200) -> Wait:
        // At t=66.7: P1(0)->reset 66.7, E=133.3; t=133.3: P2(0)->reset 66.7, E=66.7;
        // On exact tie 66.7 <= 66.7+0.01, player still goes 3rd time, then enemy fires after Turn 3!
        b.endTurn(pid, "end2", b.revision)
        assertEquals(3, playerStarts)
        assertEquals(0, enemyActs)
        b.endTurn(pid, "end3", b.revision)
        assertEquals(4, playerStarts)
        assertEquals(1, enemyActs)
    }

    @Test
    fun `faster enemy gets at most one opening strike before player first turn`() {
        val speeds = mutableMapOf("player:0" to 40, "enemy:0" to 160)
        val p = mon("player:0", hp = 200)
        val e = mon("enemy:0", hp = 500)
        var enemyActs = 0
        val b = SpireBattle(
            listOf(SpireBattlePlayer(pid, listOf(p))),
            listOf(e),
            42L,
            hook = SpireBattleHook { ev, battle ->
                if (ev.name == "REFRESH_INTENTS") {
                    battle.intents = listOf(EnemyIntent(0, SpireMove("quickattack", 1, 10)))
                } else if (ev.name == "ENEMY_ACTION_RESOLVED") {
                    enemyActs++
                }
            },
            speedOf = { m -> speeds[m.uid] ?: 100 }
        )
        b.start()
        assertEquals(1, enemyActs, "Faster enemy opens with 1 first-strike without multi-lapping before Turn 1")
        assertTrue(p.hp < 200)
        assertEquals(SpireBattlePhase.PLAYER, b.phase)
    }

    @Test
    fun `boss music tracks resolve iconic act boss ids`() {
        assertEquals(SpireMusic.Track.BOSS_ACT1, SpireMusic.battleTrack("BOSS_ACT1"))
        assertEquals(SpireMusic.Track.BOSS_ACT2_MAGMA, SpireMusic.battleTrack("BOSS_ACT2_MAGMA"))
        assertEquals(SpireMusic.Track.BOSS_ACT2_AQUA, SpireMusic.battleTrack("BOSS_ACT2_AQUA"))
        assertEquals(SpireMusic.Track.BOSS_ACT3, SpireMusic.battleTrack("BOSS_ACT3"))
    }
}
