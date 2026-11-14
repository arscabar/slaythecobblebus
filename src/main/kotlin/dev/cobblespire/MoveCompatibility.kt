package dev.cobblespire

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlin.math.floor

fun JsonObject.obj(key:String):JsonObject?=get(key)?.takeIf{it.isJsonObject}?.asJsonObject
fun JsonObject.array(key:String):JsonArray?=get(key)?.takeIf{it.isJsonArray}?.asJsonArray
fun JsonObject.decimal(key:String,default:Double=0.0)=get(key)?.takeIf{it.isJsonPrimitive&&it.asJsonPrimitive.isNumber}?.asDouble?:default

/** Exact metadata snapshot of the installed engine. Unknown callbacks are never silently dropped. */
object MoveCompatibility {
    val snapshot:JsonObject by lazy{Definitions.read("move_effect_snapshot.json")}
    val specs:Map<String,JsonObject> by lazy{snapshot.getAsJsonObject("moves").entrySet().associate{it.key to it.value.asJsonObject}}
    val allowedStatuses=setOf("brn","psn","tox","par","slp")
    val allowedVolatiles=setOf("confusion","flinch")
    val types get()=snapshot.getAsJsonObject("types").keySet()
    fun reason(id:String):String? {
        val m=specs[id]?:return "원본 효과 데이터가 없습니다."
        if(m.array("callbacks")?.size()!=0)return "전용 콜백 검증 전: "+m.array("callbacks")!!.joinToString{it.asString}
        if(m.obj("extraEffects")?.size()!=0)return "추가 원본 규칙 검증 전: "+m.obj("extraEffects")!!.keySet().joinToString()
        if(m.text("type").lowercase() !in types)return "추가 타입의 상성 검증 전"
        if(m.text("target") !in setOf("normal","adjacentFoe","any","self","allAdjacentFoes"))return "원본 대상 범위 검증 전: "+m.text("target")
        for(k in listOf("weather","terrain","sideCondition","pseudoWeather","selfdestruct","damage"))if(m.get(k)?.isJsonNull==false)return "원본 $k 효과 검증 전"
        val (minH,maxH)=multihitRange(m)
        if(minH<1||maxH<minH||maxH>5)return "원본 multihit 효과 검증 전"
        fun effect(o:JsonObject?):Boolean {
            if(o==null)return true
            if(o.text("status").let{it.isNotBlank()&&it !in allowedStatuses})return false
            if(o.text("volatileStatus").let{it.isNotBlank()&&it !in allowedVolatiles})return false
            if(o.obj("self")!=null&&!effect(o.obj("self")))return false
            return o.keySet().all{it in setOf("chance","status","volatileStatus","boosts","self")}
        }
        if(!effect(m.obj("self"))||!effect(m.obj("secondary"))||m.array("secondaries")?.any{!effect(it.asJsonObject)}==true)return "부가 효과 검증 전"
        if(m.text("status").let{it.isNotBlank()&&it !in allowedStatuses})return "원본 상태이상 검증 전"
        if(m.text("volatileStatus").let{it.isNotBlank()&&it !in allowedVolatiles})return "원본 휘발성 상태 검증 전"
        val hasEffect=m.decimal("basePower")>0||m.has("boosts")||m.has("status")||m.has("volatileStatus")||m.has("heal")
        if(!hasEffect)return "실행할 효과 검증 전"
        return null
    }
    fun multihitRange(m:JsonObject):Pair<Int,Int> {
        val v=m.get("multihit")?:return 1 to 1
        if(v.isJsonNull)return 1 to 1
        if(v.isJsonPrimitive&&v.asJsonPrimitive.isNumber){val n=v.asInt;return n to n}
        if(v.isJsonArray&&v.asJsonArray.size()==2){
            val a=v.asJsonArray
            if(a[0].isJsonPrimitive&&a[0].asJsonPrimitive.isNumber&&a[1].isJsonPrimitive&&a[1].asJsonPrimitive.isNumber)return a[0].asInt to a[1].asInt
        }
        return -1 to -1
    }
    fun fraction(m:JsonObject,key:String):Double {val a=m.array(key)?:return 0.0;return a[0].asDouble/a[1].asDouble}
    fun accuracy(id:String):Int {val a=specs.getValue(id).get("accuracy");return if(a.isJsonPrimitive&&a.asJsonPrimitive.isBoolean)100 else a.asDouble.toInt()}
    fun move(id:String):SpireMove {
        require(reason(id)==null){"$id: ${reason(id)}"};val m=specs.getValue(id);val power=m.number("basePower")
        val (minHits,maxHits)=multihitRange(m)
        val avgHits=if(minHits==2&&maxHits==5)3.1 else (minHits+maxHits)/2.0
        val effectivePower=kotlin.math.round(power*avgHits).toInt()
        val boostSum=(m.obj("boosts")?.entrySet()?.sumOf{kotlin.math.abs(it.value.asInt)}?:0)+(m.obj("self")?.obj("boosts")?.entrySet()?.sumOf{kotlin.math.abs(it.value.asInt)}?:0)
        val cost=when(id){"tackle","leechseed"->1;"flamethrower","gigadrain","swordsdance","irondefense","synthesis"->2;"hydropump"->3
            else->when{
                m.text("target")=="self"&&!m.has("heal")->if(boostSum>=3)3 else if(boostSum==2)2 else 1
                effectivePower>=140->5
                effectivePower>=115->4
                effectivePower>95->3
                effectivePower>65||m.has("heal")->2
                else->1
            }
        }
        return SpireMove(id,cost,power,m.text("type").lowercase(),accuracy(id),healFraction=fraction(m,"heal"),selfTarget=m.text("target")=="self",targetAll=m.text("target")=="allAdjacentFoes",minHits=minHits,maxHits=maxHits)
    }
    fun cost(card:MoveInstance)=if(card.upgraded&&(card.move.cost>=3||card.move.id in setOf("swordsdance","irondefense")))card.move.cost-1 else card.move.cost
    fun upgradeMultiplier(id:String)=when(id){"flamethrower"->1.15;"gigadrain"->1.10;"hydropump","swordsdance","irondefense"->1.0;else->1.20}
    fun multiplier(attack:String,defence:Set<String>):Double=defence.fold(1.0){out,t->
        val row=snapshot.getAsJsonObject("types").obj(t)?:error("Unsupported type $t")
        val label=attack.replaceFirstChar{it.uppercase()}
        out*when(row.number(label)){1->2.0;2->0.5;3->0.0;else->1.0}
    }
    /** Lv50 reference formula, before STAB/type/variance. Rounding is explicit. */
    fun baseDamage(power:Int,attack:Double,defence:Double):Int=floor(floor(22.0*power*attack/defence.coerceAtLeast(1.0))/50.0).toInt()+2
}
