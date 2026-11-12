package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

/** Terminal battle modal. Loot is granted by the server, not by this screen. */
class BattleResultScreen(data:JsonObject):SpirePane(data){
    override val maximumWidth=560
    override val maximumHeight=322
    private var waiting=false
    private val won get()=data.text("phase")=="VICTORY"
    private fun loot()=data.getAsJsonObject("loot")
    private fun offers()=loot()?.array("offers")?.map{it.asJsonObject}.orEmpty()
    private val settled get()=loot()?.get("settled")?.asBoolean==true
    init{SpireMusic.onBattleResult(won,(loot()?.number("revivedCount")?:0)>0)}
    private val cardW get()=minOf((pw-46)/3,(cardH*2.0/3).toInt())
    private val cardH get()=(ph-130).coerceIn(58,160)
    private fun cardX(i:Int)=px+(pw-(3*cardW+20))/2+i*(cardW+10)
    private val cardY get()=py+64
    override fun accept(value:JsonObject){waiting=false;super.accept(value)}
    override fun showError(s:String){waiting=false;super.showError(s);rebuildWidgets()}
    private fun command(action:String,choice:Int?=null){
        if(waiting)return;waiting=true;rebuildWidgets()
        SpireClient.send(action){addProperty("session",data.text("session"));addProperty("battleNumber",data.number("battleNumber"));addProperty("revision",data.number("revision"));if(choice!=null)addProperty("choice",choice)}
    }
    override fun init(){
        if(won&&!settled)offers().forEachIndexed{i,c->
            button(cardX(i),cardY+cardH+4,cardW,18,"선택",!waiting){command("claim",i)}
                .tooltip=Tooltip.create(Component.literal("${c.text("ownerName")}의 모험용 덱에 ${SpireWidgets.move(c.text("move"))} 1장 추가\n"+SpireCardArt.description(c)))
        }
        if(won)button(px+12,py+ph-28,(pw-30)/2,20,if(settled)"다음 전투"else "카드 건너뛰기",!waiting){if(settled)command("next")else command("claim",-1)}.primary=true
        button(if(won)px+pw/2+3 else px+12,py+ph-28,if(won)(pw-30)/2-3 else pw-24,20,"모험 종료 · 원래 위치 복귀",!waiting){command("quit")}
    }
    override fun background(g:GuiGraphics){g.fill(0,0,width,height,0xB0061119.toInt());super.background(g)}
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        g.pose().pushPose();g.pose().translate((px+pw/2).toDouble(),(py+11).toDouble(),0.0);g.pose().scale(1.7f,1.7f,1f)
        g.drawCenteredString(font,if(won)"전투 승리!"else "전투 패배",0,0,if(won)0xE8CD86 else 0xE79D96);g.pose().popPose()
        g.drawCenteredString(font,if(won)"진행 +2 반영 · 누적 ${data.number("points")}점 · 부활 ${loot()?.number("revivedCount")?:0}마리"else "모험 파티가 모두 기절했습니다.",px+pw/2,py+34,0xD7E6D8)
        if(!won){text(g,"보상은 지급되지 않습니다.",px+16,py+64,0xACBCBE);return}
        val state=if(!settled)"전리품 · 기술카드 1장 선택 (모험 전용)"else if(loot()?.number("selected")==-1)"카드를 건너뛰었습니다."else "선택한 카드가 모험용 덱에 추가됐습니다."
        g.drawCenteredString(font,state,px+pw/2,py+49,0xA9DFCF)
        offers().forEachIndexed{i,c->
            SpireCardArt.draw(g,c,cardX(i),cardY,cardW,cardH,hover=settled&&loot()?.number("selected")==i,affordable=!settled||loot()?.number("selected")==i)
            val name=short(c.text("ownerName"),cardW)
            g.drawCenteredString(font,name,cardX(i)+cardW/2,if(settled)cardY+cardH+8 else cardY+cardH+24,0xD7E6D8)
        }
    }
    override fun onClose(){} // Result acknowledgement must not silently dismiss unclaimed loot.
}
