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

/** Development milestone: solo, server-authoritative battle UI. No real party ownership changes. */
class TrialService(private val server:MinecraftServer,val catalogue:PokemonCatalogue){
    val bridge=PokemonBridge(catalogue)
    private data class Session(val player:UUID,val id:UUID,val seed:Long,val party:List<PokemonBridge.Actor>,var points:Int=0,var battleNumber:Int=0,var activeIndex:Int=0){
        lateinit var battle:SpireBattle;lateinit var effects:NativeEffects;lateinit var ai:IntentRules
        var actors=emptyMap<String,PokemonBridge.Actor>();var profiles=emptyMap<String,JsonObject>();var choices=emptyMap<String,IntentRules.Choice>()
        val log=ArrayDeque<String>()
        val evolution=EvolutionAccess()
        var logFailureReported=false
    }
    private val sessions=mutableMapOf<UUID,Session>()
    private val catalogueActors by lazy{catalogue.entries.filter{it.formName==it.species.standardForm.name}.sortedWith(compareBy<PokemonCatalogue.Entry>{Definitions.characters.none{d->Definitions.speciesId(d.text("species_id"))==it.id}}.thenBy{it.id})}
    private val lastAction=mutableMapOf<UUID,Int>()
    fun inBattle(id:UUID)=sessions[id]?.battle?.phase in setOf(SpireBattlePhase.PLAYER,SpireBattlePhase.ENEMY)
    fun clear(){sessions.clear();lastAction.clear()}
    fun writeAudit(){val f=FabricLoader.getInstance().gameDir.resolve("logs/cobblespire/move-compatibility.json");Files.createDirectories(f.parent);Files.writeString(f,SpireMod.gson.toJson(bridge.audit()))}
    fun handle(p:ServerPlayer,j:JsonObject){
        require(j.text("action").length<=32)
        val action=j.text("action")
        if(action in setOf("move","switch","end","next","start")){
            require(server.tickCount-(lastAction[p.uuid]?:-999)>=2){"이전 요청 처리 후 다시 시도하세요."};lastAction[p.uuid]=server.tickCount
        }
        when(action){
            "open"->sessions[p.uuid]?.let{send(p,it,true)}?:menu(p,j)
            "catalog"->SpireMod.send(p,catalogue.page(j.text("query").take(64),j.number("page")))
            "menu"->menu(p,j)
            "start"->start(p,j.text("entry"))
            "quit"->{requireSession(p,j);sessions.remove(p.uuid);menu(p,JsonObject())}
            "move","switch","end","next"->{
                val s=requireSession(p,j);val b=s.battle
                when(action){
                    "move"->b.useMove(p.uuid,j.number("handIndex",-1),j.get("instance")?.asLong?:-1,j.number("target",-1),j.text("request"),j.number("revision",-1))
                    "switch"->{b.switch(p.uuid,j.number("index",-1),j.text("request"),j.number("revision",-1));s.activeIndex=b.players.single().activeIndex}
                    "end"->b.endTurn(p.uuid,j.text("request"),j.number("revision",-1))
                    "next"->{require(j.number("revision",-1)==b.revision&&b.phase==SpireBattlePhase.VICTORY);require(BattleRegistry.getBattleByParticipatingPlayer(p)==null){"일반 전투가 끝난 뒤 진행하세요."};begin(s)}
                }
                send(p,s,false)
            }
            else->error("지원하지 않는 요청입니다.")
        }
    }
    private fun requireSession(p:ServerPlayer,j:JsonObject):Session{
        val s=requireNotNull(sessions[p.uuid]){"진행 중인 시험 전투가 없습니다."};require(j.number("battleNumber",-1)==s.battleNumber){"이전 전투의 요청입니다."};require(j.text("session")==s.id.toString()){"이전 모험의 요청입니다."};return s
    }
    private fun menu(p:ServerPlayer,j:JsonObject){
        val query=j.text("query").take(64);val extended=j.get("extended")?.asBoolean==true
        val filtered=catalogueActors.filter{e->val design=Definitions.characters.firstOrNull{Definitions.speciesId(it.text("species_id"))==e.id};
            val starter=e.species.standardForm.types.any{it.name.lowercase() in setOf("fire","water","grass")}
            starter&&(extended||design!=null)&&(query.isBlank()||e.id.contains(query,true)||(design?.text("name_ko")?:e.species.translatedName.string).contains(query,true))}
        val page=j.number("page").coerceIn(0,((filtered.size-1)/8).coerceAtLeast(0))
        SpireMod.send(p,JsonObject().apply{addProperty("kind","menu");addProperty("query",query);addProperty("extended",extended);addProperty("page",page);addProperty("total",filtered.size)
            add("entries",JsonArray().also{a->filtered.drop(page*8).take(8).forEach{e->a.add(e.json().apply{addProperty("reason",bridge.reason(e).orEmpty());addProperty("ability",bridge.ability(e).orEmpty());addProperty("design",Definitions.characters.any{Definitions.speciesId(it.text("species_id"))==e.id});add("types",JsonArray().also{t->e.species.standardForm.types.forEach{t.add(it.name.lowercase())}})})}})
        })
    }
    private fun start(p:ServerPlayer,key:String){
        require(p.uuid !in sessions){"기존 시험 전투를 먼저 종료하세요."}
        require(BattleRegistry.getBattleByParticipatingPlayer(p)==null){"일반 포켓몬 전투가 끝난 뒤 시작하세요."}
        val otherDraft=runCatching{val c=Class.forName("dev.cobbleleague.LeagueMod").getField("Companion").get(null);val service=c.javaClass.getMethod("getService").invoke(c);service?.javaClass?.getMethod("draftParty",UUID::class.java)?.invoke(service,p.uuid)!=null}.getOrDefault(false)
        require(!otherDraft){"리그 도전을 끝낸 뒤 시작하세요."}
        val entry=requireNotNull(catalogueActors.firstOrNull{it.key==key})
        require(entry.species.standardForm.types.any{it.name.lowercase() in setOf("fire","water","grass")}){"불꽃·물·풀 대표를 선택하세요."}
        require(bridge.reason(entry)==null){bridge.reason(entry).orEmpty()}
        val seed=SecureRandom().nextLong();val rng=Random(seed);val selected=mutableListOf(entry)
        for(e in catalogueActors.shuffled(rng).sortedBy{v->Definitions.characters.none{Definitions.speciesId(it.text("species_id"))==v.id}}){
            if(selected.size==3)break
            if(e.id in selected.map{it.id}||bridge.reason(e)!=null)continue
            val types=(selected+e).map{it.species.standardForm.types.map{t->t.name.lowercase()}.toSet()}
            if(SpirePartyRules.disjoint(types))selected.add(e)
        }
        require(selected.size==3){"타입이 겹치지 않는 지원 파트너가 부족합니다."}
        val party=selected.mapIndexed{i,e->bridge.create(e,"player:$i",seed+i)}
        val s=Session(p.uuid,UUID.randomUUID(),seed,party);begin(s);sessions[p.uuid]=s;send(p,s,true,true)
    }
    private fun begin(s:Session){
        s.battleNumber++;val encounter=Definitions.data.getAsJsonArray("encounters").map{it.asJsonObject}.first{it.text("encounter_id")=="ACT1-R0${(s.battleNumber-1)%3+1}"}
        val profiles=Definitions.enemies.associateBy{it.text("enemy_id")}
        val instances=encounter.getAsJsonArray("enemy_instances").map{it.asJsonObject}
        val hp=SpirePartyRules.hpBudget(180,1.0,instances.map{it.decimal("threat_weight",1.0)},1)
        val enemyActors=instances.mapIndexed{i,e->val d=profiles.getValue(e.text("enemy_id"));val entry=catalogueActors.first{it.id==Definitions.speciesId(d.text("species_id"))}
            bridge.create(entry,"enemy:$i",s.seed+s.battleNumber*100+i,enemyHp=hp[i],allowed=d.getAsJsonArray("move_pool").map{it.asJsonObject.text("id")})}
        s.party.forEachIndexed{i,a->a.body.deck=a.body.deck?.let{BattleDeck(it.original,Random(s.seed+s.battleNumber*1000+i))}}
        s.actors=(s.party+enemyActors).associateBy{it.body.uid};s.profiles=enemyActors.mapIndexed{i,a->a.body.uid to profiles.getValue(instances[i].text("enemy_id"))}.toMap()
        s.ai=IntentRules(s.seed+s.battleNumber*913);s.effects=NativeEffects(s.actors,s.seed+s.battleNumber*701)
        s.battle=SpireBattle(listOf(SpireBattlePlayer(s.player,s.party.map{it.body}).also{it.activeIndex=s.activeIndex}),enemyActors.map{it.body},s.seed+s.battleNumber,effectiveness=MoveCompatibility::multiplier,hook=SpireBattleHook{e,_->event(s,e)},effects=s.effects)
        s.battle.start();s.log.addLast("시험 전투 ${s.battleNumber} · ${encounter.text("faction_theme")}")
    }
    private fun event(s:Session,e:SpireBattleEvent){
        when(e.name){
            "BATTLE_START"->{s.effects.enter(s.battle.players.single().active,s.battle.enemies);s.battle.enemies.forEach{s.effects.enter(it,s.battle.players.map{a->a.active})}}
            "ROUND_START"->{s.choices=s.battle.enemies.filter{it.alive}.associate{actor->actor.uid to s.ai.choose(actor,s.profiles.getValue(actor.uid),s.battle,s.actors.getValue(actor.uid).nativeMoves.map{it.name}.toSet())};s.battle.intents=s.battle.enemies.mapIndexedNotNull{i,a->s.choices[a.uid]?.let{EnemyIntent(i,it.move)}}}
            "ENEMY_MOVE_STARTED"->s.choices[e.actor]?.let{s.ai.used(it,s.battle.round)}
            "PROGRESSION"->{s.points+=2}
        }
        if(e.name in setOf("MOVE_HIT","MOVE_MISS","AFTER_DAMAGE_TAKEN","NORMAL_SWITCH","FORCED_SWITCH","BATTLE_WON","BATTLE_LOST")){
            val actor=s.actors[e.actor]?.name.orEmpty();val target=s.actors[e.target]?.name.orEmpty()
            val line=when(e.name){"MOVE_HIT"->if(e.amount>0)"$actor → $target ${e.amount} 피해" else "$actor 기술 효과 처리";"AFTER_DAMAGE_TAKEN"->if(e.amount>0)"$target ${e.amount} 피해" else "$actor 기술 효과 처리";"MOVE_MISS"->"$actor 기술 실패·무효";"BATTLE_WON"->"승리 · 진행점수 +2 (능력치 고정)";"BATTLE_LOST"->"모든 포켓몬 기절 · 패배";else->"$target 출전"}
            s.log.addLast(line);while(s.log.size>12)s.log.removeFirst()
        }
        runCatching{
            val path=FabricLoader.getInstance().gameDir.resolve("logs/cobblespire/trials/${s.id}.jsonl");Files.createDirectories(path.parent)
        Files.writeString(path,SpireMod.gson.toJson(JsonObject().apply{addProperty("seed",s.seed);addProperty("battle",s.battleNumber);addProperty("event",e.name);addProperty("round",e.round);addProperty("actor",e.actor);addProperty("target",e.target);addProperty("amount",e.amount)}).replace("\n","")+"\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND)
        }.onFailure{if(!s.logFailureReported){s.logFailureReported=true;SpireMod.logger.warn("Trial trace could not be written; in-memory events are retained",it)}}
    }
    private fun card(a:PokemonBridge.Actor)=JsonObject().apply{
        addProperty("uid",a.body.uid);addProperty("species",a.body.species);addProperty("name",a.name);addProperty("hp",a.body.hp);addProperty("maxHp",a.body.maxHp);addProperty("ability",a.ability)
        add("aspects",JsonArray().also{v->a.aspects.forEach{v.add(it)}});add("types",JsonArray().also{v->a.body.types.forEach{v.add(it)}})
        add("stats",JsonObject().also{v->a.stats.forEach{(k,n)->v.addProperty(k,n)}});add("stages",JsonObject().also{v->a.body.stages.forEach{(k,n)->v.addProperty(k,n)}})
        add("statuses",JsonArray().also{v->a.body.statuses.values.forEach{v.add(it.id+if(it.remaining>0)" ${it.remaining}" else "")}})
    }
    private fun cardInfo(m:MoveInstance,fx:NativeEffects)=JsonObject().apply{
        addProperty("instance",m.uid);addProperty("move",m.move.id);addProperty("cost",fx.cost(m));addProperty("type",m.move.type)
        addProperty("trainer",m.move.trainer);addProperty("exhaust",m.move.exhaust);addProperty("retain",m.retain);addProperty("upgraded",m.upgraded)
        addProperty("draw",fx.drawCount(m));addProperty("accuracy",m.move.accuracy)
    }
    fun snapshot(id:UUID):JsonObject?=sessions[id]?.let{buildSnapshot(it,false)}
    private fun send(p:ServerPlayer,s:Session,open:Boolean,fresh:Boolean=false){SpireMod.send(p,buildSnapshot(s,open).apply{addProperty("fresh",fresh)})}
    private fun buildSnapshot(s:Session,open:Boolean):JsonObject{
        val b=s.battle;val player=b.players.single();return JsonObject().apply{
            addProperty("kind","battle");addProperty("open",open);addProperty("session",s.id.toString());addProperty("revision",b.revision);addProperty("round",b.round);addProperty("phase",b.phase.name)
            addProperty("progress",s.points);addProperty("points",s.points);addProperty("energy",player.energy);addProperty("active",player.activeIndex);addProperty("forced",player.forcedSwitch);addProperty("switchUsed",player.normalSwitchUsed);addProperty("battleNumber",s.battleNumber)
            add("party",JsonArray().also{a->s.party.forEach{a.add(card(it))}})
            add("enemies",JsonArray().also{a->b.enemies.forEach{enemy->val c=card(s.actors.getValue(enemy.uid));s.choices[enemy.uid]?.let{choice->c.addProperty("intent",choice.move.id);val damage=s.effects.estimate(enemy,player.active,choice.move);c.addProperty("minDamage",damage.first);c.addProperty("maxDamage",damage.second)};a.add(c)}})
            addProperty("evolutionCredits",s.evolution.available().size);add("badges",JsonArray().also{a->s.evolution.earnedBadges().forEach{a.add(it)}})
            val deck=player.active.deck!!
            addProperty("deckOwner",player.active.uid);addProperty("handLimit",deck.handLimit);addProperty("motionSequence",deck.sequence)
            add("hand",JsonArray().also{a->deck.hand.forEach{a.add(cardInfo(it,s.effects))}})
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
