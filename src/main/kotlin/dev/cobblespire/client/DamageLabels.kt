package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.network.chat.Component

object DamageLabels {
    fun type(id:String)=if(id.isBlank())"효과" else Component.translatable("cobblemon.type.$id").string
    fun matchup(value:Double)=when{value==0.0->"무효 ×0";value>1->"효과 굉장 ×${number(value)}";value<1->"반감 ×${number(value)}";else->"보통 ×1"}
    private fun number(n:Double)=if(n==n.toInt().toDouble())n.toInt().toString()else n.toString()
    fun multiplier(p:JsonObject)=p.get("multiplier")?.asDouble?:1.0
    fun damage(p:JsonObject)="${p.number("minDamage")}~${p.number("maxDamage") }"
    fun preview(c:JsonObject,target:Int)=c.array("previews")?.toList()?.getOrNull(target)?.asJsonObject
    fun headline(c:JsonObject,p:JsonObject?):String=when{
        c.number("power")<=0->if(c.number("draw")>0)"${c.number("draw")}장 뽑기"else "직접 피해 없음"
        p==null->"위력 ${c.number("power")}"
        multiplier(p)==0.0->"무효 · 피해 0"
        p.number("maxDamage")==0->"차단 · 피해 0"
        else->"${damage(p)} 피해"
    }
}
