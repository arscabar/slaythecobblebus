package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import com.cobblemon.mod.common.api.moves.Moves
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import java.util.UUID

class SpireBattleScreen(data:JsonObject):SpirePane(data){
    private var target=0
    private fun enemies()=data.getAsJsonArray("enemies").map{it.asJsonObject}
    private fun party()=data.getAsJsonArray("party").map{it.asJsonObject}
    private val compact get()=ph<240
    private val active get()=party()[data.number("active")]
    override fun accept(value:JsonObject){data=value;if(enemies().getOrNull(target)?.number("hp",0)==0)target=enemies().indexOfFirst{it.number("hp")>0}.coerceAtLeast(0);super.accept(value)}
    private fun command(action:String,body:JsonObject.()->Unit={}){SpireClient.send(action){addProperty("session",data.text("session"));addProperty("battleNumber",data.number("battleNumber"));addProperty("revision",data.number("revision"));addProperty("request",UUID.randomUUID().toString());body()}}
    override fun init(){
        val list=enemies();val ew=(pw-24-(list.size-1)*6)/list.size
        list.forEachIndexed{i,e->button(px+12+i*(ew+6),py+if(compact)31 else 64,ew,16,(if(target==i)"● " else "")+short(SpireWidgets.name(e),ew-25),e.number("hp")>0){target=i;rebuildWidgets()}
            .tooltip=Tooltip.create(Component.literal("특성: "+Component.translatable("cobblemon.ability.${e.text("ability")}").string+"\n예상 피해는 급소를 제외한 범위입니다.\n"+(Moves.getByName(e.text("intent"))?.description?.string?:"")))}
        button(px+pw-66,py+8,54,16,"닫기"){onClose()}
        val phase=data.text("phase")
        if(phase in setOf("VICTORY","DEFEAT")){
            if(phase=="VICTORY")button(px+12,py+ph-58,(pw-30)/2,22,"다음 시험 전투"){command("next")}
            button(px+pw/2+3,py+ph-58,(pw-30)/2-3,22,"시험 모험 종료"){command("quit")}
            button(px+12,py+ph-28,pw-24,20,"화면 닫기 · F10으로 다시 열기"){onClose()};return
        }
        val y=py+if(compact)124 else 150;val h=((ph-(y-py)-34)/2).coerceAtMost(43);val w=(pw-30)/2
        data.getAsJsonArray("slots").forEachIndexed{i,v->if(!v.isJsonObject)return@forEachIndexed;val m=v.asJsonObject
            val label="${i+1}. ${SpireWidgets.move(m.text("move"))} · ${m.number("cost")}에너지"
            val enabled=phase=="PLAYER"&&active.number("hp")>0&&data.number("energy")>=m.number("cost")&&!SpireWidgets.status(active).contains("수면")
            button(px+12+(i%2)*(w+6),y+(i/2)*h,w,h-3,short(label,w-10),enabled){command("move"){addProperty("slot",i);addProperty("instance",m.get("instance").asLong);addProperty("target",target)}}
                .tooltip=Tooltip.create(Component.literal((Moves.getByName(m.text("move"))?.description?.string?:"")+"\n비용 ${m.number("cost")} · 사용한 슬롯만 즉시 보충"))
        }
        val endW=104;val partyW=(pw-endW-36)/3
        party().forEachIndexed{i,c->val enabled=c.number("hp")>0&&i!=data.number("active")&&(data.get("forced")?.asBoolean==true||data.get("switchUsed")?.asBoolean!=true&&data.number("energy")>0)
            button(px+12+i*(partyW+4),py+ph-28,partyW,20,(if(i==data.number("active"))"● " else "↔ ")+short(SpireWidgets.name(c),partyW-22),enabled){command("switch"){addProperty("index",i)}}
                .tooltip=Tooltip.create(Component.literal("${c.number("hp")}/${c.number("maxHp")} HP\n특성: "+Component.translatable("cobblemon.ability.${c.text("ability")}").string+"\n기준 능력치: "+listOf("atk","def","spa","spd","spe").joinToString(" / "){c.obj("stats")?.number(it).toString()}+"\n"+if(data.get("forced")?.asBoolean==true)"기절 교체 무료"else "일반 교체: 에너지 1 · 라운드당 1회"))
        }
        button(px+pw-endW-12,py+ph-28,endW,20,"차례 종료",data.get("forced")?.asBoolean!=true){command("end")}
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,"CobbleSpire · ${data.number("round")}라운드 · Lv.${data.number("level")}",px+12,py+12,0xE7C98F)
        val list=enemies();val ew=(pw-24-(list.size-1)*6)/list.size
        list.forEachIndexed{i,e->val x=px+12+i*(ew+6)
            if(!compact)SpireWidgets.portrait(g,e,x+ew/2,py+48)
            SpireWidgets.hp(g,e,x,py+if(compact)50 else 84,ew)
            text(g,"${e.number("hp")}/${e.number("maxHp")} HP",x,py+if(compact)57 else 91,if(e.number("hp")>0)0xCFDCD7 else 0x72818B)
            val intent=SpireWidgets.move(e.text("intent"));val damage=if(e.number("maxDamage")>0)" ${e.number("minDamage")}~${e.number("maxDamage")}"else ""
            text(g,short("예고: $intent$damage",ew),x,py+if(compact)70 else 103,0xD8B6AD)
        }
        val phase=data.text("phase")
        if(phase in setOf("VICTORY","DEFEAT")){
            text(g,if(phase=="VICTORY")"승리 · 훈련점수 ${data.number("points")}점"else "모든 모험 포켓몬이 기절했습니다.",px+12,py+if(compact)96 else 132,0x99D5B8)
            data.getAsJsonArray("log").toList().takeLast(3).forEachIndexed{i,e->text(g,short(e.asString,pw-24),px+12,py+(if(compact)112 else 150)+i*12,0x9AB5BE)};return
        }
        val ay=py+if(compact)91 else 121
        SpireWidgets.portrait(g,active,px+30,ay+5,10f)
        text(g,short(SpireWidgets.name(active)+"  ${active.number("hp")}/${active.number("maxHp")} HP",pw-75),px+56,ay-4)
        text(g,if(data.get("forced")?.asBoolean==true)"아래에서 생존 포켓몬을 선택하세요 · 무료 교체"else "에너지 ${data.number("energy")}/3 · "+SpireWidgets.status(active),px+56,ay+9,0xB9D7C8)
    }
}
