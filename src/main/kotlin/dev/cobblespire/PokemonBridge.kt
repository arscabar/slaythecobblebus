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
    data class Actor(val body:SpireCombatant,var name:String,var ability:String,val stats:MutableMap<String,Int>,var growth:Map<String,Int>,var aspects:Set<String>,var nativeMoves:List<MoveTemplate>,var level:Int=1,var exp:Int=0){
        val expToNext get()=80+level*20
    }
    fun levelUp(a:Actor,levels:Int=1){
        repeat(levels){
            if(a.level>=30)return@repeat
            a.level++
            a.growth.forEach{(k,gain)->
                if(k=="hp"){
                    a.body.growHp(gain)
                    a.stats["hp"]=a.body.maxHp
                }else{
                    a.stats[k]=(a.stats[k]?:50)+gain
                }
            }
        }
    }
    fun grantExp(a:Actor,amount:Int):Int{
        if(amount<=0||a.level>=30)return 0
        a.exp+=amount
        var gained=0
        while(a.level<30&&a.exp>=a.expToNext){
            a.exp-=a.expToNext
            levelUp(a,1)
            gained++
        }
        return gained
    }
    fun stageOf(entry:PokemonCatalogue.Entry):Int{
        val pre1=entry.species.preEvolution?.species?:entry.species.standardForm.preEvolution?.species?:return 0
        val preEntry=catalogue.entries.firstOrNull{it.id==Definitions.speciesId(pre1.resourceIdentifier.toString())&&it.formName==it.species.standardForm.name}?:return 1
        val pre2=preEntry.species.preEvolution?.species?:preEntry.species.standardForm.preEvolution?.species
        return if(pre2==null)1 else 2
    }
    fun requiredEvoLevel(stage:Int)=when(stage){0->4;1->8;else->99}
    fun legalEvolutions(a:Actor):List<PokemonCatalogue.Entry>{
        val cur=catalogue.entries.firstOrNull{it.id==a.body.species&&it.formName==it.species.standardForm.name}?:return emptyList()
        val rawTargets=(cur.species.standardForm.evolutions+cur.species.evolutions).mapNotNull{it.result.species?.let{sp->Definitions.speciesId(sp)}}.distinct()
        return rawTargets.mapNotNull{tid->catalogue.entries.firstOrNull{it.id==tid&&it.formName==it.species.standardForm.name&&reason(it)==null}}
    }
    fun canEvolve(a:Actor,credits:Int=0):Boolean{
        if(legalEvolutions(a).isEmpty())return false
        val cur=catalogue.entries.firstOrNull{it.id==a.body.species&&it.formName==it.species.standardForm.name}?:return false
        return credits>0||a.level>=requiredEvoLevel(stageOf(cur))
    }
    fun evolve(a:Actor,target:PokemonCatalogue.Entry,seed:Long):String?{
        require(reason(target)==null){reason(target)?:"Unsupported"}
        val oldEntry=catalogue.entries.firstOrNull{it.id==a.body.species&&it.formName==it.species.standardForm.name}
        val oldBase=oldEntry?.let{e->
            val m=PokemonProperties.parse("${e.id} level=50 nature=hardy").create()
            stats.forEach{(_,s)->m.ivs[s]=31;m.evs[s]=0}
            stats.associate{(k,s)->k to m.getStat(s)}
        }
        val newMon=PokemonProperties.parse("${target.id} level=50 nature=hardy").create()
        stats.forEach{(_,s)->newMon.ivs[s]=31;newMon.evs[s]=0}
        val newBase=stats.associate{(k,s)->k to newMon.getStat(s)}
        val hpDelta=((newBase["hp"]?:100)-(oldBase?.get("hp")?:80)).coerceAtLeast(18)
        a.body.growHp(hpDelta)
        a.stats["hp"]=a.body.maxHp
        for((k,_) in stats){
            if(k=="hp")continue
            val delta=((newBase[k]?:70)-(oldBase?.get(k)?:55)).coerceAtLeast(8)
            a.stats[k]=(a.stats[k]?:50)+delta
        }
        a.body.species=target.id
        a.body.types=newMon.types.map{it.name.lowercase()}.toSet()
        a.body.baseForm=target.formName;a.body.form=target.formName
        a.name=known[target.id]?.text("name_ko")?:target.species.translatedName.string
        a.ability=requireNotNull(ability(target))
        a.growth=growth(target)
        a.aspects=target.aspects
        val eligible=legalMoves(target).filter{moveReason(it)==null}
        a.nativeMoves=eligible
        val deck=a.body.deck?:return null
        val existingIds=deck.original.map{it.move.id}.toSet()
        val bonusMove=(eligible.filter{it.name !in existingIds&&it.power>=65}.maxByOrNull{it.power}
            ?:eligible.firstOrNull{it.name !in existingIds&&it.power>0})
        if(bonusMove!=null){
            val nextUid=(deck.original.maxOfOrNull{it.uid}?:10L)+1L
            val inst=MoveInstance(nextUid,MoveCompatibility.move(bonusMove.name))
            a.body.deck=BattleDeck(deck.original+inst,Random(seed xor 0xE701L))
            return bonusMove.name
        }
        return null
    }
    val supportedAbilities=setOf("blaze","torrent","overgrow","swarm","technician","intimidate","levitate","thickfat","waterabsorb","flashfire","insomnia","vitalspirit","innerfocus","keeneye","clearbody","hypercutter","purepower","hugepower","rockhead","compoundeyes","superluck","shellarmor","battlearmor","naturalcure","regenerator","soundproof","immunity","limber","waterveil","guts","owntempo","solidrock","filter","sheerforce","unnerve","airlock","cloudnine","pressure","drought","drizzle","telepathy")
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
        val deckPool=if(enemyHp==null){
            val commons=eligible.filter{val sm=MoveCompatibility.move(it.name);sm.cost<=3&&CardSemantics.rarity(sm.cost,sm.power,false)==CardRarity.COMMON}
            if(commons.any{it.power>0})commons else eligible.filter{MoveCompatibility.move(it.name).cost<=3}.ifEmpty{eligible}
        }else eligible
        val attacks=deckPool.filter{it.power>0}.shuffled(random)
        val status=deckPool.filter{it.power<=0}.shuffled(random)
        val own=attacks.filter{it.elementalType.name in mon.types.map{t->t.name}}
        val coverage=attacks.filter{it !in own}
        val selected=mutableListOf<MoveTemplate>();selected.addAll((own+attacks).distinct().take(4));selected.addAll(coverage.take(2))
        selected.addAll(status.filter{MoveCompatibility.specs.getValue(it.name).text("target")=="self"}.take(2));selected.addAll(status.filter{it !in selected}.take(2))
        val filler=(attacks+status).ifEmpty{deckPool};var i=0
        while(selected.size<10){selected.add(filler[i++%filler.size])}
        val cards=selected.take(if(enemyHp==null)9 else 10).mapIndexed{index,m->MoveInstance(index.toLong()+1,MoveCompatibility.move(m.name))}.toMutableList()
        if(enemyHp==null)cards.add(MoveInstance(10,TrainerCards.encourage))
        val deck=BattleDeck(cards,Random(seed xor 0x32CAFEBABEL))
        val body=SpireCombatant(uid,entry.id,mon.types.map{it.name.lowercase()}.toSet(),enemyHp?:values.getValue("hp"),deck)
        body.baseForm=entry.formName;body.form=entry.formName
        return Actor(body,known[entry.id]?.text("name_ko")?:entry.species.translatedName.string,requireNotNull(ability(entry)),values,growth,entry.aspects,eligible,level)
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
