package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.roundToInt

object BattleHudRenderer {
    private fun text(g:GuiGraphics,s:String,x:Int,y:Int,c:Int=0xE4ECE5){val f=Minecraft.getInstance().font;g.drawString(f,s,x,y,c)}
    private fun short(s:String,w:Int):String{val f=Minecraft.getInstance().font;return if(f.width(s)<=w)s else f.plainSubstrByWidth(s,(w-7).coerceAtLeast(0))+"…"}
    fun card(g:GuiGraphics,c:JsonObject,b:CardBox,target:Int,hover:Boolean=false,key:String=""){val face=c.deepCopy();DamageLabels.preview(c,target)?.let{face.add("displayPreview",it)}
        SpireCardArt.draw(g,face,b.x.roundToInt(),b.y.roundToInt()-(if(hover)8 else 0),b.w.toInt(),b.h.toInt(),hover,!BattleHudState.pending&&BattleHudState.canPlay(c),key=key)}
    fun frame(g:GuiGraphics,d:JsonObject,l:BattleHudLayout,interactive:Boolean){
        val message=BattleHudState.toast()
        val turn=if(d.get("ended")?.asBoolean==true)"아군 대기 (${d.number("endedCount")}/${d.number("alivePlayers")})"else if(d.text("phase")=="PLAYER")"내 차례"else "상대 행동"
        val title=if(l.height<280&&message.isNotBlank())"${d.number("round")}턴 · $message"else if(d.number("surrenderVotes")>0)"항복 동의 ${d.number("surrenderVotes")}/${d.number("participantCount")}"else "${d.number("round")}턴 · $turn"
        text(g,short(title,if(interactive)l.width-(if(l.width<420)174 else 260)else l.width/2-12),12,10,0xE8D4A8)
        if(!interactive)text(g,"숫자키: 카드 사용 · F10: HUD 펼치기",(l.width-214).coerceAtLeast(130),10,0xBAD6D0)
        val enemies=d.getAsJsonArray("enemies")
        val pings=d.array("pings")?.map{it.asJsonObject}.orEmpty()
        enemies.forEachIndexed{i,v->val e=v.asJsonObject;val b=l.enemy(i,enemies.size());val x=b.x.toInt();val y=b.y.toInt();val w=b.w.toInt()
            val marks=pings.filter{it.number("target")==i}
            SpireSkin.panel(g,x,y,w,b.h.toInt(),if(marks.isNotEmpty())SpireSkin.gold else if(i==BattleHudState.target)SpireSkin.teal else 0xFF576762.toInt())
            text(g,short(SpireWidgets.name(e),w-70),x+5,y+4);text(g,"${e.number("hp")}/${e.number("maxHp")}",x+w-62,y+4,0xBBD3CB)
            SpireWidgets.hp(g,e,x+5,y+15,w-10)
            val role=CardRole.parse(e.text("intentRole"));val move=SpireWidgets.move(e.text("intent"))
            text(g,short(move,w-10),x+5,y+21,role.color)
            val intent=if(role==CardRole.ATTACK)"${DamageLabels.type(e.text("intentType"))} · ${DamageLabels.damage(e)} 피해"else e.text("intentEffect")
            text(g,short(intent,w-10),x+5,y+32,0xC8D6D1)
            if(marks.isNotEmpty()&&l.stripY>=116){
                val minDamage=marks.sumOf{it.number("minDamage")};val maxDamage=marks.sumOf{it.number("maxDamage")}
                val lethal=maxDamage>=e.number("hp")&&e.number("hp")>0
                SpireSkin.panel(g,x,y+46,w,22,if(lethal)0x4ADE80 else SpireSkin.gold)
                text(g,short("◎ ${if(lethal)"처치각"else "합계"} $minDamage~$maxDamage",w-10),x+5,y+49,0xF7D379)
                text(g,short(marks.joinToString(" · "){it.text("playerName")},w-10),x+5,y+59,0xDCECE8)
            }
        }
        val allies=d.array("allies")?.map{it.asJsonObject}.orEmpty()
        if(allies.isNotEmpty()&&l.toastY>=136){
            val w=minOf(160,(l.width-24-(allies.size-1)*4)/allies.size)
            allies.forEachIndexed{i,a->val x=12+i*(w+4);val p=a.getAsJsonObject("active")
                val state=if(a.get("alive")?.asBoolean!=true)"기절"else if(a.get("ended")?.asBoolean==true)"완료"else "행동"
                SpireSkin.panel(g,x,104,w,28,SpireSkin.gold)
                text(g,short("${a.text("name")} · $state · ${a.number("damageDealt")}뎀",w-8),x+4,108)
                SpireWidgets.hp(g,p,x+4,120,w-8)
            }
        }
        if(l.toastY>=157)d.array("damageEvents")?.lastOrNull()?.asJsonObject?.let{hit->
            val who=if(hit.get("incoming")?.asBoolean==true)"적"else if(hit.get("isSelf")?.asBoolean==true)"나"else hit.text("ownerName")
            val result=if(hit.get("miss")?.asBoolean==true)"빗나감/무효"else "${hit.number("amount")} 피해"
            text(g,short("$who: ${hit.text("actor")} → ${hit.text("target")} $result",l.width-24),12,138,0xDCECE8)
        }
        g.fillGradient(0,l.stripY-5,l.width,l.height,0x20102025,0xE90A171F.toInt())
        val p=d.getAsJsonArray("party")[d.number("active")].asJsonObject
        val infoX=(l.hand(0).x.toInt()).coerceAtLeast(12)
        val energyX=(l.width-90).coerceAtLeast(170)
        val infoW=minOf(220,energyX-infoX-12).coerceAtLeast(60)
        text(g,short("${SpireWidgets.name(p)}  ${p.number("hp")}/${p.number("maxHp")} HP · 누적 ${d.number("selfDamageDealt")}뎀",infoW),infoX,l.stripY+3)
        SpireWidgets.hp(g,p,infoX,l.stripY+15,minOf(128,infoW))
        for(i in 0..2)SpireSkin.gem(g,energyX+i*17,l.stripY+8,4,i<d.number("energy"))
        text(g,"${d.number("energy")}/3",energyX+52,l.stripY+4,0xBBE5D8)
        if(d.get("forced")?.asBoolean==true)text(g,"생존 포켓몬으로 교체하세요",infoX+140,l.stripY+4,0xF1B28D)
        val toast=if(l.height>=280)BattleHudState.toast()else ""
        if(toast.isNotBlank()){val w=minOf(340,l.width-28);val x=(l.width-w)/2;SpireSkin.panel(g,x,l.toastY,w,18,0xFF8A9E91.toInt());text(g,short(toast,w-12),x+6,l.toastY+5,0xD4E5D7)}
    }
    @JvmStatic fun render(g:GuiGraphics){
        val d=BattleHudState.latest?:return;val mc=Minecraft.getInstance();val l=BattleHudLayout(mc.window.guiScaledWidth,mc.window.guiScaledHeight,d.getAsJsonArray("hand").size(),false)
        frame(g,d,l,false)
        d.getAsJsonArray("hand").forEachIndexed{i,v->card(g,v.asJsonObject,l.hand(i),BattleHudState.target,key=if(i==9)"0"else (i+1).toString())}
        val piles=d.getAsJsonObject("piles")
        for((key,label,b) in listOf(Triple("draw","뽑기",l.draw),Triple("discard","버림",l.discard),Triple("exhaust","소멸",l.exhaust))){SpireSkin.panel(g,b.x.toInt(),b.y.toInt(),b.w.toInt(),b.h.toInt());text(g,"$label ${piles.getAsJsonArray(key).size()}",b.x.toInt()+4,b.y.toInt()+6,0xB9CBC6)}
        val party=d.getAsJsonArray("party");val w=minOf(130,(l.width-130)/3)
        party.forEachIndexed{i,v->val p=v.asJsonObject;text(g,short((if(i==d.number("active"))"● "else "")+SpireWidgets.name(p)+" ${p.number("hp")}",w-5),12+i*w,l.partyY+6,if(i==d.number("active"))0xA9DBCA else 0x8DABA8)}
    }
}
