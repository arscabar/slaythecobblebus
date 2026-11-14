package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class MoveCompatibilityTest {
 @Test fun `plain attacks status boosts healing and drain have explicit metadata support`(){
  listOf("tackle","ember","flamethrower","tailwhip","glare","leechlife","agility","recover").forEach{assertNull(MoveCompatibility.reason(it),it)}
 }
 @Test fun `unknown callbacks special switches friendly fire and freezing are not silently dropped`(){
  listOf("protect","uturn","eruption","earthquake","icepunch","roost","not_a_real_move").forEach{assertNotNull(MoveCompatibility.reason(it),it)}
 }
 @Test fun `source upgrade rules affect only the selected move instance`(){
  val hydro=MoveCompatibility.move("hydropump");val a=MoveInstance(1,hydro,true);val b=MoveInstance(2,hydro)
  assertEquals(2,MoveCompatibility.cost(a));assertEquals(3,MoveCompatibility.cost(b));assertEquals(1.15,MoveCompatibility.upgradeMultiplier("flamethrower"));assertEquals(1.1,MoveCompatibility.upgradeMultiplier("gigadrain"))
 }
 @Test fun `type chart preserves exact immunity quarter resistance and quadruple weakness`(){
  assertEquals(0.0,MoveCompatibility.multiplier("normal",setOf("ghost")))
  assertEquals(.25,MoveCompatibility.multiplier("grass",setOf("fire","flying")))
  assertEquals(4.0,MoveCompatibility.multiplier("rock",setOf("fire","flying")))
 }
 @Test fun `reference damage formula floors at the defined steps`(){assertEquals(19,MoveCompatibility.baseDamage(40,100.0,100.0));assertEquals(41,MoveCompatibility.baseDamage(90,100.0,100.0))}
 @Test fun `self boosts scale cost by stage and high power moves reach 4 to 5 cost`(){
  assertEquals(1,MoveCompatibility.move("withdraw").cost)
  assertEquals(2,MoveCompatibility.move("irondefense").cost)
  assertEquals(4,MoveCompatibility.move("focusblast").cost)
  val supported=MoveCompatibility.specs.keys.filter{MoveCompatibility.reason(it)==null}.map{MoveCompatibility.move(it)}
  assertTrue(supported.any{it.cost==4},"Expected at least one 4-cost move")
  assertTrue(supported.any{it.cost==5},"Expected at least one 5-cost move")
 }
 @Test fun `multihit moves validate hit ranges estimate min max and stop on target ko`(){
  listOf("doublekick","doublehit","dualwingbeat","bulletseed","rockblast","iciclespear").forEach{assertNull(MoveCompatibility.reason(it),it)}
  listOf("populationbomb","tripleaxel","beatup").forEach{assertNotNull(MoveCompatibility.reason(it),it)}
  val dk=MoveCompatibility.move("doublekick")
  assertEquals(2,dk.minHits);assertEquals(2,dk.maxHits);assertEquals(1,dk.cost)
  val rb=MoveCompatibility.move("rockblast")
  assertEquals(2,rb.minHits);assertEquals(5,rb.maxHits);assertEquals(2,rb.cost)
  val src=SpireCombatant("player:0","cobblemon:turtwig",setOf("grass"),200)
  val tgt=SpireCombatant("enemy:0","cobblemon:geodude",setOf("rock"),200)
  val fx=NativeEffects(mapOf(
   "player:0" to PokemonBridge.Actor(src,"Turtwig","overgrow",mutableMapOf("hp" to 200,"atk" to 100,"def" to 100,"spa" to 100,"spd" to 100,"spe" to 80),emptyMap(),emptySet(),emptyList()),
   "enemy:0" to PokemonBridge.Actor(tgt,"Geodude","rockhead",mutableMapOf("hp" to 200,"atk" to 100,"def" to 100,"spa" to 100,"spd" to 100,"spe" to 50),emptyMap(),emptySet(),emptyList())
  ),1L)
  val singleEst=fx.estimate(src,tgt,dk.copy(minHits=1,maxHits=1))
  val multiEst=fx.estimate(src,tgt,dk)
  assertEquals(singleEst.first*2,multiEst.first)
  assertEquals(singleEst.second*2,multiEst.second)

  val p1=java.util.UUID(1,1)
  val fragile=SpireCombatant("enemy:0","cobblemon:geodude",setOf("rock"),15)
  val attacker=SpireCombatant("player:0","cobblemon:turtwig",setOf("grass"),200,BattleDeck((1L..6L).map{MoveInstance(it,rb)},kotlin.random.Random(7)))
  val b=SpireBattle(listOf(SpireBattlePlayer(p1,listOf(attacker))),listOf(fragile),7L).also{it.start()}
  val card=attacker.deck!!.hand[0]!!
  b.useMove(p1,0,card.uid,0,java.util.UUID.randomUUID().toString(),b.revision)
  assertEquals(0,fragile.hp)
  val hits=b.events.filter{it.name=="MOVE_HIT"}
  assertEquals(1,hits.size)
  assertEquals(15,hits.single().amount)
  assertEquals(SpireBattlePhase.VICTORY,b.phase)
 }
}
