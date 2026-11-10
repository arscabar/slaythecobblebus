package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.random.Random
import java.util.UUID

class HandDeckTest {
 private fun deck(n:Int=10)=BattleDeck((1L..n.toLong()).map{MoveInstance(it,SpireMove("hit",1,1))},Random(1))
 private fun battle(d:BattleDeck):SpireBattle {val p=SpireCombatant("p","p",setOf("normal"),100,d);return SpireBattle(listOf(SpireBattlePlayer(UUID(1,2),listOf(p))),listOf(SpireCombatant("e","e",setOf("normal"),999)),1).also{it.start()}}
 @Test fun `turn starts with five and playing never instantly replaces the card`(){val d=deck();val b=battle(d);val c=d.hand[0];b.useMove(UUID(1,2),0,c.uid,0,"play",b.revision);assertEquals(4,d.hand.size);assertEquals(5,d.draw.size);assertEquals(1,d.discard.size)}
 @Test fun `end turn discards all normal cards before dealing five for next turn`(){val d=deck();val b=battle(d);val old=d.hand.map{it.uid}.toSet();b.endTurn(UUID(1,2),"end",b.revision);assertEquals(5,d.hand.size);assertTrue(d.discard.map{it.uid}.containsAll(old));assertEquals(2,b.round)}
 @Test fun `draw effect adds cards and an exhausted support card never enters discard`(){val d=deck();val b=battle(d);d.hand[0]=MoveInstance(99,TrainerCards.encourage);b.useMove(UUID(1,2),0,99,0,"support",b.revision);assertEquals(6,d.hand.size);assertEquals(3,d.draw.size);assertEquals(1,d.exhausted.size);assertTrue(d.discard.isEmpty());assertEquals(2,b.players[0].energy)}
 @Test fun `playing draw card is not eligible for its own reshuffle`(){val c=MoveInstance(1,SpireMove("cantrip",1,drawCards=2,trainer=true,selfTarget=true));val d=BattleDeck(listOf(c),Random(1));val b=battle(d);b.useMove(UUID(1,2),0,1,0,"one",b.revision);assertTrue(d.hand.isEmpty());assertEquals(listOf(1L),d.discard.map{it.uid})}
 @Test fun `discard reshuffles only when draw is empty and never includes exhausted cards`(){val d=deck(6);d.beginTurn(1);val c=d.take(0,1);d.resolve(c,1);assertEquals(1,d.draw.size);d.drawCards(2,1);assertTrue(d.transfers.any{it.cause=="RESHUFFLE"});assertTrue(d.discard.isEmpty())}
 @Test fun `exhausted cards cannot return in a later turn`(){val c=MoveInstance(1,SpireMove("one",1,exhaust=true));val d=BattleDeck(listOf(c,MoveInstance(2,SpireMove("two",1))),Random(1));d.beginTurn(1);val index=d.hand.indexOfFirst{it.uid==1L};val used=d.take(index,1);d.resolve(used,1);d.finishTurn(1);d.beginTurn(2);assertEquals(listOf(2L),d.hand.map{it.uid});assertEquals(listOf(1L),d.exhausted.map{it.uid})}
 @Test fun `retained cards survive ordinary turn end but switching discards every hand card`(){val d=deck();d.beginTurn(1);val c=d.hand[0];c.retain=true;d.finishTurn(1);assertEquals(listOf(c),d.hand);d.beginTurn(2);assertEquals(6,d.hand.size);d.discardHand(2);assertTrue(d.hand.isEmpty());assertTrue(c in d.discard)}
 @Test fun `extra draw is bounded without losing overflow cards`(){val d=deck(20);d.beginTurn(1);d.drawCards(20,1);assertEquals(10,d.hand.size);assertEquals(10,d.draw.size);assertTrue(d.discard.isEmpty())}
 @Test fun `the same round start cannot generate another free hand`(){val d=deck();d.beginTurn(1);d.beginTurn(1);assertEquals(5,d.hand.size);assertEquals(5,d.draw.size)}
 @Test fun `switching redeals a new hand even when a deck was already active this round`(){val d=deck();d.beginTurn(1);val old=d.hand.map{it.uid}.toSet();d.switchIn(1);assertEquals(5,d.hand.size);assertTrue(d.hand.none{it.uid in old});assertEquals(5,d.discard.size)}
 @Test fun `all pile transitions conserve unique cards after repeated turns`(){val d=deck();repeat(12){round->d.beginTurn(round);if(d.hand.isNotEmpty()){val c=d.take(0,round);d.resolve(c,round)};d.finishTurn(round);val ids=(d.hand+d.draw+d.discard+d.exhausted).map{it.uid};assertEquals(10,ids.size);assertEquals(10,ids.distinct().size)}}
}
