package dev.cobblespire.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.Music
import net.minecraft.sounds.SoundEvent

/**
 * Location- and encounter-based BGM & jingle controller.
 * Sound events are declared in assets/cobblespire/sounds.json and backed by bundled .ogg tracks.
 */
object SpireMusic {
    enum class Track(val id:String){
        LOBBY("bgm.lobby_prep"),
        MAP_ACT1("bgm.map_act1"),MAP_ACT2_MAGMA("bgm.map_act2_magma"),MAP_ACT2_AQUA("bgm.map_act2_aqua"),MAP_ACT3("bgm.map_act3_galaxy"),
        BATTLE_ACT1("bgm.battle_act1"),BATTLE_ACT2_MAGMA("bgm.battle_act2_magma"),BATTLE_ACT2_AQUA("bgm.battle_act2_aqua"),BATTLE_ACT3("bgm.battle_act3_galaxy"),
        BATTLE_ELITE("bgm.battle_elite"),
        BOSS_ACT1("bgm.boss_act1"),BOSS_ACT2_MAGMA("bgm.boss_act2_magma"),BOSS_ACT2_AQUA("bgm.boss_act2_aqua"),BOSS_ACT3("bgm.boss_act3_finale"),
        EVENT_MYSTERY("bgm.event_mystery"),FACILITY_SHOP("bgm.facility_shop"),FACILITY_REST("bgm.facility_rest"),RUN_VICTORY("bgm.run_victory")
    }
    enum class Jingle(val id:String){BATTLE_WIN("jingle.battle_win"),ACT_CLEAR("jingle.act_clear"),RUN_DEFEAT("jingle.run_defeat")}

    private fun event(namespace:String,id:String)=SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(namespace,id))
    private val music by lazy{Track.entries.associateWith{Music(Holder.direct(event("cobblespire",it.id)),0,0,true)}}
    private var current:Track?=null
    private var battle:Track=Track.BATTLE_ACT1
    private var activeJingle:SoundInstance?=null

    /** Set by map/shop/rest/event/victory screens; takes priority over screen detection. */
    var facility:Track?=null

    private val elite=setOf("ACT1-R04","ACT1-R05","ACT2-M03","ACT2-A03","ACT3-G03","ACT3-G04")
    private val boss=setOf("ACT1-R06","ACT1-R07","ACT2-M04","ACT2-A04","ACT3-G05","ACT3-G06")
    private val actClearBosses=setOf(Track.BOSS_ACT1,Track.BOSS_ACT2_MAGMA,Track.BOSS_ACT2_AQUA)
    fun battleTrack(encounter:String):Track=when{
        encounter in boss->when{encounter.startsWith("ACT2-M")->Track.BOSS_ACT2_MAGMA;encounter.startsWith("ACT2-A")->Track.BOSS_ACT2_AQUA;encounter.startsWith("ACT3")->Track.BOSS_ACT3;else->Track.BOSS_ACT1}
        encounter in elite->Track.BATTLE_ELITE
        encounter.startsWith("ACT2-M")->Track.BATTLE_ACT2_MAGMA
        encounter.startsWith("ACT2-A")->Track.BATTLE_ACT2_AQUA
        encounter.startsWith("ACT3")->Track.BATTLE_ACT3
        else->Track.BATTLE_ACT1
    }
    fun onBattle(encounter:String){facility=null;battle=battleTrack(encounter)}
    fun enterFacility(track:Track){
        facility=track
        if(track==Track.FACILITY_REST)pokemonCenterHeal()
    }
    fun exitFacility(){facility=null}
    fun onBattleResult(won:Boolean,revived:Boolean){
        if(!won){jingle(Jingle.RUN_DEFEAT);return}
        if(battle==Track.BOSS_ACT3)facility=Track.RUN_VICTORY
        else jingle(if(battle in actClearBosses)Jingle.ACT_CLEAR else Jingle.BATTLE_WIN)
        victoryFanfare()
        if(revived)pokemonCenterHeal()
    }
    fun clear(){
        val mc=Minecraft.getInstance()
        stopJingle(mc)
        mc.musicManager.stopPlaying()
        current=null;facility=null;battle=Track.BATTLE_ACT1
    }

    fun tick(mc:Minecraft){
        val desired=facility?:when(mc.screen){
            is SpireMenuScreen,is RegistryScreen->Track.LOBBY
            is BattleResultScreen->null // the result jingle owns this moment
            is SpireBattleScreen,is PileScreen->battle
            else->if(WorldBattleView.inArena(mc))battle else null
        }
        val manager=mc.musicManager
        if(desired!=null){
            stopJingle(mc)
            val m=music.getValue(desired)
            if(current!=desired||!manager.isPlayingMusic(m)){manager.stopPlaying();manager.startPlaying(m);current=desired}
        }else if(current!=null){manager.stopPlaying();current=null}
    }

    fun jingle(j:Jingle){
        val mc=Minecraft.getInstance();stopJingle(mc);mc.musicManager.stopPlaying();current=null
        val instance=SimpleSoundInstance.forMusic(event("cobblespire",j.id))
        activeJingle=instance
        mc.soundManager.play(instance)
    }
    private fun stopJingle(mc:Minecraft){
        activeJingle?.let{mc.soundManager.stop(it);activeJingle=null}
    }
    /** Cobblemon's own Pokémon Center healing-machine chime (shipped by Cobblemon itself). */
    fun pokemonCenterHeal()=ui("cobblemon","block.healing_machine.active")
    /** Cobblemon's own level-up fanfare, used as the built-in victory flourish. */
    fun victoryFanfare()=ui("cobblemon","gui.levelup")
    private fun ui(namespace:String,id:String){Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event(namespace,id),1f))}
}
