package dev.cobblespire

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.cobblemon.mod.common.battles.BattleRegistry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.util.UUID
import java.security.SecureRandom
import kotlin.random.Random

/** Server-authoritative 1-4 player cooperative trial battle service. */
class TrialService(private val server:MinecraftServer,val catalogue:PokemonCatalogue){
    val bridge=PokemonBridge(catalogue)
    private data class Member(val player:UUID,val name:String,val party:MutableList<PokemonBridge.Actor>,var activeIndex:Int=0,var loot:VictoryLoot?=null,var damageDealt:Int=0,var maxEnergy:Int=3,val relics:MutableList<String> = mutableListOf(),var relicOffer:String?=null,val items:MutableList<String> = mutableListOf(SpireItem.POTION.id),var disconnected:Boolean=false)
    private data class Ping(val player:UUID,val playerName:String,val target:Int,val move:String,val minDamage:Int,val maxDamage:Int,val round:Int)
    private data class ActiveEvent(val kind:String,val title:String,val description:String,val lotteryKind:String="",val candidate:PokemonBridge.Actor?=null,val candidateEntry:PokemonCatalogue.Entry?=null,val choices:MutableMap<UUID,String> = mutableMapOf(),var resolved:Boolean=false,var winnerName:String="",var outcomeText:String="")
    private data class Session(val id:UUID,val seed:Long,val members:MutableList<Member>,var host:UUID,var started:Boolean=false,var points:Int=0,var battleNumber:Int=0){
        lateinit var battle:SpireBattle;lateinit var effects:NativeEffects;lateinit var ai:IntentRules
        var actors=emptyMap<String,PokemonBridge.Actor>();var profiles=emptyMap<String,JsonObject>();var choices=emptyMap<String,IntentRules.Choice>()
        val log=ArrayDeque<String>()
        val pings=mutableMapOf<UUID,Ping>()
        val evolution=EvolutionAccess()
        var encounter=""
        var act=1
        var tier="NORMAL"
        var battleEventId=""
        var battleEventLabel=""
        var battleEventDesc=""
        var phaseMode="MAP"
        var actMap=ActMap.generate(1,seed)
        var currentNodeKind=NodeKind.ORG_BATTLE
        var bossUids=emptySet<String>()
        var activeEvent:ActiveEvent?=null
        var mapMessage=""
        var logFailureReported=false
    }
    val arena=WorldArena(server)
    private val sessions=mutableMapOf<UUID,Session>()
    private val rooms=mutableMapOf<UUID,Session>()
    private val catalogueActors by lazy{catalogue.entries.filter{it.formName==it.species.standardForm.name}.sortedWith(compareBy<PokemonCatalogue.Entry>{Definitions.characters.none{d->Definitions.speciesId(d.text("species_id"))==it.id}}.thenBy{it.id})}
    private val orderedEncounters by lazy{
        Definitions.data.getAsJsonArray("encounters").map{it.asJsonObject}.sortedWith(compareBy({it.number("act",1)},{when(it.text("tier","NORMAL")){"NORMAL"->0;"ELITE"->1;else->2}},{it.text("encounter_id")}))
    }
    private val lastAction=mutableMapOf<UUID,Int>()
    private val lastPing=mutableMapOf<UUID,Int>()
    private val menuSeeds=mutableMapOf<UUID,Long>()
    private fun onlineMembers(s:Session)=s.members.filter{!it.disconnected}
    fun inBattle(id:UUID)=sessions[id]?.let{it.started&&it.battle.phase in setOf(SpireBattlePhase.PLAYER,SpireBattlePhase.ENEMY)}==true
    fun clear(){arena.close();sessions.clear();rooms.clear();lastAction.clear();lastPing.clear();menuSeeds.clear()}
    fun leave(p:ServerPlayer){lastAction.remove(p.uuid);lastPing.remove(p.uuid);menuSeeds.remove(p.uuid);removeMember(p)}
    fun onDisconnect(p:ServerPlayer){
        lastAction.remove(p.uuid);lastPing.remove(p.uuid);menuSeeds.remove(p.uuid)
        val s=sessions[p.uuid]
        val remainingOnline=s?.let{onlineMembers(it).filter{m->m.player!=p.uuid}}.orEmpty()
        if(s!=null&&s.started&&remainingOnline.isNotEmpty()){
            val m=s.members.first{it.player==p.uuid}
            m.disconnected=true
            arena.detachForOffline(p)
            if(s.host==p.uuid)s.host=remainingOnline.first().player
            s.log.addLast("${m.name} 연결 끊김 (재접속 대기)");while(s.log.size>12)s.log.removeFirst()
            when(s.phaseMode){
                "MAP"->{
                    if(s.actMap.votes.isNotEmpty()&&remainingOnline.all{it.player in s.actMap.votes}){
                        val chosen=s.actMap.leadingNode(s.host,s.seed)
                        if(chosen!=null){triggerNode(s,chosen);return}
                    }
                    broadcastMap(s)
                }
                "EVENT"->{
                    if(!tryResolveEvent(s))broadcastEvent(s)
                }
                else->{s.battle.setPlayerOnline(p.uuid,false);broadcastBattle(s,false,false)}
            }
            return
        }
        removeMember(p)
    }
    fun onReconnect(p:ServerPlayer):Boolean{
        val s=sessions[p.uuid]?:return false
        val m=s.members.firstOrNull{it.player==p.uuid}?:return false
        if(!s.started)return false
        m.disconnected=false
        val slot=s.members.indexOf(m).coerceAtLeast(0)
        arena.reenter(p,s.id,slot)
        s.battle.setPlayerOnline(p.uuid,true)
        s.log.addLast("${m.name} 재접속 복귀");while(s.log.size>12)s.log.removeFirst()
        sendState(p,s,true)
        when(s.phaseMode){
            "MAP"->broadcastMap(s)
            "EVENT"->broadcastEvent(s)
            else->broadcastBattle(s,false,false)
        }
        return true
    }
    fun writeAudit(){val f=FabricLoader.getInstance().gameDir.resolve("logs/cobblespire/move-compatibility.json");Files.createDirectories(f.parent);Files.writeString(f,SpireMod.gson.toJson(bridge.audit()))}
    fun handle(p:ServerPlayer,j:JsonObject){
        require(j.text("action").length<=32)
        val action=j.text("action")
        if(action=="ping"){
            require(server.tickCount-(lastPing[p.uuid]?:-999)>=2){"핑 전송 대기 중입니다."};lastPing[p.uuid]=server.tickCount
        }else if(action in setOf("move","switch","end","next","start","random_start","host","random_host","join","random_join","launch","claim","choose_node","confirm_map","event_choice","event_continue","use_item","evolve")){
            require(server.tickCount-(lastAction[p.uuid]?:-999)>=2){"이전 요청 처리 후 다시 시도하세요."};lastAction[p.uuid]=server.tickCount
        }
        when(action){
            "open"->sessions[p.uuid]?.let{if(it.started)sendState(p,it,true)else menu(p,j)}?:menu(p,j)
            "catalog"->SpireMod.send(p,catalogue.page(j.text("query").take(64),j.number("page")))
            "menu","reroll"->menu(p,j)
            "start"->start(p,j.text("entry"))
            "random_start"->start(p,randomStarterKey())
            "host"->hostRoom(p,j.text("entry"))
            "random_host"->hostRoom(p,randomStarterKey())
            "join"->joinRoom(p,j.text("roomId"),j.text("entry"))
            "random_join"->joinRoom(p,j.text("roomId"),randomStarterKey())
            "launch"->launchRoom(p)
            "choose_node"->{
                val s=requireNotNull(sessions[p.uuid]?.takeIf{it.started&&it.phaseMode=="MAP"}){"현재 경로를 선택할 수 있는 상태가 아닙니다."}
                val nodeId=j.number("nodeId",-1)
                require(nodeId in s.actMap.reachableIds()){"현재 위치에서 이동할 수 없는 경로입니다."}
                s.actMap.votes[p.uuid]=nodeId
                val online=onlineMembers(s)
                val everyoneVoted=online.all{it.player in s.actMap.votes}
                if(online.size<=1||everyoneVoted){
                    val chosen=s.actMap.leadingNode(s.host,s.seed)?:nodeId
                    triggerNode(s,chosen)
                }else{
                    val votedOnline=online.count{it.player in s.actMap.votes}
                    s.mapMessage="${p.scoreboardName}님이 경로에 투표했습니다 ($votedOnline/${online.size})"
                    broadcastMap(s)
                }
            }
            "confirm_map"->{
                val s=requireNotNull(sessions[p.uuid]?.takeIf{it.started&&it.phaseMode=="MAP"}){"현재 경로를 선택할 수 있는 상태가 아닙니다."}
                require(s.host==p.uuid){"방장만 투표를 조기 확정할 수 있습니다."}
                val chosen=requireNotNull(s.actMap.leadingNode(s.host,s.seed)){"먼저 이동할 노드에 투표하세요."}
                triggerNode(s,chosen)
            }
            "evolve"->{
                val s=requireNotNull(sessions[p.uuid]?.takeIf{it.started&&(it.phaseMode in setOf("MAP","EVENT")||it.battle.phase==SpireBattlePhase.VICTORY)}){"전투 중에는 진화할 수 없습니다."}
                val member=s.members.first{it.player==p.uuid}
                val idx=j.number("partyIndex",-1)
                val actor=requireNotNull(member.party.getOrNull(idx)){"올바른 파티 슬롯을 선택하세요."}
                val legal=bridge.legalEvolutions(actor)
                require(legal.isNotEmpty()){"${actor.name}은(는) 더 이상 진화할 수 없습니다."}
                val targetKey=j.text("targetKey")
                val target=if(targetKey.isNotBlank())requireNotNull(legal.firstOrNull{it.key==targetKey}){"허용된 진화 대상이 아닙니다."} else legal.first()
                val curEntry=catalogueActors.firstOrNull{it.id==actor.body.species}
                val reqLv=curEntry?.let{bridge.requiredEvoLevel(bridge.stageOf(it))}?:4
                val oldName=actor.name
                var learnedMove:String?=null
                if(actor.level>=reqLv){
                    learnedMove=bridge.evolve(actor,target,s.seed+s.battleNumber+idx)
                }else{
                    val credit=s.evolution.available().firstOrNull()
                        ?:error("진화 조건 미달: Lv.${reqLv} 도달 또는 보스 진화권이 필요합니다.")
                    s.evolution.evolve(credit.id,target.id,legal.map{it.id}.toSet()){
                        learnedMove=bridge.evolve(actor,target,s.seed+s.battleNumber+idx)
                    }
                }
                val bonusSuffix=if(learnedMove!=null)" · 새 기술 카드 획득!" else ""
                s.mapMessage="${oldName} → ${actor.name} 진화 완료!$bonusSuffix"
                when(s.phaseMode){
                    "MAP"->broadcastMap(s)
                    "EVENT"->broadcastEvent(s)
                    else->broadcastBattle(s,false,false)
                }
            }
            "event_choice"->{
                val s=requireNotNull(sessions[p.uuid]?.takeIf{it.started&&it.phaseMode=="EVENT"}){"진행 중인 이벤트가 없습니다."}
                handleEventChoice(p,s,j.text("choice"))
            }
            "event_continue"->{
                val s=requireNotNull(sessions[p.uuid]?.takeIf{it.started&&it.phaseMode=="EVENT"}){"진행 중인 이벤트가 없습니다."}
                require(s.activeEvent?.resolved==true){"모든 팀원의 선택이 끝난 뒤 이동할 수 있습니다."}
                require(s.host==p.uuid){"방장만 다음으로 진행할 수 있습니다."}
                s.activeEvent=null;s.phaseMode="MAP";broadcastMap(s)
            }
            "use_item"->{
                val s=requireSession(p,j);val b=s.battle;val member=s.members.first{it.player==p.uuid}
                val player=b.players.first{it.id==p.uuid}
                require(b.phase==SpireBattlePhase.PLAYER&&!player.ended&&!player.forcedSwitch){"지금은 도구를 사용할 수 없습니다."}
                val idx=j.number("index",-1)
                require(idx in member.items.indices){"유효한 도구가 아닙니다."}
                val item=requireNotNull(SpireItem.byId(member.items.removeAt(idx)))
                when(item){
                    SpireItem.POTION->player.active.heal((player.active.maxHp*0.40).toInt().coerceAtLeast(10))
                    SpireItem.ETHER->player.energy=(player.energy+2).coerceAtMost(9)
                    SpireItem.X_ATTACK->{
                        player.active.stages["atk"]=((player.active.stages["atk"]?:0)+2).coerceIn(-6,6)
                        player.active.stages["spa"]=((player.active.stages["spa"]?:0)+2).coerceIn(-6,6)
                    }
                    SpireItem.X_DEFEND->{
                        player.active.stages["def"]=((player.active.stages["def"]?:0)+2).coerceIn(-6,6)
                        player.active.stages["spd"]=((player.active.stages["spd"]?:0)+2).coerceIn(-6,6)
                    }
                }
                s.log.addLast("${member.name} · ${item.label} 사용!");while(s.log.size>12)s.log.removeFirst()
                broadcastBattle(s,false,false)
            }
            "ping"->{
                val s=requireSession(p,j);val b=s.battle;val target=j.number("target",-1)
                require(target in b.enemies.indices){"유효한 대상이 아닙니다."}
                val minD=j.number("minDamage",0).coerceAtLeast(0);val maxD=j.number("maxDamage",minD).coerceAtLeast(minD)
                s.pings[p.uuid]=Ping(p.uuid,p.scoreboardName,target,j.text("move").take(48),minD,maxD,b.round)
                broadcastBattle(s,false,false)
            }
            "quit"->{val s=sessions[p.uuid];if(s!=null&&s.started&&s.phaseMode=="BATTLE")requireSession(p,j);removeMember(p);menu(p,JsonObject())}
            "move","switch","end","next","claim"->{
                val s=requireSession(p,j);val b=s.battle;val member=s.members.first{it.player==p.uuid}
                when(action){
                    "move"->b.useMove(p.uuid,j.number("handIndex",-1),j.get("instance")?.asLong?:-1,j.number("target",-1),j.text("request"),j.number("revision",-1))
                    "switch"->{b.switch(p.uuid,j.number("index",-1),j.text("request"),j.number("revision",-1));member.activeIndex=b.players.first{it.id==p.uuid}.activeIndex}
                    "end"->b.endTurn(p.uuid,j.text("request"),j.number("revision",-1))
                    "claim"->{
                        require(b.phase==SpireBattlePhase.VICTORY&&j.number("revision",-1)==b.revision){"승리 정산 화면을 다시 열어주세요."}
                        val loot=requireNotNull(member.loot);val index=j.number("choice",-2)
                        if(index==99&&member.relicOffer!=null){
                            loot.claim(-1)
                            val relic=SpireRelic.byId(member.relicOffer!!)
                            if(relic!=null){
                                member.relics.add(relic.id)
                                when(relic){
                                    SpireRelic.ENERGY_CORE,SpireRelic.ENERGY_CORE_2->member.maxEnergy=(member.maxEnergy+1).coerceAtMost(5)
                                    SpireRelic.POWER_CHARM->member.party.forEach{a->a.stats["atk"]=(a.stats.getValue("atk")*1.10).toInt();a.stats["spa"]=(a.stats.getValue("spa")*1.10).toInt()}
                                    SpireRelic.GUARD_CHARM->member.party.forEach{a->a.stats["def"]=(a.stats.getValue("def")*1.10).toInt();a.stats["spd"]=(a.stats.getValue("spd")*1.10).toInt()}
                                    SpireRelic.VITAL_SEED->{}
                                }
                            }
                            member.relicOffer=null
                        }else{
                            loot.validate(index)
                            val offer=loot.offers.getOrNull(index)
                            val replacement=offer?.let{BattleDeck(member.party[it.owner].body.deck!!.original+it.card,Random(s.seed+s.battleNumber*37L+member.player.hashCode()))}
                            loot.claim(index);if(offer!=null)member.party[offer.owner].body.deck=replacement
                        }
                    }
                    "next"->{
                        require(onlineMembers(s).all{it.loot?.settled==true}){"모든 팀원이 전리품을 선택하거나 건너뛴 뒤 진행하세요."}
                        require(j.number("revision",-1)==b.revision&&b.phase==SpireBattlePhase.VICTORY)
                        require(s.members.none{m->server.playerList.getPlayer(m.player)?.let{BattleRegistry.getBattleByParticipatingPlayer(it)!=null}==true}){"일반 전투가 끝난 뒤 진행하세요."}
                        if(s.actMap.node(s.actMap.currentId)?.kind==NodeKind.BOSS){
                            s.act++
                            s.actMap=ActMap.generate(s.act,s.seed)
                            s.mapMessage="${s.act}막으로 진입했습니다!"
                        }else{
                            s.mapMessage="전투 승리! 다음 이동 경로를 선택하세요."
                        }
                        s.phaseMode="MAP"
                        broadcastMap(s)
                        return
                    }
                }
                broadcastBattle(s,false,false)
            }
            else->error("지원하지 않는 요청입니다.")
        }
    }
    private fun requireSession(p:ServerPlayer,j:JsonObject):Session{
        val s=requireNotNull(sessions[p.uuid]?.takeIf{it.started}){"진행 중인 시험 전투가 없습니다."};require(j.number("battleNumber",-1)==s.battleNumber){"이전 전투의 요청입니다."};require(j.text("session")==s.id.toString()){"이전 모험의 요청입니다."};return s
    }
    private fun removeMember(p:ServerPlayer){
        val s=sessions.remove(p.uuid)?:return
        arena.leave(p)
        s.pings.remove(p.uuid)
        s.members.removeAll{it.player==p.uuid}
        val online=onlineMembers(s)
        if(online.isEmpty()){s.members.forEach{sessions.remove(it.player)};rooms.remove(s.id);return}
        if(s.host==p.uuid)s.host=online.first().player
        if(s.started){
            when(s.phaseMode){
                "MAP"->broadcastMap(s)
                "EVENT"->broadcastEvent(s)
                else->{s.battle.removePlayer(p.uuid);broadcastBattle(s,false,false)}
            }
        }else broadcastMenu(s)
    }
    private fun sendState(p:ServerPlayer,s:Session,open:Boolean){
        val m=s.members.firstOrNull{it.player==p.uuid}?:return
        when(s.phaseMode){
            "MAP"->SpireMod.send(p,buildMapJson(s,m))
            "EVENT"->SpireMod.send(p,buildEventJson(s,m))
            else->send(p,s,open,false)
        }
    }
    private fun broadcastMap(s:Session){s.members.forEach{m->server.playerList.getPlayer(m.player)?.let{SpireMod.send(it,buildMapJson(s,m))}}}
    private fun broadcastEvent(s:Session){s.members.forEach{m->server.playerList.getPlayer(m.player)?.let{SpireMod.send(it,buildEventJson(s,m))}}}
    private fun isBaseStage(e:PokemonCatalogue.Entry)=e.species.preEvolution==null&&e.species.standardForm.preEvolution==null
    private fun supportedStarters()=catalogueActors.filter{e->isBaseStage(e)&&e.species.standardForm.types.any{it.name.lowercase() in setOf("fire","water","grass")}&&bridge.reason(e)==null}
    private fun randomStarterKey():String=supportedStarters().random(Random(SecureRandom().nextLong())).key
    private fun menu(p:ServerPlayer,j:JsonObject){
        val query=j.text("query").take(64);val extended=j.get("extended")?.asBoolean==true
        if(j.text("action")=="reroll")menuSeeds[p.uuid]=SecureRandom().nextLong()
        val randomMode=!extended&&query.isBlank()
        val filtered=if(randomMode){
            val seed=menuSeeds.getOrPut(p.uuid){SecureRandom().nextLong()}
            supportedStarters().shuffled(Random(seed)).take(3)
        }else{
            catalogueActors.filter{e->val design=Definitions.characters.firstOrNull{Definitions.speciesId(it.text("species_id"))==e.id};
                val starter=isBaseStage(e)&&e.species.standardForm.types.any{it.name.lowercase() in setOf("fire","water","grass")}
                starter&&(extended||design!=null)&&(query.isBlank()||e.id.contains(query,true)||(design?.text("name_ko")?:e.species.translatedName.string).contains(query,true))}
        }
        val page=if(randomMode)0 else j.number("page").coerceIn(0,((filtered.size-1)/8).coerceAtLeast(0))
        SpireMod.send(p,JsonObject().apply{addProperty("kind","menu");addProperty("query",query);addProperty("extended",extended);addProperty("randomMode",randomMode);addProperty("page",page);addProperty("total",filtered.size)
            add("rooms",JsonArray().also{arr->rooms.values.filter{!it.started&&it.members.size<4}.forEach{r->arr.add(JsonObject().apply{
                addProperty("id",r.id.toString());addProperty("hostName",r.members.firstOrNull{it.player==r.host}?.name.orEmpty());addProperty("count",r.members.size)
                add("members",JsonArray().also{m->r.members.forEach{m.add(it.name)}})
            })}})
            sessions[p.uuid]?.takeIf{!it.started}?.let{r->add("room",JsonObject().apply{
                addProperty("id",r.id.toString());addProperty("isHost",r.host==p.uuid);addProperty("count",r.members.size)
                add("members",JsonArray().also{m->r.members.forEach{m.add(it.name)}})
            })}
            add("entries",JsonArray().also{a->filtered.drop(page*8).take(8).forEach{e->a.add(e.json().apply{addProperty("reason",bridge.reason(e).orEmpty());addProperty("ability",bridge.ability(e).orEmpty());addProperty("design",Definitions.characters.any{Definitions.speciesId(it.text("species_id"))==e.id});add("types",JsonArray().also{t->e.species.standardForm.types.forEach{t.add(it.name.lowercase())}})})}})
        })
    }
    private fun broadcastMenu(s:Session){s.members.forEach{m->server.playerList.getPlayer(m.player)?.let{menu(it,JsonObject())}}}
    private fun broadcastBattle(s:Session,open:Boolean,fresh:Boolean){s.members.forEach{m->server.playerList.getPlayer(m.player)?.let{send(it,s,open,fresh)}}}
    private fun pickParty(p:ServerPlayer,key:String,slot:Int,seed:Long):MutableList<PokemonBridge.Actor>{
        require(BattleRegistry.getBattleByParticipatingPlayer(p)==null){"일반 포켓몬 전투가 끝난 뒤 시작하세요."}
        val otherDraft=runCatching{val c=Class.forName("dev.cobbleleague.LeagueMod").getField("Companion").get(null);val service=c.javaClass.getMethod("getService").invoke(c);service?.javaClass?.getMethod("draftParty",UUID::class.java)?.invoke(service,p.uuid)!=null}.getOrDefault(false)
        require(!otherDraft){"리그 도전을 끝낸 뒤 시작하세요."}
        val entry=requireNotNull(catalogueActors.firstOrNull{it.key==key})
        require(isBaseStage(entry)){"초기 진화 단계의 포켓몬만 시작 포켓몬으로 선택할 수 있습니다."}
        require(entry.species.standardForm.types.any{it.name.lowercase() in setOf("fire","water","grass")}){"불꽃·물·풀 대표를 선택하세요."}
        require(bridge.reason(entry)==null){bridge.reason(entry).orEmpty()}
        val rng=Random(seed);val selected=mutableListOf(entry)
        for(e in catalogueActors.shuffled(rng).sortedBy{v->Definitions.characters.none{Definitions.speciesId(it.text("species_id"))==v.id}}){
            if(selected.size==3)break
            if(!isBaseStage(e)||e.id in selected.map{it.id}||bridge.reason(e)!=null)continue
            val types=(selected+e).map{it.species.standardForm.types.map{t->t.name.lowercase()}.toSet()}
            if(SpirePartyRules.disjoint(types))selected.add(e)
        }
        require(selected.size==3){"타입이 겹치지 않는 지원 파트너가 부족합니다."}
        return selected.mapIndexed{i,e->bridge.create(e,"player:${slot*3+i}",seed+i)}.toMutableList()
    }
    private fun start(p:ServerPlayer,key:String){
        require(p.uuid !in sessions){"기존 시험 전투를 먼저 종료하세요."}
        val seed=SecureRandom().nextLong()
        val party=pickParty(p,key,0,seed)
        val s=Session(UUID.randomUUID(),seed,mutableListOf(Member(p.uuid,p.scoreboardName,party)),p.uuid,started=true)
        s.phaseMode="MAP";s.mapMessage="첫 번째 이동 경로를 선택하세요."
        arena.enter(p,s.id,0);sessions[p.uuid]=s;rooms[s.id]=s
        try{SpireMod.send(p,buildMapJson(s,s.members.first()))}catch(e:Exception){leave(p);throw e}
    }
    private fun hostRoom(p:ServerPlayer,key:String){
        require(p.uuid !in sessions){"이미 참가 중인 모험 또는 대기 방이 있습니다."}
        val seed=SecureRandom().nextLong()
        val party=pickParty(p,key,0,seed)
        val s=Session(UUID.randomUUID(),seed,mutableListOf(Member(p.uuid,p.scoreboardName,party)),p.uuid,started=false)
        sessions[p.uuid]=s;rooms[s.id]=s;menu(p,JsonObject())
    }
    private fun joinRoom(p:ServerPlayer,roomId:String,key:String){
        require(p.uuid !in sessions){"이미 참가 중인 모험 또는 대기 방이 있습니다."}
        val id=runCatching{UUID.fromString(roomId)}.getOrNull()?:error("유효하지 않은 방 ID입니다.")
        val s=requireNotNull(rooms[id]){"존재하지 않는 협동 방입니다."}
        require(!s.started){"이미 시작된 전투입니다."}
        require(s.members.size<4){"협동 정원(4명)이 가득 찼습니다."}
        val slot=s.members.size
        val party=pickParty(p,key,slot,s.seed+slot*100L)
        s.members.add(Member(p.uuid,p.scoreboardName,party));sessions[p.uuid]=s
        broadcastMenu(s)
    }
    private fun launchRoom(p:ServerPlayer){
        val s=requireNotNull(sessions[p.uuid]){"대기 중인 협동 방이 없습니다."}
        require(!s.started){"이미 전투가 시작되었습니다."}
        require(s.host==p.uuid){"방장만 모험을 시작할 수 있습니다."}
        s.started=true;s.phaseMode="MAP";s.mapMessage="방장이 첫 번째 이동 경로를 선택하세요."
        s.members.forEachIndexed{slot,m->server.playerList.getPlayer(m.player)?.let{arena.enter(it,s.id,slot)}}
        try{broadcastMap(s)}catch(e:Exception){s.members.toList().forEach{m->server.playerList.getPlayer(m.player)?.let{leave(it)}};throw e}
    }
    private fun bst(e:PokemonCatalogue.Entry)=e.species.standardForm.baseStats.values.sum()
    private fun begin(s:Session,nodeKind:NodeKind=NodeKind.ORG_BATTLE){
        s.currentNodeKind=nodeKind
        s.members.forEach{it.loot=null;it.damageDealt=0};s.pings.clear();s.battleNumber++
        val totalRows=s.actMap.rowCount.coerceAtLeast(2)
        val row=(s.actMap.node(s.actMap.currentId)?.row?:0).coerceIn(0,totalRows-1)
        val progress=row.toFloat()/(totalRows-1)
        val tierStep=(s.act-1)*5+kotlin.math.round(progress*5f).toInt().coerceIn(0,5)
        val isEliteRow=progress>=0.60f&&row<totalRows-1
        val rng=Random(s.seed+s.battleNumber*577L)
        val evRoll=if(s.battleNumber<=1)3 else Random(s.seed+s.battleNumber*409L).nextInt(5)
        when(evRoll){
            0->{s.battleEventId="FOG";s.battleEventLabel="짙은 안개";s.battleEventDesc="시야 방해 · 적 의도 및 피해량 비공개"}
            1->{s.battleEventId="PRESSURE";s.battleEventLabel="위압감";s.battleEventDesc="전장 디버프 · 시작 시 내 포켓몬 방어 -1랭크"}
            2->{s.battleEventId="MIASMA";s.battleEventLabel="독기 서린 전장";s.battleEventDesc="전장 디버프 · 시작 시 내 포켓몬 특방 -1랭크"}
            else->{s.battleEventId="";s.battleEventLabel="";s.battleEventDesc=""}
        }
        val enemyActors:List<PokemonBridge.Actor>
        val profileMap:Map<String,JsonObject>
        val themeLabel:String
        s.bossUids=emptySet()
        if(nodeKind==NodeKind.WILD_BATTLE){
            s.encounter="wild_act${s.act}_r${row}_${s.battleNumber}";s.tier=if(isEliteRow)"ELITE" else "NORMAL"
            val count=if(tierStep<=1)1 else if(s.act>=2||rng.nextBoolean())2 else 1
            val tiered=catalogueActors.filter{e->
                if(bridge.reason(e)!=null)return@filter false
                val total=bst(e)
                when{
                    tierStep<=1->isBaseStage(e)&&total in 180..350
                    tierStep<=3->total in 310..445
                    s.act==2->total in 385..520
                    else->total>=460
                }
            }.ifEmpty{catalogueActors.filter{bridge.reason(it)==null}}
            val wildPool=tiered.shuffled(rng)
            val baseHp=(115+tierStep*32+s.members.size*30)/count.coerceAtLeast(1)*(if(count==2)1 else 1)+if(count==2)20 else 45
            val built=mutableListOf<Pair<PokemonBridge.Actor,JsonObject>>()
            for(i in 0 until count){
                val entry=wildPool[i%wildPool.size]
                val actor=bridge.create(entry,"enemy:$i",s.seed+s.battleNumber*100+i,enemyHp=baseHp).also{a->
                    val scale=0.85+tierStep*0.09
                    val floorAtk=55+tierStep*6;val floorDef=50+tierStep*6
                    for(k in listOf("atk","spa"))a.stats[k]=maxOf(floorAtk,(a.stats.getValue(k)*scale).toInt())
                    for(k in listOf("def","spd"))a.stats[k]=maxOf(floorDef,(a.stats.getValue(k)*scale).toInt())
                    a.level=1+tierStep
                }
                val cand=JsonArray().also{arr->actor.nativeMoves.forEach{mv->arr.add(JsonObject().apply{addProperty("move_id",mv.name);addProperty("weight",if(mv.power>0)2 else 1)})}}
                val rule=JsonObject().apply{
                    addProperty("priority",1);addProperty("cooldown_rounds",0)
                    add("condition",JsonObject().apply{addProperty("op","ALWAYS")})
                    add("choose",JsonObject().apply{addProperty("type","WEIGHTED");add("candidates",cand)})
                }
                val prof=JsonObject().apply{add("ai_rules",JsonArray().apply{add(rule)})}
                built.add(actor to prof)
            }
            enemyActors=built.map{it.first}
            profileMap=built.associate{it.first.body.uid to it.second}
            themeLabel="야생 조우 (단계 ${tierStep+1})"
        }else if(nodeKind==NodeKind.BOSS){
            data class BossSpec(val encId:String,val species:String,val moves:List<String>,val theme:String)
            val bossSpecs=when(s.act){
                1->listOf(BossSpec("BOSS_ACT1","cobblemon:mewtwo",listOf("psychic","aurasphere","shadowball","calmmind","recover"),"로켓단 최강 병기 · 뮤츠"))
                2->listOf(
                    BossSpec("BOSS_ACT2_MAGMA","cobblemon:groudon",listOf("precipiceblades","fireblast","earthpower","rockslide","swordsdance"),"마그마단 심부 · 대지의 화신 그란돈"),
                    BossSpec("BOSS_ACT2_AQUA","cobblemon:kyogre",listOf("originpulse","hydropump","icebeam","calmmind","recover"),"아쿠아단 심해 · 바다의 제왕 가이오가")
                )
                else->listOf(
                    BossSpec("BOSS_ACT3","cobblemon:dialga",listOf("dragonpulse","flashcannon","earthpower","calmmind"),"갤럭시단 창기둥 · 시간의 신 디아루가"),
                    BossSpec("BOSS_ACT3","cobblemon:palkia",listOf("spacialrend","hydropump","dragonpulse","calmmind"),"갤럭시단 창기둥 · 공간의 신 펄기아"),
                    BossSpec("BOSS_ACT3","cobblemon:giratina",listOf("shadowball","dragonclaw","earthpower","calmmind"),"갤럭시단 창기둥 · 반전세계 기라티나")
                )
            }
            val chosenBoss=bossSpecs[rng.nextInt(bossSpecs.size)]
            val bossEntry=catalogueActors.firstOrNull{it.id==chosenBoss.species&&it.formName==it.species.standardForm.name&&bridge.reason(it)==null}
            if(bossEntry!=null){
                s.encounter=chosenBoss.encId;s.tier="BOSS"
                val partyMult=listOf(1.0,1.75,2.50,3.25)[(s.members.size-1).coerceIn(0,3)]
                val bossHp=(when(s.act){1->580;2->1050;else->1680}*partyMult).toInt()
                val actor=bridge.create(bossEntry,"enemy:0",s.seed+s.battleNumber*100L,enemyHp=bossHp,allowed=chosenBoss.moves).also{a->
                    val scale=1.0+s.act*0.14
                    for(k in listOf("atk","spa"))a.stats[k]=(a.stats.getValue(k)*scale).toInt().coerceAtLeast(80+s.act*15)
                    for(k in listOf("def","spd"))a.stats[k]=(a.stats.getValue(k)*scale).toInt().coerceAtLeast(75+s.act*15)
                    a.level=4+s.act*4
                }
                val cand=JsonArray().also{arr->actor.nativeMoves.forEach{mv->arr.add(JsonObject().apply{addProperty("move_id",mv.name);addProperty("weight",if(mv.power>=95)3 else if(mv.power>0)2 else 1)})}}
                val rule=JsonObject().apply{
                    addProperty("priority",1);addProperty("cooldown_rounds",0)
                    add("condition",JsonObject().apply{addProperty("op","ALWAYS")})
                    add("choose",JsonObject().apply{addProperty("type","WEIGHTED");add("candidates",cand)})
                }
                val prof=JsonObject().apply{add("ai_rules",JsonArray().apply{add(rule)})}
                enemyActors=listOf(actor)
                profileMap=mapOf(actor.body.uid to prof)
                themeLabel=chosenBoss.theme
                s.bossUids=setOf(actor.body.uid)
            }else{
                val actPool=orderedEncounters.filter{it.number("act",1)==s.act}.ifEmpty{orderedEncounters}
                val pool=actPool.filter{it.text("tier","NORMAL")=="BOSS"}.ifEmpty{orderedEncounters.filter{it.text("tier","NORMAL")=="BOSS"}}.ifEmpty{actPool}
                val encounter=pool[(s.battleNumber-1)%pool.size]
                s.encounter=encounter.text("encounter_id");s.tier="BOSS"
                val profiles=Definitions.enemies.associateBy{it.text("enemy_id")}
                val instances=encounter.getAsJsonArray("enemy_instances").map{it.asJsonObject}
                val hp=SpirePartyRules.encounterHpBudget(s.act,s.tier,s.encounter,instances.map{it.decimal("threat_weight",1.0)},s.members.size)
                val hpMult=1.2+tierStep*0.12
                enemyActors=instances.mapIndexed{i,e->val d=profiles.getValue(e.text("enemy_id"));val entry=catalogueActors.first{it.id==Definitions.speciesId(d.text("species_id"))}
                    bridge.create(entry,"enemy:$i",s.seed+s.battleNumber*100+i,enemyHp=(hp[i]*hpMult).toInt().coerceAtLeast(hp[i]),allowed=d.getAsJsonArray("move_pool").map{it.asJsonObject.text("id")}).also{actor->actor.level=4+s.act*4}}
                profileMap=enemyActors.mapIndexed{i,a->a.body.uid to profiles.getValue(instances[i].text("enemy_id"))}.toMap()
                themeLabel=encounter.text("faction_theme")
                s.bossUids=enemyActors.map{it.body.uid}.toSet()
            }
        }else{
            val actPool=orderedEncounters.filter{it.number("act",1)==s.act}.ifEmpty{orderedEncounters}
            val pool=actPool.filter{val t=it.text("tier","NORMAL");if(isEliteRow)t=="ELITE" else t=="NORMAL"}.ifEmpty{actPool.filter{it.text("tier","NORMAL")!="BOSS"}}.ifEmpty{actPool}
            val encounter=pool[(s.battleNumber-1)%pool.size]
            s.encounter=encounter.text("encounter_id");s.tier=encounter.text("tier","NORMAL")
            val profiles=Definitions.enemies.associateBy{it.text("enemy_id")}
            val instances=encounter.getAsJsonArray("enemy_instances").map{it.asJsonObject}
            val hp=SpirePartyRules.encounterHpBudget(s.act,s.tier,s.encounter,instances.map{it.decimal("threat_weight",1.0)},s.members.size)
            val hpMult=1.2+tierStep*0.12
            enemyActors=instances.mapIndexed{i,e->val d=profiles.getValue(e.text("enemy_id"));val entry=catalogueActors.first{it.id==Definitions.speciesId(d.text("species_id"))}
                val scaledHp=(hp[i]*hpMult).toInt().coerceAtLeast(hp[i])
                bridge.create(entry,"enemy:$i",s.seed+s.battleNumber*100+i,enemyHp=scaledHp,allowed=d.getAsJsonArray("move_pool").map{it.asJsonObject.text("id")}).also{actor->
                    val scale=0.90+tierStep*0.09
                    val floorAtk=58+tierStep*6;val floorDef=55+tierStep*6
                    for(k in listOf("atk","spa"))actor.stats[k]=maxOf(floorAtk,(actor.stats.getValue(k)*scale).toInt())
                    for(k in listOf("def","spd"))actor.stats[k]=maxOf(floorDef,(actor.stats.getValue(k)*scale).toInt())
                    actor.level=1+tierStep
                }}
            profileMap=enemyActors.mapIndexed{i,a->a.body.uid to profiles.getValue(instances[i].text("enemy_id"))}.toMap()
            themeLabel=encounter.text("faction_theme")
        }
        s.members.forEachIndexed{slot,m->m.party.forEachIndexed{i,a->a.body.deck=a.body.deck?.let{BattleDeck(it.original,Random(s.seed+s.battleNumber*1000L+slot*10+i))}}}
        s.actors=(s.members.flatMap{it.party}+enemyActors).associateBy{it.body.uid};s.profiles=profileMap
        s.ai=IntentRules(s.seed+s.battleNumber*913);s.effects=NativeEffects(s.actors,s.seed+s.battleNumber*701)
        s.battle=SpireBattle(s.members.map{m->SpireBattlePlayer(m.player,m.party.map{it.body}).also{it.activeIndex=m.activeIndex;it.maxEnergy=m.maxEnergy;it.energy=m.maxEnergy}},enemyActors.map{it.body},s.seed+s.battleNumber,effectiveness=MoveCompatibility::multiplier,hook=SpireBattleHook{e,_->event(s,e)},effects=s.effects,speedOf={mon->kotlin.math.round(s.effects.effectiveSpeed(mon)).toInt().coerceAtLeast(25)})
        s.battle.start();s.log.addLast("시험 전투 ${s.battleNumber} (${s.members.size}인) · $themeLabel")
    }
    private fun refreshIntents(s:Session){
        val aliveIds=s.battle.enemies.filter{it.alive}.map{it.uid}.toSet()
        val kept=s.choices.filterKeys{it in aliveIds}.toMutableMap()
        for(enemy in s.battle.enemies.filter{it.alive}){
            if(enemy.uid !in kept){
                s.ai.choose(enemy,s.profiles.getValue(enemy.uid),s.battle,s.actors.getValue(enemy.uid).nativeMoves.map{it.name}.toSet())?.let{kept[enemy.uid]=it}
            }
        }
        s.choices=kept
        s.battle.intents=s.battle.enemies.mapIndexedNotNull{i,a->kept[a.uid]?.let{EnemyIntent(i,it.move)}}
    }
    private fun event(s:Session,e:SpireBattleEvent){
        when(e.name){
            "BATTLE_START"->{
                s.battle.players.forEach{pl->
                    s.effects.enter(pl.active,s.battle.enemies)
                    when(s.battleEventId){
                        "PRESSURE"->pl.active.stages["def"]=((pl.active.stages["def"]?:0)-1).coerceIn(-6,6)
                        "MIASMA"->pl.active.stages["spd"]=((pl.active.stages["spd"]?:0)-1).coerceIn(-6,6)
                    }
                }
                s.battle.enemies.forEach{s.effects.enter(it,s.battle.players.map{a->a.active})}
            }
            "REFRESH_INTENTS"->refreshIntents(s)
            "ROUND_START"->{s.pings.clear();refreshIntents(s)}
            "ENEMY_MOVE_STARTED"->{
                s.choices[e.actor]?.let{s.ai.used(it,s.battle.round)}
                s.choices=s.choices-e.actor
            }
            "ENEMY_ACTION_RESOLVED"->{s.choices=s.choices-e.actor}
            "PROGRESSION"->{s.points+=2}
            "BATTLE_WON"->{
                val baseExp=when(s.tier){"BOSS"->240;"ELITE"->160;else->100}
                if(s.tier=="BOSS")s.evolution.badge("act${s.act}_boss")
                s.members.forEachIndexed{slot,m->
                    if(m.loot==null){val rng=Random(s.seed+s.battleNumber*117L+slot*31)
                        m.party.forEach{a->
                            val expGain=if(a.body.uid in s.battle.winningSurvivors)baseExp else baseExp/2
                            bridge.grantExp(a,expGain)
                            if(a.body.uid in s.battle.winningSurvivors&&SpireRelic.VITAL_SEED.id in m.relics)a.body.heal((a.body.maxHp*0.15).toInt().coerceAtLeast(5))
                        }
                        if(m.items.size<4&&rng.nextInt(100)<40)m.items.add(SpireItem.entries[rng.nextInt(SpireItem.entries.size)].id)
                        val offers=m.party.mapIndexed{owner,a->
                            val entry=catalogueActors.first{it.id==a.body.species}
                            val legal=bridge.legalMoves(entry).filter{bridge.moveReason(it)==null}
                            val fresh=legal.filter{mv->a.body.deck!!.original.none{it.move.id==mv.name}}
                            val chosen=(fresh.ifEmpty{legal}).shuffled(rng).first()
                            LootCard(owner,MoveInstance(-s.battleNumber.toLong()*100-slot*10-owner-1,MoveCompatibility.move(chosen.name)))
                        }
                        m.loot=VictoryLoot(s.battleNumber,offers)
                        val charms=listOf(SpireRelic.POWER_CHARM,SpireRelic.GUARD_CHARM,SpireRelic.VITAL_SEED).filter{it.id !in m.relics}
                        m.relicOffer=when{
                            s.currentNodeKind==NodeKind.BOSS->when{
                                m.maxEnergy==3->SpireRelic.ENERGY_CORE.id
                                m.maxEnergy==4->SpireRelic.ENERGY_CORE_2.id
                                else->charms.shuffled(rng).firstOrNull()?.id
                            }
                            s.tier=="ELITE"&&rng.nextInt(100)<45->{
                                val pool=buildList{
                                    if(m.maxEnergy==3)add(SpireRelic.ENERGY_CORE)
                                    else if(m.maxEnergy==4)add(SpireRelic.ENERGY_CORE_2)
                                    addAll(charms)
                                }
                                pool.shuffled(rng).firstOrNull()?.id
                            }
                            rng.nextInt(100)<15->charms.shuffled(rng).firstOrNull()?.id
                            else->null
                        }
                    }
                }
            }
        }
        if(e.name in setOf("MOVE_HIT","MOVE_MISS")){
            val cat=MoveCompatibility.specs[e.moveId]?.text("category")?.lowercase().orEmpty().ifBlank{"status"}
            arena.playMoveAnimation(s.id,e.actor,e.target,e.moveId,cat,e.name=="MOVE_MISS")
        }
        if(e.name in setOf("MOVE_HIT","AFTER_DAMAGE_TAKEN")&&e.amount>0&&e.target.startsWith("enemy:")){
            s.members.firstOrNull{m->m.party.any{it.body.uid==e.actor}}?.let{it.damageDealt+=e.amount}
        }
        if(e.name in setOf("MOVE_HIT","MOVE_MISS","AFTER_DAMAGE_TAKEN","STATUS_DAMAGE","STATUS_APPLIED","MOVE_PARALYZED","MOVE_SLEEP","MOVE_FLINCHED","MOVE_CONFUSED","NORMAL_SWITCH","FORCED_SWITCH","BATTLE_WON","BATTLE_LOST")){
            val actor=s.actors[e.actor]?.name.orEmpty();val target=s.actors[e.target]?.name.orEmpty()
            val stLabel=mapOf("burn" to "화상","poison" to "독","toxic" to "맹독","paralysis" to "마비","sleep" to "수면","confusion" to "혼란","flinch" to "풀죽음")[e.moveId]?:e.moveId
            val line=when(e.name){
                "MOVE_HIT"->if(e.amount>0)"$actor → $target ${e.amount} 피해" else "$actor 기술 효과 처리"
                "AFTER_DAMAGE_TAKEN"->if(e.amount>0)"$target ${e.amount} 피해" else "$actor 기술 효과 처리"
                "MOVE_MISS"->"$actor 기술 실패·무효"
                "STATUS_DAMAGE"->"$target $stLabel 피해 -${e.amount}"
                "STATUS_APPLIED"->"$target $stLabel 상태이상!"
                "MOVE_PARALYZED"->"$actor 마비로 행동 불가!"
                "MOVE_SLEEP"->"$actor 잠들어 움직일 수 없음"
                "MOVE_FLINCHED"->"$actor 풀죽어 움직일 수 없음"
                "MOVE_CONFUSED"->"$actor 혼란에 빠짐!"
                "BATTLE_WON"->"승리 · 진행점수 +2 (능력치 고정)"
                "BATTLE_LOST"->"모든 포켓몬 기절 · 패배"
                else->"$target 출전"
            }
            s.log.addLast(line);while(s.log.size>12)s.log.removeFirst()
        }
        runCatching{
            val path=FabricLoader.getInstance().gameDir.resolve("logs/cobblespire/trials/${s.id}.jsonl");Files.createDirectories(path.parent)
        Files.writeString(path,SpireMod.gson.toJson(JsonObject().apply{addProperty("move",e.moveId);addProperty("type",e.moveType);addProperty("multiplier",e.multiplier);addProperty("seed",s.seed);addProperty("battle",s.battleNumber);addProperty("event",e.name);addProperty("round",e.round);addProperty("actor",e.actor);addProperty("target",e.target);addProperty("amount",e.amount)}).replace("\n","")+"\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND)
        }.onFailure{if(!s.logFailureReported){s.logFailureReported=true;SpireMod.logger.warn("Trial trace could not be written; in-memory events are retained",it)}}
    }
    private fun card(a:PokemonBridge.Actor,credits:Int=0,effectiveSpe:Int?=null)=JsonObject().apply{
        addProperty("uid",a.body.uid);addProperty("species",a.body.species);addProperty("name",a.name);addProperty("level",a.level);addProperty("exp",a.exp);addProperty("expToNext",a.expToNext);addProperty("hp",a.body.hp);addProperty("maxHp",a.body.maxHp);addProperty("ability",a.ability)
        if(effectiveSpe!=null)addProperty("effectiveSpe",effectiveSpe)
        addProperty("canEvolve",bridge.canEvolve(a,credits))
        val curEntry=catalogueActors.firstOrNull{it.id==a.body.species&&it.formName==it.species.standardForm.name}
        val reqLv=curEntry?.let{bridge.requiredEvoLevel(bridge.stageOf(it))}?:99
        addProperty("evoReqLevel",reqLv)
        add("evolutions",JsonArray().also{arr->bridge.legalEvolutions(a).forEach{t->arr.add(JsonObject().apply{
            addProperty("key",t.key);addProperty("species",t.id);addProperty("name",t.species.translatedName.string);addProperty("reqLevel",reqLv);addProperty("readyByLevel",a.level>=reqLv);addProperty("readyByCredit",credits>0)
        })}})
        add("aspects",JsonArray().also{v->a.aspects.forEach{v.add(it)}});add("types",JsonArray().also{v->a.body.types.forEach{v.add(it)}})
        add("stats",JsonObject().also{v->a.stats.forEach{(k,n)->v.addProperty(k,n)}});add("stages",JsonObject().also{v->a.body.stages.forEach{(k,n)->v.addProperty(k,n)}})
        add("statuses",JsonArray().also{v->a.body.statuses.values.forEach{v.add(it.id+if(it.remaining>0)" ${it.remaining}" else "")}})
    }
    private fun cardInfo(m:MoveInstance,fx:NativeEffects)=JsonObject().apply{
        addProperty("instance",m.uid);addProperty("move",m.move.id);addProperty("cost",fx.cost(m))
        addProperty("trainer",m.move.trainer);addProperty("exhaust",m.move.exhaust);addProperty("retain",m.retain);addProperty("upgraded",m.upgraded)
        val role=CardSemantics.role(m.move);addProperty("role",role.name)
        addProperty("rarity",CardSemantics.rarity(fx.cost(m),m.move.power,m.move.trainer).name)
        addProperty("draw",fx.drawCount(m));addProperty("selfTarget",m.move.selfTarget);addProperty("targetAll",m.move.targetAll)
        addProperty("effectSummary",CardSemantics.summary(m.move,fx.drawCount(m)))
        if(role==CardRole.ATTACK){
            addProperty("type",m.move.type);addProperty("accuracy",m.move.accuracy);addProperty("power",m.move.power)
            if(m.move.maxHits>1){addProperty("minHits",m.move.minHits);addProperty("maxHits",m.move.maxHits)}
            addProperty("category",MoveCompatibility.specs[m.move.id]?.text("category").orEmpty())
        }
    }
    fun snapshot(id:UUID):JsonObject?=sessions[id]?.takeIf{it.started}?.let{s->s.members.firstOrNull{it.player==id}?.let{buildSnapshot(s,it,false)}}
    private fun send(p:ServerPlayer,s:Session,open:Boolean,fresh:Boolean=false){
        val member=s.members.firstOrNull{it.player==p.uuid}?:return
        SpireMod.send(p,buildSnapshot(s,member,open).apply{addProperty("fresh",fresh);add("arena",arena.sync(p,this))})
    }
    private fun buildSnapshot(s:Session,member:Member,open:Boolean):JsonObject{
        val b=s.battle;val player=b.players.first{it.id==member.player};return JsonObject().apply{
            addProperty("kind","battle");addProperty("open",open);addProperty("session",s.id.toString());addProperty("revision",b.revision);addProperty("round",b.round);addProperty("phase",b.phase.name)
            addProperty("progress",s.points);addProperty("points",s.points);addProperty("energy",player.energy);addProperty("maxEnergy",player.maxEnergy);add("relics",JsonArray().also{a->member.relics.forEach{a.add(it)}})
            add("items",JsonArray().also{a->member.items.forEach{id->SpireItem.byId(id)?.let{it->a.add(JsonObject().apply{addProperty("id",it.id);addProperty("label",it.label);addProperty("desc",it.desc)})}}});addProperty("active",player.activeIndex);addProperty("forced",player.forcedSwitch);addProperty("switchUsed",player.normalSwitchUsed);addProperty("battleNumber",s.battleNumber);addProperty("encounter",s.encounter)
            addProperty("act",s.act);addProperty("tier",s.tier);addProperty("partySize",s.members.size);addProperty("isHost",s.host==member.player)
            addProperty("battleEventLabel",s.battleEventLabel);addProperty("battleEventDesc",s.battleEventDesc)
            val onlineIds=onlineMembers(s).map{it.player}.toSet()
            addProperty("ended",player.ended);addProperty("endedCount",b.players.count{it.alive&&it.id in onlineIds&&it.ended});addProperty("alivePlayers",b.players.count{it.alive&&it.id in onlineIds})
            addProperty("selfDamageDealt",member.damageDealt)
            val credits=s.evolution.available().size
            addProperty("playerGaugePct",b.gaugePercent(player.active.uid));addProperty("playerAv",b.actionValue(player.active.uid))
            add("timeline",JsonArray().also{arr->b.timelinePreview(7).forEach{t->
                val actorName=s.actors[t.uid]?.name?:t.uid
                arr.add(JsonObject().apply{
                    addProperty("uid",t.uid);addProperty("name",actorName);addProperty("species",s.actors[t.uid]?.body?.species.orEmpty())
                    addProperty("isPlayer",t.isPlayer);addProperty("isSelf",t.playerId==member.player);addProperty("av",t.av);addProperty("gaugePct",t.gaugePct)
                })
            }})
            fun effSpe(body:SpireCombatant)=kotlin.math.round(s.effects.effectiveSpeed(body)).toInt().coerceAtLeast(25)
            add("party",JsonArray().also{a->member.party.forEach{a.add(card(it,credits,effSpe(it.body)))}})
            add("allies",JsonArray().also{a->s.members.filter{it.player!=member.player}.forEach{ally->
                val bp=b.players.first{it.id==ally.player}
                a.add(JsonObject().apply{
                    addProperty("name",ally.name);addProperty("energy",bp.energy);addProperty("ended",bp.ended);addProperty("alive",bp.alive);addProperty("disconnected",ally.disconnected);addProperty("damageDealt",ally.damageDealt)
                    add("active",card(ally.party[bp.activeIndex],credits,effSpe(bp.active)).apply{addProperty("gaugePct",b.gaugePercent(bp.active.uid));addProperty("av",b.actionValue(bp.active.uid))})
                })
            }})
            add("pings",JsonArray().also{a->s.pings.values.forEach{pg->a.add(JsonObject().apply{
                addProperty("playerName",pg.playerName);addProperty("isSelf",pg.player==member.player);addProperty("target",pg.target)
                addProperty("move",pg.move);addProperty("minDamage",pg.minDamage);addProperty("maxDamage",pg.maxDamage);addProperty("round",pg.round)
            })}})
            add("arenaAllies",JsonArray().also{a->s.members.forEach{m->val bp=b.players.firstOrNull{it.id==m.player};if(bp!=null&&bp.alive)a.add(card(m.party[bp.activeIndex],credits,effSpe(bp.active)))}})
            add("enemies",JsonArray().also{a->b.enemies.forEach{enemy->val c=card(s.actors.getValue(enemy.uid),credits,effSpe(enemy))
                c.addProperty("boss",enemy.uid in s.bossUids)
                c.addProperty("gaugePct",b.gaugePercent(enemy.uid));c.addProperty("av",b.actionValue(enemy.uid))
                s.choices[enemy.uid]?.let{choice->
                val move=choice.move;val role=CardSemantics.role(move)
                val blinded=s.battleEventId=="FOG"||player.active.statuses.containsKey("confusion")||player.active.statuses.containsKey("sleep")
                val strong=role==CardRole.ATTACK&&move.power>=95
                val hidden=when{blinded->"DEBUFF";strong->"STRONG";else->""}
                val effMul=if(role==CardRole.ATTACK)MoveCompatibility.multiplier(move.type,player.active.types) else 1.0
                if(hidden.isNotBlank()){
                    c.addProperty("intentHidden",hidden)
                    c.addProperty("intentRole",if(hidden=="STRONG")CardRole.ATTACK.name else "UNKNOWN")
                    if(hidden=="STRONG")c.addProperty("multiplier",effMul)
                }else{
                    c.addProperty("intent",move.id);c.addProperty("intentRole",role.name);c.addProperty("intentEffect",CardSemantics.summary(move))
                    c.addProperty("selfTarget",move.selfTarget);c.addProperty("targetAll",move.targetAll)
                    if(role==CardRole.ATTACK){
                        c.addProperty("intentType",move.type);c.addProperty("intentPower",move.power)
                        if(move.maxHits>1){c.addProperty("minHits",move.minHits);c.addProperty("maxHits",move.maxHits)}
                        c.addProperty("intentAccuracy",s.effects.accuracy(enemy,player.active,move))
                        val damage=s.effects.estimate(enemy,player.active,move,spread=move.targetAll&&b.players.count{it.active.alive}>1)
                        c.addProperty("minDamage",damage.first.coerceAtMost(player.active.hp));c.addProperty("maxDamage",damage.second.coerceAtMost(player.active.hp))
                        c.addProperty("multiplier",effMul)
                    }
                }
            };a.add(c)}})
            add("damageEvents",JsonArray().also{a->b.events.withIndex().filter{it.value.name in setOf("MOVE_HIT","AFTER_DAMAGE_TAKEN","MOVE_MISS","STATUS_DAMAGE","STATUS_APPLIED","MOVE_PARALYZED","MOVE_SLEEP","MOVE_FLINCHED","MOVE_CONFUSED")}.takeLast(8).forEach{(index,e)->
                val owner=s.members.firstOrNull{m->m.party.any{it.body.uid==e.actor}}
                val isIncoming=e.target.startsWith("player:")||(e.target==e.actor&&e.actor.startsWith("player:"))
                a.add(JsonObject().apply{
                    addProperty("eventKind",e.name);addProperty("role",CardSemantics.role(e.moveId).name);addProperty("incoming",isIncoming);addProperty("seq",index+1);addProperty("actorUid",e.actor);addProperty("targetUid",e.target);addProperty("actor",s.actors[e.actor]?.name.orEmpty());addProperty("actorSpecies",s.actors[e.actor]?.body?.species.orEmpty());addProperty("target",s.actors[e.target]?.name.orEmpty());addProperty("targetSpecies",s.actors[e.target]?.body?.species.orEmpty());addProperty("amount",e.amount)
                    addProperty("ownerName",owner?.name.orEmpty());addProperty("isSelf",owner?.player==member.player)
                    addProperty("move",e.moveId);addProperty("type",e.moveType);addProperty("category",MoveCompatibility.specs[e.moveId]?.text("category")?.lowercase().orEmpty());addProperty("multiplier",e.multiplier);addProperty("miss",e.name=="MOVE_MISS")
                })
            }})
            member.loot?.let{loot->add("loot",JsonObject().apply{
                addProperty("settled",loot.settled);addProperty("allSettled",onlineMembers(s).all{it.loot?.settled==true});addProperty("selected",loot.selected?:-2);addProperty("progressAward",2);addProperty("revivedCount",member.party.count{it.body.uid !in b.winningSurvivors})
                member.relicOffer?.let{rid->SpireRelic.byId(rid)?.let{r->addProperty("relicOffer",r.id);addProperty("relicOfferLabel","${r.label} (${r.desc})")}}
                add("offers",JsonArray().also{a->loot.offers.forEach{offer->a.add(cardInfo(offer.card,s.effects).apply{addProperty("owner",offer.owner);addProperty("ownerName",member.party[offer.owner].name);addProperty("ownerSpecies",member.party[offer.owner].body.species)})}})
            })}
            addProperty("evolutionCredits",s.evolution.available().size);add("badges",JsonArray().also{a->s.evolution.earnedBadges().forEach{a.add(it)}})
            val deck=player.active.deck!!
            addProperty("deckOwner",player.active.uid);addProperty("handLimit",deck.handLimit);addProperty("motionSequence",deck.sequence)
            add("hand",JsonArray().also{a->deck.hand.forEach{m->a.add(cardInfo(m,s.effects).apply{
                if(CardSemantics.role(m.move)==CardRole.ATTACK)add("previews",JsonArray().also{out->b.enemies.forEach{foe->
                    val damage=s.effects.estimate(player.active,foe,m.move,m.upgraded,m.move.targetAll&&b.enemies.count{it.alive}>1)
                    val previewTarget=s.actors[if(m.move.selfTarget)player.active.uid else foe.uid]
                    out.add(JsonObject().apply{addProperty("accuracy",s.effects.accuracy(player.active,if(m.move.selfTarget)player.active else foe,m.move));addProperty("targetName",previewTarget?.name.orEmpty());addProperty("targetSpecies",previewTarget?.body?.species.orEmpty());addProperty("minDamage",damage.first.coerceAtMost(foe.hp));addProperty("maxDamage",damage.second.coerceAtMost(foe.hp));addProperty("multiplier",MoveCompatibility.multiplier(m.move.type,foe.types))})
                }})
            })}})
            add("piles",JsonObject().apply{
                add("draw",JsonArray().also{a->deck.draw.sortedWith(compareBy<MoveInstance>{it.move.id}.thenBy{it.uid}).forEach{a.add(cardInfo(it,s.effects))}})
                add("discard",JsonArray().also{a->deck.discard.forEach{a.add(cardInfo(it,s.effects))}})
                add("exhaust",JsonArray().also{a->deck.exhausted.forEach{a.add(cardInfo(it,s.effects))}})
            })
            add("motions",JsonArray().also{a->deck.transfers.forEach{t->a.add(JsonObject().apply{addProperty("seq",t.sequence);add("card",cardInfo(t.card,s.effects));addProperty("from",t.from);addProperty("to",t.to);addProperty("index",t.index);addProperty("round",t.round);addProperty("cause",t.cause)})}})
            add("log",JsonArray().also{a->s.log.takeLast(4).forEach{a.add(it)}})
        }
    }
    private fun triggerNode(s:Session,nodeId:Int){
        require(nodeId in s.actMap.reachableIds()){"현재 위치에서 이동할 수 없는 경로입니다."}
        val node=requireNotNull(s.actMap.node(nodeId))
        s.actMap.votes.clear()
        s.actMap.currentId=nodeId;node.visited=true;s.mapMessage=""
        val rng=Random(s.seed+s.act*1009L+nodeId*37L)
        when(node.kind){
            NodeKind.ORG_BATTLE,NodeKind.WILD_BATTLE,NodeKind.BOSS->{
                s.phaseMode="BATTLE";begin(s,node.kind);broadcastBattle(s,true,true)
            }
            NodeKind.REST->{
                s.members.forEach{m->m.party.forEach{a->
                    if(a.body.alive)a.body.heal((a.body.maxHp*0.50).toInt().coerceAtLeast(10)) else a.body.revive(0.50)
                    bridge.grantExp(a,80)
                }}
                s.mapMessage="휴식처: 모든 파티 HP 50% 회복 및 경험치 +80!"
                broadcastMap(s)
            }
            NodeKind.RECRUIT->{
                s.activeEvent=createRecruitEvent(s,rng);s.phaseMode="EVENT";broadcastEvent(s)
            }
            NodeKind.EVENT->{
                val roll=rng.nextInt(4)
                s.activeEvent=when(roll){
                    0->createRecruitEvent(s,rng)
                    1->ActiveEvent("LOTTERY","수상한 보물볼을 발견했다","참가자 중 1명이 무작위로 선택됩니다.\n보상을 얻거나 함정(HP 25% 피해)에 걸릴 수 있습니다.","TREASURE")
                    2->ActiveEvent("LOTTERY","고대 제단의 시련","자원자 중 1명의 출전 포켓몬이 HP 20% 피해를 입는 대신,\n모두의 출전 포켓몬이 Lv+1 성장하고 도구를 얻습니다.","ALTAR")
                    else->ActiveEvent("LOTTERY","야생 열매 밭","참가자 중 1명이 무작위로 선택되어\n파티 전체 HP 50% 회복과 상처약을 얻습니다.","BERRY")
                }
                s.phaseMode="EVENT";broadcastEvent(s)
            }
        }
    }
    private fun createRecruitEvent(s:Session,rng:Random):ActiveEvent{
        val owned=s.members.flatMap{it.party}.map{it.body.species}.toSet()
        val pool=catalogueActors.filter{isBaseStage(it)&&bridge.reason(it)==null&&it.id !in owned}.ifEmpty{catalogueActors.filter{isBaseStage(it)&&bridge.reason(it)==null}}
        val entry=pool[rng.nextInt(pool.size)]
        val candidate=bridge.create(entry,"recruit:${s.act}:${s.actMap.currentId}",s.seed+s.actMap.currentId*91L)
        return ActiveEvent("RECRUIT","야생 ${candidate.name}을(를) 만났다!","동료로 영입할지 결정하세요. 파티가 가득 찬 경우 교체할 수 있습니다.",candidate=candidate,candidateEntry=entry)
    }
    private fun handleEventChoice(p:ServerPlayer,s:Session,choice:String){
        val ev=requireNotNull(s.activeEvent)
        require(!ev.resolved){"이미 결과가 확정된 이벤트입니다."}
        val member=s.members.first{it.player==p.uuid}
        if(ev.kind=="RECRUIT"){
            require(p.uuid !in ev.choices){"이미 선택을 완료했습니다."}
            val entry=requireNotNull(ev.candidateEntry)
            val slot=s.members.indexOf(member)
            when{
                choice=="ACCEPT"&&member.party.size<3->{
                    val actor=bridge.create(entry,"player:${slot*3+member.party.size}",s.seed+s.actMap.currentId*17L+slot)
                    member.party.add(actor)
                    ev.choices[p.uuid]="ACCEPT"
                }
                choice.startsWith("REPLACE:")->{
                    val idx=choice.removePrefix("REPLACE:").toIntOrNull()?:-1
                    require(idx in member.party.indices){"교체할 슬롯이 올바르지 않습니다."}
                    val actor=bridge.create(entry,"player:${slot*3+idx}",s.seed+s.actMap.currentId*17L+slot)
                    member.party[idx]=actor
                    if(member.activeIndex !in member.party.indices)member.activeIndex=0
                    ev.choices[p.uuid]="REPLACE"
                }
                else->ev.choices[p.uuid]="SKIP"
            }
            if(tryResolveEvent(s))return
            broadcastEvent(s)
            return
        }
        require(choice in setOf("JOIN","SKIP")){"올바른 선택이 아닙니다."}
        require(p.uuid !in ev.choices){"이미 참가 여부를 선택했습니다."}
        ev.choices[p.uuid]=choice
        if(!tryResolveEvent(s))broadcastEvent(s)
    }
    private fun tryResolveEvent(s:Session):Boolean{
        val ev=s.activeEvent?:return false
        if(ev.resolved)return true
        val online=onlineMembers(s)
        if(!online.all{it.player in ev.choices})return false
        if(ev.kind=="RECRUIT"){
            ev.resolved=true
            ev.outcomeText="모든 팀원의 영입 선택이 완료되었습니다."
            broadcastEvent(s)
            return true
        }
        val participants=online.filter{ev.choices[it.player]=="JOIN"}
        if(participants.isEmpty()){
            ev.outcomeText="아무도 참가하지 않아 조용히 지나갔습니다."
        }else{
            val rng=Random(s.seed+s.act*313L+s.actMap.currentId*19L)
            val winner=participants[rng.nextInt(participants.size)]
            ev.winnerName=winner.name
            val active=winner.party[winner.activeIndex]
            when(ev.lotteryKind){
                "TREASURE"->{
                    if(rng.nextInt(100)<65){
                        if(winner.maxEnergy<5&&rng.nextInt(100)<35){
                            winner.maxEnergy++
                            winner.relics.add(SpireRelic.ENERGY_CORE.id)
                            ev.outcomeText="${winner.name} 당첨! 보물볼에서 [${SpireRelic.ENERGY_CORE.label}] 유물을 획득했습니다!"
                        }else{
                            val item=SpireItem.entries[rng.nextInt(SpireItem.entries.size)]
                            if(winner.items.size<4)winner.items.add(item.id)
                            ev.outcomeText="${winner.name} 당첨! 보물볼에서 [${item.label}]을(를) 획득했습니다!"
                        }
                    }else{
                        val dmg=(active.body.maxHp*0.25).toInt().coerceAtLeast(1)
                        active.body.hp=(active.body.hp-dmg).coerceAtLeast(1)
                        ev.outcomeText="${winner.name} 당첨... 함정 발동! ${active.name}이(가) ${dmg} 피해를 입었습니다."
                    }
                }
                "ALTAR"->{
                    val dmg=(active.body.maxHp*0.20).toInt().coerceAtLeast(1)
                    active.body.hp=(active.body.hp-dmg).coerceAtLeast(1)
                    s.members.forEach{m->bridge.grantExp(m.party[m.activeIndex],120)}
                    val item=SpireItem.entries[rng.nextInt(SpireItem.entries.size)]
                    if(winner.items.size<4)winner.items.add(item.id)
                    ev.outcomeText="${winner.name}의 희생(-${dmg} HP)으로 전체 출전 포켓몬 EXP +120 & ${winner.name}에게 [${item.label}] 지급!"
                }
                else->{
                    winner.party.forEach{a->if(a.body.alive)a.body.heal((a.body.maxHp*0.50).toInt().coerceAtLeast(10)) else a.body.revive(0.50)}
                    if(winner.items.size<4)winner.items.add(SpireItem.POTION.id)
                    ev.outcomeText="${winner.name} 당첨! 파티 전체 HP 50% 회복 & [상처약]을 획득했습니다!"
                }
            }
        }
        ev.resolved=true
        broadcastEvent(s)
        return true
    }
    private fun buildMapJson(s:Session,member:Member)=JsonObject().apply{
        val credits=s.evolution.available().size;val online=onlineMembers(s)
        addProperty("kind","map");addProperty("session",s.id.toString());addProperty("act",s.act);addProperty("points",s.points)
        addProperty("isHost",s.host==member.player);addProperty("partySize",online.size);addProperty("totalMembers",s.members.size);addProperty("message",s.mapMessage)
        addProperty("maxEnergy",member.maxEnergy);addProperty("relicCount",member.relics.size);addProperty("evolutionCredits",credits)
        addProperty("myVote",s.actMap.votes[member.player]?:-1)
        addProperty("votedCount",online.count{it.player in s.actMap.votes})
        addProperty("leadingNodeId",s.actMap.leadingNode(s.host,s.seed)?:-1)
        add("map",s.actMap.json())
        add("voters",JsonArray().also{a->s.members.forEach{m->a.add(JsonObject().apply{
            addProperty("name",m.name);addProperty("isSelf",m.player==member.player);addProperty("disconnected",m.disconnected);addProperty("nodeId",s.actMap.votes[m.player]?:-1)
        })}})
        add("party",JsonArray().also{a->member.party.forEach{a.add(card(it,credits))}})
        add("items",JsonArray().also{a->member.items.forEach{id->SpireItem.byId(id)?.let{it->a.add(JsonObject().apply{addProperty("id",it.id);addProperty("label",it.label);addProperty("desc",it.desc)})}}})
    }
    private fun buildEventJson(s:Session,member:Member):JsonObject{
        val ev=requireNotNull(s.activeEvent);val credits=s.evolution.available().size
        return JsonObject().apply{
            addProperty("kind","event");addProperty("session",s.id.toString());addProperty("eventKind",ev.kind)
            addProperty("title",ev.title);addProperty("description",ev.description);addProperty("resolved",ev.resolved)
            addProperty("winnerName",ev.winnerName);addProperty("outcomeText",ev.outcomeText);addProperty("evolutionCredits",credits)
            addProperty("isHost",s.host==member.player);addProperty("myChoice",ev.choices[member.player].orEmpty())
            ev.candidate?.let{add("candidate",card(it,credits))}
            add("party",JsonArray().also{a->member.party.forEach{a.add(card(it,credits))}})
            add("joined",JsonArray().also{a->s.members.filter{ev.choices[it.player] in setOf("JOIN","ACCEPT","REPLACE")}.forEach{a.add(it.name)}})
            add("skipped",JsonArray().also{a->s.members.filter{ev.choices[it.player]=="SKIP"}.forEach{a.add(it.name)}})
            add("waiting",JsonArray().also{a->onlineMembers(s).filter{it.player !in ev.choices}.forEach{a.add(it.name)}})
        }
    }
}
