package dev.cobblespire.client

import com.google.gson.JsonObject
import dev.cobblespire.*
import net.minecraft.client.Minecraft
import java.util.UUID

object BattleHudState {
    var latest:JsonObject?=null;private set
    var target=0
    var pending=false;private set
    private var scope=""
    private var feedbackSeq=0
    private var message=""
    private var messageUntil=0L
    fun update(d:JsonObject){
        val key=d.text("session")+":"+d.number("battleNumber")
        if(key!=scope){scope=key;feedbackSeq=0;message=""}
        latest=d;pending=false
        val enemies=d.getAsJsonArray("enemies")
        if(target !in 0 until enemies.size()||enemies[target].asJsonObject.number("hp")<=0)target=enemies.indexOfFirst{it.asJsonObject.number("hp")>0}.coerceAtLeast(0)
        d.array("feedback")?.lastOrNull()?.asJsonObject?.let{if(it.number("seq")>feedbackSeq){feedbackSeq=it.number("seq");message=it.text("text");messageUntil=System.currentTimeMillis()+4200}}
    }
    fun clear(){latest=null;pending=false;scope="";message="";feedbackSeq=0}
    fun toast()=message.takeIf{System.currentTimeMillis()<messageUntil}.orEmpty()
    fun error(s:String){pending=false;message=s;messageUntil=System.currentTimeMillis()+6000}
    @JvmStatic fun isActive():Boolean {
        val d=latest?:return false
        return Minecraft.getInstance().level?.dimension()?.location()?.toString()==d.obj("arena")?.text("dimension")
    }
    fun canPlay(c:JsonObject):Boolean{
        val d=latest?:return false;val p=d.getAsJsonArray("party")[d.number("active")].asJsonObject
        return !pending&&d.text("phase")=="PLAYER"&&d.get("ended")?.asBoolean!=true&&p.number("hp")>0&&d.number("energy")>=c.number("cost")&&(c.get("trainer")?.asBoolean==true||p.array("statuses")?.none{it.asString.substringBefore(' ')=="sleep"}!=false)
    }
    fun command(action:String,body:JsonObject.()->Unit={}){
        val d=latest?:return;if(pending)return;pending=true
        SpireClient.send(action){addProperty("session",d.text("session"));addProperty("battleNumber",d.number("battleNumber"));addProperty("revision",d.number("revision"));addProperty("request",UUID.randomUUID().toString());body()}
    }
    fun play(index:Int){val c=latest?.array("hand")?.toList()?.getOrNull(index)?.asJsonObject?:return
        if(canPlay(c)){SpireMusic.cardPlay();command("move"){addProperty("handIndex",index);addProperty("instance",c.get("instance").asLong);addProperty("target",target)}}
    }
    @JvmStatic fun handleKey(key:Int,action:Int):Boolean{
        if(!isActive()||Minecraft.getInstance().screen!=null)return false
        val i=if(key in 49..57)key-49 else if(key==48)9 else return false
        if(action==1)play(i)
        return true
    }
}
