package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
class NativeEffectsTest {
 private fun a(uid:String,ability:String="blaze",types:Set<String> = setOf("normal"))=PokemonBridge.Actor(SpireCombatant(uid,"cobblemon:rattata",types,100),uid,ability,mutableMapOf("hp" to 100,"atk" to 100,"def" to 100,"spa" to 100,"spd" to 100,"spe" to 100),mapOf("hp" to 5),emptySet(),emptyList())
 private fun b(p:PokemonBridge.Actor,e:PokemonBridge.Actor)=SpireBattle(listOf(SpireBattlePlayer(UUID(1,2),listOf(p.body))),listOf(e.body),1)
 @Test fun `damage preview does not consume RNG and ordinary tackle range matches reference`(){val p=a("player:0");val e=a("enemy:0");val fx=NativeEffects(listOf(p,e).associateBy{it.body.uid},2)
  assertEquals(24 to 28,fx.estimate(p.body,e.body,MoveCompatibility.move("tackle")));assertEquals(24 to 28,fx.estimate(p.body,e.body,MoveCompatibility.move("tackle")))
 }
 @Test fun `ability immunity is separate from softened type immunity and preview does not heal`(){
  val p=a("player:0");val e=a("enemy:0","waterabsorb",setOf("water"));e.body.hp=50;val fx=NativeEffects(listOf(p,e).associateBy{it.body.uid},2);val water=MoveCompatibility.move("watergun")
  assertEquals(0 to 0,fx.estimate(p.body,e.body,water));assertEquals(50,e.body.hp)
  assertFalse(fx.canHit(p.body,e.body,water,b(p,e)));assertEquals(75,e.body.hp)
  val lev=a("enemy:1","levitate",setOf("flying"));val f=NativeEffects(listOf(p,lev).associateBy{it.body.uid},2)
  assertFalse(f.canHit(p.body,lev.body,MoveCompatibility.move("mudshot"),b(p,lev)))
 }
 @Test fun `guts gains attack and prevents the ordinary physical burn penalty`(){
  val p=a("player:0","guts");p.body.afflict("burn",1);val e=a("enemy:0");val fx=NativeEffects(listOf(p,e).associateBy{it.body.uid},2)
  assertEquals(42,fx.estimate(p.body,e.body,MoveCompatibility.move("tackle")).second)
  val other=a("player:1");other.body.afflict("burn",1);val normal=NativeEffects(listOf(other,e).associateBy{it.body.uid},2)
  assertEquals(14,normal.estimate(other.body,e.body,MoveCompatibility.move("tackle")).second)
 }
 @Test fun `clear body blocks hostile stat drops and still allows own boosts`(){val p=a("player:0");val e=a("enemy:0","clearbody");val fx=NativeEffects(listOf(p,e).associateBy{it.body.uid},2);val battle=b(p,e)
  fx.hit(p.body,e.body,MoveCompatibility.move("tailwhip"),0,battle);assertNull(e.body.stages["def"])
  fx.hit(e.body,e.body,MoveCompatibility.move("swordsdance"),0,battle);assertEquals(2,e.body.stages["atk"])
 }
 @Test fun `status immunities and own tempo are preserved`(){val p=a("player:0");val e=a("enemy:0","owntempo",setOf("electric"));val fx=NativeEffects(listOf(p,e).associateBy{it.body.uid},2);val battle=b(p,e)
  fx.hit(p.body,e.body,MoveCompatibility.move("glare"),0,battle);fx.hit(p.body,e.body,MoveCompatibility.move("confuseray"),0,battle);assertTrue(e.body.statuses.isEmpty())
 }
 @Test fun `self stat effects resolve once after all target effects`(){val p=a("player:0");val e=a("enemy:0");val fx=NativeEffects(listOf(p,e).associateBy{it.body.uid},2);val battle=b(p,e);val move=MoveCompatibility.move("superpower")
  fx.beforeAction(p.body,move,battle);fx.hit(p.body,e.body,move,10,battle);fx.hit(p.body,e.body,move,10,battle);assertNull(p.body.stages["atk"])
  fx.resolved(p.body,move,20,battle);assertEquals(-1,p.body.stages["atk"]);assertEquals(-1,p.body.stages["def"])
 }
 @Test fun `a self secondary boost still applies when the target is knocked out`(){val p=a("player:0");val e=a("enemy:0");val fx=NativeEffects(listOf(p,e).associateBy{it.body.uid},2);val battle=b(p,e);val move=MoveCompatibility.move("flamecharge")
  e.body.hp=0;fx.hit(p.body,e.body,move,10,battle);assertEquals(1,p.body.stages["spe"])
 }
 @Test fun `recovery uses original half-up rounding for odd maximum HP`(){
  val p=a("player:0").copy(body=SpireCombatant("player:0","cobblemon:charizard",setOf("fire","flying"),153));val fx=NativeEffects(mapOf(p.body.uid to p),2)
  // Installed engine actions.useMove(recover): maxHP 153, HP 1 -> HP 78.
  assertEquals(77,fx.healing(p.body,MoveCompatibility.move("recover")))
 }
 @Test fun `switch clears original volatile confusion and keeps major status duration`(){
  val p=a("player:0");val into=a("player:1");val e=a("enemy:0");val fx=NativeEffects(listOf(p,into,e).associateBy{it.body.uid},2);val battle=b(p,e)
  p.body.afflict("sleep",1,2);p.body.afflict("confusion",1,4);p.body.afflict("flinch",1)
  fx.switched(p.body,into.body,battle);assertEquals(2,p.body.statuses["sleep"]!!.remaining);assertFalse("confusion" in p.body.statuses);assertFalse("flinch" in p.body.statuses)
 }
}
