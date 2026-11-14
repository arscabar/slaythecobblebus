package dev.cobblespire
import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SurrenderTest {
    private fun battle():SpireBattle {val ps=(1..3).map{SpireBattlePlayer(UUID(0,it.toLong()),listOf(SpireCombatant("p$it","x",setOf("normal"),100)))};return SpireBattle(ps,listOf(SpireCombatant("enemy","x",setOf("normal"),100)),9).also{it.start()}}
    @Test fun `every actual participant must agree and a player can withdraw their vote`(){
        val b=battle();b.voteSurrender(UUID(0,1),true,b.revision);assertEquals(SpireBattlePhase.PLAYER,b.phase)
        b.voteSurrender(UUID(0,1),false,b.revision);assertEquals(0,b.surrenderCount)
        b.voteSurrender(UUID(0,2),true,b.revision);b.voteSurrender(UUID(0,3),true,b.revision);assertEquals(SpireBattlePhase.PLAYER,b.phase)
        b.voteSurrender(UUID(0,1),true,b.revision);assertEquals(SpireBattlePhase.DEFEAT,b.phase);assertTrue(b.surrendered)
        assertEquals(0,b.victoryCount);assertTrue(b.events.none{it.name=="PROGRESSION"||it.name=="BATTLE_WON"});assertTrue(b.players.all{it.active.hp==100&&it.energy==3})
    }
    @Test fun `outsiders and stale votes cannot decide or alter a battle`(){
        val b=battle();assertThrows(IllegalArgumentException::class.java){b.voteSurrender(UUID(0,99),true,b.revision)}
        val old=b.revision;b.voteSurrender(UUID(0,1),true,old)
        assertThrows(IllegalArgumentException::class.java){b.voteSurrender(UUID(0,2),true,old)};assertEquals(1,b.surrenderCount)
    }
    @Test fun `departed participants neither vote nor block remaining unanimous surrender`(){
        val b=battle();b.voteSurrender(UUID(0,1),true,b.revision);b.voteSurrender(UUID(0,2),true,b.revision)
        b.removePlayer(UUID(0,3))
        assertEquals(2,b.participantCount);assertEquals(2,b.surrenderCount)
        assertTrue(b.surrendered);assertEquals(SpireBattlePhase.DEFEAT,b.phase)
        assertThrows(IllegalArgumentException::class.java){b.voteSurrender(UUID(0,3),true,b.revision)}
        assertTrue(b.events.none{it.name=="PROGRESSION"||it.name=="BATTLE_WON"})
    }
    @Test fun `departed voters are removed while other participants can keep playing`(){
        val b=battle();b.voteSurrender(UUID(0,1),true,b.revision);b.removePlayer(UUID(0,1))
        assertEquals(2,b.participantCount);assertEquals(0,b.surrenderCount);assertFalse(b.surrendered)
        b.voteSurrender(UUID(0,2),true,b.revision);assertEquals(SpireBattlePhase.PLAYER,b.phase)
        b.voteSurrender(UUID(0,3),true,b.revision);assertTrue(b.surrendered)
    }
}
