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
    override val maximumWidth=740
    override val maximumHeight=410
    override val supportsAdventureMenu=true
    private var target=0;private var hover=-1;private var waiting=false;private var draggingCamera=false
    private var topLeftExpanded=false
    private var topLeftToggleBox=intArrayOf(0,0,0,0)
    private var hoveredEnemy=-1
    private var hoveredPile:String?=null
    private val pileBoxes=mutableMapOf<String,IntArray>()
    private val animation=HandAnimation()
    private val controls=mutableListOf<Pair<Button,Boolean>>()
    private var oldCount=data.getAsJsonArray("hand").size()
    private val cardHover=FloatArray(12)
    private var lastFrameMs=SpireAnim.now()
    private var lastPingCount=data.array("pings")?.size()?:0
    private var pingFlashAt=0L
    private var lastEventCount=data.array("damageEvents")?.size()?:0
    private var feedFlashAt=0L
    private var lastEventSeq=data.array("damageEvents")?.lastOrNull()?.asJsonObject?.number("seq")?:0
    private var superEffHitAt=0L
    init{animation.update(null,data,data.get("fresh")?.asBoolean==true)}
    private class DeckPileButton(
        val kind:String,val title:String,x:Int,y:Int,w:Int,h:Int,
        val count:Int,val topCard:JsonObject?,onPress:OnPress
    ):Button(x,y,w,h,Component.literal(title),onPress,DEFAULT_NARRATION){
        private var hoverAnim=0f
        private var lastMs=SpireAnim.now()
        override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float){
            val now=SpireAnim.now();val dt=((now-lastMs).coerceIn(0L,80L))/1000f;lastMs=now
            val hot=isHoveredOrFocused&&active
            hoverAnim=SpireAnim.approach(hoverAnim,if(hot)1f else 0f,dt,16f)
            val x0=x;val y0=y-(hoverAnim*2f).roundToInt();val bw=width;val bh=height
            val layers=when{count>=6->2;count>=2->1;else->0}
            val (backRim,topCol,botCol,rimCol,inkCol)=when(kind){
                "draw"->listOf(0xFF3E706A.toInt(),0xFF1A434D.toInt(),0xFF0D2027.toInt(),SpireAnim.lerpColor(SpireSkin.gold,SpireSkin.teal,hoverAnim),0xFFF7EED6.toInt())
                "discard"->listOf(0xFF4F4232.toInt(),0xFF332920.toInt(),0xFF191411.toInt(),SpireAnim.lerpColor(0xFF9C8156.toInt(),SpireSkin.gold,hoverAnim),0xFFE4D5BA.toInt())
                else->listOf(0xFF28222E.toInt(),0xFF14101A.toInt(),0xFF08060B.toInt(),SpireAnim.lerpColor(0xFF4B3F57.toInt(),0xFF8C72A6.toInt(),hoverAnim),0xFF9A8BA8.toInt())
            }
            if(count==0){
                g.fill(x0,y0,x0+bw,y0+bh,if(kind=="exhaust")0xB009070C.toInt() else 0xB0111216.toInt())
                val emptyRim=if(hot)rimCol else if(kind=="exhaust")0xFF342C3D.toInt() else 0xFF443D33.toInt()
                g.fill(x0,y0,x0+bw,y0+1,emptyRim);g.fill(x0,y0+bh-1,x0+bw,y0+bh,emptyRim)
                g.fill(x0,y0,x0+1,y0+bh,emptyRim);g.fill(x0+bw-1,y0,x0+bw,y0+bh,emptyRim)
            }else{
                for(s in layers downTo 1){
                    val ox=-s*2;val oy=-s*2
                    g.fill(x0+ox,y0+oy,x0+bw+ox,y0+bh+oy,0xFF0A0A0D.toInt())
                    g.fill(x0+ox+1,y0+oy+1,x0+bw+ox-1,y0+bh+oy-1,backRim)
                }
                g.fill(x0+2,y0+3,x0+bw+2,y0+bh+3,0x70000000)
                SpireSkin.panel(g,x0,y0,bw,bh,rimCol)
                g.fillGradient(x0+2,y0+2,x0+bw-2,y0+bh-2,topCol,botCol)
                if(kind=="draw"&&bw>=28&&bh>=34){
                    val cx=x0+bw/2;val cy=y0+bh/2-2;val r=minOf(bw,bh)/4
                    g.fill(cx-r,cy-r/2,cx+r,cy,0x88E05A47.toInt())
                    g.fill(cx-r,cy,cx+r,cy+r/2,0x88DCE6E8.toInt())
                    g.fill(cx-r,cy-1,cx+r,cy+1,0xFF0A0A0C.toInt())
                    g.fill(cx-2,cy-2,cx+2,cy+2,SpireSkin.gold)
                }else if(kind=="discard"&&topCard!=null){
                    val tc=CardSemantics.role(topCard).color
                    g.fill(x0+3,y0+3,x0+bw-3,y0+6,tc)
                }else if(kind=="exhaust"){
                    val cx=x0+bw/2;val cy=y0+bh/2-1
                    g.fill(cx-3,cy-1,cx+3,cy+1,0x5575628A)
                    g.fill(cx-1,cy-3,cx+1,cy+3,0x5575628A)
                }
            }
            val f=net.minecraft.client.Minecraft.getInstance().font
            val cntStr="$count"
            g.drawCenteredString(f,cntStr,x0+bw/2,y0+bh/2-(if(bh>=30)3 else 5),if(count==0)0xFF6B6560.toInt() else inkCol)
            if(bh>=20){
                val lbl=f.plainSubstrByWidth(title,(bw-4).coerceAtLeast(10))
                g.pose().pushPose();g.pose().translate((x0+bw/2).toDouble(),(y0+bh-9).toDouble(),2.0);g.pose().scale(0.68f,0.68f,1f)
                g.drawCenteredString(f,lbl,0,0,if(count==0)0xFF5D5852.toInt() else inkCol);g.pose().popPose()
            }
        }
    }
    private fun enemies()=data.getAsJsonArray("enemies").map{it.asJsonObject}
    private fun party()=data.getAsJsonArray("party").map{it.asJsonObject}
    private fun hand()=data.getAsJsonArray("hand").map{it.asJsonObject}
    private val compact get()=ph<270
    private val active get()=party()[data.number("active")]
    private fun layout()=CardBoardLayout(pw,ph,hand().size)
    private fun enemyWidth()=minOf(if(compact)116 else 148,(pw/2-18-(enemies().size-1)*4)/enemies().size)
    private fun enemyX(i:Int)=px+pw-8-enemies().size*(enemyWidth()+4)+i*(enemyWidth()+4)

    private fun busy()=waiting||animation.busy()
    override fun showError(s:String){waiting=false;SpireMusic.uiError();super.showError(s)}
    override fun accept(value:JsonObject){
        if(value.text("session")==data.text("session")&&(value.number("battleNumber")<data.number("battleNumber")||value.number("battleNumber")==data.number("battleNumber")&&value.number("revision")<data.number("revision")))return
        val now=SpireAnim.now()
        val pCnt=value.array("pings")?.size()?:0
        if(pCnt>lastPingCount)pingFlashAt=now
        lastPingCount=pCnt
        val eCnt=value.array("damageEvents")?.size()?:0
        if(eCnt>lastEventCount)feedFlashAt=now
        lastEventCount=eCnt
        value.array("damageEvents")?.forEach{v->
            val ev=v.asJsonObject;val seq=ev.number("seq")
            if(seq>lastEventSeq){
                lastEventSeq=seq
                if(ev.get("incoming")?.asBoolean==true&&ev.get("miss")?.asBoolean!=true&&DamageLabels.multiplier(ev)>=2.0){
                    superEffHitAt=now
                }
            }
        }
        oldCount=hand().size;animation.update(data,value,true);waiting=false;hover=-1;cardHover.fill(0f);data=value
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
        pileBoxes.clear()
        val list=enemies();val ew=enemyWidth()
        list.forEachIndexed{i,e->val bossTag=if(e.get("boss")?.asBoolean==true)"★" else ""
            control(enemyX(i),py+22,ew,12,(if(target==i)"◆ " else "")+bossTag+short("Lv.${e.number("level",1)} ${SpireWidgets.name(e)}",ew-16-(if(bossTag.isEmpty())0 else 8)),e.number("hp")>0){target=i;rebuildWidgets()}
            .also{it.selected=target==i}
        }
        addAdventureMenuButton(px+pw-48,py+3,40,14).also{controls.add(it to true)}
        val l=layout()
        val pilesObj=data.getAsJsonObject("piles")
        for((kind,title,box) in listOf(Triple("draw","뽑기",l.draw),Triple("discard","버림",l.discard),Triple("exhaust","소멸",l.exhaust))){
            val arr=pilesObj.getAsJsonArray(kind)
            val n=arr.size()
            val topCard=if(n>0&&kind!="draw")arr.last().asJsonObject else null
            val bx=px+box.x.toInt();val by=py+box.y.toInt();val bw=box.w.toInt();val bh=box.h.toInt()
            pileBoxes[kind]=intArrayOf(bx,by,bw,bh)
            val pb=addRenderableWidget(DeckPileButton(kind,title,bx,by,bw,bh,n,topCard){minecraft?.setScreen(PileScreen(this,data,kind))})
            controls.add(pb to true)
        }
        val phase=data.text("phase")
        if(phase in setOf("VICTORY","DEFEAT")){
            if(phase=="VICTORY")control(px+8,py+ph-20,(pw-20)/2,16,"다음 시험 전투"){command("next")}
            control(px+pw/2+2,py+ph-20,(pw-20)/2-2,16,"시험 모험 종료"){command("quit")};return
        }
        val ended=data.get("ended")?.asBoolean==true
        val endW=74;val pingW=36;val partyW=(pw-endW-pingW-28)/3
        party().forEachIndexed{i,c->val enabled=(!ended||data.get("forced")?.asBoolean==true)&&c.number("hp")>0&&i!=data.number("active")&&(data.get("forced")?.asBoolean==true||data.get("switchUsed")?.asBoolean!=true&&data.number("energy")>0)
            val hp=c.number("hp");val maxHp=c.number("maxHp",1).coerceAtLeast(1)
            val prefix=if(hp<=0)"✕ " else if(i==data.number("active"))"★ " else "↔ "
            control(px+8+i*(partyW+3),py+ph-20,partyW,16,short("${prefix}Lv.${c.number("level",1)} ${SpireWidgets.name(c)} $hp/$maxHp",partyW-10),enabled){SpireMusic.switchPokemon();command("switch"){addProperty("index",i)}}
                .also{it.selected=i==data.number("active");it.hpRatio=hp.toFloat()/maxHp}.tooltip=Tooltip.create(Component.literal("$hp/$maxHp HP\n교체: 남은 손패 모두 버림 → 새로 5장\n"+if(data.get("forced")?.asBoolean==true)"기절 교체 무료"else "일반 교체 에너지 1 · 라운드당 1회"))
        }
        control(px+pw-endW-pingW-11,py+ph-20,pingW,16,"◎ 핑",enemies().getOrNull(target)?.number("hp",0)?:0>0){sendPing(hand().getOrNull(hover))}
            .tooltip=Tooltip.create(Component.literal("선택한 적에게 넣을 수 있는 예상 피해량 핑 전송\n(손패 카드를 우클릭해도 핑 전송)"))
        val endLabel=if(ended)"대기 (${data.number("endedCount")}/${data.number("alivePlayers")})"else "턴 종료 ▸"
        control(px+pw-endW-8,py+ph-20,endW,16,endLabel,!ended&&data.get("forced")?.asBoolean!=true){SpireMusic.turnEnd();command("end")}.primary=!ended
        val items=data.array("items")?.map{it.asJsonObject}.orEmpty()
        if(items.isNotEmpty()){
            val ay=py+l.handY.toInt()-24
            val ownW=if(compact)(pw/2-44).coerceAtLeast(108)else (pw-180).coerceAtMost(210)
            val maxE=data.number("maxEnergy",3).coerceIn(3,5)
            val energyW=if(compact)(60+maxE*6)else (76+maxE*8)
            var ix=px+16+ownW+energyW
            val canItem=data.text("phase")=="PLAYER"&&!ended&&data.get("forced")?.asBoolean!=true
            items.forEachIndexed{idx,it->
                val iw=if(compact)44 else 54
                if(ix+iw<=px+pw-8){
                    control(ix,ay+1,iw,17,short(it.text("label"),iw-6),canItem){command("use_item"){addProperty("index",idx)}}
                        .tooltip=Tooltip.create(Component.literal("${it.text("label")}\n${it.text("desc")}"))
                    ix+=iw+3
                }
            }
        }
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
        if(adventureMenuOpen)return super.mouseClicked(mx,my,button)
        if(button==0&&mx>=topLeftToggleBox[0]&&mx<topLeftToggleBox[0]+topLeftToggleBox[2]&&my>=topLeftToggleBox[1]&&my<topLeftToggleBox[1]+topLeftToggleBox[3]){
            topLeftExpanded=!topLeftExpanded;SpireMusic.cardHover();return true
        }
        val index=hovered(mx,my)
        if(button==0&&index>=0){play(index);return true}
        if(button==1&&index>=0){sendPing(hand()[index]);return true}
        if(super.mouseClicked(mx,my,button))return true
        if(button in 0..2){draggingCamera=true;return true}
        return false
    }
    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean{
        if(adventureMenuOpen)return true
        if(draggingCamera){WorldBattleView.rotate((dx*0.45f).toFloat(),(dy*0.35f).toFloat());return true}
        return super.mouseDragged(mx,my,button,dx,dy)
    }
    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean{
        if(draggingCamera){draggingCamera=false;return true}
        return super.mouseReleased(mx,my,button)
    }
    override fun mouseScrolled(mx:Double,my:Double,scrollX:Double,scrollY:Double):Boolean{
        if(adventureMenuOpen)return true
        WorldBattleView.zoom((scrollY*0.8f).toFloat());return true
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean{
        if(adventureMenuOpen)return super.keyPressed(key,scan,mods)
        when(key){263->{WorldBattleView.rotate(-5f,0f);return true};262->{WorldBattleView.rotate(5f,0f);return true};265->{WorldBattleView.rotate(0f,-4f);return true};264->{WorldBattleView.rotate(0f,4f);return true};268,82->{WorldBattleView.resetAngle();return true};84->{minecraft?.setScreen(net.minecraft.client.gui.screens.ChatScreen(""));return true}}
        val i=if(key in 49..57)key-49 else if(key==48)9 else -1
        if(i>=0){play(i);return true};return super.keyPressed(key,scan,mods)
    }
    override fun background(g:GuiGraphics){
        SpireSkin.banner(g,px,py,pw,20)
        val l=layout();g.fillGradient(px,py+l.handY.toInt()-4,px+pw,py+ph,0x18120F0E,0xCC09090B.toInt())
        g.fill(px,py+l.handY.toInt()-4,px+pw,py+l.handY.toInt()-3,0x44C7A46A)
    }
    private fun drawCard(g:GuiGraphics,c:JsonObject,b:CardBox,liftFactor:Float=0f,moving:Boolean=false){
        val face=c.deepCopy();DamageLabels.preview(c,target)?.let{face.add("displayPreview",it)}
        val t=SpireAnim.easeOutCubic(liftFactor.coerceIn(0f,1f))
        val scale=1.0+1.2*t
        val w=(b.w*scale).roundToInt().coerceAtLeast(20)
        val h=(b.h*scale).roundToInt().coerceAtLeast(26)
        val x=(px+b.cx-w/2.0).roundToInt().coerceIn(px+4,px+pw-w-4)
        val y=(py+b.y-(if(t>0.01f)(h-b.h)+8.0*t else 0.0)).roundToInt().coerceAtLeast(py+22)
        SpireCardArt.draw(g,face,x,y,w,h,t>0.25f,
            moving||data.number("energy")>=c.number("cost"),key=if(moving)""else (hand().indexOf(c)+1).let{if(it==10)"0"else it.toString()})
    }
    private fun formatCombatEvent(e:JsonObject):Pair<String,Int>{
        val kind=e.text("eventKind")
        val inc=e.get("incoming")?.asBoolean==true
        val actorKo=SpireWidgets.speciesName(e.text("actorSpecies"),e.text("actor"))
        val targetKo=SpireWidgets.speciesName(e.text("targetSpecies"),e.text("target"))
        val who=if(inc)"적"else if(e.get("isSelf")?.asBoolean==true)"[나]"else "[${e.text("ownerName").ifBlank{actorKo}}]"
        val stMap=mapOf("burn" to "화상","poison" to "독","toxic" to "맹독","paralysis" to "마비","sleep" to "수면","confusion" to "혼란","flinch" to "풀죽음")
        val stName=stMap[e.text("move")]?:e.text("move")
        return when(kind){
            "STATUS_DAMAGE"->{
                val col=if(e.text("move") in setOf("poison","toxic"))0xD19ADB else if(e.text("move")=="confusion")0xF078C8 else 0xF39C6B
                "$targetKo $stName -${e.number("amount")}뎀" to col
            }
            "STATUS_APPLIED"->"$who $actorKo → $targetKo $stName!" to 0xF7D379
            "MOVE_PARALYZED"->"$actorKo 마비로 행동 불가!" to 0xFFE066
            "MOVE_SLEEP"->"$actorKo 잠들어 행동 불가" to 0x8AA8FF
            "MOVE_FLINCHED"->"$actorKo 풀죽어 행동 불가" to 0xE6A23C
            "MOVE_CONFUSED"->"$actorKo 혼란!" to 0xF078C8
            else->{
                val result=if(e.get("miss")?.asBoolean==true){if(DamageLabels.multiplier(e)==0.0)"무효"else "빗나감"}else if(e.number("amount")>0)"-${e.number("amount")}뎀"else "효과"
                val role=CardRole.parse(e.text("role"))
                val color=if(inc)0xE58A8A else if(e.get("isSelf")?.asBoolean==true)0xF7D379 else 0x8CE8B5
                "$who $actorKo → $targetKo $result" to (if(role==CardRole.ATTACK)color else role.color)
            }
        }
    }
    private fun renderExpandedEnemy(g:GuiGraphics,i:Int,e:JsonObject,ep:List<JsonObject>,pulse:Float){
        val ew=maxOf(enemyWidth(),166)
        val x=enemyX(i).coerceIn(px+6,px+pw-ew-6)
        val y=py+20
        val hidden=e.text("intentHidden")
        val attack=e.text("intentType");val role=CardRole.parse(e.text("intentRole"))
        val mul=DamageLabels.multiplier(e)
        val isWeak2x=e.number("hp")>0&&hidden!="DEBUFF"&&role==CardRole.ATTACK&&mul>=2.0&&mul<4.0
        val isWeak4x=e.number("hp")>0&&hidden!="DEBUFF"&&role==CardRole.ATTACK&&mul>=4.0
        val condLines=SpireWidgets.detailedConditions(e)
        val types=e.array("types")?.joinToString(" / "){DamageLabels.type(it.asString)}.orEmpty()
        val ability=Component.translatable("cobblemon.ability.${e.text("ability")}").string
        val moveDesc=if(hidden.isBlank())Moves.getByName(e.text("intent"))?.description?.string.orEmpty() else ""
        val descLines=if(moveDesc.isNotBlank())font.split(Component.literal(moveDesc),ew-10).take(2)else emptyList()
        var h=72+condLines.size*10+descLines.size*10+(if(ep.isNotEmpty())12 else 0)
        h=h.coerceAtMost(ph-36)
        val breath=0.60f+0.40f*pulse
        g.pose().pushPose();g.pose().translate(0.0,0.0,320.0)
        if(isWeak4x)SpireWidgets.drawSoftGlow(g,x,y,ew,h,0xFFEF3B3B.toInt(),4,0.28f*breath,0.08f*breath)
        else if(isWeak2x)SpireWidgets.drawSoftGlow(g,x,y,ew,h,0xFFF2D04B.toInt(),2,0.22f*breath,0f)
        val borderCol=when{
            isWeak4x->SpireAnim.lerpColor(0xFFC93A3A.toInt(),0xFFFF7A7A.toInt(),breath)
            isWeak2x->SpireAnim.lerpColor(0xFFC9A634.toInt(),0xFFFFEB78.toInt(),breath)
            target==i->SpireSkin.teal
            else->SpireSkin.gold
        }
        SpireSkin.panel(g,x,y,ew,h,borderCol)
        val bossTag=if(e.get("boss")?.asBoolean==true)"★" else ""
        text(g,short("$bossTag Lv.${e.number("level",1)} ${SpireWidgets.name(e)} ($types)",ew-8),x+4,y+4,0xFFE7C98F.toInt())
        SpireWidgets.hp(g,e,x+4,y+15,ew-8)
        val gPct=e.number("gaugePct",0).coerceIn(0,100)
        val gw=((ew-8)*gPct/100f).roundToInt()
        g.fill(x+4,y+21,x+ew-4,y+23,0xFF1A2028.toInt())
        if(gw>0)g.fill(x+4,y+21,x+4+gw,y+23,if(gPct>=85)0xFFF7D379.toInt() else 0xFF4CC9F0.toInt())
        val spe=e.number("effectiveSpe",e.getAsJsonObject("stats")?.number("spe",0)?:0)
        text(g,short("${e.number("hp")}/${e.number("maxHp")} HP · 게이지 ${gPct}% · 속도 $spe",ew-8),x+4,y+26,0xD7E6D8)
        text(g,short("특성: $ability",ew-8),x+4,y+36,0xA9DFCF)
        var cy=y+47
        val ink=if(role==CardRole.ATTACK)SpireCardArt.tint(JsonObject().apply{addProperty("type",attack)})else role.color
        when{
            e.number("hp")<=0->text(g,"기절 상태",x+4,cy,0x8A847C)
            hidden=="STRONG"->{
                text(g,short(if(isWeak4x)"⚠ 4배 치명 강공 준비!" else if(isWeak2x)"⚠ 2배 약점 강공 준비!" else "⚠ 강한 공격 준비!",ew-8),x+4,cy,if(isWeak4x)0xFF6B6B else if(isWeak2x)0xFFE866 else 0xFF8C8C)
                text(g,short("기술·피해량 비공개",ew-8),x+4,cy+10,0xBDD3D1)
            }
            hidden=="DEBUFF"->{
                text(g,"? 알 수 없는 의도 (시야 차단)",x+4,cy,0xC792EA)
                text(g,"디버프·안개로 행동 비공개",x+4,cy+10,0xBDD3D1)
            }
            else->{
                val badge=role.label+if(role==CardRole.ATTACK)" · "+DamageLabels.type(attack)else ""
                text(g,short("$badge · ${SpireWidgets.move(e.text("intent"))}",ew-8),x+4,cy,ink)
                val hitTag=DamageLabels.hitsText(e).let{if(it.isNotBlank())" · $it" else ""}
                val line2=if(role==CardRole.ATTACK)"예상 피해 ${DamageLabels.damage(e)} (${DamageLabels.matchup(mul)}$hitTag) · 명중 ${e.number("intentAccuracy")}%" else "${e.text("intentEffect")} · 대상: ${if(e.get("selfTarget")?.asBoolean==true)"자신"else "내 포켓몬"}"
                text(g,short(line2,ew-8),x+4,cy+10,if(isWeak4x)0xFF8888 else if(isWeak2x)0xFFF080 else 0xEAD6B5)
            }
        }
        cy+=22
        descLines.forEach{seq->g.drawString(font,seq,x+4,cy,0x9FC5CB);cy+=10}
        condLines.forEach{cl->text(g,short(cl,ew-8),x+4,cy,0xF7D379);cy+=10}
        if(ep.isNotEmpty()){
            val sMin=ep.sumOf{it.number("minDamage")};val sMax=ep.sumOf{it.number("maxDamage")}
            text(g,short("◎ 팀원 합계 핑: ${sMin}~${sMax}뎀",ew-8),x+4,cy,0x6EE7B7)
        }
        g.pose().popPose()
    }
    private fun renderPilePreview(g:GuiGraphics,kind:String){
        val box=pileBoxes[kind]?:return
        val arr=data.getAsJsonObject("piles").getAsJsonArray(kind).map{it.asJsonObject}
        val title=when(kind){"draw"->"뽑기 덱 · ${arr.size}장 (순서 비공개)";"discard"->"버림 덱 · ${arr.size}장 (최근 순)";else->"소멸 덱 · ${arr.size}장 (재뽑기 불가)"}
        val rows:List<Triple<String,Int,Int>> = if(arr.isEmpty()){
            emptyList()
        }else if(kind=="draw"){
            arr.groupBy{it.text("move")+"|"+(it.get("upgraded")?.asBoolean==true)+"|"+it.number("cost")+"|"+CardSemantics.role(it).name}
                .values.map{grp->
                    val c=grp.first()
                    val up=if(c.get("upgraded")?.asBoolean==true)"+" else ""
                    val cnt=if(grp.size>1)" ×${grp.size}" else ""
                    Triple("${SpireWidgets.move(c.text("move"))}$up$cnt",c.number("cost"),CardSemantics.role(c).color)
                }.sortedBy{it.first}.take(8)
        }else{
            arr.reversed().take(8).map{c->
                val up=if(c.get("upgraded")?.asBoolean==true)"+" else ""
                Triple("${SpireWidgets.move(c.text("move"))}$up",c.number("cost"),CardSemantics.role(c).color)
            }
        }
        val rowCount=rows.size.coerceAtLeast(1)
        val pwBox=158
        val phBox=28+rowCount*11
        val rx=(box[0]+box[2]/2-pwBox/2).coerceIn(px+4,px+pw-pwBox-4)
        val ry=(box[1]-phBox-6).coerceAtLeast(py+20)
        g.pose().pushPose();g.pose().translate(0.0,0.0,350.0)
        SpireSkin.panel(g,rx,ry,pwBox,phBox,if(kind=="exhaust")0xFF6B587A.toInt() else SpireSkin.gold)
        text(g,short(title,pwBox-10),rx+5,ry+4,if(kind=="exhaust")0xC5B4D4 else 0xE7C98F)
        if(rows.isEmpty()){
            text(g,"카드가 없습니다.",rx+5,ry+17,0x8A847C)
        }else{
            rows.forEachIndexed{idx,(name,cost,col)->
                val rowY=ry+16+idx*11
                g.fill(rx+5,rowY+1,rx+7,rowY+8,col)
                text(g,"[$cost]",rx+10,rowY,0x8DDED2)
                text(g,short(name,pwBox-34),rx+26,rowY,0xEAE4D3)
            }
        }
        text(g,"클릭하여 전체 보기",rx+5,ry+phBox-11,0x9FC5CB)
        g.pose().popPose()
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=SpireAnim.now()
        val dt=((now-lastFrameMs).coerceIn(0L,80L))/1000f
        lastFrameMs=now
        controls.forEach{(b,enabled)->b.active=enabled&&!busy()&&!adventureMenuOpen}
        text(g,"COBBLESPIRE",px+8,py+6,0xE7C98F)
        val evLabel=data.text("battleEventLabel")
        val ended=data.get("ended")?.asBoolean==true
        val turnText=when{data.text("phase")=="VICTORY"->"승리";data.text("phase")!="PLAYER"->data.text("phase");ended->"대기 (${data.number("endedCount")}/${data.number("alivePlayers")})${SpireAnim.loadingDots()}";else->"내 차례"}
        text(g,"${data.number("round")}턴 · $turnText",px+pw/2-30,py+6,if(ended)0xE7C98F else 0x8DDED2)
        val l=layout();val ay=py+l.handY.toInt()-24
        val allies=data.array("allies")?.map{it.asJsonObject}.orEmpty()
        val pings=data.array("pings")?.map{it.asJsonObject}.orEmpty()
        val list=enemies();val ew=enemyWidth()
        val pulse=SpireAnim.pulse(1400L)
        val gentlePulse=SpireAnim.pulse(1600L)
        val fastPulse=SpireAnim.pulse(650L)
        val pingFlash=if(now-pingFlashAt<450L)1f-(now-pingFlashAt)/450f else 0f
        val hitFlash=if(now-superEffHitAt in 0L..500L)(1f-(now-superEffHitAt)/500f)else 0f
        if(hitFlash>0.01f){
            val edge=SpireAnim.withAlpha(0xFF2B2B,hitFlash*0.36f)
            g.fill(px,py,px+pw,py+6,edge);g.fill(px,py+ph-6,px+pw,py+ph,edge)
            g.fill(px,py+6,px+6,py+ph-6,edge);g.fill(px+pw-6,py+6,px+pw,py+ph-6,edge)
        }
        var anyWeakThreat=false
        hoveredEnemy=-1
        list.forEachIndexed{i,e->val x=enemyX(i)
            val ep=pings.filter{it.number("target")==i}
            val hasIcons=SpireWidgets.hasIcons(e)
            val hidden=e.text("intentHidden")
            val attack=e.text("intentType");val role=CardRole.parse(e.text("intentRole"))
            val mul=DamageLabels.multiplier(e)
            val activeThreat=e.number("hp")>0&&hidden!="DEBUFF"&&role==CardRole.ATTACK
            val isWeak2x=activeThreat&&mul>=2.0&&mul<4.0
            val isWeak4x=activeThreat&&mul>=4.0
            if(isWeak2x||isWeak4x)anyWeakThreat=true
            val eh=if(hasIcons)50 else 38
            val breath=0.60f+0.40f*gentlePulse
            if(isWeak4x)SpireWidgets.drawSoftGlow(g,x,py+20,ew,eh,0xFFEF3B3B.toInt(),4,0.26f*breath,0.08f*breath)
            else if(isWeak2x)SpireWidgets.drawSoftGlow(g,x,py+20,ew,eh,0xFFF2D04B.toInt(),2,0.20f*breath,0f)
            val borderCol=when{
                isWeak4x->SpireAnim.lerpColor(0xFFC93A3A.toInt(),0xFFFF7A7A.toInt(),breath)
                isWeak2x->SpireAnim.lerpColor(0xFFC9A634.toInt(),0xFFFFEB78.toInt(),breath)
                ep.isNotEmpty()->SpireAnim.lerpColor(0xFFE6B84D.toInt(),0xFFFFF2A0.toInt(),maxOf(pingFlash,pulse*0.4f))
                target==i->SpireAnim.lerpColor(SpireSkin.teal,0xFFA8FFF1.toInt(),pulse*0.65f)
                else->SpireSkin.gold
            }
            SpireSkin.panel(g,x,py+20,ew,eh,borderCol)
            SpireWidgets.hp(g,e,x+4,py+35,ew-8)
            val gPct=e.number("gaugePct",0).coerceIn(0,100)
            val gw=((ew-8)*gPct/100f).roundToInt()
            g.fill(x+4,py+41,x+ew-4,py+43,0xFF1A2028.toInt())
            if(gw>0)g.fill(x+4,py+41,x+4+gw,py+43,if(gPct>=85)0xFFF7D379.toInt() else 0xFF4CC9F0.toInt())
            val hpTxt="${e.number("hp")}/${e.number("maxHp")}"
            val hpW=font.width(hpTxt)
            val dmgAreaW=(ew-hpW-12).coerceAtLeast(36)
            val (intentSummary,intentCol)=when{
                e.number("hp")<=0->"기절" to 0x8A847C
                hidden=="STRONG"->(if(isWeak4x)"⚠ 강공(×4)" else if(isWeak2x)"⚠ 강공(×2)" else "⚠ 강공 준비") to (if(isWeak4x)0xFF7B7B else if(isWeak2x)0xFFE866 else 0xFFA5A5)
                hidden=="DEBUFF"->"? 비공개" to 0xC792EA
                role==CardRole.ATTACK->{
                    val tag=if(isWeak4x)" ×4" else if(isWeak2x)" ×2" else ""
                    "⚔ ${DamageLabels.damage(e)}$tag" to (if(isWeak4x)0xFF7B7B else if(isWeak2x)0xFFE866 else 0xEAD6B5)
                }
                else->"✦ ${e.text("intentEffect")}" to role.color
            }
            text(g,short(intentSummary,dmgAreaW),x+4,py+45,intentCol)
            text(g,hpTxt,x+ew-4-hpW,py+45,0xC9D5D3)
            if(hasIcons)SpireWidgets.drawStatusStatIcons(g,e,x+4,py+56,ew-8,mx,my)
            if(!adventureMenuOpen&&mx in x until (x+ew)&&my in (py+20) until (py+20+eh))hoveredEnemy=i
            val pingY=py+22+eh
            if(ep.isNotEmpty()&&ay>=pingY+24){
                val sMin=ep.sumOf{it.number("minDamage")};val sMax=ep.sumOf{it.number("maxDamage")}
                val lethal=sMax>=e.number("hp")&&e.number("hp")>0
                val pCol=if(lethal)0xFF4ADE80.toInt() else SpireAnim.lerpColor(0xFFE6B84D.toInt(),0xFFFFF2A0.toInt(),pingFlash)
                SpireSkin.panel(g,x,pingY,ew,22,pCol)
                if(pingFlash>0f)g.fill(x+2,pingY+2,x+ew-2,pingY+20,SpireAnim.withAlpha(0xFFE799,pingFlash*0.24f))
                text(g,short((if(lethal)"◎ 처치각! "else "◎ 합계 ")+"${sMin}~${sMax}뎀",ew-8),x+4,pingY+2,if(lethal)0x6EE7B7 else 0xF7D379)
                val byPlayer=ep.joinToString(" · "){pg->"${if(pg.get("isSelf")?.asBoolean==true)"나"else pg.text("playerName")} ${pg.number("minDamage")}~${pg.number("maxDamage")}"}
                text(g,short(byPlayer,ew-8),x+4,pingY+11,0xDCECE8)
            }
        }
        val feedW=(pw/2-30).coerceAtMost(290)
        val allHits=data.array("damageEvents")?.toList().orEmpty()
        val timeline=data.array("timeline")?.map{it.asJsonObject}.orEmpty()
        val feedAge=now-feedFlashAt
        if(!topLeftExpanded){
            val bx=px+10;val by=py+23;val bh=17
            val hotToggle=!adventureMenuOpen&&mx in bx until (bx+feedW)&&my in by until (by+bh)
            val flashCol=if(feedAge in 0L..260L)SpireAnim.lerpColor(SpireSkin.gold,0xFFFFF2A0.toInt(),1f-feedAge/260f) else if(hotToggle)SpireSkin.teal else SpireSkin.gold
            SpireSkin.panel(g,bx,by,feedW,bh,flashCol)
            topLeftToggleBox=intArrayOf(bx,by,feedW,bh)
            val toggleTag="▾ 펼치기"
            val tagW=font.width(toggleTag)+6
            val (lineStr,lineCol)=when{
                allHits.isNotEmpty()->formatCombatEvent(allHits.last().asJsonObject)
                evLabel.isNotBlank()->"⚡ $evLabel · ${data.text("battleEventDesc")}" to 0xF3B562
                timeline.isNotEmpty()->{
                    val firstT=timeline.first()
                    val fn=if(firstT.get("isSelf")?.asBoolean==true)"나" else SpireWidgets.speciesName(firstT.text("species"),firstT.text("name"))
                    "⏱ 다음 행동: $fn" to 0xDCECE8
                }
                else->"전투 진행 중 · ${data.number("round")}턴" to 0xBDD3D1
            }
            val slideX=if(allHits.isNotEmpty()&&feedAge in 0L..260L)((1f-SpireAnim.easeOutCubic(feedAge/260f))*8f).roundToInt() else 0
            text(g,short(lineStr,feedW-tagW-10-slideX),bx+5+slideX,by+4,lineCol)
            text(g,toggleTag,bx+feedW-tagW,by+4,if(hotToggle)0xA8FFF1 else 0x8AA3A0)
        }else{
            val bx=px+10;val by=py+23
            val shownHits=allHits.takeLast(5)
            val hasEv=evLabel.isNotBlank()
            val hBox=18+(if(hasEv)12 else 0)+(shownHits.size.coerceAtLeast(1)*12)+6
            SpireSkin.panel(g,bx,by,feedW,hBox,SpireSkin.teal)
            topLeftToggleBox=intArrayOf(bx,by,feedW,16)
            val header=if(allies.isNotEmpty())"전투 현황·기록 (${allies.size+1}인 협동)" else "전투 현황·기록"
            text(g,header,bx+6,by+4,0x8DDED2)
            val closeTag="▴ 접기"
            text(g,closeTag,bx+feedW-font.width(closeTag)-6,by+4,0xFFE7C98F.toInt())
            var cy=by+17
            if(hasEv){
                text(g,short("⚡ $evLabel · ${data.text("battleEventDesc")}",feedW-12),bx+6,cy,0xF3B562);cy+=12
            }
            if(shownHits.isEmpty()){
                text(g,"아직 전투 기록이 없습니다.",bx+6,cy,0x8A847C);cy+=12
            }else{
                shownHits.forEachIndexed{i,v->
                    val isNewest=(i==shownHits.lastIndex&&feedAge in 0L..260L)
                    val slideX=if(isNewest)((1f-SpireAnim.easeOutCubic(feedAge/260f))*10f).roundToInt() else 0
                    if(isNewest)g.fill(bx+3,cy-1,bx+feedW-3,cy+10,SpireAnim.withAlpha(0xFFE08A,(1f-feedAge/260f)*0.18f))
                    val (line,col)=formatCombatEvent(v.asJsonObject)
                    text(g,short(line,feedW-12-slideX),bx+6+slideX,cy,col);cy+=12
                }
            }
            var nextY=by+hBox+3
            if(allies.isNotEmpty()&&ay>=nextY+30){
                val gap=4;val aw=((feedW-(allies.size-1)*gap)/allies.size).coerceAtLeast(78)
                allies.forEachIndexed{i,a->val ax=bx+i*(aw+gap);val act=a.getAsJsonObject("active")
                    val alive=a.get("alive")?.asBoolean==true;val aEnded=a.get("ended")?.asBoolean==true;val dc=a.get("disconnected")?.asBoolean==true
                    val st=when{dc->"재접대기";!alive->"기절";aEnded->"완료";else->"행동"}
                    SpireSkin.panel(g,ax,nextY,aw,28,if(dc)0xFF6B7280.toInt() else if(!alive)0x7A4B4B else if(aEnded)0xE6B84D else SpireSkin.teal)
                    text(g,short("${a.text("name")}($st) ${a.number("damageDealt")}뎀",aw-8),ax+4,nextY+3,if(dc)0x9CA3AF else if(aEnded)0xF7D379 else 0xDDF2EC)
                    SpireWidgets.hp(g,act,ax+4,nextY+14,aw-8)
                    text(g,short("${SpireWidgets.name(act)} ${act.number("hp")}/${act.number("maxHp")}",aw-8),ax+4,nextY+18,0xBDD3D1)
                }
                nextY+=31
            }
            if(timeline.isNotEmpty()&&ay>=nextY+18){
                val label=timeline.take(5).joinToString(" ▸ "){t->if(t.get("isSelf")?.asBoolean==true)"나" else SpireWidgets.speciesName(t.text("species"),t.text("name"))}
                val lap=timeline.size>=2&&timeline[0].get("isSelf")?.asBoolean==true&&timeline[1].get("isSelf")?.asBoolean==true
                SpireSkin.panel(g,bx,nextY,feedW,15,if(lap)0xFF4CC9F0.toInt() else SpireSkin.teal)
                text(g,short((if(lap)"⚡연속행동! " else "⏱순서: ")+label,feedW-10),bx+4,nextY+3,if(lap)0x7EE8FA else 0xDCECE8)
            }
        }
        val ownW=if(compact)(pw/2-44).coerceAtLeast(108)else (pw-180).coerceAtMost(210)
        val effSpe=active.number("effectiveSpe",active.getAsJsonObject("stats")?.number("spe",0)?:0)
        val speTag=if(effSpe>0)"속도$effSpe" else ""
        val noticeLeft=px+10
        val warnStr=if(anyWeakThreat)"⚠ 약점 피격 주의" else ""
        var curX=noticeLeft
        if(warnStr.isNotBlank()){text(g,warnStr,curX,ay-11,0xFF6B6B);curX+=font.width(warnStr)+6}
        if(SpireWidgets.hasIcons(active)){
            val (usedW,_)=SpireWidgets.drawStatusStatIcons(g,active,curX,ay-12,(ownW+60-(curX-noticeLeft)).coerceAtLeast(40),mx,my)
            curX+=usedW+4
        }
        if(speTag.isNotBlank())text(g,speTag,curX,ay-11,if("paralysis" in SpireWidgets.statusEntries(active).map{it.first})0xFFD84D else 0xC9D5D3)
        SpireSkin.panel(g,px+8,ay,ownW,19,if(anyWeakThreat)SpireAnim.lerpColor(0xFFE03A3A.toInt(),SpireSkin.teal,fastPulse*0.5f) else SpireSkin.teal)
        val selfDmg=if(data.number("partySize",1)>1||data.number("selfDamageDealt")>0)" · ${data.number("selfDamageDealt")}뎀"else ""
        text(g,short("Lv.${active.number("level",1)} ${SpireWidgets.name(active)} ${active.number("hp")}/${active.number("maxHp")}$selfDmg",ownW-8),px+12,ay+2)
        SpireWidgets.hp(g,active,px+12,ay+12,ownW-8)
        val pgPct=data.number("playerGaugePct",100).coerceIn(0,100)
        val pgw=((ownW-8)*pgPct/100f).roundToInt()
        g.fill(px+12,ay+15,px+ownW+4,ay+17,0xFF1A2028.toInt())
        if(pgw>0)g.fill(px+12,ay+15,px+12+pgw,ay+17,0xFF4CC9F0.toInt())
        val energyX=px+12+ownW
        val maxE=data.number("maxEnergy",3).coerceIn(3,5)
        val energyW=if(compact)(60+maxE*6)else (76+maxE*8)
        SpireSkin.panel(g,energyX,ay,energyW,19,SpireSkin.gold)
        if(data.get("forced")?.asBoolean==true)text(g,"무료 교체",energyX+8,ay+5,0xECAF93)
        else{
            val eNow=data.number("energy")
            val orbCol=if(eNow>0)SpireAnim.lerpColor(SpireSkin.teal,0xFFB8FFF4.toInt(),pulse*0.45f) else SpireSkin.teal
            SpireSkin.orb(g,energyX+12,ay+9,6,eNow>0,orbCol)
            g.drawCenteredString(font,"$eNow",energyX+12,ay+5,if(eNow>0)0xFFFDF0D0.toInt()else 0x7A756C)
            val gemStep=if(compact)10 else 13
            for(i in 0 until maxE)SpireSkin.gem(g,energyX+24+i*gemStep,ay+6,3,i<eNow)
            val relicCount=data.array("relics")?.size()?:0
            text(g,if(relicCount>0)"$eNow/$maxE · 유물$relicCount"else "$eNow/$maxE",energyX+24,ay+10,0xE8DEC4)
        }
        hoveredPile=if(!adventureMenuOpen&&!busy()){
            pileBoxes.entries.firstOrNull{(_,b)->mx>=b[0]&&mx<b[0]+b[2]&&my>=b[1]&&my<b[1]+b[3]}?.key
        }else null
        val nextHover=if(adventureMenuOpen)-1 else hovered(mx.toDouble(),my.toDouble())
        if(nextHover!=hover&&nextHover>=0)SpireMusic.cardHover()
        hover=nextHover
        for(i in cardHover.indices){
            cardHover[i]=SpireAnim.approach(cardHover[i],if(i==hover&&!busy())1f else 0f,dt,19f)
        }
        if(!compact)text(g,"손패 ${hand().size}/${data.number("handLimit")} · 숫자키: 카드 · ESC: 메뉴 · WASD/Space: 비행 · T: 채팅",px+pw/2-135,py+l.handY.toInt()-13,0xDDEDE5)
        hand().forEachIndexed{i,c->if(i!=hover&&!animation.hiddenHand(c.get("instance").asLong,now))drawCard(g,c,l.hand(i),liftFactor=cardHover.getOrElse(i){0f}*0.35f)}
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
        if(adventureMenuOpen)return
        val l=layout()
        val pulse=SpireAnim.pulse(1600L)
        hand().getOrNull(hover)?.let{c->if(!busy()){
            g.pose().pushPose();g.pose().translate(0.0,0.0,300.0)
            drawCard(g,c,l.hand(hover),liftFactor=cardHover.getOrElse(hover){1f}.coerceAtLeast(0.18f))
            g.pose().popPose()
            val preview=DamageLabels.preview(c,target)?.deepCopy()?.also{pv->
                if(c.get("selfTarget")?.asBoolean!=true)enemies().getOrNull(target)?.let{e->pv.addProperty("targetName",SpireWidgets.name(e))}
            }
            val lines=CardTooltip.lines(c,SpireWidgets.move(c.text("move")),SpireCardArt.description(c),
                if(CardSemantics.role(c)==CardRole.ATTACK)DamageLabels.type(c.text("type"))else "",preview,
                if(data.number("energy")<c.number("cost"))"에너지 부족"else "")
            g.renderTooltip(font,font.split(Component.literal(lines.joinToString("\n")),(pw-24).coerceAtMost(280)),mx,my)
            return
        }}
        val hovE=enemies().getOrNull(hoveredEnemy)
        if(hovE!=null){
            renderExpandedEnemy(g,hoveredEnemy,hovE,pingsForEnemy(hoveredEnemy),pulse)
            return
        }
        hoveredPile?.let{kind->renderPilePreview(g,kind)}
    }
    private fun pingsForEnemy(i:Int)=data.array("pings")?.map{it.asJsonObject}?.filter{it.number("target")==i}.orEmpty()
}
