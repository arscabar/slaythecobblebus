package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import org.lwjgl.glfw.GLFW

/** The camera is detached; the real player remains on the arena's side balcony. */
object WorldBattleView {
    private data class AttackAnim(val actorId:Int,val targetId:Int,val type:String,val role:String,val category:String,val miss:Boolean,val multiplier:Double=1.0,var ticksLeft:Int=12,val totalTicks:Int=12,var baseX:Double=0.0,var baseY:Double=0.0,var baseZ:Double=0.0,var initialized:Boolean=false)
    private var arena:JsonObject?=null
    private var camera:ArmorStand?=null
    private var previousType:CameraType?=null
    private var previousHideGui:Boolean?=null
    private var yawOffset=0f;private var pitchOffset=0f;private var zoomOffset=0f
    private var flyX=0.0;private var flyY=0.0;private var flyZ=0.0
    private var shakeTicks=0;private var shakeStrength=0f
    private var lastBaseYaw=0f;private var lastBasePitch=0f
    private var lastBattleKey="";private var lastEventSeq=0
    private val anims=mutableListOf<AttackAnim>()
    private fun applyCameraPose(e:ArmorStand,d:JsonObject){
        val mc=Minecraft.getInstance()
        lastBaseYaw=d.get("yaw").asFloat
        lastBasePitch=d.get("pitch").asFloat+(if(mc.window.guiScaledHeight<316)8f else 0f)
        val yaw=lastBaseYaw+yawOffset;val pitch=(lastBasePitch+pitchOffset).coerceIn(-89f,89f)
        val yr=Math.toRadians(yaw.toDouble());val pr=Math.toRadians(pitch.toDouble())
        val fx=-kotlin.math.sin(yr)*kotlin.math.cos(pr);val fy=-kotlin.math.sin(pr);val fz=kotlin.math.cos(yr)*kotlin.math.cos(pr)
        e.setPos(d.get("cameraX").asDouble+flyX+fx*zoomOffset,d.get("cameraY").asDouble+flyY-e.eyeHeight+fy*zoomOffset,d.get("cameraZ").asDouble+flyZ+fz*zoomOffset)
        e.yRot=yaw;e.xRot=pitch
        e.yRotO=yaw;e.xRotO=pitch
        e.yHeadRot=yaw;e.yHeadRotO=yaw;e.yBodyRot=yaw;e.yBodyRotO=yaw
    }
    fun rotate(yaw:Float,pitch:Float){
        yawOffset+=yaw
        pitchOffset=(pitchOffset+pitch).coerceIn(-45f,45f)
        val d=arena?:return;val e=camera?:return
        applyCameraPose(e,d)
    }
    fun zoom(delta:Float){
        zoomOffset=(zoomOffset+delta).coerceIn(-8f,10f)
        val d=arena?:return;val e=camera?:return
        applyCameraPose(e,d)
    }
    fun resetAngle(){yawOffset=0f;pitchOffset=0f;zoomOffset=0f;flyX=0.0;flyY=0.0;flyZ=0.0;val d=arena?:return;val e=camera?:return;applyCameraPose(e,d)}
    fun update(data:JsonObject){
        val a=data.getAsJsonObject("arena");arena=a
        val key=data.text("session")+":"+data.number("battleNumber")
        if(key!=lastBattleKey){lastBattleKey=key;lastEventSeq=0;anims.clear()}
        val entities=a?.getAsJsonObject("entities")?:return
        data.array("damageEvents")?.forEach{v->
            val ev=v.asJsonObject;val seq=ev.number("seq")
            if(seq>lastEventSeq){
                lastEventSeq=seq
                val aid=entities.get(ev.text("actorUid"))?.asInt?:return@forEach
                val tid=entities.get(ev.text("targetUid"))?.asInt?:aid
                anims.add(AttackAnim(aid,tid,ev.text("type"),ev.text("role"),ev.text("category"),ev.get("miss")?.asBoolean==true,ev.decimal("multiplier",1.0),12))
            }
        }
    }
    fun clear(){restore(Minecraft.getInstance());arena=null;camera=null;lastBattleKey="";lastEventSeq=0;anims.clear()}
    /** True while the local player is still inside the synced battle arena dimension. */
    fun inArena(mc:Minecraft)=arena?.let{d->mc.level?.dimension()?.location()?.toString()==d.text("dimension")}==true
    private fun restore(mc:Minecraft){
        if(camera!=null&&mc.cameraEntity===camera)mc.player?.let{mc.setCameraEntity(it)}
        previousType?.let{mc.options.cameraType=it};previousType=null
        previousHideGui?.let{mc.options.hideGui=it};previousHideGui=null
    }
    fun tick(mc:Minecraft){
        val d=arena;val level=mc.level
        val tactical=mc.screen is SpireBattleScreen||mc.screen is PileScreen||mc.screen is BattleResultScreen
        if(d==null||level==null||mc.player==null||!tactical||level.dimension().location().toString()!=d.text("dimension")){restore(mc);return}
        val e=camera?.takeIf{it.level()===level}?:ArmorStand(level,0.0,0.0,0.0).also{camera=it;it.isInvisible=true;it.setPos(d.get("cameraX").asDouble,d.get("cameraY").asDouble-it.eyeHeight,d.get("cameraZ").asDouble);it.xo=it.x;it.yo=it.y;it.zo=it.z;it.xOld=it.x;it.yOld=it.y;it.zOld=it.z;it.yRot=d.get("yaw").asFloat;it.xRot=d.get("pitch").asFloat;it.yRotO=it.yRot;it.xRotO=it.xRot}
        e.xo=e.x;e.yo=e.y;e.zo=e.z;e.xOld=e.x;e.yOld=e.y;e.zOld=e.z
        val yaw=d.get("yaw").asFloat+yawOffset;val pitch=(d.get("pitch").asFloat+pitchOffset+(if(mc.window.guiScaledHeight<316)8f else 0f)).coerceIn(-89f,89f)
        val yr=Math.toRadians(yaw.toDouble());val pr=Math.toRadians(pitch.toDouble())
        if(mc.screen is SpireBattleScreen){
            val win=mc.window.window;val speed=0.45
            val fwdX=-kotlin.math.sin(yr);val fwdZ=kotlin.math.cos(yr)
            val rightX=-fwdZ;val rightZ=fwdX
            if(GLFW.glfwGetKey(win,GLFW.GLFW_KEY_W)==GLFW.GLFW_PRESS){flyX+=fwdX*speed;flyZ+=fwdZ*speed}
            if(GLFW.glfwGetKey(win,GLFW.GLFW_KEY_S)==GLFW.GLFW_PRESS){flyX-=fwdX*speed;flyZ-=fwdZ*speed}
            if(GLFW.glfwGetKey(win,GLFW.GLFW_KEY_D)==GLFW.GLFW_PRESS){flyX+=rightX*speed;flyZ+=rightZ*speed}
            if(GLFW.glfwGetKey(win,GLFW.GLFW_KEY_A)==GLFW.GLFW_PRESS){flyX-=rightX*speed;flyZ-=rightZ*speed}
            if(GLFW.glfwGetKey(win,GLFW.GLFW_KEY_SPACE)==GLFW.GLFW_PRESS)flyY+=speed
            if(GLFW.glfwGetKey(win,GLFW.GLFW_KEY_LEFT_SHIFT)==GLFW.GLFW_PRESS)flyY-=speed
            flyX=flyX.coerceIn(-26.0,26.0);flyZ=flyZ.coerceIn(-26.0,26.0);flyY=flyY.coerceIn(-12.0,22.0)
        }
        applyCameraPose(e,d)
        tickAnims(level)
        if(previousType==null)previousType=mc.options.cameraType
        if(previousHideGui==null)previousHideGui=mc.options.hideGui
        mc.options.hideGui=true
        mc.options.cameraType=CameraType.FIRST_PERSON;if(mc.cameraEntity!==e)mc.setCameraEntity(e)
    }
    private fun particleFor(type:String,role:String):ParticleOptions=if(role!="ATTACK")ParticleTypes.HEART else when(type){
        "fire"->ParticleTypes.FLAME;"water"->ParticleTypes.SPLASH;"grass","bug"->ParticleTypes.HAPPY_VILLAGER
        "electric"->ParticleTypes.ELECTRIC_SPARK;"ice"->ParticleTypes.SNOWFLAKE;"poison","psychic","ghost","dark"->ParticleTypes.WITCH
        "dragon","fairy","flying"->ParticleTypes.END_ROD;else->ParticleTypes.CRIT
    }
    private fun tickAnims(level:net.minecraft.client.multiplayer.ClientLevel){
        while(anims.isNotEmpty()){
            val a=anims.first()
            val actor=level.getEntity(a.actorId);val target=level.getEntity(a.targetId)
            if(actor==null){anims.removeFirst();continue}
            if(!a.initialized){a.baseX=actor.x;a.baseY=actor.y;a.baseZ=actor.z;a.initialized=true}
            val p=1.0-(a.ticksLeft.toDouble()/a.totalTicks)
            val lunge=kotlin.math.sin(p*Math.PI)
            if(a.category=="physical"&&target!=null&&target!==actor){
                val dx=target.x-a.baseX;val dz=target.z-a.baseZ;val dist=kotlin.math.hypot(dx,dz).coerceAtLeast(0.1)
                val reach=minOf(2.4,dist*0.35)*lunge
                actor.setPos(a.baseX+dx/dist*reach,a.baseY+lunge*0.45,a.baseZ+dz/dist*reach)
            }else if(a.category=="special"&&target!=null&&target!==actor){
                val particle=if(a.miss)ParticleTypes.SMOKE else particleFor(a.type,a.role)
                val px=a.baseX+(target.x-a.baseX)*p;val py=a.baseY+0.8+(target.y+0.8-(a.baseY+0.8))*p;val pz=a.baseZ+(target.z-a.baseZ)*p
                for(k in 0..2)level.addParticle(particle,px+(Math.random()-0.5)*0.3,py+(Math.random()-0.5)*0.3,pz+(Math.random()-0.5)*0.3,0.0,0.02,0.0)
            }else{
                actor.setPos(a.baseX,a.baseY+lunge*0.35,a.baseZ)
            }
            if(a.ticksLeft==a.totalTicks/2&&target!=null){
                val superEff=!a.miss&&a.multiplier>=2.0&&a.role=="ATTACK"
                if(!a.miss&&target is LivingEntity)target.hurtTime=if(superEff)14 else 8
                val particle=if(a.miss)ParticleTypes.SMOKE else particleFor(a.type,a.role)
                val burstCount=if(superEff)30 else 14
                for(n in 0..burstCount){
                    val ox=(Math.random()-0.5)*(if(superEff)2.2 else 1.4);val oy=Math.random()*(if(superEff)1.8 else 1.2)+0.3;val oz=(Math.random()-0.5)*(if(superEff)2.2 else 1.4)
                    level.addParticle(particle,target.x+ox,target.y+oy,target.z+oz,ox*0.20,if(superEff)0.14 else 0.08,oz*0.20)
                }
                if(superEff){
                    level.addParticle(ParticleTypes.EXPLOSION,target.x,target.y+0.8,target.z,0.0,0.0,0.0)
                    for(n in 0..16){
                        val ox=(Math.random()-0.5)*1.8;val oy=Math.random()*1.4+0.4;val oz=(Math.random()-0.5)*1.8
                        level.addParticle(ParticleTypes.CRIT,target.x+ox,target.y+oy,target.z+oz,ox*0.25,0.16,oz*0.25)
                    }
                    shakeTicks=8;shakeStrength=if(a.multiplier>=4.0)0.45f else 0.28f
                }
            }
            if(shakeTicks>0){
                shakeTicks--
                val d=arena;val e=camera
                if(d!=null&&e!=null){
                    val jx=(Math.random()-0.5)*shakeStrength;val jy=(Math.random()-0.5)*shakeStrength
                    applyCameraPose(e,d)
                    if(shakeTicks>0)e.setPos(e.x+jx,e.y+jy,e.z)
                }
            }
            a.ticksLeft--
            if(a.ticksLeft<=0){actor.setPos(a.baseX,a.baseY,a.baseZ);anims.removeFirst()}
            break
        }
    }
}
