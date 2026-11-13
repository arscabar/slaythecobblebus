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
    private data class Member(val player:UUID,val name:String,val party:List<PokemonBridge.Actor>,var activeIndex:Int=0,var loot:VictoryLoot?=null)
    private data class Session(val id:UUID,val seed:Long,val members:MutableList<Member>,var host:UUID,var started:Boolean=false,var points:Int=0,var battleNumber:Int=0){
        lateinit var battle:SpireBattle;lateinit var effects:NativeEffects;lateinit var ai:IntentRules
        var actors=emptyMap<String,PokemonBridge.Actor>();var profiles=emptyMap<String,JsonObject>();var choices=emptyMap<String,IntentRules.Choice>()
        val log=ArrayDeque<String>()
        val evolution=EvolutionAccess()
        var encounter=""
        var act=1
        var tier="NORMAL"
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
    fun inBattle(id:UUID)=sessions[id]?.let{it.started&&it.battle.phase in setOf(SpireBattlePhase.PLAYER,SpireBattlePhase.ENEMY)}==true
    fun clear(){arena.close();sessions.clear();rooms.clear();lastAction.clear()}
    fun leave(p:ServerPlayer){lastAction.remove(p.uuid);removeMember(p)}
    fun writeAudit(){val f=FabricLoader.getInstance().gameDir.resolve("logs/cobblespire/move-compatibility.json");Files.createDirectories(f.parent);Files.writeString(f,SpireMod.gson.toJson(bridge.audit()))}
    fun handle(p:ServerPlayer,j:JsonObject){
        require(j.text("action").length<=32)
        val action=j.text("action")
        if(action in setOf("move","switch","end","next","start","host","join","launch","claim")){
            require(server.tickCount-(lastAction[p.uuid]?:-999)>=2){"이전 요청 처리 후 다시 시도하세요."};lastAction[p.uuid]=server.tickCount
        }
        when(action){
            "open"->sessions[p.uuid]?.let{if(it.started)send(p,it,true)else menu(p,j)}?:menu(p,j)
            "catalog"->SpireMod.send(p,catalogue.page(j.text("query").take(64),j.number("page")))
            "menu"->menu(p,j)
            "start"->start(p,j.text("entry"))
            "host"->hostRoom(p,j.text("entry"))
            "join"->joinRoom(p,j.text("roomId"),j.text("entry"))
            "launch"->launchRoom(p)
            "quit"->{val s=sessions[p.uuid];if(s!=null&&s.started)requireSession(p,j);removeMember(p);menu(p,JsonObject())}
            "move","switch","end","next","claim"->{
                val s=requireSession(p,j);val b=s.battle;val member=s.members.first{it.player==p.uuid}
                when(action){
                    "move"->b.useMove(p.uuid,j.number("handIndex",-1),j.get("instance")?.asLong?:-1,j.number("target",-1),j.text("request"),j.number("revision",-1))
                    "switch"->{b.switch(p.uuid,j.number("index",-1),j.text("request"),j.number("revision",-1));member.activeIndex=b.players.first{it.id==p.uuid}.activeIndex}
                    "end"->b.endTurn(p.uuid,j.text("request"),j.number("revision",-1))
                    "claim"->{
                        require(b.phase==SpireBattlePhase.VICTORY&&j.number("revision",-1)==b.revision){"승리 정산 화면을 다시 열어주세요."}
                        val loot=requireNotNull(member.loot);val index=j.number("choice",-2);loot.validate(index)
                        val offer=loot.offers.getOrNull(index)
                        val replacement=offer?.let{BattleDeck(member.party[it.owner].body.deck!!.original+it.card,Random(s.seed+s.battleNumber*37L+member.player.hashCode()))}
                        loot.claim(index);if(offer!=null)member.party[offer.owner].body.deck=replacement
                    }
                    "next"->{
                        require(s.members.all{it.loot?.settled==true}){"모든 팀원이 전리품을 선택하거나 건너뛴 뒤 진행하세요."}
                        require(j.number("revision",-1)==b.revision&&b.phase==SpireBattlePhase.VICTORY)
                        require(s.members.none{m->server.playerList.getPlayer(m.player)?.let{BattleRegistry.getBattleByParticipatingPlayer(it)!=null}==true}){"일반 전투가 끝난 뒤 진행하세요."}
                        begin(s)
                    }
                }
                broadcastBattle(s,false,action=="next")
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
        s.members.removeAll{it.player==p.uuid}
        if(s.members.isEmpty()){rooms.remove(s.id);return}
        if(s.host==p.uuid)s.host=s.members.first().player
        if(s.started){s.battle.removePlayer(p.uuid);broadcastBattle(s,false,false)}else broadcastMenu(s)
    }
    private fun broadcastMenu(s:Session){s.members.forEach{m->server.playerList.getPlayer(m.player)?.let{menu(it,JsonObject())}}}
    private fun broadcastBattle(s:Session,open:Boolean,fresh:Boolean){s.members.forEach{m->server.playerList.getPlayer(m.player)?.let{send(it,s,open,fresh)}}}
    private fun menu(p:ServerPlayer,j:JsonObject){
        val query=j.text("query").take(64);val extended=j.get("extended")?.asBoolean==true
        val filtered=catalogueActors.filter{e->val design=Definitions.characters.firstOrNull{Definitions.speciesId(it.text("species_id"))==e.id};
            val starter=e.species.standardForm.types.any{it.name.lowercase() in setOf("fire","water","grass")}
            starter&&(extended||design!=null)&&(query.isBlank()||e.id.contains(query,true)||(design?.text("name_ko")?:e.species.translatedName.string).contains(query,true))}
        val page=j.number("page").coerceIn(0,((filtered.size-1)/8).coerceAtLeast(0))
        SpireMod.send(p,JsonObject().apply{addProperty("kind","menu");addProperty("query",query);addProperty("extended",extended);addProperty("page",page);addProperty("total",filtered.size)
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
    private fun pickParty(p:ServerPlayer,key:String,slot:Int,seed:Long):List<PokemonBridge.Actor>{
        require(BattleRegistry.getBattleByParticipatingPlayer(p)==null){"일반 포켓몬 전투가 끝난 뒤 시작하세요."}
        val otherDraft=runCatching{val c=Class.forName("dev.cobbleleague.LeagueMod").getField("Companion").get(null);val service=c.javaClass.getMethod("getService").invoke(c);service?.javaClass?.getMethod("draftParty",UUID::class.java)?.invoke(service,p.uuid)!=null}.getOrDefault(false)
        require(!otherDraft){"리그 도전을 끝낸 뒤 시작하세요."}
        val entry=requireNotNull(catalogueActors.firstOrNull{it.key==key})
        require(entry.species.standardForm.types.any{it.name.lowercase() in setOf("fire","water","grass")}){"불꽃·물·풀 대표를 선택하세요."}
        require(bridge.reason(entry)==null){bridge.reason(entry).orEmpty()}
        val rng=Random(seed);val selected=mutableListOf(entry)
        for(e in catalogueActors.shuffled(rng).sortedBy{v->Definitions.characters.none{Definitions.speciesId(it.text("species_id"))==v.id}}){
            if(selected.size==3)break
            if(e.id in selected.map{it.id}||bridge.reason(e)!=null)continue
            val types=(selected+e).map{it.species.standardForm.types.map{t->t.name.lowercase()}.toSet()}
            if(SpirePartyRules.disjoint(types))selected.add(e)
        }
        require(selected.size==3){"타입이 겹치지 않는 지원 파트너가 부족합니다."}
        return selected.mapIndexed{i,e->bridge.create(e,"player:${slot*3+i}",seed+i)}
    }
    private fun start(p:ServerPlayer,key:String){
        require(p.uuid !in sessions){"기존 시험 전투를 먼저 종료하세요."}
        val seed=SecureRandom().nextLong()
        val party=pickParty(p,key,0,seed)
        val s=Session(UUID.randomUUID(),seed,mutableListOf(Member(p.uuid,p.scoreboardName,party)),p.uuid,started=true)
        begin(s);arena.enter(p,s.id,0);sessions[p.uuid]=s;rooms[s.id]=s
        try{send(p,s,true,true)}catch(e:Exception){leave(p);throw e}
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
        s.started=true;begin(s)
        s.members.forEachIndexed{slot,m->server.playerList.getPlayer(m.player)?.let{arena.enter(it,s.id,slot)}}
        try{broadcastBattle(s,true,true)}catch(e:Exception){s.members.toList().forEach{m->server.playerList.getPlayer(m.player)?.let{leave(it)}};throw e}
    }
    private fun begin(s:Session){
        s.members.forEach{it.loot=null};s.battleNumber++
        val encounter=orderedEncounters[(s.battleNumber-1)%orderedEncounters.size]
        s.encounter=encounter.text("encounter_id");s.act=encounter.number("act",1);s.tier=encounter.text("tier","NORMAL")
        val profiles=Definitions.enemies.associateBy{it.text("enemy_id")}
        val instances=encounter.getAsJsonArray("enemy_instances").map{it.asJsonObject}
        val hp=SpirePartyRules.encounterHpBudget(s.act,s.tier,s.encounter,instances.map{it.decimal("threat_weight",1.0)},s.members.size)
        val enemyActors=instances.mapIndexed{i,e->val d=profiles.getValue(e.text("enemy_id"));val entry=catalogueActors.first{it.id==Definitions.speciesId(d.text("species_id"))}
            bridge.create(entry,"enemy:$i",s.seed+s.battleNumber*100+i,enemyHp=hp[i],allowed=d.getAsJsonArray("move_pool").map{it.asJsonObject.text("id")})}
        s.members.forEachIndexed{slot,m->m.party.forEachIndexed{i,a->a.body.deck=a.body.deck?.let{BattleDeck(it.original,Random(s.seed+s.battleNumber*1000L+slot*10+i))}}}
        s.actors=(s.members.flatMap{it.party}+enemyActors).associateBy{it.body.uid};s.profiles=enemyActors.mapIndexed{i,a->a.body.uid to profiles.getValue(instances[i].text("enemy_id"))}.toMap()
        s.ai=IntentRules(s.seed+s.battleNumber*913);s.effects=NativeEffects(s.actors,s.seed+s.battleNumber*701)
        s.battle=SpireBattle(s.members.map{m->SpireBattlePlayer(m.player,m.party.map{it.body}).also{it.activeIndex=m.activeIndex}},enemyActors.map{it.body},s.seed+s.battleNumber,effectiveness=MoveCompatibility::multiplier,hook=SpireBattleHook{e,_->event(s,e)},effects=s.effects)
        s.battle.start();s.log.addLast("시험 전투 ${s.battleNumber} (${s.members.size}인) · ${encounter.text("faction_theme")}")
    }
    private fun event(s:Session,e:SpireBattleEvent){
        when(e.name){
            "BATTLE_START"->{s.battle.players.forEach{pl->s.effects.enter(pl.active,s.battle.enemies)};s.battle.enemies.forEach{s.effects.enter(it,s.battle.players.map{a->a.active})}}
            "ROUND_START"->{s.choices=s.battle.enemies.filter{it.alive}.associate{actor->actor.uid to s.ai.choose(actor,s.profiles.getValue(actor.uid),s.battle,s.actors.getValue(actor.uid).nativeMoves.map{it.name}.toSet())};s.battle.intents=s.battle.enemies.mapIndexedNotNull{i,a->s.choices[a.uid]?.let{EnemyIntent(i,it.move)}}}
            "ENEMY_MOVE_STARTED"->s.choices[e.actor]?.let{s.ai.used(it,s.battle.round)}
            "PROGRESSION"->{s.points+=2}
            "BATTLE_WON"->{
                s.members.forEachIndexed{slot,m->
                    if(m.loot==null){val rng=Random(s.seed+s.battleNumber*117L+slot*31)
                        val offers=m.party.mapIndexed{owner,a->
                            val entry=catalogueActors.first{it.id==a.body.species}
                            val legal=bridge.legalMoves(entry).filter{bridge.moveReason(it)==null}
                            val fresh=legal.filter{mv->a.body.deck!!.original.none{it.move.id==mv.name}}
                            val chosen=(fresh.ifEmpty{legal}).shuffled(rng).first()
                            LootCard(owner,MoveInstance(-s.battleNumber.toLong()*100-slot*10-owner-1,MoveCompatibility.move(chosen.name)))
                        }
                        m.loot=VictoryLoot(s.battleNumber,offers)
                    }
                }
            }
        }
        if(e.name in setOf("MOVE_HIT","MOVE_MISS","AFTER_DAMAGE_TAKEN","NORMAL_SWITCH","FORCED_SWITCH","BATTLE_WON","BATTLE_LOST")){
            val actor=s.actors[e.actor]?.name.orEmpty();val target=s.actors[e.target]?.name.orEmpty()
            val line=when(e.name){"MOVE_HIT"->if(e.amount>0)"$actor → $target ${e.amount} 피해" else "$actor 기술 효과 처리";"AFTER_DAMAGE_TAKEN"->if(e.amount>0)"$target ${e.amount} 피해" else "$actor 기술 효과 처리";"MOVE_MISS"->"$actor 기술 실패·무효";"BATTLE_WON"->"승리 · 진행점수 +2 (능력치 고정)";"BATTLE_LOST"->"모든 포켓몬 기절 · 패배";else->"$target 출전"}
            s.log.addLast(line);while(s.log.size>12)s.log.removeFirst()
        }
        runCatching{
            val path=FabricLoader.getInstance().gameDir.resolve("logs/cobblespire/trials/${s.id}.jsonl");Files.createDirectories(path.parent)
        Files.writeString(path,SpireMod.gson.toJson(JsonObject().apply{addProperty("move",e.moveId);addProperty("type",e.moveType);addProperty("multiplier",e.multiplier);addProperty("seed",s.seed);addProperty("battle",s.battleNumber);addProperty("event",e.name);addProperty("round",e.round);addProperty("actor",e.actor);addProperty("target",e.target);addProperty("amount",e.amount)}).replace("\n","")+"\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND)
        }.onFailure{if(!s.logFailureReported){s.logFailureReported=true;SpireMod.logger.warn("Trial trace could not be written; in-memory events are retained",it)}}
    }
    private fun card(a:PokemonBridge.Actor)=JsonObject().apply{
        addProperty("uid",a.body.uid);addProperty("species",a.body.species);addProperty("name",a.name);addProperty("hp",a.body.hp);addProperty("maxHp",a.body.maxHp);addProperty("ability",a.ability)
        add("aspects",JsonArray().also{v->a.aspects.forEach{v.add(it)}});add("types",JsonArray().also{v->a.body.types.forEach{v.add(it)}})
        add("stats",JsonObject().also{v->a.stats.forEach{(k,n)->v.addProperty(k,n)}});add("stages",JsonObject().also{v->a.body.stages.forEach{(k,n)->v.addProperty(k,n)}})
        add("statuses",JsonArray().also{v->a.body.statuses.values.forEach{v.add(it.id+if(it.remaining>0)" ${it.remaining}" else "")}})
    }
    private fun cardInfo(m:MoveInstance,fx:NativeEffects)=JsonObject().apply{
        addProperty("instance",m.uid);addProperty("move",m.move.id);addProperty("cost",fx.cost(m))
        addProperty("trainer",m.move.trainer);addProperty("exhaust",m.move.exhaust);addProperty("retain",m.retain);addProperty("upgraded",m.upgraded)
        val role=CardSemantics.role(m.move);addProperty("role",role.name)
        addProperty("draw",fx.drawCount(m));addProperty("selfTarget",m.move.selfTarget);addProperty("targetAll",m.move.targetAll)
        addProperty("effectSummary",CardSemantics.summary(m.move,fx.drawCount(m)))
        if(role==CardRole.ATTACK){
            addProperty("type",m.move.type);addProperty("accuracy",m.move.accuracy);addProperty("power",m.move.power)
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
            addProperty("progress",s.points);addProperty("points",s.points);addProperty("energy",player.energy);addProperty("active",player.activeIndex);addProperty("forced",player.forcedSwitch);addProperty("switchUsed",player.normalSwitchUsed);addProperty("battleNumber",s.battleNumber);addProperty("encounter",s.encounter)
            addProperty("act",s.act);addProperty("tier",s.tier);addProperty("partySize",s.members.size);addProperty("isHost",s.host==member.player)
            addProperty("ended",player.ended);addProperty("endedCount",b.players.count{it.alive&&it.ended});addProperty("alivePlayers",b.players.count{it.alive})
            add("party",JsonArray().also{a->member.party.forEach{a.add(card(it))}})
            add("allies",JsonArray().also{a->s.members.filter{it.player!=member.player}.forEach{ally->
                val bp=b.players.first{it.id==ally.player}
                a.add(JsonObject().apply{
                    addProperty("name",ally.name);addProperty("energy",bp.energy);addProperty("ended",bp.ended);addProperty("alive",bp.alive)
                    add("active",card(ally.party[bp.activeIndex]))
                })
            }})
            add("arenaAllies",JsonArray().also{a->s.members.forEach{m->val bp=b.players.firstOrNull{it.id==m.player};if(bp!=null&&bp.alive)a.add(card(m.party[bp.activeIndex]))}})
            add("enemies",JsonArray().also{a->b.enemies.forEach{enemy->val c=card(s.actors.getValue(enemy.uid));s.choices[enemy.uid]?.let{choice->
                val move=choice.move;val role=CardSemantics.role(move)
                c.addProperty("intent",move.id);c.addProperty("intentRole",role.name);c.addProperty("intentEffect",CardSemantics.summary(move))
                c.addProperty("selfTarget",move.selfTarget);c.addProperty("targetAll",move.targetAll)
                if(role==CardRole.ATTACK){
                    c.addProperty("intentType",move.type);c.addProperty("intentPower",move.power)
                    c.addProperty("intentAccuracy",s.effects.accuracy(enemy,player.active,move))
                    val damage=s.effects.estimate(enemy,player.active,move,spread=move.targetAll&&b.players.count{it.active.alive}>1)
                    c.addProperty("minDamage",damage.first.coerceAtMost(player.active.hp));c.addProperty("maxDamage",damage.second.coerceAtMost(player.active.hp))
                    c.addProperty("multiplier",MoveCompatibility.multiplier(move.type,player.active.types))
                }
            };a.add(c)}})
            add("damageEvents",JsonArray().also{a->b.events.withIndex().filter{it.value.name in setOf("MOVE_HIT","AFTER_DAMAGE_TAKEN","MOVE_MISS")}.takeLast(8).forEach{(index,e)->a.add(JsonObject().apply{
                addProperty("role",CardSemantics.role(e.moveId).name);addProperty("incoming",e.target.startsWith("player:"));addProperty("seq",index+1);addProperty("actor",s.actors[e.actor]?.name.orEmpty());addProperty("target",s.actors[e.target]?.name.orEmpty());addProperty("amount",e.amount)
                addProperty("move",e.moveId);addProperty("type",e.moveType);addProperty("multiplier",e.multiplier);addProperty("miss",e.name=="MOVE_MISS")
            })}})
            member.loot?.let{loot->add("loot",JsonObject().apply{
                addProperty("settled",loot.settled);addProperty("allSettled",s.members.all{it.loot?.settled==true});addProperty("selected",loot.selected?:-2);addProperty("progressAward",2);addProperty("revivedCount",member.party.count{it.body.uid !in b.winningSurvivors})
                add("offers",JsonArray().also{a->loot.offers.forEach{offer->a.add(cardInfo(offer.card,s.effects).apply{addProperty("owner",offer.owner);addProperty("ownerName",member.party[offer.owner].name)})}})
            })}
            addProperty("evolutionCredits",s.evolution.available().size);add("badges",JsonArray().also{a->s.evolution.earnedBadges().forEach{a.add(it)}})
            val deck=player.active.deck!!
            addProperty("deckOwner",player.active.uid);addProperty("handLimit",deck.handLimit);addProperty("motionSequence",deck.sequence)
            add("hand",JsonArray().also{a->deck.hand.forEach{m->a.add(cardInfo(m,s.effects).apply{
                if(CardSemantics.role(m.move)==CardRole.ATTACK)add("previews",JsonArray().also{out->b.enemies.forEach{foe->
                    val damage=s.effects.estimate(player.active,foe,m.move,m.upgraded,m.move.targetAll&&b.enemies.count{it.alive}>1)
                    out.add(JsonObject().apply{addProperty("accuracy",s.effects.accuracy(player.active,if(m.move.selfTarget)player.active else foe,m.move));addProperty("targetName",s.actors[if(m.move.selfTarget)player.active.uid else foe.uid]?.name.orEmpty());addProperty("minDamage",damage.first.coerceAtMost(foe.hp));addProperty("maxDamage",damage.second.coerceAtMost(foe.hp));addProperty("multiplier",MoveCompatibility.multiplier(m.move.type,foe.types))})
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
}
