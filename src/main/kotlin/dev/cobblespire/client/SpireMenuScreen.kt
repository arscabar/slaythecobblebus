package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

class SpireMenuScreen(data:JsonObject):SpirePane(data){
    private lateinit var search:EditBox
    private fun choices()=data.getAsJsonArray("entries").map{it.asJsonObject}
    override fun init(){
        search=EditBox(font,px+12,py+44,pw-204,18,Component.literal("대표 포켓몬 검색")).also{it.value=data.text("query");addRenderableWidget(it)}
        button(px+pw-186,py+44,54,18,"검색"){page(0)}
        button(px+pw-126,py+44,114,18,if(data.get("extended")?.asBoolean==true)"확장 후보 켜짐" else "기본 50종"){page(0,true)}
        val w=(pw-30)/2;val row=((ph-112)/4).coerceAtMost(47)
        choices().forEachIndexed{i,c->
            val label=SpireWidgets.name(c);val supported=c.text("reason").isBlank()
            button(px+12+(i%2)*(w+6),py+72+(i/2)*row,w,row-4,short(label,w-12),supported){SpireClient.send("start"){addProperty("entry",c.text("key"))}}
                .tooltip=Tooltip.create(Component.literal(if(supported)"특성: "+Component.translatable("cobblemon.ability.${c.text("ability")}").string+"\n파트너 2마리는 모든 타입이 겹치지 않게 자동 편성됩니다."+(if(c.get("design")?.asBoolean!=true)"\n추가 등록 종: 기준 능력치 고정 · 균형 미검증"else "") else c.text("reason")))
        }
        button(px+12,py+ph-28,56,20,"이전",data.number("page")>0){page(data.number("page")-1)}
        button(px+73,py+ph-28,56,20,"다음",(data.number("page")+1)*8<data.number("total")){page(data.number("page")+1)}
        button(px+pw-162,py+ph-28,72,20,"전체 도감"){SpireClient.send("catalog")}
        button(px+pw-84,py+ph-28,72,20,"닫기"){onClose()}
    }
    private fun page(p:Int,toggle:Boolean=false){SpireClient.send("menu"){addProperty("query",search.value);addProperty("page",p);addProperty("extended",(data.get("extended")?.asBoolean==true) xor toggle)}}
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,"CobbleSpire · 첫 전투 시험",px+12,py+12,0xE7C98F)
        text(g,short("불꽃·물·풀 대표 선택 → 독립 모험 파티 3마리",pw-24),px+12,py+29,0x9FC5CB)
    }
}
