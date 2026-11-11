package dev.cobblespire

import com.google.gson.JsonObject

enum class CardRole(val label:String,val color:Int,val dark:Int,val paper:Int){
    ATTACK("공격",0xFFF58D72.toInt(),0xFF392025.toInt(),0xFFF4D6CA.toInt()),
    DEFENCE("방어",0xFF86C8FC.toInt(),0xFF16354A.toInt(),0xFFD5EAF9.toInt()),
    SUPPORT("지원",0xFFD0A5FF.toInt(),0xFF322344.toInt(),0xFFE9DCF8.toInt());
    companion object{fun parse(id:String)=entries.firstOrNull{it.name==id}?:SUPPORT}
}

/** Descriptive UI roles only: they never change damage, legal moves, or card effects. */
object CardSemantics {
    fun role(id:String):CardRole {
        if(id==TrainerCards.ENCOURAGE)return CardRole.SUPPORT
        val m=MoveCompatibility.specs[id]?:return CardRole.SUPPORT
        return role(SpireMove(id,0,m.number("basePower"),healFraction=MoveCompatibility.fraction(m,"heal"),selfTarget=m.text("target")=="self"))
    }
    fun role(move:SpireMove):CardRole {
        if(move.trainer)return CardRole.SUPPORT
        if(move.power>0)return CardRole.ATTACK
        val boosts=MoveCompatibility.specs[move.id]?.obj("boosts")
        val defensive=move.selfTarget&&listOf("def","spd","evasion").any{(boosts?.number(it)?:0)>0}
        return if(move.healFraction>0||defensive)CardRole.DEFENCE else CardRole.SUPPORT
    }
    fun role(card:JsonObject):CardRole {
        if(card.get("trainer")?.asBoolean==true)return CardRole.SUPPORT
        if(card.number("power")>0)return CardRole.ATTACK
        return CardRole.parse(card.text("role")).takeUnless{it==CardRole.ATTACK}?:CardRole.SUPPORT
    }
    fun summary(move:SpireMove,draw:Int=move.drawCards):String {
        if(draw>0)return "카드 ${draw}장 뽑기"
        if(move.healFraction>0)return "최대 HP ${(move.healFraction*100).toInt()}% 회복"
        val m=MoveCompatibility.specs[move.id]?:return "효과 적용"
        val labels=mapOf("atk" to "공격","def" to "방어","spa" to "특공","spd" to "특방","spe" to "속도","accuracy" to "명중 단계","evasion" to "회피")
        m.obj("boosts")?.entrySet()?.takeIf{it.isNotEmpty()}?.let{boosts->return boosts.joinToString(" · "){(k,v)->"${labels[k]?:k} ${if(v.asInt>0)"+"else ""}${v.asInt}랭크"}}
        val status=m.text("status").ifBlank{m.text("volatileStatus")}
        return mapOf("brn" to "화상 부여","psn" to "독 부여","tox" to "맹독 부여","par" to "마비 부여","slp" to "수면 부여","confusion" to "혼란 부여","flinch" to "풀죽음 유발")[status]?:"효과 적용"
    }
    fun target(card:JsonObject)=when{
        card.get("selfTarget")?.asBoolean==true||card.get("trainer")?.asBoolean==true->"자신"
        card.get("targetAll")?.asBoolean==true->"모든 적"
        else->"선택한 적"
    }
}
