package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class SpireMenuScreen(data:JsonObject):SpirePane(data){
    private var search:EditBox?=null
    private var coopHost=false
    private var joinRoomId:String?=null
    private var roomPage=0
    private val cardHover=FloatArray(3)
    private var lastFrameMs=SpireAnim.now()
    private fun choices()=data.getAsJsonArray("entries").map{it.asJsonObject}
    private fun openRooms()=data.array("rooms")?.map{it.asJsonObject}.orEmpty()
    private fun currentRoom()=data.getAsJsonObject("room")
    private val isExtended get()=data.get("extended")?.asBoolean==true
    private val isRandomMode get()=data.get("randomMode")?.asBoolean==true&&!isExtended
    private fun launchStarter(entryKey:String){
        val rid=joinRoomId
        when{
            rid!=null->SpireClient.send("join"){addProperty("roomId",rid);addProperty("entry",entryKey)}
            coopHost->SpireClient.send("host"){addProperty("entry",entryKey)}
            else->SpireClient.send("start"){addProperty("entry",entryKey)}
        }
    }
    private fun launchRandom(){
        val rid=joinRoomId
        when{
            rid!=null->SpireClient.send("random_join"){addProperty("roomId",rid)}
            coopHost->SpireClient.send("random_host")
            else->SpireClient.send("random_start")
        }
    }
    override fun init(){
        val room=currentRoom();val rooms=openRooms()
        if(room!=null){coopHost=false;joinRoomId=null}
        if(isRandomMode){
            search=null
            button(px+12,py+44,120,18,"다시 뽑기",room==null){SpireClient.send("reroll")}
            button(px+138,py+44,132,18,"완전 랜덤 시작",room==null){launchRandom()}.primary=true
            button(px+pw-126,py+44,114,18,"확장 (전체 선택)"){page(0,true)}
            val cards=choices().take(3)
            val cw=(pw-36)/3;val ch=(ph-150).coerceIn(72,118)
            cards.forEachIndexed{i,c->
                val cx=px+12+i*(cw+6);val label=SpireWidgets.name(c);val supported=c.text("reason").isBlank()&&room==null
                button(cx,py+72+ch-22,cw,20,short(label,cw-10),supported){launchStarter(c.text("key"))}
                    .tooltip=Tooltip.create(Component.literal(if(room!=null)"협동 대기 중에는 대표를 변경할 수 없습니다." else "특성: "+Component.translatable("cobblemon.ability.${c.text("ability")}").string+"\n파트너 2마리는 타입이 겹치지 않게 자동 편성됩니다."))
            }
        }else{
            val box=EditBox(font,px+12,py+44,pw-204,18,Component.literal("대표 포켓몬 검색")).also{it.value=data.text("query");addRenderableWidget(it)}
            search=box
            button(px+pw-186,py+44,54,18,"검색"){page(0)}
            button(px+pw-126,py+44,114,18,"확장 켜짐 (3종 랜덤)"){page(0,true)}
            val w=(pw-30)/2;val row=((ph-136)/4).coerceAtMost(42)
            choices().forEachIndexed{i,c->
                val label=SpireWidgets.name(c);val supported=c.text("reason").isBlank()&&room==null
                button(px+12+(i%2)*(w+6),py+70+(i/2)*row,w,row-4,short(label,w-12),supported){launchStarter(c.text("key"))}
                    .tooltip=Tooltip.create(Component.literal(if(room!=null)"협동 대기 중에는 대표를 변경할 수 없습니다." else if(c.text("reason").isBlank())"특성: "+Component.translatable("cobblemon.ability.${c.text("ability")}").string+"\n파트너 2마리는 모든 타입이 겹치지 않게 자동 편성됩니다."+(if(c.get("design")?.asBoolean!=true)"\n추가 등록 종: 기준 능력치 고정 · 균형 미검증"else "") else c.text("reason")))
            }
        }
        val lobbyY=py+ph-52
        if(room!=null){
            val isHost=room.get("isHost")?.asBoolean==true
            button(px+12,lobbyY,120,18,if(isHost)"모험 시작 (${room.number("count")}/4)" else "방장 시작 대기",isHost){SpireClient.send("launch")}.primary=isHost
            button(px+138,lobbyY,78,18,"방 나가기"){SpireClient.send("quit")}
            button(px+222,lobbyY,58,18,"새로고침"){SpireClient.send("open")}
        }else{
            if(joinRoomId!=null&&rooms.none{it.text("id")==joinRoomId})joinRoomId=null
            button(px+12,lobbyY,98,18,if(coopHost)"협동 방 열기 ✓" else "협동 방 열기"){coopHost=!coopHost;if(coopHost)joinRoomId=null;rebuildWidgets()}
            button(px+114,lobbyY,52,18,"새로고침"){page(data.number("page"))}
            if(rooms.isNotEmpty()){
                val maxRoomPages=((rooms.size-1)/2).coerceAtLeast(0)
                roomPage=roomPage.coerceIn(0,maxRoomPages)
                val navW=if(maxRoomPages>0)18 else 0
                val listLeft=px+172+(if(navW>0)navW+2 else 0)
                val availW=(px+pw-12-(if(navW>0)navW+2 else 0))-listLeft
                if(maxRoomPages>0){
                    button(px+172,lobbyY,navW,18,"<",roomPage>0){roomPage--;rebuildWidgets()}
                    button(px+pw-12-navW,lobbyY,navW,18,">",roomPage<maxRoomPages){roomPage++;rebuildWidgets()}
                }
                val visible=rooms.drop(roomPage*2).take(2)
                val slotGap=4
                val rw=((availW-(visible.size-1)*slotGap)/visible.size).coerceAtLeast(70)
                visible.forEachIndexed{idx,rm->
                    val rid=rm.text("id");val active=joinRoomId==rid
                    val members=rm.array("members")?.joinToString(", "){it.asString}.orEmpty()
                    val label=short((if(active)"✓ " else "참가 · ")+"${rm.text("hostName")} (${rm.number("count")}/4)",rw-8)
                    button(listLeft+idx*(rw+slotGap),lobbyY,rw,18,label){
                        joinRoomId=if(active)null else rid;if(joinRoomId!=null)coopHost=false;rebuildWidgets()
                    }.tooltip=Tooltip.create(Component.literal("방장: ${rm.text("hostName")} (${rm.number("count")}/4)\n참가자: ${members.ifBlank{"없음"}}\n선택 후 위 대표 포켓몬을 고르면 입장합니다."))
                }
            }
        }
        if(!isRandomMode){
            button(px+12,py+ph-28,56,20,"이전",data.number("page")>0){page(data.number("page")-1)}
            button(px+73,py+ph-28,56,20,"다음",(data.number("page")+1)*8<data.number("total")){page(data.number("page")+1)}
        }
        button(px+pw-162,py+ph-28,72,20,"전체 도감"){SpireClient.send("catalog")}
        button(px+pw-84,py+ph-28,72,20,"닫기"){onClose()}
    }
    private fun page(p:Int,toggle:Boolean=false){SpireClient.send("menu"){addProperty("query",search?.value.orEmpty());addProperty("page",p);addProperty("extended",isExtended xor toggle)}}
    override fun mouseClicked(mx:Double,my:Double,button:Int):Boolean{
        if(button==0&&isRandomMode&&currentRoom()==null){
            val cards=choices().take(3)
            val cw=(pw-36)/3;val ch=(ph-150).coerceIn(72,118)
            cards.forEachIndexed{i,c->
                val cx=px+12+i*(cw+6);val cy=py+72
                if(mx>=cx&&mx<=cx+cw&&my>=cy&&my<=cy+ch-24&&c.text("reason").isBlank()){
                    SpireMusic.cardHover()
                    launchStarter(c.text("key"))
                    return true
                }
            }
        }
        return super.mouseClicked(mx,my,button)
    }
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        val now=SpireAnim.now()
        val dt=((now-lastFrameMs).coerceIn(0L,80L))/1000f
        lastFrameMs=now
        text(g,"CobbleSpire · 협동 / 솔로 시험 전투",px+12,py+12,0xE7C98F)
        val room=currentRoom()
        val subtitle=when{
            room!=null->"협동 대기실${SpireAnim.loadingDots()} (${room.number("count")}/4): "+room.array("members")?.joinToString(", "){it.asString}.orEmpty()
            joinRoomId!=null->"참가할 대표 포켓몬을 선택하세요 (랜덤 3종 / 완전 랜덤 / 확장)"
            coopHost->"협동 방을 열 대표 포켓몬을 선택하세요 (랜덤 3종 / 완전 랜덤 / 확장)"
            else->"랜덤 3종 중 선택 또는 완전 랜덤 시작 · 전체 선택은 [확장]"
        }
        text(g,short(subtitle,pw-24),px+12,py+29,0x9FC5CB)
        if(isRandomMode){
            val cards=choices().take(3)
            val cw=(pw-36)/3;val ch=(ph-150).coerceIn(72,118)
            val pulse=SpireAnim.pulse(1800L)
            cards.forEachIndexed{i,c->
                val cx=px+12+i*(cw+6);val baseCy=py+72
                val over=room==null&&mx>=cx&&mx<=cx+cw&&my>=baseCy&&my<=baseCy+ch-24
                cardHover[i]=SpireAnim.approach(cardHover[i],if(over)1f else 0f,dt,15f)
                val cy=baseCy-(cardHover[i]*4f).roundToInt()
                val edge=SpireAnim.lerpColor(SpireSkin.teal,SpireSkin.gold,cardHover[i])
                SpireSkin.panel(g,cx,cy,cw,ch-24,edge)
                if(cardHover[i]>0.05f){
                    SpireAnim.drawRing(g,cx+cw/2,cy+ch/2-24,(20+pulse*3f+cardHover[i]*4f).roundToInt(),SpireAnim.withAlpha(0xFFE07D,cardHover[i]*0.55f))
                }
                SpireWidgets.portrait(g,c,cx+cw/2,cy+ch/2-20,16f+cardHover[i]*1.8f)
                val types=c.array("types")?.joinToString(" / "){DamageLabels.type(it.asString)}.orEmpty()
                g.drawCenteredString(font,short(types,cw-10),cx+cw/2,cy+ch-36,0xBFD6D2)
            }
        }
    }
}
