package dev.cobblespire.client
import dev.cobblespire.*
import com.google.gson.JsonObject
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

class SpireMenuScreen(data:JsonObject):SpirePane(data){
    private lateinit var search:EditBox
    private var coopHost=false
    private var joinRoomId:String?=null
    private fun choices()=data.getAsJsonArray("entries").map{it.asJsonObject}
    private fun openRooms()=data.array("rooms")?.map{it.asJsonObject}.orEmpty()
    private fun currentRoom()=data.getAsJsonObject("room")
    override fun init(){
        val room=currentRoom();val rooms=openRooms()
        if(room!=null){coopHost=false;joinRoomId=null}
        search=EditBox(font,px+12,py+44,pw-204,18,Component.literal("대표 포켓몬 검색")).also{it.value=data.text("query");addRenderableWidget(it)}
        button(px+pw-186,py+44,54,18,"검색"){page(0)}
        button(px+pw-126,py+44,114,18,if(data.get("extended")?.asBoolean==true)"확장 후보 켜짐" else "기본 50종"){page(0,true)}
        val w=(pw-30)/2;val row=((ph-136)/4).coerceAtMost(42)
        choices().forEachIndexed{i,c->
            val label=SpireWidgets.name(c);val supported=c.text("reason").isBlank()&&room==null
            button(px+12+(i%2)*(w+6),py+70+(i/2)*row,w,row-4,short(label,w-12),supported){
                val rid=joinRoomId
                when{
                    rid!=null->SpireClient.send("join"){addProperty("roomId",rid);addProperty("entry",c.text("key"))}
                    coopHost->SpireClient.send("host"){addProperty("entry",c.text("key"))}
                    else->SpireClient.send("start"){addProperty("entry",c.text("key"))}
                }
            }.tooltip=Tooltip.create(Component.literal(if(room!=null)"협동 대기 중에는 대표를 변경할 수 없습니다." else if(c.text("reason").isBlank())"특성: "+Component.translatable("cobblemon.ability.${c.text("ability")}").string+"\n파트너 2마리는 모든 타입이 겹치지 않게 자동 편성됩니다."+(if(c.get("design")?.asBoolean!=true)"\n추가 등록 종: 기준 능력치 고정 · 균형 미검증"else "") else c.text("reason")))
        }
        val lobbyY=py+ph-52
        if(room!=null){
            val isHost=room.get("isHost")?.asBoolean==true
            button(px+12,lobbyY,120,18,if(isHost)"모험 시작 (${room.number("count")}/4)" else "방장 시작 대기",isHost){SpireClient.send("launch")}.primary=isHost
            button(px+138,lobbyY,78,18,"방 나가기"){SpireClient.send("quit")}
        }else{
            button(px+12,lobbyY,108,18,if(coopHost)"협동 방 열기 ✓" else "협동 방 열기"){coopHost=!coopHost;if(coopHost)joinRoomId=null;rebuildWidgets()}
            val firstRoom=rooms.firstOrNull()
            if(firstRoom!=null){
                val rid=firstRoom.text("id");val active=joinRoomId==rid
                button(px+126,lobbyY,180,18,short((if(active)"참가 선택 ✓ · " else "방 참가 · ")+"${firstRoom.text("hostName")} (${firstRoom.number("count")}/4)",172)){
                    joinRoomId=if(active)null else rid;if(joinRoomId!=null)coopHost=false;rebuildWidgets()
                }
            }
        }
        button(px+12,py+ph-28,56,20,"이전",data.number("page")>0){page(data.number("page")-1)}
        button(px+73,py+ph-28,56,20,"다음",(data.number("page")+1)*8<data.number("total")){page(data.number("page")+1)}
        button(px+pw-162,py+ph-28,72,20,"전체 도감"){SpireClient.send("catalog")}
        button(px+pw-84,py+ph-28,72,20,"닫기"){onClose()}
    }
    private fun page(p:Int,toggle:Boolean=false){SpireClient.send("menu"){addProperty("query",search.value);addProperty("page",p);addProperty("extended",(data.get("extended")?.asBoolean==true) xor toggle)}}
    override fun content(g:GuiGraphics,mx:Int,my:Int,delta:Float){
        text(g,"CobbleSpire · 협동 / 솔로 시험 전투",px+12,py+12,0xE7C98F)
        val room=currentRoom()
        val subtitle=when{
            room!=null->"협동 대기실 (${room.number("count")}/4): "+room.array("members")?.joinToString(", "){it.asString}.orEmpty()
            joinRoomId!=null->"참가할 대표 포켓몬을 선택하세요 (최대 4인 협동)"
            coopHost->"협동 방을 열 대표 포켓몬을 선택하세요"
            else->"불꽃·물·풀 대표 선택 → 독립 모험 파티 3마리 (솔로/협동)"
        }
        text(g,short(subtitle,pw-24),px+12,py+29,0x9FC5CB)
    }
}
