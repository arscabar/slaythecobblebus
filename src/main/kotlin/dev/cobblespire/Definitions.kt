package dev.cobblespire
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object Definitions {
    val data:JsonObject by lazy { read("all_definitions_v4.json") }
    val characters get()=data.getAsJsonArray("characters").map{it.asJsonObject}
    val enemies get()=data.getAsJsonArray("enemy_actors").map{it.asJsonObject}
    val relics get()=data.getAsJsonArray("relics").map{it.asJsonObject}
    fun read(name:String):JsonObject {
        val stream=requireNotNull(javaClass.getResourceAsStream("/assets/cobblespire/design/$name")){"Missing design data: $name"}
        return stream.bufferedReader(Charsets.UTF_8).use{JsonParser.parseReader(it).asJsonObject}
    }
    fun validate(){
        require(characters.size==50&&relics.size==165&&enemies.size==25)
        val registry=data.getAsJsonObject("registries")
        val ids=mutableSetOf<String>()
        relics.forEach{relic->require(ids.add(relic.text("id")));relic.getAsJsonArray("rules").forEach{value->val rule=value.asJsonObject
            require(registry.getAsJsonObject("events").has(rule.text("event")))
            listOf("conditions","actions").forEach{kind->rule.getAsJsonArray(kind).forEach{require(registry.getAsJsonObject(kind).has(it.asJsonObject.text("op")))}}
        }}
    }
}
