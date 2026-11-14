package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*

/** Non-attacks do not inherit any of the attack-only tooltip rows, even from old payloads. */
object CardTooltip {
    fun lines(c:JsonObject,name:String,description:String,typeLabel:String,p:JsonObject?,unavailable:String=""):List<String>{
        val role=CardSemantics.role(c);val rarity=CardSemantics.rarity(c)
        return buildList{
            add("$name · ${rarity.label} · ${role.label} · 에너지 ${c.number("cost")}")
            if(role==CardRole.ATTACK){
                val hitTag=DamageLabels.hitsText(c).let{if(it.isNotBlank())" · $it" else ""}
                add("$typeLabel 타입 · 기본 위력 ${c.number("power")}$hitTag")
                p?.let{val tName=SpireWidgets.speciesName(it.text("targetSpecies"),it.text("targetName"));add("$tName: ${DamageLabels.headline(c,it)}");add("${DamageLabels.matchup(DamageLabels.multiplier(it))} · 명중 ${it.number("accuracy")}%");add("명중 시 예상 HP 감소 · 급소 제외")}
            }else{
                add(c.text("effectSummary").ifBlank{if(c.number("draw")>0)"카드 ${c.number("draw")}장 뽑기"else "효과 적용"})
                add("대상: ${CardSemantics.target(c)}")
            }
            if(description.isNotBlank())add(description)
            if(unavailable.isNotBlank())add(unavailable)
            add(when{c.get("exhaust")?.asBoolean==true->"사용 후 소멸 · 이번 전투에서 다시 뽑지 않음";c.get("retain")?.asBoolean==true->"턴 종료 시 손패에 보존";else->"턴 종료 시 버림"})
        }
    }
}
