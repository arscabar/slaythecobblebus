package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class SpireMapScreen(data:JsonObject):SpirePane(data){
    override val maximumWidth=640
    override val maximumHeight=380
    override val supportsAdventureMenu=true
    private val mapObj get()=data.getAsJsonObject("map")
    private val nodeButtons=mutableMapOf<Int,MapNodeButton>()
    private data class VoteRipple(val nodeId:Int,val startMs:Long,val color:Int,val maxR:Int,val duration:Long=650L)
    private val ripples=mutableListOf<VoteRipple>()
    private val votePopTime=mutableMapOf<Int,Long>()
    private val hoverScale=mutableMapOf<Int,Float>()
    private var lastVotes=mapOf<Int,Int>()
    private var lastMyVote=data.number("myVote",-1)
    private var lastLeadingId=data.number("leadingNodeId",-1)
    private var lastFrameMs=SpireAnim.now()
    private var voteFillRatio=0f
    private var lastInspectedId=-1
    private var inspectorFlashAt=0L
    private var evolveFlashAt=0L
    private var scrollY=-1f
    private var targetScrollY=-1f
    private var lastFocusKey=""
    private var draggingMap=false
    private var draggingBar=false
    private var dragStartMouseY=0.0
    private var dragStartScroll=0f
    private fun nodes()=mapObj.array("nodes")?.map{it.asJsonObject}.orEmpty()
    private fun voters()=data.array("voters")?.map{it.asJsonObject}.orEmpty()
    private fun party()=data.array("party")?.map{it.asJsonObject}.orEmpty()
    private fun items()=data.array("items")?.map{it.asJsonObject}.orEmpty()
    private fun bgTexture():ResourceLocation=when(data.number("act",1)){
        2->ResourceLocation.fromNamespaceAndPath("cobblespire","textures/gui/map_bg_act2_magma.png")
        3->ResourceLocation.fromNamespaceAndPath("cobblespire","textures/gui/map_bg_act3_galaxy.png")
        else->ResourceLocation.fromNamespaceAndPath("cobblespire","textures/gui/map_bg_act1.png")
    }
    override fun accept(value:JsonObject){
        val now=SpireAnim.now()
        val newNodes=value.getAsJsonObject("map")?.array("nodes")?.map{it.asJsonObject}.orEmpty()
        val newVotes=newNodes.associate{it.number("id") to it.number("votes",0)}
        val newMyVote=value.number("myVote",-1)
        val newLeading=value.number("leadingNodeId",-1)
        newVotes.forEach{(id,cnt)->
            val prev=lastVotes[id]?:0
            if(cnt>prev){
                ripples.add(VoteRipple(id,now,if(id==newMyVote)0x72FFE5 else 0xFFD966,24))
                votePopTime[id]=now
            }
        }
        if(newMyVote>=0&&newMyVote!=lastMyVote){
            ripples.add(VoteRipple(newMyVote,now,0x72FFE5,26))
            votePopTime[newMyVote]=now
        }
        if(newLeading>=0&&newLeading!=lastLeadingId){
            ripples.add(VoteRipple(newLeading,now+60L,0xFFE57F,28))
        }
        lastVotes=newVotes;lastMyVote=newMyVote;lastLeadingId=newLeading
        super.accept(value)
    }
    private fun barY()=py+ph-52
    private fun mapRect():IntArray{
        val bodyY=py+25
        val bodyBottom=barY()-4
        val bodyH=(bodyBottom-bodyY).coerceAtLeast(140)
        val sideW=160
        val mapW=(pw-sideW-26).coerceAtLeast(240)
        return intArrayOf(px+10,bodyY,mapW,bodyH)
    }
    private fun totalRows():Int{
        val explicit=mapObj.number("rowCount",0)
        if(explicit>1)return explicit
        return ((nodes().maxOfOrNull{it.number("row")}?:5)+1).coerceAtLeast(2)
    }
    private fun contentHeight(mapH:Int):Int{
        val rows=totalRows().coerceAtLeast(2)
        val padY=26
        val rowStep=44
        return maxOf(mapH,padY*2+(rows-1)*rowStep)
    }
    private fun maxScroll(mapH:Int)=(contentHeight(mapH)-mapH).coerceAtLeast(0)
    private fun virtualRowY(row:Int,contentH:Int):Int{
        val padY=26
        val rows=totalRows().coerceAtLeast(2)
        val innerH=(contentH-padY*2).coerceAtLeast(80)
        return padY+innerH-(row*innerH/(rows-1))
    }
    private fun nodeVirtualPos(n:JsonObject,byRow:Map<Int,List<JsonObject>>,mapX:Int,mapW:Int,contentH:Int):Pair<Int,Int>{
        val padX=42
        val innerW=(mapW-padX-30).coerceAtLeast(60)
        val row=n.number("row");val col=n.number("col")
        val rowNodes=byRow[row].orEmpty().size.coerceAtLeast(1)
        val baseX=when{
            rowNodes==1->mapX+padX+innerW/2
            rowNodes==2->mapX+padX+(innerW*(0.24f+col*0.52f)).roundToInt()
            else->mapX+padX+(innerW*(col.toFloat()/(rowNodes-1))).roundToInt()
        }
        val baseY=virtualRowY(row,contentH)
        val jy=n.number("jy",0).coerceIn(-2,2)
        val x=(baseX+n.number("jx",0).coerceIn(-10,10)).coerceIn(mapX+30,mapX+mapW-18)
        val y=(baseY+jy).coerceIn(14,contentH-14)
        return x to y
    }
    private fun nodePos(n:JsonObject,byRow:Map<Int,List<JsonObject>>):Pair<Int,Int>{
        val r=mapRect()
        val contentH=contentHeight(r[3])
        val(vx,vy)=nodeVirtualPos(n,byRow,r[0],r[2],contentH)
        return vx to (r[1]+vy-scrollY.coerceAtLeast(0f).roundToInt())
    }
    private fun ensureFocusScroll(mapH:Int,byId:Map<Int,JsonObject>){
        val focusKey="${data.number("act",1)}:${mapObj.number("currentId",-1)}:${totalRows()}"
        val maxS=maxScroll(mapH).toFloat()
        if(focusKey!=lastFocusKey){
            lastFocusKey=focusKey
            val rows=totalRows().coerceAtLeast(2)
            val currentId=mapObj.number("currentId",-1)
            val curRow=byId[currentId]?.number("row")?:-1
            val focusRow=if(curRow<0)0 else (curRow+1).coerceAtMost(rows-1)
            val contentH=contentHeight(mapH)
            val focusVY=virtualRowY(focusRow,contentH)
            val desired=(focusVY-mapH*0.45f).coerceIn(0f,maxS)
            if(scrollY<0f){scrollY=desired;targetScrollY=desired}else{targetScrollY=desired}
        }else{
            scrollY=scrollY.coerceIn(0f,maxS)
            targetScrollY=targetScrollY.coerceIn(0f,maxS)
        }
    }
    private fun syncNodeButtons(){
        val r=mapRect();val mapX=r[0];val mapY=r[1];val mapW=r[2];val mapH=r[3];val mapBottom=mapY+mapH
        val contentH=contentHeight(mapH)
        val sc=scrollY.coerceAtLeast(0f).roundToInt()
        val all=nodes();val byRow=all.groupBy{it.number("row")}
        all.forEach{n->
            val btn=nodeButtons[n.number("id")]?:return@forEach
            val(vx,vy)=nodeVirtualPos(n,byRow,mapX,mapW,contentH)
            val cx=vx;val cy=mapY+vy-sc
            btn.x=cx-btn.width/2
            btn.y=cy-btn.height/2
            btn.visible=(btn.y+btn.height>mapY+1&&btn.y<mapBottom-1)
        }
    }
    override fun init(){
        nodeButtons.clear()
        val all=nodes();val byId=all.associateBy{it.number("id")};val byRow=all.groupBy{it.number("row")}
        val r=mapRect()
        ensureFocusScroll(r[3],byId)
        if(lastVotes.isEmpty())lastVotes=all.associate{it.number("id") to it.number("votes",0)}
        val isHost=data.get("isHost")?.asBoolean!=false
        val partySize=data.number("partySize",1)
        val myVote=data.number("myVote",-1)
        val currentId=mapObj.number("currentId",-1)
        val leadingId=data.number("leadingNodeId",-1)
        all.forEach{n->
            val nid=n.number("id")
            val(cx,cy)=nodePos(n,byRow)
            val reach=n.get("reachable")?.asBoolean==true
            val visited=n.get("visited")?.asBoolean==true
            val current=currentId==nid
            val boss=n.text("kind")=="BOSS"
            val size=if(boss)22 else 18
            val canClick=reach&&(partySize>1||isHost)
            val btn=MapNodeButton(
                cx-size/2,cy-size/2,size,nid,n,
                reachable=reach,visited=visited,current=current,
                votedByMe=(myVote==nid),leading=(leadingId==nid),
                clickable=canClick
            ){
                val clickNow=SpireAnim.now()
                ripples.add(VoteRipple(nid,clickNow,0x72FFE5,26))
                votePopTime[nid]=clickNow
                SpireMusic.cardHover()
                SpireClient.send("choose_node"){addProperty("nodeId",nid)}
            }
            val tipStatus=when{
                current->"현재 위치"
                myVote==nid->"내가 투표함"
                visited->"방문 완료"
                canClick->if(partySize>1)"클릭하여 투표" else "클릭하여 이동"
                reach->"방장이 이동을 결정합니다"
                else->"이동 불가"
            }
            val floorName=if(boss)"보스" else "${n.number("row")+1}층"
            btn.tooltip=Tooltip.create(Component.literal("$floorName · ${n.text("icon")} ${n.text("label")} [$tipStatus]\n${n.text("desc")}"))
            nodeButtons[nid]=addRenderableWidget(btn)
        }
        syncNodeButtons()
        val sideX=r[0]+r[2]+6
        val sideW=pw-(sideX-px)-10
        val sideBottom=r[1]+r[3]
        if(partySize>1&&isHost){
            val votedCount=data.number("votedCount",0)
            button(sideX+6,sideBottom-20,sideW-12,15,"최다 득표 출발",votedCount>0){
                SpireClient.send("confirm_map")
            }.also{if(votedCount>0)it.primary=true}
        }
        addAdventureMenuButton(px+pw-52,py+5,42,15)
        val bY=barY()
        val pList=party().take(3)
        val slotW=((pw-36)/3).coerceAtLeast(110)
        pList.forEachIndexed{i,c->
            if(c.get("canEvolve")?.asBoolean!=true)return@forEachIndexed
            val evos=c.array("evolutions")?.map{it.asJsonObject}.orEmpty()
            if(evos.isEmpty())return@forEachIndexed
            val pxBox=px+16+i*slotW
            val shown=evos.take(2)
            val bw=((slotW-14)/(shown.size)).coerceAtMost(84)
            shown.forEachIndexed{j,ev->
                val label=if(shown.size==1)"✨ ${ev.text("name")} 진화" else "✨ ${ev.text("name")}"
                button(pxBox+j*(bw+2),bY+22,bw,12,short(label,bw-6)){
                    evolveFlashAt=SpireAnim.now()
                    SpireMusic.switchPokemon()
                    SpireClient.send("evolve"){addProperty("partyIndex",i);addProperty("targetKey",ev.text("key"))}
                }.also{
                    it.primary=true
                    val how=if(ev.get("readyByLevel")?.asBoolean==true)"레벨 조건 달성 (Lv.${ev.number("reqLevel")})" else "보스 진화권 사용"
                    it.tooltip=Tooltip.create(Component.literal("${SpireWidgets.name(c)} → ${ev.text("name")} 진화!\n($how · 능력치 상승 + 새 기술 카드 획득)"))
                }
            }
        }
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=SpireAnim.now()
        val dt=((now-lastFrameMs).coerceIn(0L,80L))/1000f
        lastFrameMs=now
        ripples.removeIf{now>=it.startMs+it.duration}

        val title="CobbleSpire · ${data.number("act",1)}막 탐험 지도 · 진행 ${data.number("points")}점"
        text(g,title,px+10,py+8,0xE7C98F)
        val msg=data.text("message")
        if(msg.isNotBlank()){
            val msgX=px+18+font.width(title)
            val maxW=(px+pw-62)-msgX
            if(maxW>40)text(g,short(msg,maxW),msgX,py+8,0x8CE8B5)
        }

        val r=mapRect();val mapX=r[0];val mapY=r[1];val mapW=r[2];val mapH=r[3]
        val mapBottom=mapY+mapH
        val all=nodes();val byId=all.associateBy{it.number("id")};val byRow=all.groupBy{it.number("row")}
        ensureFocusScroll(mapH,byId)
        val maxS=maxScroll(mapH)
        if(!draggingMap&&!draggingBar){
            scrollY=if(kotlin.math.abs(targetScrollY-scrollY)<0.6f)targetScrollY else SpireAnim.approach(scrollY,targetScrollY,dt,15f)
        }
        syncNodeButtons()
        val contentH=contentHeight(mapH)
        val scroll=scrollY.coerceIn(0f,maxS.toFloat()).roundToInt()
        val rows=totalRows()

        // Parchment map frame & scrollable aspect-preserved background
        SpireSkin.panel(g,mapX-2,mapY-2,mapW+4,mapH+4,SpireSkin.gold)
        g.enableScissor(mapX,mapY,mapX+mapW,mapBottom)
        drawMapBackground(g,mapX,mapY,mapW,mapH,contentH,scroll)
        g.fill(mapX,mapY,mapX+mapW,mapBottom,0x1E140F0A)

        val currentId=mapObj.number("currentId",-1)
        val currentRow=byId[currentId]?.number("row")?:-1
        val myVote=data.number("myVote",-1)
        val leadingId=data.number("leadingNodeId",-1)

        // Subtle horizontal floor guides & step labels on the left margin
        for(row in 0 until rows){
            val ry=mapY+virtualRowY(row,contentH)-scroll
            if(ry<mapY-14||ry>mapBottom+14)continue
            for(gx in (mapX+30) until (mapX+mapW-14) step 6){
                g.fill(gx,ry,gx+3,ry+1,0x243B2B1E)
            }
            val isNextRow=(row==currentRow+1)
            val isCurRow=(row==currentRow)
            val stepLabel=if(row==rows-1)"보스" else "${row+1}층"
            val stepCol=when{
                isCurRow->0xFF5CEAD6.toInt()
                isNextRow->0xFFE8B84E.toInt()
                else->0xAA4A3826.toInt()
            }
            g.drawString(font,stepLabel,mapX+5,ry-4,stepCol,isCurRow||isNextRow)
        }

        // Connecting paths with clear hierarchy (abandoned < future < taken < active < chosen)
        all.forEach{n->
            val(x1,y1)=nodePos(n,byRow)
            val srcRow=n.number("row")
            val srcVisited=n.get("visited")?.asBoolean==true
            n.array("next")?.forEach{nid->
                byId[nid.asInt]?.let{t->
                    val(x2,y2)=nodePos(t,byRow)
                    if(maxOf(y1,y2)<mapY-16||minOf(y1,y2)>mapBottom+16)return@let
                    val targetId=t.number("id")
                    val targetVisited=t.get("visited")?.asBoolean==true
                    val active=n.number("id")==currentId&&t.get("reachable")?.asBoolean==true
                    val chosen=active&&(targetId==myVote||targetId==leadingId)
                    val taken=srcVisited&&targetVisited
                    val abandoned=!active&&!taken&&srcRow<=currentRow
                    drawPath(g,x1,y1,x2,y2,active,chosen,taken,abandoned,t.text("kind")=="BOSS",now)
                }
            }
        }

        // Vote & click ripples
        for(rp in ripples){
            val targetNode=byId[rp.nodeId]?:continue
            if(now<rp.startMs)continue
            val(nx,ny)=nodePos(targetNode,byRow)
            val p=((now-rp.startMs).toFloat()/rp.duration).coerceIn(0f,1f)
            val ep=SpireAnim.easeOutCubic(p)
            val rad=(8+rp.maxR*ep).roundToInt()
            SpireAnim.drawRing(g,nx,ny,rad,SpireAnim.withAlpha(rp.color,(1f-p)*0.85f))
            if(rad>13)SpireAnim.drawRing(g,nx,ny,rad-4,SpireAnim.withAlpha(rp.color,(1f-p)*0.45f))
        }
        g.disableScissor()

        // Scrollbar & direction hints when scrollable
        if(maxS>0){
            val trackX=mapX+mapW-7;val trackTop=mapY+6;val trackH=(mapH-12).coerceAtLeast(24)
            g.fill(trackX,trackTop,trackX+4,trackTop+trackH,0x66120D09)
            val thumbH=(trackH*(mapH.toFloat()/contentH)).roundToInt().coerceIn(22,trackH)
            val thumbY=trackTop+((trackH-thumbH)*(scrollY/maxS)).roundToInt().coerceIn(0,trackH-thumbH)
            val barHot=draggingBar||(mx in (trackX-3)..(trackX+6)&&my in trackTop..(trackTop+trackH))
            g.fill(trackX,thumbY,trackX+4,thumbY+thumbH,if(barHot)0xFFFFE588.toInt() else 0xFFD4AA59.toInt())
            if(scrollY>4f){
                val hint="▲ 위층 보스 (휠 스크롤)"
                val hw=font.width(hint)+10
                val hx=mapX+(mapW-hw)/2
                g.fill(hx,mapY+3,hx+hw,mapY+14,0xB818120D.toInt())
                g.drawString(font,hint,hx+5,mapY+5,0xFFE8C87A.toInt(),false)
            }
            if(scrollY<maxS-4f){
                val hint="▼ 아래층"
                val hw=font.width(hint)+10
                val hx=mapX+(mapW-hw)/2
                g.fill(hx,mapBottom-14,hx+hw,mapBottom-3,0xB818120D.toInt())
                g.drawString(font,hint,hx+5,mapBottom-12,0xFFD8C9AA.toInt(),false)
            }
        }

        // Right-side Inspector + Legend + Co-op Vote strictly bounded to mapH
        val sideX=mapX+mapW+6
        val sideW=pw-(sideX-px)-10
        val inspH=(mapH*0.41f).roundToInt().coerceIn(66,100)
        val legY=mapY+inspH+4;val legH=50
        val voteY=legY+legH+4;val voteH=(mapBottom-voteY).coerceAtLeast(36)

        val inMapViewport=mx in mapX until (mapX+mapW)&&my in mapY until mapBottom
        val hoveredId=if(!inMapViewport)null else nodeButtons.entries.firstOrNull{(_,b)->b.visible&&mx>=b.x&&mx<b.x+b.width&&my>=b.y&&my<b.y+b.height}?.key
        val focusNode=byId[hoveredId]?:byId[myVote]?:all.firstOrNull{it.get("reachable")?.asBoolean==true}?:byId[currentId]
        val fidNow=focusNode?.number("id")?:-1
        if(fidNow!=lastInspectedId){
            lastInspectedId=fidNow
            inspectorFlashAt=now
        }

        // 1. Node Inspector
        val inspFlash=if(now-inspectorFlashAt<240L)1f-((now-inspectorFlashAt)/240f) else 0f
        SpireSkin.panel(g,sideX,mapY,sideW,inspH,SpireAnim.lerpColor(SpireSkin.gold,SpireSkin.teal,inspFlash*0.75f))
        if(inspFlash>0f)g.fill(sideX+2,mapY+2,sideX+sideW-2,mapY+inspH-2,SpireAnim.withAlpha(0x8CE8B5,inspFlash*0.12f))
        if(focusNode!=null){
            val fid=focusNode.number("id")
            val icon=focusNode.text("icon")
            val fFloor=if(focusNode.text("kind")=="BOSS")"보스" else "${focusNode.number("row")+1}층"
            val label="$fFloor · ${focusNode.text("label")}"
            val iconCol=(focusNode.number("color",0xE8DEC4) and 0xFFFFFF) or 0xFF000000.toInt()
            text(g,icon,sideX+6,mapY+6,iconCol)
            text(g,short(label,sideW-24),sideX+19,mapY+6,0xFFE8C87A.toInt())
            val stateTag=when{
                hoveredId==fid->"▲ 가리킨 노드"
                myVote==fid->"★ 내가 투표한 노드"
                fid==currentId->"▶ 현재 위치"
                focusNode.get("reachable")?.asBoolean==true->"● 다음 이동 가능"
                focusNode.get("visited")?.asBoolean==true->"✓ 방문 완료"
                else->"• 예정 경로"
            }
            text(g,short(stateTag,sideW-12),sideX+6,mapY+17,0x8CE8B5)
            val wrapped=font.split(Component.literal(focusNode.text("desc")),sideW-12)
            var descY=mapY+29
            val descMaxY=mapY+inspH-14
            for(line in wrapped){
                if(descY+9>descMaxY)break
                g.drawString(font,line,sideX+6,descY,0xDDD5C2)
                descY+=10
            }
            val vCount=focusNode.number("votes",0)
            if(vCount>0){
                val names=voters().filter{it.number("nodeId",-1)==fid}.joinToString(", "){it.text("name")}
                text(g,short("득표 ${vCount}표 ($names)",sideW-12),sideX+6,mapY+inspH-12,0xFF7CF2DF.toInt())
            }
        }else{
            text(g,"아이콘에 마우스를 올리면",sideX+6,mapY+20,0xDDD5C2)
            text(g,"상세 정보가 표시됩니다.",sideX+6,mapY+31,0xDDD5C2)
        }

        // 2. Icon Legend (strictly fits in legH = 50)
        SpireSkin.panel(g,sideX,legY,sideW,legH,0xFF7A6342.toInt())
        text(g,"아이콘 범례",sideX+6,legY+5,0xE7C98F)
        val legend=listOf(
            Triple("⚔","조직 전투",0xD9534F),
            Triple("🐾","야생 조우",0x66BB6A),
            Triple("◉","영입 만남",0x4FC3F7),
            Triple("♥","휴식처",0x81C784),
            Triple("?","이벤트",0xBA68C8),
            Triple("★","보스전",0xFFB74D)
        )
        legend.forEachIndexed{idx,(ic,nm,col)->
            val lx=sideX+6+(idx%2)*(sideW/2-3)
            val ly=legY+16+(idx/2)*11
            text(g,ic,lx,ly,(col and 0xFFFFFF) or 0xFF000000.toInt())
            text(g,short(nm,sideW/2-16),lx+10,ly,0xE2DBC9)
        }

        // 3. Co-op Vote / Run Status (strictly bounded to voteH)
        SpireSkin.panel(g,sideX,voteY,sideW,voteH,SpireSkin.teal)
        val partySize=data.number("partySize",1)
        val votedCount=data.number("votedCount",0)
        val isHost=data.get("isHost")?.asBoolean!=false
        if(partySize>1){
            val targetRatio=(votedCount.toFloat()/partySize.coerceAtLeast(1)).coerceIn(0f,1f)
            voteFillRatio=SpireAnim.approach(voteFillRatio,targetRatio,dt,10f)
            text(g,"협동 투표 ($votedCount/$partySize)",sideX+6,voteY+5,0x8CE8B5)
            val barX=sideX+6;val progW=sideW-12;val pBarY=voteY+15
            g.fill(barX,pBarY,barX+progW,pBarY+3,0xFF10181A.toInt())
            val fillW=(progW*voteFillRatio).roundToInt()
            if(fillW>0)g.fillGradient(barX,pBarY,barX+fillW,pBarY+3,0xFF6CF2DC.toInt(),0xFF287D70.toInt())
            val voterLimitY=voteY+voteH-(if(isHost)23 else 14)
            voters().forEachIndexed{idx,v->
                val vy=voteY+21+idx*10
                if(vy+9>voterLimitY)return@forEachIndexed
                val vn=byId[v.number("nodeId",-1)]
                val dc=v.get("disconnected")?.asBoolean==true
                val voteStr=when{dc->"연결 끊김";vn!=null->"${vn.text("icon")} ${vn.text("label")}";else->"대기${SpireAnim.loadingDots()}"}
                val prefix=if(v.get("isSelf")?.asBoolean==true)"★" else "•"
                val col=if(dc)0xFF70787E.toInt() else if(vn!=null)0xFFE9DEC4.toInt() else 0xFF8C9498.toInt()
                text(g,short("$prefix ${v.text("name")}: $voteStr",sideW-12),sideX+6,vy,col)
            }
            if(!isHost&&voteH>=38){
                text(g,short("전원 투표 시 자동 출발",sideW-12),sideX+6,voteY+voteH-12,0xAFC4C2)
            }
        }else{
            text(g,"탐험 안내",sideX+6,voteY+5,0x8CE8B5)
            val tips=listOf("• 금색 원: 다음 이동 노드","• 휠·드래그로 위아래 스크롤","• 호버로 보상·효과 확인")
            tips.forEachIndexed{idx,line->
                val ty=voteY+17+idx*11
                if(ty+9<=voteY+voteH-4)text(g,short(line,sideW-12),sideX+6,ty,0xE2DBC9)
            }
        }

        // Bottom Party & Item Bar (46px, zero overlap, includes EXP & evolution buttons)
        val bY=barY()
        val evoPulse=if(now-evolveFlashAt in 0L..600L)1f-(now-evolveFlashAt)/600f else 0f
        val barCol=if(evoPulse>0f)SpireAnim.lerpColor(SpireSkin.teal,0xFFFFE57F.toInt(),evoPulse) else SpireSkin.teal
        SpireSkin.panel(g,px+10,bY,pw-20,46,barCol)
        val pList=party().take(3)
        val slotW=((pw-36)/3).coerceAtLeast(110)
        pList.forEachIndexed{i,c->
            val pxBox=px+16+i*slotW
            val hpText="${c.number("hp")}/${c.number("maxHp")}"
            val hpW=font.width(hpText)
            text(g,short("Lv.${c.number("level",1)} ${SpireWidgets.name(c)}",slotW-hpW-14),pxBox,bY+4,0xE7EFE6)
            text(g,hpText,pxBox+slotW-10-hpW,bY+4,0xBDD3D1)
            SpireWidgets.hp(g,c,pxBox,bY+14,slotW-10)
            val exp=c.number("exp",0);val expMax=c.number("expToNext",100).coerceAtLeast(1)
            val expRatio=(exp.toFloat()/expMax).coerceIn(0f,1f)
            g.fill(pxBox,bY+18,pxBox+slotW-10,bY+20,0xFF1A2028.toInt())
            val ew=((slotW-10)*expRatio).roundToInt()
            if(ew>0)g.fill(pxBox,bY+18,pxBox+ew,bY+20,0xFF4CC9F0.toInt())
            if(c.get("canEvolve")?.asBoolean!=true){
                val evos=c.array("evolutions")?.map{it.asJsonObject}.orEmpty()
                val evoHint=if(evos.isNotEmpty())"EXP $exp/$expMax · Lv.${c.number("evoReqLevel",4)} 진화" else "EXP $exp/$expMax"
                text(g,short(evoHint,slotW-10),pxBox,bY+23,0x9FC6C3)
            }
        }
        val itemText=items().joinToString(" · "){it.text("label")}.ifBlank{"없음"}
        val evoCred=data.number("evolutionCredits",0)
        val credTag=if(evoCred>0)" · 진화권 ${evoCred}개" else ""
        text(g,short("최대 에너지 ${data.number("maxEnergy",3)} · 유물 ${data.number("relicCount",0)}개$credTag · 도구: $itemText",pw-32),px+16,bY+35,0xE8DEC4)
    }

    private fun drawMapBackground(g:GuiGraphics,mapX:Int,mapY:Int,mapW:Int,mapH:Int,contentH:Int,scroll:Int){
        val tex=bgTexture()
        val texW=1024;val texH=576
        val natH=(mapW*texH.toFloat()/texW).roundToInt().coerceAtLeast(160)
        val topH=(natH*0.32f).roundToInt().coerceAtMost(contentH/2)
        val botH=(natH*0.38f).roundToInt().coerceAtMost(contentH-topH)
        val midTileH=(natH*0.30f).roundToInt().coerceAtLeast(36)
        val midSrcV=184f;val midSrcH=173

        // Top mountain / border cap
        val topY=mapY-scroll
        if(topY+topH>mapY&&topY<mapY+mapH){
            g.blit(tex,mapX,topY,mapW,topH,0f,0f,texW,184,texW,texH)
        }
        // Seamless ping-pong parchment middle strips
        var vy=topH
        val midEnd=(contentH-botH).coerceAtLeast(topH)
        var tileIdx=0
        while(vy<midEnd){
            val sliceH=minOf(midTileH,midEnd-vy)
            val sy=mapY+vy-scroll
            if(sy+sliceH>mapY&&sy<mapY+mapH){
                val srcH=(midSrcH*(sliceH.toFloat()/midTileH)).roundToInt().coerceIn(1,midSrcH)
                if(tileIdx%2==0){
                    g.blit(tex,mapX,sy,mapW,sliceH,0f,midSrcV,texW,srcH,texW,texH)
                }else{
                    g.blit(tex,mapX,sy,mapW,sliceH,0f,midSrcV+midSrcH,texW,-srcH,texW,texH)
                }
            }
            vy+=sliceH
            tileIdx++
        }
        // Bottom start-forest border cap
        val botY=mapY+contentH-botH-scroll
        if(botY+botH>mapY&&botY<mapY+mapH){
            g.blit(tex,mapX,botY,mapW,botH,0f,357f,texW,219,texW,texH)
        }
    }

    override fun mouseScrolled(mx:Double,my:Double,hScroll:Double,vScroll:Double):Boolean{
        if(adventureMenuOpen)return true
        val r=mapRect()
        val maxS=maxScroll(r[3])
        if(maxS>0&&mx>=px&&mx<=px+pw&&my>=py&&my<=py+ph){
            targetScrollY=(targetScrollY-vScroll.toFloat()*38f).coerceIn(0f,maxS.toFloat())
            return true
        }
        return super.mouseScrolled(mx,my,hScroll,vScroll)
    }

    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        if(super.mouseClicked(mx,my,button))return true
        if(button==0){
            val r=mapRect();val mapX=r[0];val mapY=r[1];val mapW=r[2];val mapH=r[3]
            val maxS=maxScroll(mapH)
            if(maxS>0&&mx>=mapX&&mx<mapX+mapW&&my>=mapY&&my<mapY+mapH){
                val trackX=mapX+mapW-10
                if(mx>=trackX){
                    draggingBar=true
                    val ratio=((my-(mapY+6)).toFloat()/(mapH-12).coerceAtLeast(1)).coerceIn(0f,1f)
                    val s=ratio*maxS
                    scrollY=s;targetScrollY=s
                }else{
                    draggingMap=true;dragStartMouseY=my;dragStartScroll=scrollY
                }
                return true
            }
        }
        return false
    }

    override fun mouseDragged(mx:Double,my:Double,button:Int,dx:Double,dy:Double):Boolean{
        if(adventureMenuOpen)return true
        val r=mapRect();val mapY=r[1];val mapH=r[3]
        val maxS=maxScroll(mapH).toFloat()
        if(draggingBar&&maxS>0f){
            val ratio=((my-(mapY+6)).toFloat()/(mapH-12).coerceAtLeast(1)).coerceIn(0f,1f)
            val s=ratio*maxS;scrollY=s;targetScrollY=s
            return true
        }
        if(draggingMap&&maxS>0f){
            val s=(dragStartScroll+(dragStartMouseY-my).toFloat()).coerceIn(0f,maxS)
            scrollY=s;targetScrollY=s
            return true
        }
        return super.mouseDragged(mx,my,button,dx,dy)
    }

    override fun mouseReleased(mx:Double,my:Double,button:Int):Boolean{
        draggingMap=false;draggingBar=false
        return super.mouseReleased(mx,my,button)
    }

    private fun drawPath(g:GuiGraphics,x1:Int,y1:Int,x2:Int,y2:Int,active:Boolean,chosen:Boolean,taken:Boolean,abandoned:Boolean,toBoss:Boolean,now:Long){
        val dist=hypot((x2-x1).toDouble(),(y2-y1).toDouble()).toInt().coerceAtLeast(1)
        val endMargin=if(toBoss)13 else 11
        val startMargin=11
        if(dist<=startMargin+endMargin+2)return
        val pulse=SpireAnim.pulse(if(chosen)900L else 1400L)
        val col=when{
            chosen->SpireAnim.lerpColor(0xFF5CEAD6.toInt(),0xFFB8FFF2.toInt(),pulse)
            active->SpireAnim.lerpColor(0xFFE0A438.toInt(),0xFFFFE585.toInt(),pulse)
            taken->0xCC9C7A49.toInt()
            abandoned->0x2C3B2B1E
            else->0x993B2B1E.toInt()
        }
        val shadow=when{
            active||chosen->0xDD140F0A.toInt()
            abandoned->0x00000000
            else->0x44F2DFC0
        }
        val flowShift=if(active||chosen)((now/(if(chosen)48L else 80L))%9L).toInt() else 0
        for(s in startMargin until (dist-endMargin)){
            if(!taken){
                val phase=((s-flowShift)%9+9)%9
                if(phase>=(if(active||chosen)5 else 4))continue
            }
            val t=s.toFloat()/dist
            val x=(x1+(x2-x1)*t).toInt()
            val y=(y1+(y2-y1)*t).toInt()
            if(shadow!=0)g.fill(x-1,y,x+2,y+2,shadow)
            if(active||chosen)g.fill(x-1,y-1,x+1,y+1,col) else g.fill(x,y,x+1,y+1,col)
        }
        // Directional arrowhead pointing into reachable next node
        if(active||chosen){
            val angle=atan2((y2-y1).toDouble(),(x2-x1).toDouble())
            val tipDist=(dist-endMargin-1).toDouble()
            val tipX=x1+cos(angle)*tipDist
            val tipY=y1+sin(angle)*tipDist
            for(side in listOf(-1,1)){
                val wingAngle=angle+Math.PI+side*0.52
                for(len in 1..5){
                    val wx=(tipX+cos(wingAngle)*len).roundToInt()
                    val wy=(tipY+sin(wingAngle)*len).roundToInt()
                    g.fill(wx-1,wy-1,wx+1,wy+1,col)
                }
            }
        }
    }

    private inner class MapNodeButton(
        bx:Int,by:Int,size:Int,
        private val nodeId:Int,
        private val node:JsonObject,
        private val reachable:Boolean,
        private val visited:Boolean,
        private val current:Boolean,
        private val votedByMe:Boolean,
        private val leading:Boolean,
        clickable:Boolean,
        onClick:()->Unit
    ):Button(bx,by,size,size,Component.empty(),OnPress{onClick()},DEFAULT_NARRATION){
        init{active=clickable}
        private var nodeFrameMs=SpireAnim.now()
        private fun inViewport(mx:Double,my:Double):Boolean{
            val r=mapRect()
            return mx>=r[0]&&mx<r[0]+r[2]&&my>=r[1]&&my<r[1]+r[3]
        }
        override fun isMouseOver(mx:Double,my:Double)=inViewport(mx,my)&&super.isMouseOver(mx,my)
        override fun clicked(mx:Double,my:Double)=inViewport(mx,my)&&super.clicked(mx,my)
        override fun mouseClicked(mx:Double,my:Double,button:Int)=inViewport(mx,my)&&super.mouseClicked(mx,my,button)
        override fun renderWidget(g:GuiGraphics,mx:Int,my:Int,delta:Float){
            val rClip=mapRect()
            g.enableScissor(rClip[0],rClip[1],rClip[0]+rClip[2],rClip[1]+rClip[3])
            val now=SpireAnim.now()
            val dt=((now-nodeFrameMs).coerceIn(0L,80L))/1000f
            nodeFrameMs=now
            val hot=inViewport(mx.toDouble(),my.toDouble())&&isHoveredOrFocused
            val curScale=hoverScale[nodeId]?:1f
            val nextScale=SpireAnim.approach(curScale,if(hot)1.20f else 1f,dt,16f)
            hoverScale[nodeId]=nextScale

            val cx=x+width/2;val cy=y+height/2
            val baseR=width/2-1
            val r=(baseR*nextScale).roundToInt().coerceAtLeast(baseR)
            val pulse=SpireAnim.pulse(1500L,(nodeId*210).toLong())

            if(current){
                val ar=r+3+(pulse*2f).roundToInt()
                SpireAnim.drawRing(g,cx,cy,ar,SpireAnim.withAlpha(0x5CEAD6,0.32f+pulse*0.42f))
            }else if(reachable){
                val beaconCol=if(votedByMe)0x72FFE5 else if(leading)0xFFE57F else 0xE8B448
                val ar=r+2+(pulse*2.2f).roundToInt()
                SpireAnim.drawRing(g,cx,cy,ar,SpireAnim.withAlpha(beaconCol,0.22f+(1f-pulse)*0.40f))
            }

            fillCircle(g,cx+1,cy+2,r+1,0x66000000)
            val ringCol=when{
                current->SpireAnim.lerpColor(0xFF47C7B5.toInt(),0xFF8CFFF0.toInt(),pulse)
                votedByMe->0xFF72FFE5.toInt()
                leading&&reachable->SpireAnim.lerpColor(0xFFE6BA45.toInt(),0xFFFFF0A3.toInt(),pulse)
                reachable->SpireAnim.lerpColor(0xFFD9A338.toInt(),0xFFFFE07D.toInt(),maxOf(if(hot)1f else 0f,pulse*0.65f))
                hot->0xFFD9C7A3.toInt()
                visited->0xFF6B5D4E.toInt()
                else->0xFF33261A.toInt()
            }
            val fillCol=when{
                current->0xF2173936.toInt()
                votedByMe->0xF21C3F3B.toInt()
                reachable->if(hot)0xF544321C.toInt() else 0xEE282017.toInt()
                visited->0xB81D1814.toInt()
                else->0xE2241D16.toInt()
            }
            fillCircle(g,cx,cy,r+1,0xEE0F0C09.toInt())
            fillCircle(g,cx,cy,r,ringCol)
            fillCircle(g,cx,cy,(r-2).coerceAtLeast(3),fillCol)

            val icon=node.text("icon")
            val rawCol=(node.number("color",0xE8DEC4) and 0xFFFFFF) or 0xFF000000.toInt()
            val iconCol=if(visited&&!current)0xFF8C8276.toInt() else rawCol
            val iw=font.width(icon)
            g.drawString(font,icon,cx-iw/2,cy-4,iconCol,true)

            val votes=node.number("votes",0)
            if(votes>0){
                val popAge=now-(votePopTime[nodeId]?:0L)
                val popY=if(popAge in 0L..320L)(-sin(popAge/320.0*Math.PI)*3.5).roundToInt() else 0
                val badge="$votes"
                val bw=font.width(badge)+4
                val bx=x+width-bw+3;val by=y-3+popY
                g.fill(bx-1,by-1,bx+bw+1,by+9,0xFF0A0A0C.toInt())
                g.fill(bx,by,bx+bw,by+8,if(votedByMe)0xFF2E8B7D.toInt() else 0xFFB8862D.toInt())
                g.drawString(font,badge,bx+2,by,0xFFFFFF,false)
            }
            if(current){
                val bob=(sin(now/240.0)*1.5).roundToInt()
                g.drawString(font,"▶",x-9+bob,cy-4,0xFF5CEAD6.toInt(),true)
            }
            g.disableScissor()
        }
        private fun fillCircle(g:GuiGraphics,cx:Int,cy:Int,r:Int,col:Int){
            for(dy in -r..r){
                val dx=sqrt((r*r-dy*dy).toDouble()).toInt()
                g.fill(cx-dx,cy+dy,cx+dx+1,cy+dy+1,col)
            }
        }
    }
}
