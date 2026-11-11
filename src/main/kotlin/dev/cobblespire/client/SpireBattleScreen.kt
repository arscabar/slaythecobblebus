package dev.cobblespire.client

import dev.cobblespire.*
import com.google.gson.JsonObject
import com.cobblemon.mod.common.api.moves.Moves
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
    private fun enemyWidth()=minOf(if(compact)128 else 190,(pw/2-18-(enemies().size-1)*4)/enemies().size)
    private fun enemyX(i:Int)=px+pw-12-enemies().size*(enemyWidth()+4)+i*(enemyWidth()+4)

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
    private fun control(x:Int,y:Int,w:Int,h:Int,label:String,enabled:Boolean=true,click:()->Unit):SpireButton{
        return button(x,y,w,h,label,enabled,click).also{controls.add(it to enabled)}
    }
    override fun init(){
        controls.clear()
        val list=enemies();val ew=enemyWidth()
        list.forEachIndexed{i,e->control(enemyX(i),py+31,ew,14,(if(target==i)"◆ " else "")+short(SpireWidgets.name(e),ew-20),e.number("hp")>0){target=i;rebuildWidgets()}
            .also{it.selected=target==i}.tooltip=Tooltip.create(Component.literal("특성: "+Component.translatable("cobblemon.ability.${e.text("ability")}").string+"\n예상 피해는 급소 제외\n"+SpireWidgets.status(e)+"\n"+(Moves.getByName(e.text("intent"))?.description?.string?:"")))}
        button(px+pw-66,py+8,54,16,"닫기"){onClose()}
        val l=layout()
        for((kind,title,box) in listOf(Triple("draw","뽑기",l.draw),Triple("discard","버림",l.discard),Triple("exhaust","소멸",l.exhaust))){
            val n=data.getAsJsonObject("piles").getAsJsonArray(kind).size()
            control(px+box.x.toInt(),py+box.y.toInt(),box.w.toInt(),box.h.toInt(),"$title $n"){minecraft?.setScreen(PileScreen(this,data,kind))}
                .also{it.pile=true}.tooltip=Tooltip.create(Component.literal(if(kind=="draw")"남은 카드 보기 · 순서는 공개하지 않습니다." else if(kind=="discard")"이미 사용했거나 턴 종료에 버린 카드" else "이번 전투에서 다시 뽑히지 않는 카드"))
        }
        val phase=data.text("phase")
        if(phase in setOf("VICTORY","DEFEAT")){
            if(phase=="VICTORY")control(px+12,py+ph-28,(pw-30)/2,20,"다음 시험 전투"){command("next")}
            control(px+pw/2+3,py+ph-28,(pw-30)/2-3,20,"시험 모험 종료"){command("quit")};return
        }
        val endW=102;val partyW=(pw-endW-36)/3
        party().forEachIndexed{i,c->val enabled=c.number("hp")>0&&i!=data.number("active")&&(data.get("forced")?.asBoolean==true||data.get("switchUsed")?.asBoolean!=true&&data.number("energy")>0)
            control(px+12+i*(partyW+4),py+ph-28,partyW,20,(if(i==data.number("active"))"● " else "↔ ")+short(SpireWidgets.name(c),partyW-22),enabled){command("switch"){addProperty("index",i)}}
                .also{it.selected=i==data.number("active")}.tooltip=Tooltip.create(Component.literal("${c.number("hp")}/${c.number("maxHp")} HP\n교체: 남은 손패 모두 버림 → 새로 5장\n"+if(data.get("forced")?.asBoolean==true)"기절 교체 무료"else "일반 교체 에너지 1 · 라운드당 1회"))
        }
        control(px+pw-endW-12,py+ph-28,endW,20,"턴 종료  ▸",data.get("forced")?.asBoolean!=true){command("end")}.primary=true
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
    override fun background(g:GuiGraphics){
        g.fill(0,0,width,height,0xFF06151B.toInt())
        g.blit(ResourceLocation.fromNamespaceAndPath("cobblespire","textures/gui/arena-v1.png"),px,py,0f,0f,pw,ph,pw,ph)
        SpireSkin.panel(g,px,py,pw,25)
        val l=layout();g.fillGradient(px,py+l.handY.toInt()-8,px+pw,py+ph,0x30102329,0xF00B1920.toInt())
    }
    private fun drawCard(g:GuiGraphics,c:JsonObject,b:CardBox,lift:Boolean=false,moving:Boolean=false){
        SpireCardArt.draw(g,c,(px+b.x).roundToInt(),(py+b.y-(if(lift)10 else 0)).roundToInt(),
            b.w.roundToInt().coerceAtLeast(20),b.h.roundToInt().coerceAtLeast(26),lift,
            moving||data.number("energy")>=c.number("cost"),key=if(moving)""else (hand().indexOf(c)+1).let{if(it==10)"0"else it.toString()})
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=System.currentTimeMillis();controls.forEach{(b,enabled)->b.active=enabled&&!busy()}
        text(g,"COBBLESPIRE",px+12,py+9,0xE7C98F)
        text(g,"${data.number("round")}턴 · "+if(data.text("phase")=="PLAYER")"내 차례"else if(data.text("phase")=="VICTORY")"승리"else data.text("phase"),px+pw/2-30,py+9,0x8DDED2)
        val l=layout()
        // Combatants are native animated Cobblemon models, not pictures baked into the arena.
        val stageY=py+if(compact)32 else (l.handY*.42).toInt()
        val modelScale=if(compact)26f else 64f
        g.enableScissor(px,py+26,px+pw,py+l.handY.toInt())
        SpireWidgets.portrait(g,active,px+(pw*.28).toInt(),stageY,modelScale)
        val list=enemies();val ew=enemyWidth()
        list.forEachIndexed{i,e->
            val modelX=px+(pw*.62).toInt()+i*(pw*.12).toInt()
            if(e.number("hp")>0)SpireWidgets.portrait(g,e,modelX,stageY-8,modelScale/(if(list.size>2)1.3f else 1f))
        }
        g.disableScissor()
        list.forEachIndexed{i,e->val x=enemyX(i)
            SpireSkin.panel(g,x,py+29,ew,43,if(target==i)SpireSkin.teal else SpireSkin.gold)
            SpireWidgets.hp(g,e,x+4,py+47,ew-8)
            text(g,short("${e.number("hp")}/${e.number("maxHp")} HP",ew-8),x+4,py+52)
            val damage=if(e.number("maxDamage")>0)" ${e.number("minDamage")}~${e.number("maxDamage")}"else ""
            text(g,short("◆ ${SpireWidgets.move(e.text("intent"))}$damage",ew-8),x+4,py+63,0xEDB8A7)
        }
        val ay=py+l.handY.toInt()-31
        val ownW=(pw-144).coerceAtMost(310)
        SpireSkin.panel(g,px+12,ay,ownW,25,SpireSkin.teal)
        text(g,short("${SpireWidgets.name(active)}  ${active.number("hp")}/${active.number("maxHp")} HP",ownW-10),px+17,ay+4)
        SpireWidgets.hp(g,active,px+17,ay+16,ownW-10)
        SpireSkin.panel(g,px+pw-112,ay,100,25,SpireSkin.teal)
        if(data.get("forced")?.asBoolean==true)text(g,"무료 교체",px+pw-96,ay+8,0xECAF93)
        else{for(i in 0..2)SpireSkin.gem(g,px+pw-92+i*25,ay+9,5,i<data.number("energy"))
            text(g,"에너지 ${data.number("energy")}/3",px+pw-100,ay+16,0xBFE7DC)}
        hover=hovered(mx.toDouble(),my.toDouble())
        if(!compact)text(g,"손패 ${hand().size}/${data.number("handLimit")} · 숫자키로 사용",px+pw/2-44,py+l.handY.toInt()-13,0xDDEDE5)
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
            g.renderTooltip(font,font.split(Component.literal(SpireWidgets.move(c.text("move"))+" · 에너지 ${c.number("cost")}\n"+desc+"\n"+(if(data.number("energy")<c.number("cost"))"에너지 부족\n"else "")+"턴 종료 시 버림 · 보존 카드는 유지"),(pw-24).coerceAtMost(280)),mx,my)
        }}
    }
}
