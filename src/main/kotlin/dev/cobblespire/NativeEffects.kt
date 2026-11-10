package dev.cobblespire

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random
import com.google.gson.JsonObject

/** Implements only the operations accepted by MoveCompatibility, on isolated adventure actors. */
class NativeEffects(val actors:Map<String,PokemonBridge.Actor>,seed:Long):SpireEffects {
    private val random=Random(seed)
    private val pendingSelf=mutableSetOf<String>()
    private var spreadActive=false
    private fun actor(mon:SpireCombatant)=actors.getValue(mon.uid)
    private fun ability(mon:SpireCombatant)=actor(mon).ability
    private val major=setOf("poison","toxic","burn","paralysis","sleep")
    private fun spec(move:SpireMove)=MoveCompatibility.specs.getValue(move.id)
    override fun cost(card:MoveInstance)=if(card.move.trainer)card.move.cost else MoveCompatibility.cost(card)
    override fun drawCount(card:MoveInstance)=card.move.drawCards+(if(card.move.trainer&&card.upgraded)1 else 0)
    override fun healing(source:SpireCombatant,move:SpireMove)=(source.maxHp*move.healFraction).roundToInt()
    private fun stage(mon:SpireCombatant,key:String,critical:Boolean=false,attack:Boolean=false):Double {
        var n=(mon.stages[key]?:0).coerceIn(-6,6)
        if(critical&&(attack&&n<0||!attack&&n>0))n=0
        return if(n>=0)(2.0+n)/2.0 else 2.0/(2.0-n)
    }
    private fun pokeRound(n:Double)=if(n-floor(n)>.5)kotlin.math.ceil(n).toInt()else floor(n).toInt()
    fun referenceDamage(source:SpireCombatant,target:SpireCombatant,move:SpireMove,variance:Int,critical:Boolean=false,upgraded:Boolean=false,multiplier:Double=MoveCompatibility.multiplier(move.type,target.types),spread:Boolean=false):Int {
        if(move.power<=0||multiplier==0.0)return 0
        val m=spec(move);val physical=m.text("category")=="Physical"
        val crit=critical&&ability(target) !in setOf("shellarmor","battlearmor")
        val atkKey=if(physical)"atk" else "spa";val defKey=if(physical)"def" else "spd"
        var attack=floor(actor(source).stats.getValue(atkKey)*stage(source,atkKey,crit,true)).toInt()
        val defense=floor(actor(target).stats.getValue(defKey)*stage(target,defKey,crit,false)).toInt()
        val a=ability(source);val d=ability(target)
        var attackMod=1.0
        if(physical&&a in setOf("purepower","hugepower"))attackMod*=2
        if(physical&&a=="guts"&&source.statuses.keys.any{it in major})attackMod*=1.5
        if(source.hp*3<=source.maxHp&&((a=="blaze"&&move.type=="fire")||(a=="torrent"&&move.type=="water")||(a=="overgrow"&&move.type=="grass")||(a=="swarm"&&move.type=="bug")))attackMod*=1.5
        if(a=="flashfire"&&move.type=="fire"&&(source.stages["flashfire"]?:0)>0)attackMod*=1.5
        if(d=="thickfat"&&move.type in setOf("fire","ice"))attackMod*=0.5
        attack=pokeRound(attack*attackMod).coerceAtLeast(1)
        var power=move.power
        if(a=="technician"&&power<=60)power=pokeRound(power*1.5)
        if(a=="sheerforce"&&m.array("secondaries")?.size()?.let{it>0}==true)power=pokeRound(power*5325.0/4096.0)
        var damage=MoveCompatibility.baseDamage(power,attack.toDouble(),defense.toDouble())
        if(spread)damage=pokeRound(damage*.75)
        if(crit)damage=floor(damage*1.5).toInt()
        damage=floor(damage*variance.coerceIn(85,100)/100.0).toInt()
        if(move.type in source.types)damage=pokeRound(damage*1.5)
        damage=floor(damage*multiplier).toInt()
        if(physical&&"burn" in source.statuses&&a!="guts")damage=floor(damage*.5).toInt()
        if(d in setOf("solidrock","filter")&&multiplier>1)damage=pokeRound(damage*.75)
        if(upgraded)damage=floor(damage*MoveCompatibility.upgradeMultiplier(move.id)).toInt()
        return damage.coerceAtLeast(1)
    }
    override fun damage(source:SpireCombatant,target:SpireCombatant,move:SpireMove,upgraded:Boolean,multiplier:Double):Double {
        val ratio=spec(move).number("critRatio",1)+(if(ability(source)=="superluck")1 else 0)
        val chance=when{ratio>=4->1;ratio==3->2;ratio==2->8;else->24}
        return referenceDamage(source,target,move,random.nextInt(85,101),random.nextInt(chance)==0,upgraded,multiplier,spreadActive).toDouble()
    }
    fun estimate(source:SpireCombatant,target:SpireCombatant,move:SpireMove):Pair<Int,Int>{
        if(move.power<=0)return 0 to 0
        val a=ability(target)
        if(a=="soundproof"&&spec(move).obj("flags")?.number("sound")==1)return 0 to 0
        if(a=="levitate"&&move.type=="ground"||a=="flashfire"&&move.type=="fire"||a=="waterabsorb"&&move.type=="water")return 0 to 0
        val original=MoveCompatibility.multiplier(move.type,target.types)
        val mul=original
        return referenceDamage(source,target,move,85,multiplier=mul) to referenceDamage(source,target,move,100,multiplier=mul)
    }
    override fun accuracy(source:SpireCombatant,target:SpireCombatant,move:SpireMove):Int {
        if(move.trainer)return 100
        val a=spec(move).get("accuracy")
        if(a.isJsonPrimitive&&a.asJsonPrimitive.isBoolean)return 100
        val keen=ability(source)=="keeneye"
        val acc=if(keen)maxOf(0,source.stages["accuracy"]?:0)else source.stages["accuracy"]?:0
        val eva=if(keen)minOf(0,target.stages["evasion"]?:0)else target.stages["evasion"]?:0
        val n=(acc-eva).coerceIn(-6,6);val mult=if(n>=0)(3.0+n)/3.0 else 3.0/(3.0-n)
        return floor(move.accuracy*mult*(if(ability(source)=="compoundeyes")1.3 else 1.0)).toInt().coerceIn(0,100)
    }
    override fun canHit(source:SpireCombatant,target:SpireCombatant,move:SpireMove,battle:SpireBattle):Boolean {
        if(move.trainer)return true
        if(move.id=="thunderwave"&&"ground" in target.types)return false
        val flags=spec(move).obj("flags")
        if(flags?.number("powder")==1&&"grass" in target.types)return false
        if(ability(target)=="soundproof"&&flags?.number("sound")==1)return false
        if(move.power>0){
            if(ability(target)=="levitate"&&move.type=="ground")return false
            if(ability(target)=="waterabsorb"&&move.type=="water"){target.heal(target.maxHp/4);return false}
            if(ability(target)=="flashfire"&&move.type=="fire"){target.stages["flashfire"]=1;return false}
        }
        return true
    }
    override fun beforeAction(source:SpireCombatant,move:SpireMove,battle:SpireBattle):Boolean {
        if(move.trainer)return source.alive
        spreadActive=move.targetAll&&(if(source.uid.startsWith("player:"))battle.enemies.count{it.alive}>1 else battle.players.count{it.active.alive}>1)
        pendingSelf.remove(source.uid)
        if(source.statuses.remove("flinch")!=null&&ability(source)!="innerfocus")return false
        if("confusion" in source.statuses&&ability(source)!="owntempo"&&random.nextInt(3)==0){
            val n=MoveCompatibility.baseDamage(40,actor(source).stats.getValue("atk").toDouble(),actor(source).stats.getValue("def").toDouble())
            source.hp=(source.hp-n).coerceAtLeast(0);return false
        }
        return source.alive
    }
    private fun status(target:SpireCombatant,id:String,battle:SpireBattle){
        if(!target.alive)return
        val ability=ability(target)
        val mapped=mapOf("brn" to "burn","psn" to "poison","tox" to "toxic","par" to "paralysis","slp" to "sleep")[id]
        if(mapped!=null){
            if(target.statuses.keys.any{it in major})return
            if(mapped=="burn"&&("fire" in target.types||ability=="waterveil"))return
            if(mapped in setOf("poison","toxic")&&(target.types.any{it in setOf("poison","steel")}||ability=="immunity"))return
            if(mapped=="paralysis"&&("electric" in target.types||ability=="limber"))return
            if(mapped=="sleep"&&ability in setOf("insomnia","vitalspirit"))return
            target.afflict(mapped,battle.round,if(mapped=="sleep")random.nextInt(1,4)else if(mapped=="paralysis")2 else 0)
        }else if(id in setOf("confusion","flinch")){
            if(id=="confusion"&&ability=="owntempo"||id=="flinch"&&ability=="innerfocus")return
            if(id !in target.statuses)target.afflict(id,battle.round,if(id=="confusion")random.nextInt(2,6)else 1)
        }
    }
    private fun apply(source:SpireCombatant,target:SpireCombatant,o:JsonObject,battle:SpireBattle,includeSelf:Boolean=true){
        if(!target.alive){if(includeSelf)o.obj("self")?.let{apply(source,source,it,battle)};return}
        o.obj("boosts")?.entrySet()?.forEach{(key,value)->
            val n=value.asInt;val a=ability(target)
            if(n>=0||source===target||a!="clearbody"&&!(a=="hypercutter"&&key=="atk")&&!(a=="keeneye"&&key=="accuracy"))target.stages[key]=((target.stages[key]?:0)+n).coerceIn(-6,6)
        }
        if(o.text("status").isNotBlank())status(target,o.text("status"),battle)
        if(o.text("volatileStatus").isNotBlank())status(target,o.text("volatileStatus"),battle)
        if(includeSelf)o.obj("self")?.let{apply(source,source,it,battle)}
    }
    override fun hit(source:SpireCombatant,target:SpireCombatant,move:SpireMove,damage:Int,battle:SpireBattle){
        if(move.trainer)return
        val m=spec(move);apply(source,target,m,battle,false)
        if(m.obj("self")!=null&&!(ability(source)=="sheerforce"&&m.array("secondaries")?.size()?.let{it>0}==true))pendingSelf.add(source.uid)
        if(ability(source)!="sheerforce")m.array("secondaries")?.forEach{v->val o=v.asJsonObject;if(random.nextInt(100)<o.number("chance",100))apply(source,target,o,battle)}
        val drain=MoveCompatibility.fraction(m,"drain")
        if(damage>0&&drain>0)source.heal((damage*drain).roundToInt().coerceAtLeast(1))
    }
    override fun resolved(source:SpireCombatant,move:SpireMove,totalDamage:Int,battle:SpireBattle){
        if(move.trainer)return
        if(pendingSelf.remove(source.uid))spec(move).obj("self")?.let{apply(source,source,it,battle)}
        val recoil=MoveCompatibility.fraction(spec(move),"recoil")
        if(totalDamage>0&&recoil>0&&ability(source)!="rockhead")source.hp=(source.hp-(totalDamage*recoil).roundToInt().coerceAtLeast(1)).coerceAtLeast(0)
    }
    fun enter(mon:SpireCombatant,foes:List<SpireCombatant>){if(ability(mon)=="intimidate")foes.filter{it.alive&&ability(it) !in setOf("clearbody","hypercutter")}.forEach{it.stages["atk"]=((it.stages["atk"]?:0)-1).coerceAtLeast(-6)}}
    override fun switched(out:SpireCombatant,into:SpireCombatant,battle:SpireBattle){
        out.statuses.remove("confusion");out.statuses.remove("flinch")
        if(ability(out)=="naturalcure")out.statuses.clear()
        if(ability(out)=="regenerator")out.heal(out.maxHp/3)
        enter(into,if(into.uid.startsWith("player:"))battle.enemies else battle.players.map{it.active})
    }
    override fun periodic(mon:SpireCombatant,battle:SpireBattle){
        mon.statuses.remove("flinch")
        val numerator=if("toxic" in mon.statuses){val n=(mon.stages["toxic-counter"]?:0)+1;mon.stages["toxic-counter"]=n;n}else 1
        if(mon.statuses.keys.any{it in setOf("poison","toxic","burn")}){
            val denom=if("poison" in mon.statuses)8 else 16
            mon.hp=(mon.hp-(mon.maxHp*numerator/denom).coerceAtLeast(1)).coerceAtLeast(0)
        }
    }
}
