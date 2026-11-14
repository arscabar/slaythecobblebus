package dev.cobblespire
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

class SpireMod:ModInitializer {
    override fun onInitialize(){
        Definitions.validate();SpirePayload.register()
        ServerLifecycleEvents.SERVER_STARTED.register{server->catalogue=PokemonCatalogue().also{it.reload();it.writeAudit()};trials=TrialService(server,requireNotNull(catalogue));trials!!.writeAudit();logger.info("CobbleSpire registry: {} forms",catalogue!!.entries.size)}
        ServerLifecycleEvents.SERVER_STOPPING.register{trials?.clear()}
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register{trials?.arena?.tick()}
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register{handler,_,server->
            // Defer reconnect or arena return teleport until after PlayerList.placeNewPlayer finishes addNewPlayer.
            val id=handler.player.uuid
            server.execute{server.playerList.getPlayer(id)?.let{if(!it.hasDisconnected()&&trials?.onReconnect(it)!=true)trials?.arena?.recover(it)}}
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register{handler,_->trials?.onDisconnect(handler.player)}
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register{entity,level->
            if(WorldArena.inside(level)&&entity !is ServerPlayer&&trials?.arena?.presentation(entity.uuid)!=true)entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
        }
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register{entity,_,_->!WorldArena.inside(entity.level())}
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register{level,_,_,_,_->!WorldArena.inside(level)}
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register{player,level,hand->if(WorldArena.inside(level))net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand))else net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand))}
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register{_,level,_,_->if(WorldArena.inside(level))net.minecraft.world.InteractionResult.FAIL else net.minecraft.world.InteractionResult.PASS}
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register{_,level,_,_,_->if(WorldArena.inside(level))net.minecraft.world.InteractionResult.FAIL else net.minecraft.world.InteractionResult.PASS}
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register{_,level,_,_,_->if(WorldArena.inside(level))net.minecraft.world.InteractionResult.FAIL else net.minecraft.world.InteractionResult.PASS}
        ServerLifecycleEvents.SERVER_STOPPED.register{trials?.clear();trials=null;catalogue=null}
        ServerPlayNetworking.registerGlobalReceiver(SpirePayload.TYPE){payload,ctx->ctx.server().execute{
            runCatching {require(payload.json.length<=8192){"요청이 너무 큽니다."};val j=JsonParser.parseString(payload.json).asJsonObject;require(j.number("protocol")==6){"서버와 클라이언트의 CobbleSpire 버전을 맞춰주세요."}
                requireNotNull(trials).handle(ctx.player(),j)
            }.onFailure{send(ctx.player(),com.google.gson.JsonObject().apply{addProperty("kind","error");addProperty("message",it.message?:"요청을 처리하지 못했습니다.")})}
        }}
        com.cobblemon.mod.common.api.events.CobblemonEvents.BATTLE_STARTED_PRE.subscribe{event->
            if(event.battle.actors.any{actor->trials?.inBattle(actor.uuid)==true}){
                event.reason=Component.literal("CobbleSpire 시험 전투를 종료한 뒤 일반 대전을 시작하세요.");event.cancel()
            }
        }
        CommandRegistrationCallback.EVENT.register{d,_,_->d.register(Commands.literal("cobblespire").executes{requireNotNull(trials).handle(it.source.playerOrException,com.google.gson.JsonObject().apply{addProperty("action","open")});1}.then(Commands.literal("audit").requires{it.hasPermission(2)}.executes{catalogue?.writeAudit();it.source.sendSuccess({Component.literal("CobbleSpire registry audit written")},false);1}))}
    }
    companion object {
        val logger=LoggerFactory.getLogger("cobblespire");val gson=GsonBuilder().setPrettyPrinting().create()
        var catalogue:PokemonCatalogue?=null
        var trials:TrialService?=null
        fun send(player:ServerPlayer,data:com.google.gson.JsonObject){data.addProperty("protocol",6);if(ServerPlayNetworking.canSend(player,SpirePayload.TYPE))ServerPlayNetworking.send(player,SpirePayload(data.toString()))}
    }
}
