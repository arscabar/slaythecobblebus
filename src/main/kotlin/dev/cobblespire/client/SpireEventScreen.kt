package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import kotlin.math.pow
import kotlin.math.roundToInt

class SpireEventScreen(data:JsonObject):SpirePane(data){
    override val maximumWidth=500
    override val maximumHeight=310
    override val supportsAdventureMenu=true
    private val resolved get()=data.get("resolved")?.asBoolean==true
    private val myChoice get()=data.text("myChoice")
    private val eventKind get()=data.text("eventKind")
    private var lotteryStartMs=0L
    private var lotterySeenKey=""
    private var lastJoinedCount=data.array("joined")?.size()?:0
    private var lastSkippedCount=data.array("skipped")?.size()?:0
    private var joinFlashAt=0L
    private var skipFlashAt=0L
    init{checkLottery(data)}
    private fun checkLottery(d:JsonObject){
        val win=d.text("winnerName")
        if(d.get("resolved")?.asBoolean==true&&win.isNotBlank()){
            val key="${d.text("title")}:$win"
            if(key!=lotterySeenKey){
                lotterySeenKey=key
                lotteryStartMs=SpireAnim.now()
            }
        }
    }
    override fun accept(value:JsonObject){
        val now=SpireAnim.now()
        val j=value.array("joined")?.size()?:0
        val s=value.array("skipped")?.size()?:0
        if(j>lastJoinedCount)joinFlashAt=now
        if(s>lastSkippedCount)skipFlashAt=now
        lastJoinedCount=j;lastSkippedCount=s
        checkLottery(value)
        super.accept(value)
    }
    private fun party()=data.array("party")?.map{it.asJsonObject}.orEmpty()
    private fun names(key:String)=data.array(key)?.joinToString(", "){it.asString}.orEmpty().ifBlank{"없음"}
    override fun init(){
        val chosen=myChoice.isNotBlank()||resolved
        if(!chosen){
            if(eventKind=="RECRUIT"){
                val pList=party()
                if(pList.size<3){
                    button(px+16,py+ph-62,(pw-40)/2,22,"영입 수락",true){SpireClient.send("event_choice"){addProperty("choice","ACCEPT")}}.primary=true
                    button(px+24+(pw-40)/2,py+ph-62,(pw-40)/2,22,"거절",true){SpireClient.send("event_choice"){addProperty("choice","SKIP")}}
                }else{
                    val bw=(pw-48)/4
                    pList.forEachIndexed{i,c->
                        button(px+16+i*(bw+4),py+ph-62,bw,22,short("${SpireWidgets.name(c)} 교체",bw-6),true){
                            SpireClient.send("event_choice"){addProperty("choice","REPLACE:$i")}
                        }.tooltip=Tooltip.create(Component.literal("Lv.${c.number("level",1)} ${SpireWidgets.name(c)}을(를) 내보내고 영입합니다."))
                    }
                    button(px+16+3*(bw+4),py+ph-62,bw,22,"거절",true){SpireClient.send("event_choice"){addProperty("choice","SKIP")}}
                }
            }else{
                button(px+16,py+ph-62,(pw-40)/2,22,"참가한다",true){SpireClient.send("event_choice"){addProperty("choice","JOIN")}}.primary=true
                button(px+24+(pw-40)/2,py+ph-62,(pw-40)/2,22,"참가하지 않는다",true){SpireClient.send("event_choice"){addProperty("choice","SKIP")}}
            }
        }
        val isHost=data.get("isHost")?.asBoolean!=false
        if(resolved){
            button(px+16,py+ph-32,pw-32,22,if(isHost)"지도로 이동" else "방장 진행 대기 중",isHost){SpireClient.send("event_continue")}.primary=isHost
        }else{
            button(px+16,py+ph-32,pw-32,22,"선택 및 추첨 대기 중...",false){}
        }
        addAdventureMenuButton(px+pw-52,py+8,40,14)
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=SpireAnim.now()
        text(g,data.text("title"),px+16,py+14,0xE7C98F)
        var y=py+34
        data.text("description").split("\n").forEach{line->
            text(g,short(line,pw-32),px+16,y,0xD7E6D8);y+=12
        }
        if(eventKind=="RECRUIT"){
            data.getAsJsonObject("candidate")?.let{c->
                SpireSkin.panel(g,px+16,y+6,pw-32,86,SpireSkin.teal)
                val pulse=SpireAnim.pulse(1700L)
                SpireAnim.drawRing(g,px+66,y+52,(24+pulse*4f).roundToInt(),SpireAnim.withAlpha(0x5CEAD6,0.28f+pulse*0.28f))
                SpireAnim.drawRing(g,px+66,y+52,18,SpireAnim.withAlpha(0xE8C87A,0.32f))
                SpireWidgets.portrait(g,c,px+66,y+58,20f)
                text(g,"Lv.${c.number("level",1)} ${SpireWidgets.name(c)}",px+116,y+16,0xF7D379)
                val types=c.array("types")?.joinToString(" / "){DamageLabels.type(it.asString)}.orEmpty()
                text(g,"타입: $types · HP ${c.number("maxHp")}",px+116,y+30,0xD7E6D8)
                text(g,"특성: "+Component.translatable("cobblemon.ability.${c.text("ability")}").string,px+116,y+44,0xA9DFCF)
                y+=98
            }
        }else{
            y+=10
            SpireSkin.panel(g,px+16,y,pw-32,58,SpireSkin.gold)
            val jFlash=if(now-joinFlashAt<420L)1f-(now-joinFlashAt)/420f else 0f
            val sFlash=if(now-skipFlashAt<420L)1f-(now-skipFlashAt)/420f else 0f
            if(jFlash>0f)g.fill(px+20,y+8,px+pw-20,y+20,SpireAnim.withAlpha(0x8CE8B5,jFlash*0.24f))
            if(sFlash>0f)g.fill(px+20,y+22,px+pw-20,y+34,SpireAnim.withAlpha(0xECAF93,sFlash*0.24f))
            text(g,"참가: ${names("joined")}",px+24,y+10,0x8CE8B5)
            text(g,"불참: ${names("skipped")}",px+24,y+24,0xECAF93)
            val waitText=names("waiting")
            val waitSuffix=if(!resolved&&waitText!="없음")SpireAnim.loadingDots() else ""
            text(g,"대기 중: $waitText$waitSuffix",px+24,y+38,0xBDD3D1)
            y+=66
        }
        if(resolved){
            val win=data.text("winnerName")
            val joined=data.array("joined")?.map{it.asString}.orEmpty()
            val spinning=win.isNotBlank()&&joined.size>1&&now-lotteryStartMs<1350L
            if(spinning){
                val p=((now-lotteryStartMs)/1350f).coerceIn(0f,1f)
                val eased=1f-(1f-p).pow(2)
                val ticks=(eased*14f).toInt()
                val rolling=joined[ticks%joined.size]
                val border=SpireAnim.lerpColor(SpireSkin.gold,SpireSkin.teal,SpireAnim.pulse(260L))
                SpireSkin.panel(g,px+16,y,pw-32,40,border)
                SpireAnim.drawSheen(g,px+18,y+2,pw-36,36,((now%450L)/450f),0x2EFFF3C4)
                text(g,"추첨 진행 중...",px+24,y+8,0x8CE8B5)
                text(g,"▶ $rolling",px+24,y+22,0xFFE87A)
            }else{
                val revealAge=if(win.isNotBlank()&&joined.size>1)(now-lotteryStartMs-1350L).coerceAtLeast(0L) else (now-lotteryStartMs).coerceAtLeast(0L)
                val flash=if(win.isNotBlank()&&revealAge<450L)1f-(revealAge/450f) else 0f
                SpireSkin.panel(g,px+16,y,pw-32,40,SpireAnim.lerpColor(0xFFE6B84D.toInt(),0xFFFFF2B0.toInt(),flash))
                if(flash>0f)g.fill(px+18,y+2,px+pw-18,y+38,SpireAnim.withAlpha(0xFFE885,flash*0.26f))
                if(win.isNotBlank())text(g,"추첨 결과 · 당첨: $win",px+24,y+8,0xF7D379)
                text(g,short(data.text("outcomeText"),pw-48),px+24,y+(if(win.isNotBlank())22 else 14),0xE7EFE6)
            }
        }
    }
}
