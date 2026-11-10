package dev.cobblespire
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import com.google.gson.JsonParser
import com.google.gson.JsonObject
class DamageReferenceTest {
 @Test fun `native simulator reference vectors match supported damage calculation`(){
  val stream=requireNotNull(javaClass.getResourceAsStream("/damage_reference_cases.json"))
  val rows=stream.bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")}
  fun actor(j:JsonObject,uid:String):PokemonBridge.Actor{
   val body=SpireCombatant(uid,j.text("species"),j.getAsJsonArray("types").map{it.asString}.toSet(),j.number("maxHp"));body.hp=j.number("hp")
   j.getAsJsonObject("boosts").entrySet().forEach{(k,v)->if(v.asInt!=0)body.stages[k]=v.asInt}
   if(j.text("status")=="brn")body.afflict("burn",0)
   return PokemonBridge.Actor(body,uid,j.text("ability"),j.getAsJsonObject("stats").entrySet().associate{it.key to it.value.asInt}.toMutableMap(),emptyMap(),emptySet(),emptyList())
  }
  val errors=mutableListOf<String>()
  for(row in rows){val r=row.asJsonObject;val a=actor(r.getAsJsonObject("source"),"player:0");val b=actor(r.getAsJsonObject("target"),"enemy:0")
   if(r.get("flashfire").asBoolean)a.body.stages["flashfire"]=1
   val fx=NativeEffects(listOf(a,b).associateBy{it.body.uid},1);val m=MoveCompatibility.specs.getValue(r.text("move"));val move=SpireMove(r.text("move"),1,m.number("basePower"),m.text("type").lowercase())
   val result=fx.referenceDamage(a.body,b.body,move,r.number("variance"),r.get("critical").asBoolean,spread=r.get("spread").asBoolean)
   if(result!=r.number("damage")&&errors.size<24)errors.add("${a.body.species}/${a.ability} vs ${b.body.species}/${b.ability} ${move.id} variance=${r.number("variance")} critical=${r.get("critical")} expected=${r.number("damage")} got=$result")
  }
  assertEquals(640,rows.size());assertTrue(errors.isEmpty(),errors.joinToString("\n"))
 }
}
