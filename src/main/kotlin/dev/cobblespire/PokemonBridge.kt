package dev.cobblespire

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlin.random.Random

/** Temporary, unowned native Pokemon supply stats/learnsets; they never enter a real store. */
class PokemonBridge(private val catalogue:PokemonCatalogue){
    data class Actor(val body:SpireCombatant,val name:String,val ability:String,val stats:MutableMap<String,Int>,val growth:Map<String,Int>,val aspects:Set<String>,val nativeMoves:List<MoveTemplate>)
    val supportedAbilities=setOf("blaze","torrent","overgrow","swarm","technician","intimidate","levitate","thickfat","waterabsorb","flashfire","insomnia","vitalspirit","innerfocus","keeneye","clearbody","hypercutter","purepower","hugepower","rockhead","compoundeyes","superluck","shellarmor","battlearmor","naturalcure","regenerator","soundproof","immunity","limber","waterveil","guts","owntempo","solidrock","filter","sheerforce","unnerve","airlock","cloudnine")
    private val stats=listOf("hp" to Stats.HP,"atk" to Stats.ATTACK,"def" to Stats.DEFENCE,"spa" to Stats.SPECIAL_ATTACK,"spd" to Stats.SPECIAL_DEFENCE,"spe" to Stats.SPEED)
    private val known=Definitions.characters.associateBy{Definitions.speciesId(it.text("species_id"))}
    fun legalMoves(entry:PokemonCatalogue.Entry):List<MoveTemplate>{
        val f=(listOf(entry.species.standardForm)+entry.species.forms).first{it.name==entry.formName};val m=f.moves
        return (m.tmMoves+m.eggMoves+m.evolutionMoves+m.tutorMoves+m.levelUpMoves.filterKeys{it<=50}.values.flatten()).distinctBy{it.name}
    }
    fun moveReason(move:MoveTemplate):String? {
        MoveCompatibility.reason(move.name)?.let{return it}
        val expected=MoveCompatibility.specs.getValue(move.name)
        if(expected.decimal("basePower")!=move.power||!expected.text("type").equals(move.elementalType.name,true))return "현재 모드의 기술 수치/타입이 효과 스냅샷과 다릅니다."
        val accuracy=expected.get("accuracy")
        if(accuracy.asJsonPrimitive.isNumber&&kotlin.math.abs(accuracy.asDouble-move.accuracy)>.001)return "현재 모드의 명중률이 효과 스냅샷과 다릅니다."
        return null
    }
    fun ability(entry:PokemonCatalogue.Entry):String?=(listOf(entry.species.standardForm)+entry.species.forms).first{it.name==entry.formName}.abilities.map{it.template.name}.firstOrNull{it in supportedAbilities}
    fun reason(entry:PokemonCatalogue.Entry):String?{
        if(entry.formName!=entry.species.standardForm.name)return "현재 시험 전투는 기본 폼부터 지원합니다."
        if(entry.species.standardForm.types.any{it.name.lowercase() !in MoveCompatibility.types})return "추가 타입 상성 검증 전"
        if(ability(entry)==null)return "이 폼의 특성 효과 검증 전"
        val moves=legalMoves(entry).filter{moveReason(it)==null}
        if(moves.none{MoveCompatibility.specs.getValue(it.name).number("basePower")>0})return "지원되는 합법 공격 기술이 없습니다."
        return null
    }
    fun growth(entry:PokemonCatalogue.Entry):Map<String,Int>{
        val design=known[entry.id]
        return stats.associate{(key,_)->key to (design?.number("${key}_gain") ?: when(key){"hp"->5;"spe"->2;else->2})}
    }
    fun create(entry:PokemonCatalogue.Entry,uid:String,seed:Long,level:Int=1,enemyHp:Int?=null,allowed:List<String>?=null):Actor{
        require(reason(entry)==null){reason(entry)?:"Unsupported"};require(level in 1..10)
        val mon=PokemonProperties.parse("${entry.id} level=50 nature=hardy").create()
        stats.forEach{(_,stat)->mon.ivs[stat]=31;mon.evs[stat]=0}
        val growth=growth(entry);val values=stats.associate{(key,stat)->key to mon.getStat(stat)}.toMutableMap()
        val random=Random(seed)
        val eligible=legalMoves(entry).filter{moveReason(it)==null&&(allowed==null||it.name in allowed)}
        require(eligible.any{MoveCompatibility.specs.getValue(it.name).number("basePower")>0}){"No verified attack"}
        val attacks=eligible.filter{it.power>0}.shuffled(random)
        val status=eligible.filter{it.power<=0}.shuffled(random)
        val own=attacks.filter{it.elementalType.name in mon.types.map{t->t.name}}
        val coverage=attacks.filter{it !in own}
        val selected=mutableListOf<MoveTemplate>();selected.addAll((own+attacks).distinct().take(4));selected.addAll(coverage.take(2))
        selected.addAll(status.filter{MoveCompatibility.specs.getValue(it.name).text("target")=="self"}.take(2));selected.addAll(status.filter{it !in selected}.take(2))
        val filler=(attacks+status).ifEmpty{eligible};var i=0
        while(selected.size<10){selected.add(filler[i++%filler.size])}
        val cards=selected.take(if(enemyHp==null)9 else 10).mapIndexed{index,m->MoveInstance(index.toLong()+1,MoveCompatibility.move(m.name))}.toMutableList()
        if(enemyHp==null)cards.add(MoveInstance(10,TrainerCards.encourage))
        val deck=BattleDeck(cards,Random(seed xor 0x32CAFEBABEL))
        val body=SpireCombatant(uid,entry.id,mon.types.map{it.name.lowercase()}.toSet(),enemyHp?:values.getValue("hp"),deck)
        body.baseForm=entry.formName;body.form=entry.formName
        return Actor(body,known[entry.id]?.text("name_ko")?:entry.species.translatedName.string,requireNotNull(ability(entry)),values,growth,entry.aspects,eligible)
    }
    fun audit():JsonObject=JsonObject().apply{
        addProperty("policy","Only verified metadata operations and native legal learnsets; unsupported callbacks/forms/abilities are excluded")
        addProperty("registeredMovesSnapshot",MoveCompatibility.specs.size)
        addProperty("supportedMoveDefinitions",MoveCompatibility.specs.keys.count{MoveCompatibility.reason(it)==null})
        add("moves",JsonArray().also{a->MoveCompatibility.specs.keys.sorted().forEach{id->a.add(JsonObject().apply{addProperty("id",id);addProperty("supported",MoveCompatibility.reason(id)==null);addProperty("reason",MoveCompatibility.reason(id).orEmpty())})}})
        add("forms",JsonArray().also{a->catalogue.entries.forEach{e->a.add(JsonObject().apply{addProperty("key",e.key);addProperty("supported",reason(e)==null);addProperty("reason",reason(e).orEmpty())})}})
        add("characters",JsonArray().also{a->Definitions.characters.forEach{d->val entry=catalogue.entries.firstOrNull{it.id==Definitions.speciesId(d.text("species_id"))&&it.formName==it.species.standardForm.name}
            a.add(JsonObject().apply{addProperty("species",d.text("species_id"));addProperty("supported",entry!=null&&reason(entry)==null);addProperty("reason",entry?.let{reason(it)}?:if(entry==null)"Unregistered" else "");addProperty("ability",entry?.let{ability(it)}.orEmpty())})
        }})
    }
}
