package dev.cobblespire.client

import dev.cobblespire.*
import com.google.gson.JsonObject
import com.cobblemon.mod.common.api.moves.Moves
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import java.util.UUID
import kotlin.math.roundToInt

class SpireBattleScreen(data:JsonObject):SpirePane(data){
    override val maximumWidth=760
    override val maximumHeight=430
    private var target=0;private var hover=-1;private var waiting=false
    private val animation=HandAnimation()
    private val controls=mutableListOf<Pair<Button,Boolean>>()
    private var oldCount=data.getAsJsonArray("hand").size()
    init{animation.update(null,data,data.get("fresh")?.asBoolean==true)}
    private fun enemies()=data.getAsJsonArray("enemies").map{it.asJsonObject}
    private fun party()=data.getAsJsonArray("party").map{it.asJsonObject}
    private fun hand()=data.getAsJsonArray("hand").map{it.asJsonObject}
    private val compact get()=ph<300
    private val active get()=party()[data.number("active")]
    private fun layout()=CardBoardLayout(pw,ph,hand().size)
    private fun busy()=waiting||animation.busy()
    override fun showError(s:String){waiting=false;super.showError(s)}
    override fun accept(value:JsonObject){
        if(value.text("session")==data.text("session")&&(value.number("battleNumber")<data.number("battleNumber")||value.number("battleNumber")==data.number("battleNumber")&&value.number("revision")<data.number("revision")))return
        oldCount=hand().size;animation.update(data,value,true);waiting=false;hover=-1;data=value
        if(enemies().getOrNull(target)?.number("hp",0)!=null&&enemies()[target].number("hp")==0)target=enemies().indexOfFirst{it.number("hp")>0}.coerceAtLeast(0)
        if(target !in enemies().indices)target=0
        super.accept(value)
    }
    private fun command(action:String,body:JsonObject.()->Unit={}){
        if(busy())return;waiting=true
        SpireClient.send(action){addProperty("session",data.text("session"));addProperty("battleNumber",data.number("battleNumber"));addProperty("revision",data.number("revision"));addProperty("request",UUID.randomUUID().toString());body()}
    }
    private fun control(x:Int,y:Int,w:Int,h:Int,label:String,enabled:Boolean=true,click:()->Unit):Button{
        return button(x,y,w,h,label,enabled,click).also{controls.add(it to enabled)}
    }
    override fun init(){
        controls.clear()
        val list=enemies();val ew=(pw-24-(list.size-1)*6)/list.size
        list.forEachIndexed{i,e->control(px+12+i*(ew+6),py+if(compact)31 else 64,ew,16,(if(target==i)"● " else "")+short(SpireWidgets.name(e),ew-25),e.number("hp")>0){target=i;rebuildWidgets()}
            .tooltip=Tooltip.create(Component.literal("특성: "+Component.translatable("cobblemon.ability.${e.text("ability")}").string+"\n예상 피해는 급소 제외\n"+SpireWidgets.status(e)+"\n"+(Moves.getByName(e.text("intent"))?.description?.string?:"")))}
        button(px+pw-66,py+8,54,16,"닫기"){onClose()}
        val l=layout()
        for((kind,title,box) in listOf(Triple("draw","뽑기",l.draw),Triple("discard","버림",l.discard),Triple("exhaust","소멸",l.exhaust))){
            val n=data.getAsJsonObject("piles").getAsJsonArray(kind).size()
            control(px+box.x.toInt(),py+box.y.toInt(),box.w.toInt(),box.h.toInt(),"$title $n"){minecraft?.setScreen(PileScreen(this,data,kind))}
                .tooltip=Tooltip.create(Component.literal(if(kind=="draw")"남은 카드 보기 · 순서는 공개하지 않습니다." else if(kind=="discard")"이미 사용했거나 턴 종료에 버린 카드" else "이번 전투에서 다시 뽑히지 않는 카드"))
        }
        val phase=data.text("phase")
        if(phase in setOf("VICTORY","DEFEAT")){
            if(phase=="VICTORY")control(px+12,py+ph-28,(pw-30)/2,20,"다음 시험 전투"){command("next")}
            control(px+pw/2+3,py+ph-28,(pw-30)/2-3,20,"시험 모험 종료"){command("quit")};return
        }
        val endW=102;val partyW=(pw-endW-36)/3
        party().forEachIndexed{i,c->val enabled=c.number("hp")>0&&i!=data.number("active")&&(data.get("forced")?.asBoolean==true||data.get("switchUsed")?.asBoolean!=true&&data.number("energy")>0)
            control(px+12+i*(partyW+4),py+ph-28,partyW,20,(if(i==data.number("active"))"● " else "↔ ")+short(SpireWidgets.name(c),partyW-22),enabled){command("switch"){addProperty("index",i)}}
                .tooltip=Tooltip.create(Component.literal("${c.number("hp")}/${c.number("maxHp")} HP\n교체: 남은 손패 모두 버림 → 새로 5장\n"+if(data.get("forced")?.asBoolean==true)"기절 교체 무료"else "일반 교체 에너지 1 · 라운드당 1회"))
        }
        control(px+pw-endW-12,py+ph-28,endW,20,"턴 종료",data.get("forced")?.asBoolean!=true){command("end")}
    }
    private fun canPlay(c:JsonObject)=!busy()&&data.text("phase")=="PLAYER"&&active.number("hp")>0&&data.number("energy")>=c.number("cost")&&(c.get("trainer")?.asBoolean==true||!SpireWidgets.status(active).contains("수면"))
    private fun play(index:Int){val c=hand().getOrNull(index)?:return;if(canPlay(c))command("move"){addProperty("handIndex",index);addProperty("instance",c.get("instance").asLong);addProperty("target",target)}}
    private fun hovered(mx:Double,my:Double):Int{
        if(busy())return -1
        val l=layout();val x=mx-px;val y=my-py
        if(hover in hand().indices){val b=l.hand(hover);if(CardBox(b.x,b.y-10,b.w,b.h+10).contains(x,y))return hover}
        return hand().indices.reversed().firstOrNull{l.hand(it).contains(x,y)}?:-1
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        if(button==0){val index=hovered(mx,my);if(index>=0){play(index);return true}}
        return super.mouseClicked(mx,my,button)
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean{
        val i=if(key in 49..57)key-49 else if(key==48)9 else -1
        if(i>=0){play(i);return true};return super.keyPressed(key,scan,mods)
    }
    private fun color(c:JsonObject):Int=if(c.get("trainer")?.asBoolean==true)0xFF879CCB.toInt()else mapOf("fire" to 0xFFD68E65.toInt(),"water" to 0xFF76A5CE.toInt(),"grass" to 0xFF88B88B.toInt(),"electric" to 0xFFDCC46D.toInt(),"psychic" to 0xFFC28FAF.toInt(),"ghost" to 0xFF9991B5.toInt(),"poison" to 0xFFAA91BE.toInt()).getOrDefault(c.text("type"),0xFFA8B9AD.toInt())
    private fun drawCard(g:GuiGraphics,c:JsonObject,b:CardBox,lift:Boolean=false,moving:Boolean=false){
        val x=(px+b.x).roundToInt();val y=(py+b.y-(if(lift)10 else 0)).roundToInt();val w=b.w.roundToInt().coerceAtLeast(20);val h=b.h.roundToInt().coerceAtLeast(26)
        val border=if(lift)0xFFF0D69B.toInt()else color(c)
        g.fill(x-1,y-1,x+w+1,y+h+1,border);g.fillGradient(x,y,x+w,y+h,0xFF2F4249.toInt(),0xFF17262F.toInt())
        g.fill(x+3,y+3,x+15,y+15,0xFF0B1821.toInt());text(g,c.number("cost").toString(),x+6,y+5,0xF4DCA9)
        val name=SpireWidgets.move(c.text("move"))+(if(c.get("upgraded")?.asBoolean==true)"+"else "")
        text(g,short(name,w-7),x+4,y+19,0xF2E9CE)
        if(h>57){val type=if(c.get("trainer")?.asBoolean==true)"지원"else Component.translatable("cobblemon.type.${c.text("type")}").string
            text(g,short(type,w-7),x+4,y+h/2,color(c))
            if(c.number("draw")>0)text(g,short("${c.number("draw")}장 뽑기",w-7),x+4,y+h/2+12,0xBBDACD)
        }
        if(h>37)text(g,if(c.get("exhaust")?.asBoolean==true)"소멸"else if(c.get("retain")?.asBoolean==true)"보존"else "",x+4,y+h-12,0xD7B48A)
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=System.currentTimeMillis();controls.forEach{(b,enabled)->b.active=enabled&&!busy()}
        text(g,"CobbleSpire · 전투 ${data.number("battleNumber")} · ${data.number("round")}턴",px+12,py+12,0xE7C98F)
        val list=enemies();val ew=(pw-24-(list.size-1)*6)/list.size
        list.forEachIndexed{i,e->val x=px+12+i*(ew+6)
            if(!compact)SpireWidgets.portrait(g,e,x+ew/2,py+48)
            SpireWidgets.hp(g,e,x,py+if(compact)50 else 84,ew);text(g,"${e.number("hp")}/${e.number("maxHp")} HP",x,py+if(compact)57 else 91)
            val damage=if(e.number("maxDamage")>0)" ${e.number("minDamage")}~${e.number("maxDamage")}"else ""
            text(g,short("예고: ${SpireWidgets.move(e.text("intent"))}$damage",ew),x,py+if(compact)70 else 103,0xD8B6AD)
        }
        val ay=py+if(compact)91 else 136
        SpireWidgets.portrait(g,active,px+28,ay+5,10f)
        text(g,short("${SpireWidgets.name(active)}  ${active.number("hp")}/${active.number("maxHp")} HP",pw-130),px+55,ay-4)
        text(g,short(if(data.get("forced")?.asBoolean==true)"생존 포켓몬으로 무료 교체하세요."else "에너지 ${data.number("energy")}/3 · 손패 ${hand().size}/${data.number("handLimit")} · "+SpireWidgets.status(active),pw-130),px+55,ay+9,0xB9D7C8)
        val l=layout();hover=hovered(mx.toDouble(),my.toDouble())
        for(box in listOf(l.draw,l.discard,l.exhaust)){g.fill(px+box.x.toInt()+3,py+box.y.toInt()-4,px+(box.x+box.w).toInt()+3,py+(box.y+box.h).toInt()-4,0xFF38505A.toInt())}
        hand().forEachIndexed{i,c->if(i!=hover&&!animation.hiddenHand(c.get("instance").asLong,now))drawCard(g,c,l.hand(i))}
        hand().getOrNull(hover)?.let{drawCard(g,it,l.hand(hover),true)}
        val previous=CardBoardLayout(pw,ph,oldCount)
        for(f in animation.flights.filter{it.active(now)}){
            val from=if(f.from=="HAND")previous.hand(f.fromIndex.coerceIn(0,(oldCount-1).coerceAtLeast(0)))else l.anchor(f.from,f.fromIndex)
            val to=l.anchor(f.to,f.toIndex);val p=f.progress(now);val t=p*p*(3-2*p)
            val box=CardBox(from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t-kotlin.math.sin(p*Math.PI)*14,from.w+(to.w-from.w)*t,from.h+(to.h-from.h)*t)
            drawCard(g,f.card,box,moving=true)
        }
        if(data.text("phase") in setOf("VICTORY","DEFEAT"))text(g,if(data.text("phase")=="VICTORY")"승리 · 진행 ${data.number("points")}점 · 능력치 고정"else "시험 전투 패배",px+12,py+if(compact)114 else 165,0xE7C98F)
    }
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        super.render(g,mx,my,delta)
        hand().getOrNull(hover)?.let{c->if(!busy()){
            val desc=if(c.get("trainer")?.asBoolean==true)TrainerCards.description(c.text("move"))else Moves.getByName(c.text("move"))?.description?.string.orEmpty()
            g.renderTooltip(font,font.split(Component.literal(SpireWidgets.move(c.text("move"))+"\n"+desc+"\n턴 종료 시 버림 · 보존 카드는 유지"),(pw-24).coerceAtMost(280)),mx,my)
        }}
    }
}
