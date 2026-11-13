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
    override fun showError(s:String){waiting=false;SpireMusic.uiError();super.showError(s)}
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
        control(px+pw-151,py+8,78,16,"모험 종료"){command("quit")}
        button(px+pw-66,py+8,54,16,"닫기"){onClose()}
        listOf("←" to {WorldBattleView.rotate(-5f,0f)},"→" to {WorldBattleView.rotate(5f,0f)},"↑" to {WorldBattleView.rotate(0f,-4f)},"↓" to {WorldBattleView.rotate(0f,4f)},"●" to {WorldBattleView.resetAngle()}).forEachIndexed{i,(label,action)->
            button(px+12+i*24,py+31,20,16,label,click=action).tooltip=Tooltip.create(Component.literal(if(i==4)"기본 시점"else "개인 카메라 각도 조정"))
        }
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
        val ended=data.get("ended")?.asBoolean==true
        val endW=96;val pingW=44;val partyW=(pw-endW-pingW-42)/3
        party().forEachIndexed{i,c->val enabled=(!ended||data.get("forced")?.asBoolean==true)&&c.number("hp")>0&&i!=data.number("active")&&(data.get("forced")?.asBoolean==true||data.get("switchUsed")?.asBoolean!=true&&data.number("energy")>0)
            control(px+12+i*(partyW+4),py+ph-28,partyW,20,(if(i==data.number("active"))"● " else "↔ ")+short(SpireWidgets.name(c),partyW-22),enabled){SpireMusic.switchPokemon();command("switch"){addProperty("index",i)}}
                .also{it.selected=i==data.number("active")}.tooltip=Tooltip.create(Component.literal("${c.number("hp")}/${c.number("maxHp")} HP\n교체: 남은 손패 모두 버림 → 새로 5장\n"+if(data.get("forced")?.asBoolean==true)"기절 교체 무료"else "일반 교체 에너지 1 · 라운드당 1회"))
        }
        control(px+pw-endW-pingW-16,py+ph-28,pingW,20,"📌 핑",enemies().getOrNull(target)?.number("hp",0)?:0>0){sendPing(hand().getOrNull(hover))}
            .tooltip=Tooltip.create(Component.literal("선택한 적에게 넣을 수 있는 예상 피해량 핑 전송\n(손패 카드를 우클릭해도 핑 전송)"))
        val endLabel=if(ended)"아군 대기 (${data.number("endedCount")}/${data.number("alivePlayers")})"else "턴 종료  ▸"
        control(px+pw-endW-12,py+ph-28,endW,20,endLabel,!ended&&data.get("forced")?.asBoolean!=true){SpireMusic.turnEnd();command("end")}.primary=!ended
    }
    private fun sendPing(card:JsonObject?){
        val chosen=card?:hand().filter{canPlay(it)&&it.array("previews")!=null}.maxByOrNull{it.array("previews")?.get(target)?.asJsonObject?.number("maxDamage")?:0}?:hand().firstOrNull()
        val pv=chosen?.array("previews")?.takeIf{target in 0 until it.size()}?.get(target)?.asJsonObject
        SpireMusic.cardHover()
        command("ping"){
            addProperty("target",target)
            addProperty("move",chosen?.text("move").orEmpty())
            addProperty("minDamage",pv?.number("minDamage")?:0)
            addProperty("maxDamage",pv?.number("maxDamage")?:0)
        }
    }
    private fun canPlay(c:JsonObject)=!busy()&&data.text("phase")=="PLAYER"&&data.get("ended")?.asBoolean!=true&&active.number("hp")>0&&data.number("energy")>=c.number("cost")&&(c.get("trainer")?.asBoolean==true||!SpireWidgets.status(active).contains("수면"))
    private fun play(index:Int){val c=hand().getOrNull(index)?:return;if(canPlay(c)){SpireMusic.cardPlay();command("move"){addProperty("handIndex",index);addProperty("instance",c.get("instance").asLong);addProperty("target",target)}}else if(!busy())SpireMusic.uiError()}
    private fun hovered(mx:Double,my:Double):Int{
        if(busy())return -1
        val l=layout();val x=mx-px;val y=my-py
        if(hover in hand().indices){val b=l.hand(hover);if(CardBox(b.x,b.y-10,b.w,b.h+10).contains(x,y))return hover}
        return hand().indices.reversed().firstOrNull{l.hand(it).contains(x,y)}?:-1
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        val index=hovered(mx,my)
        if(button==0&&index>=0){play(index);return true}
        if(button==1&&index>=0){sendPing(hand()[index]);return true}
        return super.mouseClicked(mx,my,button)
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean{
        when(key){263->{WorldBattleView.rotate(-5f,0f);return true};262->{WorldBattleView.rotate(5f,0f);return true};265->{WorldBattleView.rotate(0f,-4f);return true};264->{WorldBattleView.rotate(0f,4f);return true};268->{WorldBattleView.resetAngle();return true}}
        val i=if(key in 49..57)key-49 else if(key==48)9 else -1
        if(i>=0){play(i);return true};return super.keyPressed(key,scan,mods)
    }
    override fun background(g:GuiGraphics){
        SpireSkin.panel(g,px,py,pw,25)
        val l=layout();g.fillGradient(px,py+l.handY.toInt()-8,px+pw,py+ph,0x30102329,0xF00B1920.toInt())
    }
    private fun drawCard(g:GuiGraphics,c:JsonObject,b:CardBox,lift:Boolean=false,moving:Boolean=false){
        val face=c.deepCopy();DamageLabels.preview(c,target)?.let{face.add("displayPreview",it)}
        SpireCardArt.draw(g,face,(px+b.x).roundToInt(),(py+b.y-(if(lift)10 else 0)).roundToInt(),
            b.w.roundToInt().coerceAtLeast(20),b.h.roundToInt().coerceAtLeast(26),lift,
            moving||data.number("energy")>=c.number("cost"),key=if(moving)""else (hand().indexOf(c)+1).let{if(it==10)"0"else it.toString()})
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=System.currentTimeMillis();controls.forEach{(b,enabled)->b.active=enabled&&!busy()}
        text(g,"COBBLESPIRE",px+12,py+9,0xE7C98F)
        val ended=data.get("ended")?.asBoolean==true
        val turnText=when{data.text("phase")=="VICTORY"->"승리";data.text("phase")!="PLAYER"->data.text("phase");ended->"아군 대기 (${data.number("endedCount")}/${data.number("alivePlayers")})";else->"내 차례"}
        text(g,"${data.number("round")}턴 · $turnText",px+pw/2-36,py+9,if(ended)0xE7C98F else 0x8DDED2)
        val l=layout();val ay=py+l.handY.toInt()-31
        val allies=data.array("allies")?.map{it.asJsonObject}.orEmpty()
        val pings=data.array("pings")?.map{it.asJsonObject}.orEmpty()
        val list=enemies();val ew=enemyWidth()
        list.forEachIndexed{i,e->val x=enemyX(i)
            val ep=pings.filter{it.number("target")==i}
            SpireSkin.panel(g,x,py+29,ew,76,if(ep.isNotEmpty())0xE6B84D else if(target==i)SpireSkin.teal else SpireSkin.gold)
            SpireWidgets.hp(g,e,x+4,py+47,ew-8)
            text(g,short("${e.number("hp")}/${e.number("maxHp")} HP",ew-8),x+4,py+52)
            val attack=e.text("intentType");val role=CardRole.parse(e.text("intentRole"))
            val ink=if(role==CardRole.ATTACK)SpireCardArt.tint(JsonObject().apply{addProperty("type",attack)})else role.color
            val badge=role.label+if(role==CardRole.ATTACK)" · "+DamageLabels.type(attack)else ""
            text(g,short("$badge · ${SpireWidgets.move(e.text("intent"))}",ew-8),x+4,py+64,ink)
            text(g,short(if(role==CardRole.ATTACK)"내게 ${DamageLabels.damage(e)} 피해 예상"else e.text("intentEffect"),ew-8),x+4,py+76,0xEAD6B5)
            text(g,short(if(role==CardRole.ATTACK)DamageLabels.matchup(DamageLabels.multiplier(e))+" · 명중 ${e.number("intentAccuracy")}%"else "대상: "+if(e.get("selfTarget")?.asBoolean==true)"자신"else "내 포켓몬",ew-8),x+4,py+88,0xBDD3D1)
            if(ep.isNotEmpty()&&ay>=py+132){
                val sMin=ep.sumOf{it.number("minDamage")};val sMax=ep.sumOf{it.number("maxDamage")}
                val lethal=sMax>=e.number("hp")&&e.number("hp")>0
                SpireSkin.panel(g,x,py+107,ew,24,if(lethal)0x4ADE80 else 0xE6B84D)
                text(g,short((if(lethal)"📌 처치각! "else "📌 합계 ")+"${sMin}~${sMax}뎀",ew-8),x+4,py+110,if(lethal)0x6EE7B7 else 0xF7D379)
                val byPlayer=ep.joinToString(" · "){pg->"${if(pg.get("isSelf")?.asBoolean==true)"나"else pg.text("playerName")} ${pg.number("minDamage")}~${pg.number("maxDamage")}"}
                text(g,short(byPlayer,ew-8),x+4,py+120,0xDCECE8)
            }
        }
        val feedW=(pw/2-30).coerceAtMost(290)
        val hits=data.array("damageEvents")?.toList()?.takeLast(2).orEmpty()
        if(hits.isNotEmpty()){
            SpireSkin.panel(g,px+12,py+51,feedW,34)
            hits.forEachIndexed{i,v->val e=v.asJsonObject
                val inc=e.get("incoming")?.asBoolean==true
                val who=if(inc)"적"else if(e.get("isSelf")?.asBoolean==true)"[나]"else "[${e.text("ownerName").ifBlank{e.text("actor")}}]"
                val result=if(e.get("miss")?.asBoolean==true){if(DamageLabels.multiplier(e)==0.0)"무효"else "빗나감"}else if(e.number("amount")>0)"-${e.number("amount")}뎀"else "효과"
                val role=CardRole.parse(e.text("role"))
                val color=if(inc)0xE58A8A else if(e.get("isSelf")?.asBoolean==true)0xF7D379 else 0x8CE8B5
                text(g,short("$who ${e.text("actor")} → ${e.text("target")} $result",feedW-10),px+17,py+56+i*13,if(role==CardRole.ATTACK)color else role.color)
            }
        }
        if(allies.isNotEmpty()&&ay>=py+118){
            val gap=4;val aw=((feedW-(allies.size-1)*gap)/allies.size).coerceAtLeast(78)
            allies.forEachIndexed{i,a->val ax=px+12+i*(aw+gap);val act=a.getAsJsonObject("active")
                val alive=a.get("alive")?.asBoolean==true;val aEnded=a.get("ended")?.asBoolean==true
                val st=when{!alive->"기절";aEnded->"완료";else->"행동"}
                SpireSkin.panel(g,ax,py+88,aw,28,if(!alive)0x7A4B4B else if(aEnded)0xE6B84D else SpireSkin.teal)
                text(g,short("${a.text("name")}($st) ${a.number("damageDealt")}뎀",aw-8),ax+4,py+91,if(aEnded)0xF7D379 else 0xDDF2EC)
                SpireWidgets.hp(g,act,ax+4,py+102,aw-8)
                text(g,short("${SpireWidgets.name(act)} ${act.number("hp")}/${act.number("maxHp")}",aw-8),ax+4,py+106,0xBDD3D1)
            }
        }
        val ownW=if(compact)(pw/2-30).coerceAtLeast(110)else (pw-144).coerceAtMost(310)
        SpireSkin.panel(g,px+12,ay,ownW,25,SpireSkin.teal)
        val selfDmg=if(data.number("partySize",1)>1||data.number("selfDamageDealt")>0)" · 누적 ${data.number("selfDamageDealt")}뎀"else ""
        text(g,short("${SpireWidgets.name(active)}  ${active.number("hp")}/${active.number("maxHp")} HP$selfDmg",ownW-10),px+17,ay+4)
        SpireWidgets.hp(g,active,px+17,ay+16,ownW-10)
        val energyX=if(compact)px+18+ownW else px+pw-112
        SpireSkin.panel(g,energyX,ay,if(compact)70 else 100,25,SpireSkin.teal)
        if(data.get("forced")?.asBoolean==true)text(g,"무료 교체",energyX+8,ay+8,0xECAF93)
        else{for(i in 0..2)SpireSkin.gem(g,energyX+14+i*(if(compact)19 else 25),ay+9,5,i<data.number("energy"))
            text(g,"에너지 ${data.number("energy")}/3",energyX+4,ay+16,0xBFE7DC)}
        val nextHover=hovered(mx.toDouble(),my.toDouble())
        if(nextHover!=hover&&nextHover>=0)SpireMusic.cardHover()
        hover=nextHover
        if(!compact)text(g,"손패 ${hand().size}/${data.number("handLimit")} · 숫자키: 카드 · 방향키: 시점",px+pw/2-44,py+l.handY.toInt()-13,0xDDEDE5)
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
            val preview=DamageLabels.preview(c,target)
            val lines=CardTooltip.lines(c,SpireWidgets.move(c.text("move")),SpireCardArt.description(c),
                if(CardSemantics.role(c)==CardRole.ATTACK)DamageLabels.type(c.text("type"))else "",preview,
                if(data.number("energy")<c.number("cost"))"에너지 부족"else "")
            g.renderTooltip(font,font.split(Component.literal(lines.joinToString("\n")),(pw-24).coerceAtMost(280)),mx,my)


        }}
    }
}
