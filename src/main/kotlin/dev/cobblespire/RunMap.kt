package dev.cobblespire

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID
import kotlin.random.Random

enum class SpireRelic(val id:String,val label:String,val desc:String){
    ENERGY_CORE("energy_core","태양의 원석","턴당 최대 에너지 +1"),
    ENERGY_CORE_2("energy_core_2","영원의 코어","턴당 최대 에너지 +1"),
    POWER_CHARM("power_charm","힘의 부적","아군 전체 공격·특공 +10%"),
    GUARD_CHARM("guard_charm","수호의 부적","아군 전체 방어·특방 +10%"),
    VITAL_SEED("vital_seed","생명의 씨앗","전투 승리 시 생존 파티 HP +15% 추가 회복");
    companion object{fun byId(id:String)=entries.firstOrNull{it.id==id}}
}

enum class SpireItem(val id:String,val label:String,val desc:String){
    POTION("potion","상처약","출전 포켓몬 HP 40% 회복"),
    ETHER("ether","에너지칩","이번 턴 에너지 +2"),
    X_ATTACK("x_attack","플러스파워","출전 포켓몬 공격·특공 +2랭크"),
    X_DEFEND("x_defend","디펜드업","출전 포켓몬 방어·특방 +2랭크");
    companion object{fun byId(id:String)=entries.firstOrNull{it.id==id}}
}

enum class NodeKind(val label:String,val icon:String,val color:Int,val desc:String){
    ORG_BATTLE("조직 전투","⚔",0xFFE57373.toInt(),"막 테마 조직과 전투합니다.\n보상: 기술 카드 · Lv+1 · (확률) 도구"),
    WILD_BATTLE("야생 조우","🐾",0xFF81C784.toInt(),"지역 야생 포켓몬과 전투합니다.\n단계별로 강해지며, 보상: 기술 카드 · Lv+1"),
    RECRUIT("영입 만남","◎",0xFF4FC3F7.toInt(),"야생 포켓몬을 만나 동료로 영입하거나 교체할 수 있습니다."),
    EVENT("미지 이벤트","?",0xFFBA68C8.toInt(),"보물볼 추첨·고대 제단·야생 영입 등 무작위 사건이 발생합니다."),
    REST("휴식처","♥",0xFFFFB74D.toInt(),"파티 전체 HP 50% 회복(기절 부활) 및 Lv+1 성장합니다."),
    BOSS("보스전","★",0xFFFFD54F.toInt(),"막의 보스와 결전을 벌입니다.\n승리 시 유물 보상 및 다음 막으로 이동!");
    companion object{fun parse(name:String)=entries.firstOrNull{it.name==name}?:ORG_BATTLE}
}

data class MapNode(val id:Int,val row:Int,val col:Int,val kind:NodeKind,val jitterX:Int=0,val jitterY:Int=0,val next:MutableList<Int> = mutableListOf(),var visited:Boolean=false)

class ActMap(val act:Int,val nodes:List<MapNode>){
    var currentId:Int=-1
    val votes=mutableMapOf<UUID,Int>()
    val rowCount:Int get()=(nodes.maxOfOrNull{it.row}?:-1)+1
    fun node(id:Int)=nodes.firstOrNull{it.id==id}
    fun reachableIds():Set<Int>{
        if(currentId<0)return nodes.filter{it.row==0}.map{it.id}.toSet()
        return node(currentId)?.next?.toSet().orEmpty()
    }
    fun voteCounts():Map<Int,Int> = votes.values.groupingBy{it}.eachCount()
    fun leadingNode(hostId:UUID,seed:Long):Int?{
        val reach=reachableIds()
        val counts=votes.values.filter{it in reach}.groupingBy{it}.eachCount()
        if(counts.isEmpty())return null
        val maxVotes=counts.values.max()
        val tied=counts.filterValues{it==maxVotes}.keys.toList()
        if(tied.size==1)return tied.first()
        val hostPick=votes[hostId]
        if(hostPick!=null&&hostPick in tied)return hostPick
        return tied[Random(seed+act*101L+currentId).nextInt(tied.size)]
    }
    fun json():JsonObject=JsonObject().apply{
        addProperty("act",act);addProperty("currentId",currentId);addProperty("rowCount",rowCount)
        val reach=reachableIds();val counts=voteCounts()
        add("nodes",JsonArray().also{arr->nodes.forEach{n->arr.add(JsonObject().apply{
            addProperty("id",n.id);addProperty("row",n.row);addProperty("col",n.col)
            addProperty("jx",n.jitterX);addProperty("jy",n.jitterY)
            addProperty("kind",n.kind.name);addProperty("label",n.kind.label);addProperty("icon",n.kind.icon);addProperty("color",n.kind.color);addProperty("desc",n.kind.desc)
            addProperty("visited",n.visited);addProperty("reachable",n.id in reach);addProperty("votes",counts[n.id]?:0)
            add("next",JsonArray().also{nx->n.next.forEach{nx.add(it)}})
        })}})
    }
    companion object{
        fun generate(act:Int,seed:Long):ActMap{
            val rng=Random(seed+act*9973L)
            val totalRows=10+rng.nextInt(6) // 10..15 floors per Act including Boss
            val midRestRow=totalRows/2
            val preBossRow=totalRows-2
            val rows=(0 until totalRows).map{r->
                when(r){
                    0->3
                    totalRows-1->1
                    preBossRow->2+rng.nextInt(2)
                    else->if(rng.nextInt(100)<35)4 else 3
                }
            }
            val nodes=mutableListOf<MapNode>()
            var id=0
            val grid=rows.mapIndexed{r,count->
                (0 until count).map{c->
                    val kind=when(r){
                        0->if(c==1)NodeKind.WILD_BATTLE else NodeKind.ORG_BATTLE
                        totalRows-1->NodeKind.BOSS
                        preBossRow->if(c==0)NodeKind.REST else if(c==1&&count>2)NodeKind.ORG_BATTLE else if(rng.nextBoolean())NodeKind.REST else NodeKind.RECRUIT
                        midRestRow->if(c==count/2)NodeKind.REST else listOf(NodeKind.EVENT,NodeKind.RECRUIT,NodeKind.ORG_BATTLE,NodeKind.WILD_BATTLE)[(c+rng.nextInt(4))%4]
                        in 1..2->listOf(NodeKind.WILD_BATTLE,NodeKind.EVENT,NodeKind.RECRUIT,NodeKind.ORG_BATTLE)[(c+r+rng.nextInt(4))%4]
                        else->{
                            val latePool=if(r>=midRestRow+2)listOf(NodeKind.ORG_BATTLE,NodeKind.WILD_BATTLE,NodeKind.EVENT,NodeKind.RECRUIT,NodeKind.REST)
                            else listOf(NodeKind.ORG_BATTLE,NodeKind.WILD_BATTLE,NodeKind.EVENT,NodeKind.RECRUIT)
                            latePool[(c+rng.nextInt(latePool.size))%latePool.size]
                        }
                    }
                    val jx=if(count==1)0 else rng.nextInt(-12,13)
                    val jy=if(count==1)0 else rng.nextInt(-2,3)
                    MapNode(id++,r,c,kind,jx,jy).also{nodes.add(it)}
                }
            }
            for(r in 0 until grid.lastIndex){
                val cur=grid[r];val nxt=grid[r+1]
                val incoming=mutableSetOf<Int>()
                fun crosses(srcIdx:Int,dstIdx:Int)=cur.withIndex().any{(i2,other)->
                    other.next.any{nid->
                        val j2=nxt.indexOfFirst{it.id==nid}
                        j2>=0&&((i2<srcIdx&&j2>dstIdx)||(i2>srcIdx&&j2<dstIdx))
                    }
                }
                cur.forEachIndexed{i,node->
                    val targetIdx=(i*nxt.size/cur.size).coerceIn(0,nxt.lastIndex)
                    node.next.add(nxt[targetIdx].id);incoming.add(nxt[targetIdx].id)
                    if(nxt.size>1&&rng.nextBoolean()){
                        val alt=(targetIdx+(if(rng.nextBoolean())1 else -1)).coerceIn(0,nxt.lastIndex)
                        if(nxt[alt].id !in node.next&&!crosses(i,alt)){
                            node.next.add(nxt[alt].id);incoming.add(nxt[alt].id)
                        }
                    }
                }
                nxt.forEachIndexed{j,t->
                    if(t.id !in incoming){
                        val preferred=(j*cur.size/nxt.size).coerceIn(0,cur.lastIndex)
                        val srcIdx=(0..cur.lastIndex).sortedBy{kotlin.math.abs(it-preferred)}.firstOrNull{!crosses(it,j)}?:preferred
                        cur[srcIdx].next.add(t.id)
                    }
                }
                cur.forEach{it.next.sort()}
            }
            return ActMap(act,nodes)
        }
    }
}
