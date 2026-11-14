package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

/** Cursor mode expands the ordinary HUD; closing it never leaves the battle or changes camera. */
class SpireBattleScreen(data:JsonObject):SpirePane(data){
    private val animation=HandAnimation()
    private var oldCount=data.getAsJsonArray("hand").size()
    private var hover=-1
    private var cameraOpen=false
    private fun layout()=BattleHudLayout(width,height,data.getAsJsonArray("hand").size(),true)
    private fun busy()=BattleHudState.pending||animation.busy()
    private fun hand()=data.getAsJsonArray("hand").map{it.asJsonObject}
    private fun party()=data.getAsJsonArray("party").map{it.asJsonObject}
    init{animation.update(null,data,data.get("fresh")?.asBoolean==true)}
    override fun accept(value:JsonObject){
        if(value.text("session")==data.text("session")&&(value.number("battleNumber")<data.number("battleNumber")||value.number("battleNumber")==data.number("battleNumber")&&value.number("revision")<data.number("revision")))return
        oldCount=hand().size;animation.update(data,value,true);hover=-1;super.accept(value)
    }
    override fun showError(s:String){BattleHudState.error(s);super.showError(s);rebuildWidgets()}
    private fun command(action:String,body:JsonObject.()->Unit={}){if(!busy())BattleHudState.command(action,body)}
    override fun init(){
        val compact=width<420;val vote=data.number("surrenderVotes");val total=data.number("participantCount",1)
        val voted=data.get("surrenderVoted")?.asBoolean==true
        val bw=if(compact)42 else 62;val voteW=if(compact)76 else 106
        val start=width-12-voteW-2*bw-10
        button(start,6,bw,18,"시점 설정"){cameraOpen=!cameraOpen;rebuildWidgets()}
        button(start+bw+4,6,bw,18,if(compact)"접기"else "HUD 접기"){onClose()}
        button(width-12-voteW,6,voteW,18,(if(voted)"항복 취소"else "항복")+" $vote/$total",!BattleHudState.pending){command("surrender"){addProperty("vote",!voted)}}
            .tooltip=Tooltip.create(Component.literal("같은 전투의 모든 참가자가 동의하면 항복합니다.\n전리품은 지급되지 않습니다."))
        val l=layout();val piles=data.getAsJsonObject("piles")
        for((kind,label,b) in listOf(Triple("draw","뽑기",l.draw),Triple("discard","버림",l.discard),Triple("exhaust","소멸",l.exhaust))){
            button(b.x.toInt(),b.y.toInt(),b.w.toInt(),b.h.toInt(),"$label ${piles.getAsJsonArray(kind).size()}"){minecraft?.setScreen(PileScreen(this,data,kind))}
        }
        val ended=data.get("ended")?.asBoolean==true
        val endW=90;val pingW=42;val pw=((width-endW-pingW-44)/3).coerceAtMost(160)
        party().forEachIndexed{i,p->val active=i==data.number("active");val eligible=(!ended||data.get("forced")?.asBoolean==true)&&p.number("hp")>0&&!active&&(data.get("forced")?.asBoolean==true||data.get("switchUsed")?.asBoolean!=true&&data.number("energy")>0)
            button(12+i*(pw+4),l.partyY,pw,20,(if(active)"● "else "↔ ")+short(SpireWidgets.name(p)+" ${p.number("hp")}",pw-20),eligible&&!BattleHudState.pending){SpireMusic.switchPokemon();command("switch"){addProperty("index",i)}}.selected=active
        }
        button(width-endW-pingW-16,l.partyY,pingW,20,"◎ 핑"){sendPing(hand().getOrNull(hover))}
        button(width-endW-12,l.partyY,endW,20,if(ended)"아군 대기"else "턴 종료",!ended&&data.get("forced")?.asBoolean!=true&&!BattleHudState.pending){SpireMusic.turnEnd();command("end")}.primary=!ended
        if(cameraOpen){
            val x=(width-244).coerceAtLeast(12);val y=32;val w=106
            val actions=listOf("왼쪽으로" to {WorldBattleView.orbit(-10f,0f)},"오른쪽으로" to {WorldBattleView.orbit(10f,0f)},"높게 보기" to {WorldBattleView.orbit(0f,4f)},"낮게 보기" to {WorldBattleView.orbit(0f,-4f)},"가까이" to {WorldBattleView.zoom(-2f)},"멀리" to {WorldBattleView.zoom(2f)},"기본 시점" to {WorldBattleView.resetAngle()},(if(WorldBattleView.bodyView)"전술 시점"else "관전 위치") to {WorldBattleView.bodyView=!WorldBattleView.bodyView})
            actions.forEachIndexed{i,(label,action)->button(x+8+(i%2)*(w+6),y+19+(i/2)*23,w,20,label){action();rebuildWidgets()}}
        }
    }
    override fun onClose(){minecraft?.setScreen(null)}
    override fun background(g:GuiGraphics){}
    private fun sendPing(card:JsonObject?){
        val target=BattleHudState.target
        if((data.getAsJsonArray("enemies").toList().getOrNull(target)?.asJsonObject?.number("hp",0)?:0)<=0)return
        val chosen=card?:hand().filter{BattleHudState.canPlay(it)&&it.array("previews")!=null}.maxByOrNull{it.array("previews")?.toList()?.getOrNull(target)?.asJsonObject?.number("maxDamage")?:0}
        val preview=chosen?.array("previews")?.toList()?.getOrNull(target)?.asJsonObject
        SpireMusic.cardHover()
        command("ping"){addProperty("target",target);addProperty("move",chosen?.text("move").orEmpty());addProperty("minDamage",preview?.number("minDamage")?:0);addProperty("maxDamage",preview?.number("maxDamage")?:0)}
    }
    private fun hovered(mx:Double,my:Double):Int{
        if(busy())return -1;val l=layout()
        if(hover in hand().indices){val b=l.hand(hover);if(CardBox(b.x,b.y-8,b.w,b.h+8).contains(mx,my))return hover}
        return hand().indices.reversed().firstOrNull{l.hand(it).contains(mx,my)}?:-1
    }
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        // Widget hit boxes must win over cards beneath the camera popover.
        if(super.mouseClicked(mx,my,button))return true
        if(cameraOpen&&mx>=(width-244).coerceAtLeast(12)&&my in 32.0..152.0)return true
        if(button==1){val index=hovered(mx,my);if(index>=0){sendPing(hand()[index]);return true}}
        if(button==0){
            val enemies=data.getAsJsonArray("enemies");val l=layout()
            enemies.forEachIndexed{i,e->if(l.enemy(i,enemies.size()).contains(mx,my)&&e.asJsonObject.number("hp")>0){BattleHudState.target=i;return true}}
            val index=hovered(mx,my);if(index>=0){BattleHudState.play(index);return true}
        };return false
    }
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean{
        val index=if(key in 49..57)key-49 else if(key==48)9 else -1
        if(index>=0){if(!busy())BattleHudState.play(index);return true}
        if(key==268){WorldBattleView.resetAngle();return true}
        return super.keyPressed(key,scan,mods)
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val l=layout();BattleHudRenderer.frame(g,data,l,true)
        val nextHover=if(cameraOpen&&mx>=(width-244).coerceAtLeast(12)&&my in 32..152)-1 else hovered(mx.toDouble(),my.toDouble())
        if(nextHover!=hover&&nextHover>=0)SpireMusic.cardHover()
        hover=nextHover
        val now=System.currentTimeMillis()
        hand().forEachIndexed{i,c->if(i!=hover&&!animation.hiddenHand(c.get("instance").asLong,now))BattleHudRenderer.card(g,c,l.hand(i),BattleHudState.target,key=if(i==9)"0"else (i+1).toString())}
        hand().getOrNull(hover)?.let{BattleHudRenderer.card(g,it,l.hand(hover),BattleHudState.target,true,key=if(hover==9)"0"else (hover+1).toString())}
        val previous=BattleHudLayout(width,height,oldCount,true)
        for(f in animation.flights.filter{it.active(now)}){
            val from=if(f.from=="HAND")previous.hand(f.fromIndex.coerceIn(0,(oldCount-1).coerceAtLeast(0)))else l.anchor(f.from,f.fromIndex)
            val to=l.anchor(f.to,f.toIndex);val p=f.progress(now);val t=p*p*(3-2*p)
            BattleHudRenderer.card(g,f.card,CardBox(from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t-kotlin.math.sin(p*Math.PI)*12,from.w+(to.w-from.w)*t,from.h+(to.h-from.h)*t),BattleHudState.target)
        }
        if(cameraOpen){val x=(width-244).coerceAtLeast(12);SpireSkin.panel(g,x,32,236,118);text(g,"전장 중심을 기준으로 조절",x+8,38,0xCADDD1)}
    }
    override fun render(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        super.render(g,mx,my,delta)
        if(!cameraOpen&&hover<0){
            val enemies=data.getAsJsonArray("enemies");val l=layout()
            enemies.forEachIndexed{i,v->if(l.enemy(i,enemies.size()).contains(mx.toDouble(),my.toDouble())){
                val e=v.asJsonObject;val role=CardRole.parse(e.text("intentRole"))
                val lines=buildList{add(SpireWidgets.name(e));add("특성: "+Component.translatable("cobblemon.ability.${e.text("ability")}").string);add(SpireWidgets.move(e.text("intent")))
                    if(role==CardRole.ATTACK){add(DamageLabels.type(e.text("intentType"))+" · ${DamageLabels.damage(e)} 피해 · 명중 ${e.number("intentAccuracy")}%");add(DamageLabels.matchup(DamageLabels.multiplier(e))) }
                    else add(e.text("intentEffect"))
                }
                g.renderTooltip(font,font.split(Component.literal(lines.joinToString("\n")),260),mx,my)
            }}
        }
        if(!cameraOpen)hand().getOrNull(hover)?.let{c->if(!busy()){
            val lines=CardTooltip.lines(c,SpireWidgets.move(c.text("move")),SpireCardArt.description(c),if(CardSemantics.role(c)==CardRole.ATTACK)DamageLabels.type(c.text("type"))else "",DamageLabels.preview(c,BattleHudState.target),if(!BattleHudState.canPlay(c))"현재 사용할 수 없습니다"else "")
            g.renderTooltip(font,font.split(Component.literal(lines.joinToString("\n")),minOf(280,width-24)),mx,my)
        }}
    }
}
