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
        ServerLifecycleEvents.SERVER_STOPPED.register{trials?.clear();trials=null;catalogue=null}
        ServerPlayNetworking.registerGlobalReceiver(SpirePayload.TYPE){payload,ctx->ctx.server().execute{
            runCatching {require(payload.json.length<=8192){"요청이 너무 큽니다."};val j=JsonParser.parseString(payload.json).asJsonObject
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
        fun send(player:ServerPlayer,data:com.google.gson.JsonObject){if(ServerPlayNetworking.canSend(player,SpirePayload.TYPE))ServerPlayNetworking.send(player,SpirePayload(data.toString()))}
    }
}
