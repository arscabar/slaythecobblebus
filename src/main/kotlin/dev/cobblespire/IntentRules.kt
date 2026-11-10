package dev.cobblespire
import com.google.gson.JsonObject
import kotlin.random.Random

class IntentRules(seed:Long){
    private val random=Random(seed)
    private val uses=mutableMapOf<String,Int>();private val last=mutableMapOf<String,Int>()
    data class Choice(val move:SpireMove,val rule:String)
    fun choose(actor:SpireCombatant,definition:JsonObject,battle:SpireBattle,allowed:Set<String>):Choice {
        val eligible=definition.getAsJsonArray("ai_rules").mapIndexed{i,e->i to e.asJsonObject}.sortedByDescending{it.second.number("priority")}
        for((index,r) in eligible){
            val key="${actor.uid}:$index";val max=r.get("max_uses_per_battle")?.takeUnless{it.isJsonNull}?.asInt
            if(max!=null&&(uses[key]?:0)>=max)continue
            if(battle.round-(last[key]?:-999)<=r.number("cooldown_rounds"))continue
            val active=battle.players.map{it.active}.filter{it.alive}
            val valid=when(r.obj("condition")?.text("op")){
                "ALWAYS"->true
                "TARGET_NOT_AFFECTED_BY_CONTROL"->active.all{it.statuses.isEmpty()&&it.stages.values.none{v->v<0}}
                "SELF_PRIMARY_STAGE_BELOW_2"->maxOf(actor.stages["atk"]?:0,actor.stages["spa"]?:0)<2
                "SELF_HP_LTE_40"->actor.hp<=actor.maxHp*.4
                "SELF_HP_LTE_30"->actor.hp<=actor.maxHp*.3
                "BATTLE_FIRST_ROUND","PHASE_FIRST_ROUND"->battle.round==1
                else->false
            };if(!valid)continue
            val choice=r.getAsJsonObject("choose")
            val options=if(choice.text("type")=="FIXED")listOf(choice.text("move_id") to 1)else choice.array("candidates")?.map{it.asJsonObject.let{v->v.text("move_id") to v.number("weight",1)}}.orEmpty()
            val pool=options.filter{it.first in allowed&&it.second>0}
            if(pool.isEmpty())continue
            var n=random.nextInt(pool.sumOf{it.second});val selected=pool.first{n-=it.second;n<0}
            return Choice(MoveCompatibility.move(selected.first),key)
        }
        val fallback=allowed.filter{MoveCompatibility.specs.getValue(it).number("basePower")>0}.sorted().firstOrNull()?:error("검증된 기본 공격이 없습니다.")
        return Choice(MoveCompatibility.move(fallback),"${actor.uid}:fallback")
    }
    fun used(choice:Choice,round:Int){uses[choice.rule]=(uses[choice.rule]?:0)+1;last[choice.rule]=round}
}
