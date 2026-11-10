package dev.cobblespire

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.pokemon.Species
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.ResourceLocation
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.file.Files

/** Read the fully merged registry, including every addon/datapack namespace. Never edit it. */
class PokemonCatalogue {
    data class Entry(val species:Species,val formName:String,val aspects:Set<String>){
        val id get()=species.resourceIdentifier.toString()
        val key get()="$id|$formName"
        fun json()=JsonObject().apply{
            addProperty("key",key);addProperty("species",id);addProperty("form",formName)
            addProperty("name",species.translatedName.string);addProperty("namespace",species.resourceIdentifier.namespace)
            add("aspects",JsonArray().also{a->aspects.forEach{a.add(it)}})
        }
    }
    var entries:List<Entry> = emptyList(); private set
    fun reload(){
        entries=PokemonSpecies.implemented.flatMap{s->(listOf(s.standardForm)+s.forms).distinctBy{it.name}.map{Entry(s,it.name,it.aspects.toSet())}}
            .distinctBy{it.key}.sortedWith(compareBy<Entry>{it.id}.thenBy{it.formName})
        require(entries.isNotEmpty()){"Pokemon registry has not been initialized"}
    }
    fun find(id:String)=PokemonSpecies.getByIdentifier(ResourceLocation.parse(if(':' in id)id else "cobblemon:$id"))
    fun page(query:String,page:Int):JsonObject {
        val filtered=entries.filter{query.isBlank()||it.key.contains(query,true)||it.species.translatedName.string.contains(query,true)||Definitions.characters.any{d->d.text("species_id")==it.species.resourceIdentifier.path&&d.text("name_ko").contains(query,true)}}
        val p=page.coerceIn(0,((filtered.size-1)/12).coerceAtLeast(0))
        return JsonObject().apply{addProperty("kind","catalog");addProperty("query",query);addProperty("page",p);addProperty("total",filtered.size);addProperty("speciesCount",entries.map{it.id}.distinct().size)
            add("entries",JsonArray().also{a->filtered.drop(p*12).take(12).forEach{a.add(it.json())}})
        }
    }
    fun audit():JsonObject=JsonObject().apply{
        addProperty("speciesCount",entries.map{it.id}.distinct().size);addProperty("formsCount",entries.size)
        addProperty("policy","All implemented species/forms in the merged Cobblemon registry; no namespace whitelist")
        add("characters",JsonArray().also{a->Definitions.characters.forEach{c->a.add(JsonObject().apply{addProperty("id",c.text("species_id"));addProperty("registered",find(c.text("species_id"))!=null)})}})
        add("enemyMoves",JsonArray().also{a->Definitions.enemies.forEach{e->val species=find(e.text("species_id"));e.getAsJsonArray("move_pool").forEach{value->val id=value.asJsonObject.text("id");val m=Moves.getByName(id)
            val learned=species?.let{s->(listOf(s.standardForm)+s.forms).any{f->f.moves.run{m in tmMoves||m in eggMoves||m in evolutionMoves||m in tutorMoves||levelUpMoves.values.any{m in it}}}}?:false
            a.add(JsonObject().apply{addProperty("enemy",e.text("enemy_id"));addProperty("move",id);addProperty("registered",m!=null);addProperty("learnable",learned)})
        }}})
        add("mods",JsonArray().also{a->FabricLoader.getInstance().allMods.sortedBy{it.metadata.id}.forEach{mod->a.add(JsonObject().apply{addProperty("id",mod.metadata.id);addProperty("version",mod.metadata.version.friendlyString)})}})
    }
    fun writeAudit(){val path=FabricLoader.getInstance().gameDir.resolve("logs/cobblespire/registry-audit.json");Files.createDirectories(path.parent);Files.writeString(path,SpireMod.gson.toJson(audit()))}
}
