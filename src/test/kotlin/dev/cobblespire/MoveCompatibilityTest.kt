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
}
