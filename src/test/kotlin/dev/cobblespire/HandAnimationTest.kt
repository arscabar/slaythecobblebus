package dev.cobblespire
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.cobblespire.client.HandAnimation
import dev.cobblespire.client.SeenDeckMotions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class HandAnimationTest {
 @Test fun `duplicate or older snapshots do not replay deck transfers`(){
  SeenDeckMotions.clear();val c=JsonObject().apply{addProperty("instance",1);addProperty("move","tackle")}
  val motion=JsonObject().apply{addProperty("seq",1);add("card",c);addProperty("from","DRAW");addProperty("to","HAND");addProperty("index",0);addProperty("cause","START_TURN")}
  val d=JsonObject().apply{addProperty("session","test");addProperty("battleNumber",1);addProperty("deckOwner","player:0");addProperty("motionSequence",1);add("hand",JsonArray().also{it.add(c)});add("motions",JsonArray().also{it.add(motion)})}
  val a=HandAnimation();a.update(null,d,true);assertEquals(1,a.flights.size)
  val original=a.flights.single();a.update(d,d,true);assertTrue(a.flights.all{it===original})
  SeenDeckMotions.set("test:1:player:0",0);val reopen=HandAnimation();reopen.update(null,d,true);assertTrue(reopen.flights.isEmpty())
  SeenDeckMotions.clear()
 }
}
