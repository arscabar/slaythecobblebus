package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/** Terminal battle modal. Loot is granted by the server, not by this screen. */
class BattleResultScreen(data:JsonObject):SpirePane(data){
    override val maximumWidth=560
    override val maximumHeight=322
    private var waiting=false
    private var lastFrameMs=SpireAnim.now()
    private val cardHover=FloatArray(3)
    private val won get()=data.text("phase")=="VICTORY"
    private fun loot()=data.getAsJsonObject("loot")
    private fun offers()=loot()?.array("offers")?.map{it.asJsonObject}.orEmpty()
    private val settled get()=loot()?.get("settled")?.asBoolean==true
    init{SpireMusic.onBattleResult(won,(loot()?.number("revivedCount")?:0)>0)}
    private val hasRelicOffer get()=won&&!settled&&loot()?.text("relicOffer").orEmpty().isNotBlank()
    private val cardW get()=minOf((pw-46)/3,(cardH*2.0/3).toInt())
    private val cardH get()=(ph-(if(hasRelicOffer)162 else 134)).coerceIn(58,150)
    private fun cardX(i:Int)=px+(pw-(3*cardW+20))/2+i*(cardW+10)
    private val cardY get()=py+64
    override fun accept(value:JsonObject){waiting=false;super.accept(value)}
    override fun showError(s:String){waiting=false;super.showError(s);rebuildWidgets()}
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        if(button==0&&won&&!settled&&!waiting){
            val age=SpireAnim.now()-openedAt
            if(age>180L){
                offers().indices.forEach{i->
                    val x=cardX(i);val y=cardY
                    if(mx>=x&&mx<=x+cardW&&my>=y&&my<=y+cardH){
                        SpireMusic.cardPlay()
                        command("claim",i)
                        return true
                    }
                }
            }
        }
        return super.mouseClicked(mx,my,button)
    }
    private fun command(action:String,choice:Int?=null){
        if(waiting)return;waiting=true;rebuildWidgets()
        SpireClient.send(action){addProperty("session",data.text("session"));addProperty("battleNumber",data.number("battleNumber"));addProperty("revision",data.number("revision"));if(choice!=null)addProperty("choice",choice)}
    }
    override fun init(){
        if(won&&!settled)offers().forEachIndexed{i,c->
            val ownerKo=SpireWidgets.speciesName(c.text("ownerSpecies"),c.text("ownerName"))
            button(cardX(i),cardY+cardH+4,cardW,18,"선택",!waiting){command("claim",i)}
                .tooltip=Tooltip.create(Component.literal("$ownerKo 의 모험용 덱에 ${SpireWidgets.move(c.text("move"))} 1장 추가\n"+SpireCardArt.description(c)))
        }
        val relicLabel=loot()?.text("relicOfferLabel").orEmpty().ifBlank{loot()?.text("relicOffer").orEmpty()}
        if(hasRelicOffer){
            button(px+12,py+ph-52,pw-24,18,"유물 획득: $relicLabel",!waiting){command("claim",99)}
                .tooltip=Tooltip.create(Component.literal("카드 대신 유물을 획득합니다: $relicLabel"))
        }
        val allSettled=loot()?.get("allSettled")?.asBoolean!=false
        if(won)button(px+12,py+ph-28,(pw-30)/2,20,when{!settled->"카드 건너뛰기";!allSettled->"팀원 선택 대기 중";else->"경로 지도로"},!waiting&&(!settled||allSettled)){if(settled)command("next")else command("claim",-1)}.primary=true
        button(if(won)px+pw/2+3 else px+12,py+ph-28,if(won)(pw-30)/2-3 else pw-24,20,"모험 종료 · 원래 위치 복귀",!waiting){command("quit")}
    }
    override fun background(g:GuiGraphics){g.fill(0,0,width,height,0xB0061119.toInt());super.background(g)}
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=SpireAnim.now()
        val dt=((now-lastFrameMs).coerceIn(0L,80L))/1000f
        lastFrameMs=now
        val age=now-openedAt
        val headT=SpireAnim.easeOutCubic((age/220f).coerceIn(0f,1f))
        val headY=((1f-headT)*-8f).roundToInt()
        g.pose().pushPose();g.pose().translate((px+pw/2).toDouble(),(py+11+headY).toDouble(),0.0);g.pose().scale(1.7f,1.7f,1f)
        g.drawCenteredString(font,if(won)"전투 승리!"else "전투 패배",0,0,if(won)0xE8CD86 else 0xE79D96);g.pose().popPose()
        g.drawCenteredString(font,if(won)"진행 +2 반영 · 누적 ${data.number("points")}점 · 부활 ${loot()?.number("revivedCount")?:0}마리"else "모험 파티가 모두 기절했습니다.",px+pw/2,py+34,0xD7E6D8)
        if(!won){text(g,"보상은 지급되지 않습니다.",px+16,py+64,0xACBCBE);return}
        val allSettled=loot()?.get("allSettled")?.asBoolean!=false
        val state=when{!settled->"전리품 · 기술카드 1장 선택 (모험 전용)";!allSettled->"전리품 선택 완료 · 다른 팀원 선택 대기 중";loot()?.number("selected")==-1->"정산 완료 · 경로 지도로 이동하세요.";else->"선택한 카드가 모험용 덱에 추가됐습니다."}
        g.drawCenteredString(font,state,px+pw/2,py+49,0xA9DFCF)
        offers().forEachIndexed{i,c->
            val delay=i*75L
            val revealT=SpireAnim.easeOutBack(((age-delay).coerceAtLeast(0L)/240f).coerceIn(0f,1f))
            val enterY=((1f-revealT)*12f).roundToInt()
            val x=cardX(i);val baseY=cardY
            val over=!settled&&!waiting&&mx>=x&&mx<=x+cardW&&my>=baseY&&my<=baseY+cardH
            cardHover[i]=SpireAnim.approach(cardHover[i],if(over)1f else 0f,dt,15f)
            val liftY=-(cardHover[i]*6f).roundToInt()
            val selectedCard=settled&&loot()?.number("selected")==i
            val drawY=baseY+enterY+liftY
            if(selectedCard){
                val p=SpireAnim.pulse(1200L)
                SpireSkin.panel(g,x-3,drawY-3,cardW+6,cardH+6,SpireAnim.lerpColor(SpireSkin.gold,0xFFFFF0A0.toInt(),p))
            }else if(cardHover[i]>0.05f){
                SpireSkin.panel(g,x-2,drawY-2,cardW+4,cardH+4,SpireAnim.withAlpha(SpireSkin.teal,cardHover[i]*0.85f))
            }
            SpireCardArt.draw(g,c,x,drawY,cardW,cardH,hover=selectedCard||over,affordable=!settled||selectedCard)
            val name=short(SpireWidgets.speciesName(c.text("ownerSpecies"),c.text("ownerName")),cardW)
            g.drawCenteredString(font,name,x+cardW/2,if(settled)baseY+cardH+8 else baseY+cardH+24,0xD7E6D8)
        }
    }
    override fun onClose(){} // Result acknowledgement must not silently dismiss unclaimed loot.
}
