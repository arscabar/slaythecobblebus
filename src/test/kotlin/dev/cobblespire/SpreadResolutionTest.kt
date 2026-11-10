package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import kotlin.random.Random
class SpreadResolutionTest {
 @Test fun `spread action pays and replaces once and finishes effects before a simultaneous defeat`(){
  val id=UUID(1,2);val move=SpireMove("spread",1,10,targetAll=true);val deck=BattleDeck((1L..6).map{MoveInstance(it,move)},Random(1));val p=SpireCombatant("p","p",setOf("normal"),10,deck)
  val foes=listOf("a","b").map{SpireCombatant(it,it,setOf("normal"),10)}
  val effects=object:SpireEffects{override fun hit(source:SpireCombatant,target:SpireCombatant,move:SpireMove,damage:Int,battle:SpireBattle){source.hp=0}}
  val b=SpireBattle(listOf(SpireBattlePlayer(id,listOf(p))),foes,1,effects=effects);b.start();val card=deck.hand[0]!!;b.useMove(id,0,card.uid,0,"one",b.revision)
  assertTrue(foes.all{it.hp==0});assertEquals(SpireBattlePhase.DEFEAT,b.phase);assertEquals(2,b.players[0].energy)
  assertEquals(1,b.events.count{it.name=="MOVE_RESOLVED"});assertEquals(1,b.events.count{it.name=="CARD_PLAYED"})
 }
 @Test fun `enemy type immunity prevents both damage and secondary status effects`(){
  val id=UUID(1,2);val player=SpireCombatant("p","p",setOf("ghost"),100);val foe=SpireCombatant("e","e",setOf("normal"),100);var hit=0
  val effects=object:SpireEffects{override fun hit(source:SpireCombatant,target:SpireCombatant,move:SpireMove,damage:Int,battle:SpireBattle){hit++}}
  val b=SpireBattle(listOf(SpireBattlePlayer(id,listOf(player))),listOf(foe),1,effectiveness={_,_->0.0},effects=effects)
  b.intents=listOf(EnemyIntent(0,SpireMove("immune",1,10,status="paralysis",duration=2)));b.start();b.endTurn(id,"one",b.revision)
  assertEquals(100,player.hp);assertTrue(player.statuses.isEmpty());assertEquals(0,hit)
 }
}
