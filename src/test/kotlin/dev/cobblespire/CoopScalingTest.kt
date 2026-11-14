package dev.cobblespire
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class CoopScalingTest {
    private fun mon(name:String,hp:Int=100,move:SpireMove=SpireMove("tackle",1,10))=
        SpireCombatant(name,name,setOf("normal"),hp,BattleDeck((1L..8L).map{MoveInstance(it,move)},Random(name.hashCode())))

    @Test fun `stage base budgets and grade multipliers match v4 definitions`(){
        assertEquals(180,SpirePartyRules.stageBaseBudget(1,"NORMAL","ACT1-R01"))
        assertEquals(240,SpirePartyRules.stageBaseBudget(1,"NORMAL","ACT1-R05"))
        assertEquals(240,SpirePartyRules.stageBaseBudget(1,"ELITE","ACT1-E01"))
        assertEquals(240,SpirePartyRules.stageBaseBudget(1,"BOSS","ACT1-B01"))
        assertEquals(368,SpirePartyRules.stageBaseBudget(2,"NORMAL","ACT2-M01"))
        assertEquals(455,SpirePartyRules.stageBaseBudget(2,"BOSS","ACT2-B01"))
        assertEquals(640,SpirePartyRules.stageBaseBudget(3,"NORMAL","ACT3-G01"))
        assertEquals(760,SpirePartyRules.stageBaseBudget(3,"BOSS","ACT3-B01"))

        assertEquals(1.0,SpirePartyRules.gradeMultiplier("NORMAL"),1e-6)
        assertEquals(1.55,SpirePartyRules.gradeMultiplier("ELITE"),1e-6)
        assertEquals(2.4,SpirePartyRules.gradeMultiplier("BOSS"),1e-6)
    }

    @Test fun `encounter hp budget scales with party size enemy count act and tier`(){
        // 1P Act1 Early Normal, 1 enemy -> 180 * 1.0 * 1.0 * 1.0 = 180
        assertEquals(listOf(180),SpirePartyRules.encounterHpBudget(1,"NORMAL","ACT1-R01",listOf(1.0),1))
        // 4P Act1 Early Normal, 1 enemy -> 180 * 1.0 * 1.0 * 3.55 = 639
        assertEquals(listOf(639),SpirePartyRules.encounterHpBudget(1,"NORMAL","ACT1-R01",listOf(1.0),4))
        // 2P Act1 Elite, 2 enemies (1.0, 1.0) -> 240 * 1.55 * 1.10 * 1.85 = 757.02 -> 757 split 379, 378
        val act1Elite2P=SpirePartyRules.encounterHpBudget(1,"ELITE","ACT1-E01",listOf(1.0,1.0),2)
        assertEquals(757,act1Elite2P.sum())
        assertEquals(2,act1Elite2P.size)
        // 4P Act3 Boss, 1 enemy -> 760 * 2.4 * 1.0 * 3.55 = 6475.2 -> 6475
        assertEquals(listOf(6475),SpirePartyRules.encounterHpBudget(3,"BOSS","ACT3-B01",listOf(1.0),4))
    }

    @Test fun `coop battle waits for all alive players before enemy phase`(){
        val p1=UUID(1,1);val p2=UUID(2,2)
        val b=SpireBattle(
            listOf(SpireBattlePlayer(p1,listOf(mon("p1a"))),SpireBattlePlayer(p2,listOf(mon("p2a")))),
            listOf(mon("enemy",500)),
            42
        ).also{it.start()}
        assertEquals(1,b.round)
        val card1=b.players[0].active.deck!!.hand[0]!!
        b.useMove(p1,0,card1.uid,0,UUID.randomUUID().toString(),b.revision)
        assertEquals(490,b.enemies[0].hp)

        b.endTurn(p1,UUID.randomUUID().toString(),b.revision)
        assertEquals(1,b.round)
        assertTrue(b.players[0].ended)
        assertFalse(b.players[1].ended)
        assertThrows(IllegalArgumentException::class.java){
            b.endTurn(p1,UUID.randomUUID().toString(),b.revision)
        }

        b.endTurn(p2,UUID.randomUUID().toString(),b.revision)
        assertEquals(2,b.round)
        assertFalse(b.players[0].ended)
        assertFalse(b.players[1].ended)
    }

    @Test fun `disconnecting player unblocks turn progression and last player triggers defeat`(){
        val p1=UUID(1,1);val p2=UUID(2,2)
        val b=SpireBattle(
            listOf(SpireBattlePlayer(p1,listOf(mon("p1a"))),SpireBattlePlayer(p2,listOf(mon("p2a")))),
            listOf(mon("enemy",500)),
            42
        ).also{it.start()}
        b.endTurn(p1,UUID.randomUUID().toString(),b.revision)
        assertEquals(1,b.round)

        // p2 disconnects after p1 ended turn -> enemyPhase runs and advances to round 2
        b.removePlayer(p2)
        assertEquals(2,b.round)
        assertFalse(b.players[1].alive)
        assertEquals(SpireBattlePhase.PLAYER,b.phase)

        // p1 disconnects -> all players gone -> DEFEAT
        b.removePlayer(p1)
        assertEquals(SpireBattlePhase.DEFEAT,b.phase)
    }

    @Test fun `spread enemy move damages all alive players`(){
        val p1=UUID(1,1);val p2=UUID(2,2)
        val b=SpireBattle(
            listOf(SpireBattlePlayer(p1,listOf(mon("p1a",100))),SpireBattlePlayer(p2,listOf(mon("p2a",100)))),
            listOf(mon("enemy",500)),
            42
        ).also{it.start()}
        b.intents=listOf(EnemyIntent(0,SpireMove("surf",1,20,targetAll=true)))
        b.endTurn(p1,UUID.randomUUID().toString(),b.revision)
        b.endTurn(p2,UUID.randomUUID().toString(),b.revision)
        assertTrue(b.players[0].active.hp<100)
        assertTrue(b.players[1].active.hp<100)
    }

    @Test fun `temporary offline coop teammate yields turn without defeat and can rejoin`(){
        val p1=UUID(1,1);val p2=UUID(2,2)
        val b=SpireBattle(
            listOf(SpireBattlePlayer(p1,listOf(mon("p1a",120))),SpireBattlePlayer(p2,listOf(mon("p2a",120)))),
            listOf(mon("enemy",500)),
            99,
            speedOf={m->if(m.uid=="enemy")70 else 110}
        ).also{it.start()}
        b.endTurn(p1,UUID.randomUUID().toString(),b.revision)
        val revBeforeOffline=b.revision
        b.setPlayerOnline(p2,false)
        assertTrue(b.players[1].alive)
        assertTrue(b.round>=2)
        assertTrue(b.revision>revBeforeOffline)
        assertThrows(IllegalArgumentException::class.java){
            b.endTurn(p2,UUID.randomUUID().toString(),b.revision)
        }
        b.setPlayerOnline(p2,true)
        assertTrue(b.players[1].alive)
        b.endTurn(p1,UUID.randomUUID().toString(),b.revision)
        // After round advances, online p2 gets energy/turn again and can act
        if(b.players[1].ended)b.endTurn(p1,UUID.randomUUID().toString(),b.revision)
        assertFalse(b.players[1].ended)
        val c2=b.players[1].active.deck!!.hand[0]!!
        b.useMove(p2,0,c2.uid,0,UUID.randomUUID().toString(),b.revision)
        assertEquals(490,b.enemies[0].hp)
    }
}
