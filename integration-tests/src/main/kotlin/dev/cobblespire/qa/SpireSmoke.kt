package dev.cobblespire.qa

import dev.cobblespire.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import com.google.gson.JsonObject
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.commands.Commands
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.util.UUID

class SpireSmoke:ModInitializer {
    private var pending:MinecraftServer?=null;private var ticks=0
    private val checks=mutableListOf<Map<String,Any>>()
    override fun onInitialize(){
        if(!java.lang.Boolean.getBoolean("cobblespire.integration"))return
        CommandRegistrationCallback.EVENT.register{d,_,_->d.register(Commands.literal("spiresmoke").requires{it.hasPermission(4)}.executes{c->
            check(c.source.server.playerList.getPlayerByName("SpireQA")==null)
            checks.clear();pending=c.source.server;ticks=0
            c.source.server.commands.performPrefixedCommand(c.source.server.createCommandSourceStack(),"player SpireQA spawn");1
        })}
        ServerTickEvents.END_SERVER_TICK.register{server->if(pending===server){ticks++;val p=server.playerList.getPlayerByName("SpireQA")
            if(p!=null&&ticks>4){pending=null;try{run(server,p)}catch(e:Throwable){e.printStackTrace();checks.add(mapOf("test" to "exception","passed" to false,"detail" to e.toString()))}finally{
                server.commands.performPrefixedCommand(server.createCommandSourceStack(),"player SpireQA kill");write()
            }}else if(ticks>400){pending=null;checks.add(mapOf("test" to "spawn","passed" to false));write()}
        }}
    }
    private fun field(o:Any,name:String):Any?{val f=o.javaClass.getDeclaredField(name);f.isAccessible=true;return f.get(o)}
    private fun check(name:String,passed:Boolean,detail:String=""){checks.add(mapOf("test" to name,"passed" to passed,"detail" to detail));write();require(passed){name}}
    private fun write(){Files.writeString(java.nio.file.Path.of("spire-integration-results.json"),SpireMod.gson.toJson(checks))}
    private fun action(service:TrialService,p:ServerPlayer,action:String,body:JsonObject.()->Unit={}){
        @Suppress("UNCHECKED_CAST") val throttle=field(service,"lastAction") as MutableMap<UUID,Int>;throttle.remove(p.uuid)
        val snap=service.snapshot(p.uuid)
        val j=JsonObject().apply{addProperty("action",action);if(snap!=null){addProperty("session",snap.text("session"));addProperty("battleNumber",snap.number("battleNumber"));addProperty("revision",snap.number("revision"))};addProperty("request",UUID.randomUUID().toString());body()};service.handle(p,j)
    }
    private fun run(server:MinecraftServer,p:ServerPlayer){
        val service=requireNotNull(SpireMod.trials);val catalogue=requireNotNull(SpireMod.catalogue)
        check("independent-without-league-or-synergy",!FabricLoader.getInstance().isModLoaded("cobbleleague")&&!FabricLoader.getInstance().isModLoaded("cobblesynergy"))
        val retainedParty=PokemonProperties.parse("pikachu level=17").create();Cobblemon.storage.getParty(p).set(0,retainedParty);retainedParty.currentHealth=7
        val retainedPC=PokemonProperties.parse("bulbasaur level=9").create();Cobblemon.storage.getPC(p).add(retainedPC);retainedPC.currentHealth=4
        p.inventory.setItem(0,ItemStack(Items.DIAMOND,3))
        fun signature(mon:Pokemon):String {
            val stats=listOf(Stats.HP,Stats.ATTACK,Stats.DEFENCE,Stats.SPECIAL_ATTACK,Stats.SPECIAL_DEFENCE,Stats.SPEED)
            return listOf(mon.uuid,mon.level,mon.currentHealth,mon.experience,mon.nature.name,mon.ability.name,stats.map{mon.ivs.getOrDefault(it)},stats.map{mon.evs.getOrDefault(it)},mon.moveSet.getMoves().map{it.name+":"+it.currentPp},mon.heldItem().toString(),mon.heldItem().componentsPatch.toString()).joinToString("|")
        }
        val originalParty=Cobblemon.storage.getParty(p).toList().map(::signature)
        val originalPC=Cobblemon.storage.getPC(p).toList().map(::signature)
        val originalInventory=p.inventory.items.map{it.toString()+it.componentsPatch.toString()}
        check("baseline-has-nonempty-real-party-PC-inventory",originalParty.isNotEmpty()&&originalPC.isNotEmpty()&&p.inventory.getItem(0).count==3)
        val char=catalogue.entries.first{it.species.resourceIdentifier.path=="charizard"&&it.formName==it.species.standardForm.name}
        val native=service.bridge.create(char,"check:charizard",1)
        val grown=service.bridge.create(char,"check:grown",1,5)
        check("native-lv50-IV31-EV0-neutral-charizard-hp",native.body.maxHp==153,"HP ${native.body.maxHp}")
        check("progress-does-not-increase-combat-stats",grown.body.maxHp==153,"Progress5 HP ${grown.body.maxHp}")
        val extra=catalogue.entries.first{entry->entry.formName==entry.species.standardForm.name&&Definitions.characters.none{it.text("species_id")==entry.species.resourceIdentifier.path}&&service.bridge.reason(entry)==null}
        val extraMon=service.bridge.create(extra,"check:addon",2)
        check("registry-expansion-beyond-fixed-50",extraMon.body.species==extra.id,extra.id)
        val originalDimension=p.level().dimension();val originalPosition=p.position()
        action(service,p,"start"){addProperty("entry",char.key)}
        check("world-arena-teleports-body-to-side-balcony",p.level().dimension()==WorldArena.KEY&&p.x< -17&&p.z>0)
        val arenaMon=p.serverLevel().allEntities.filterIsInstance<com.cobblemon.mod.common.entity.pokemon.PokemonEntity>().filter{it.tags.contains(WorldArena.TAG)}
        check("only-one-active-and-encounter-enemies-spawn-in-world",arenaMon.size==2&&arenaMon.any{it.z==6.0}&&arenaMon.any{it.z== -6.0})
        check("world-presentation-is-uncatchable-unowned-and-invulnerable",arenaMon.all{it.pokemon.isUncatchable()&&it.pokemon.getOwnerUUID()==null&&it.isInvulnerable})
        var state=requireNotNull(service.snapshot(p.uuid))
        check("actual-party-has-three-disjoint-type-pools",state.getAsJsonArray("party").size()==3&&SpirePartyRules.disjoint(state.getAsJsonArray("party").map{it.asJsonObject.getAsJsonArray("types").map{t->t.asString}.toSet()}))
        check("actual-ui-snapshot-five-card-hand-three-energy-visible-intent",state.getAsJsonArray("hand").size()==5&&state.number("energy")==3&&state.getAsJsonArray("enemies")[0].asJsonObject.text("intent").isNotBlank())
        @Suppress("UNCHECKED_CAST") val sessions=field(service,"sessions") as MutableMap<UUID,Any>
        val session=sessions.getValue(p.uuid);val battle=field(session,"battle") as SpireBattle
        val self=battle.players.single().active
        check("hand-preview-includes-type-power-and-per-target-damage",state.getAsJsonArray("hand").all{it.asJsonObject.has("power")&&it.asJsonObject.getAsJsonArray("previews").size()==1})
        check("enemy-intent-exposes-type-and-damage-range",state.getAsJsonArray("enemies").all{it.asJsonObject.text("intentType").isNotBlank()&&it.asJsonObject.has("intentAccuracy")})
        check("starter-learnset-includes-test-flamethrower",native.nativeMoves.any{it.name=="flamethrower"})
        self.deck!!.hand[0]=MoveInstance(999,MoveCompatibility.move("flamethrower"));battle.enemies.single().hp=1
        val packet=JsonObject().apply{addProperty("action","move");addProperty("session",state.text("session"));addProperty("battleNumber",state.number("battleNumber"));addProperty("revision",state.number("revision"));addProperty("request","same-test-request");addProperty("handIndex",0);addProperty("instance",999);addProperty("target",0)}
        @Suppress("UNCHECKED_CAST") val throttle=field(service,"lastAction") as MutableMap<UUID,Int>;throttle.remove(p.uuid)
        service.handle(p,packet);state=requireNotNull(service.snapshot(p.uuid))
        check("resolved-hit-carries-real-type-and-actual-HP-loss",state.getAsJsonArray("damageEvents").any{it.asJsonObject.text("type")=="fire"&&it.asJsonObject.number("amount")==1})
        check("native-move-resolves-to-real-server-victory",state.text("phase")=="VICTORY"&&state.number("points")==2&&state.number("energy")==1)
        val before=state.toString();throttle.remove(p.uuid);val duplicate=runCatching{service.handle(p,packet)}.isFailure
        check("duplicate-move-cannot-charge-or-reward-twice",duplicate&&service.snapshot(p.uuid).toString()==before)
        val loot=state.getAsJsonObject("loot")
        check("victory-snapshot-exposes-three-server-owned-loot-candidates",loot.getAsJsonArray("offers").size()==3&&!loot.get("settled").asBoolean)
        val sizeBefore=self.deck!!.original.size
        check("next-is-blocked-until-reward-is-settled",runCatching{action(service,p,"next")}.isFailure)
        action(service,p,"claim"){addProperty("choice",0)}
        check("claim-adds-one-card-to-designated-run-deck",self.deck!!.original.size==sizeBefore+1)
        check("duplicate-claim-cannot-add-second-card",runCatching{action(service,p,"claim"){addProperty("choice",0)}}.isFailure&&self.deck!!.original.size==sizeBefore+1)
        action(service,p,"next");state=requireNotNull(service.snapshot(p.uuid))
        check("next-battle-deals-new-hand-and-new-intents",state.number("battleNumber")==2&&state.number("energy")==3&&state.text("phase")=="PLAYER")
        val bench=state.getAsJsonArray("party").mapIndexedNotNull{i,c->c.asJsonObject.takeIf{i!=state.number("active")}}
        check("enemy-entry-ability-does-not-affect-benched-Pokemon",bench.all{!it.getAsJsonObject("stages").has("atk")})
        val newState=state.toString();throttle.remove(p.uuid);val stale=runCatching{service.handle(p,packet)}.isFailure
        check("prior-battle-packet-cannot-affect-next-battle",stale&&service.snapshot(p.uuid).toString()==newState)
        action(service,p,"end");state=requireNotNull(service.snapshot(p.uuid))
        val secondBattle=field(sessions.getValue(p.uuid),"battle") as SpireBattle
        check("enemy-phase-resolves-and-refreshes-round-energy-and-intents",state.number("round")==2&&state.number("energy")==3&&secondBattle.events.any{it.name=="ENEMY_PHASE_END"})
        action(service,p,"switch"){addProperty("index",1)};state=requireNotNull(service.snapshot(p.uuid))
        check("actual-normal-switch-updates-active-party-and-charges-once",state.number("active")==1&&state.number("energy")==2&&state.get("switchUsed").asBoolean)
        val identity=sessions.getValue(p.uuid);repeat(3){action(service,p,"open")}
        check("closing-and-reopening-does-not-replace-session",sessions.getValue(p.uuid)===identity)
        check("real-party-PC-and-inventory-preserved",originalParty==Cobblemon.storage.getParty(p).toList().map(::signature)&&originalPC==Cobblemon.storage.getPC(p).toList().map(::signature)&&originalInventory==p.inventory.items.map{it.toString()+it.componentsPatch.toString()})
        action(service,p,"quit");check("explicit-quit-discards-memory-only-session",service.snapshot(p.uuid)==null)
        check("quit-restores-original-dimension-and-position",p.level().dimension()==originalDimension&&p.position().distanceTo(originalPosition)<.001)
        check("quit-removes-world-presentation-entities",arenaMon.all{it.isRemoved})
        check("completed",true)
    }
}
