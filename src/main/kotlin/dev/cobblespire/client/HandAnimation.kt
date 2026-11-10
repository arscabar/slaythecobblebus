package dev.cobblespire.client
import com.google.gson.JsonObject
import dev.cobblespire.*

object SeenDeckMotions {
    private val seen=mutableMapOf<String,Long>()
    fun get(key:String)=seen[key]?:0L
    fun set(key:String,n:Long){if(seen.size>96)seen.clear();seen[key]=maxOf(n,seen[key]?:0L)}
    fun clear(){seen.clear()}
}
data class CardFlight(val card:JsonObject,val from:String,val to:String,val fromIndex:Int,val toIndex:Int,val start:Long,val duration:Long){
    fun progress(now:Long)=((now-start).toDouble()/duration).coerceIn(0.0,1.0)
    fun active(now:Long)=now>=start&&now<start+duration
}
/** Presentation only. Costs, draws and pile membership come exclusively from server snapshots. */
class HandAnimation {
    val flights=mutableListOf<CardFlight>()
    private var until=0L
    fun busy(now:Long=System.currentTimeMillis())=now<until
    fun hiddenHand(uid:Long,now:Long)=flights.any{it.card.get("instance").asLong==uid&&it.to=="HAND"&&now<it.start+it.duration}
    fun update(old:JsonObject?,data:JsonObject,animate:Boolean){
        val key=data.text("session")+":"+data.number("battleNumber")+":"+data.text("deckOwner")
        val limit=data.get("motionSequence")?.asLong?:0L;val was=SeenDeckMotions.get(key);SeenDeckMotions.set(key,limit)
        if(!animate){flights.clear();until=0;return}
        val now=System.currentTimeMillis();flights.removeIf{now>=it.start+it.duration}
        val changed=old!=null&&old.text("deckOwner")!=data.text("deckOwner")
        val motions=data.array("motions")?.map{it.asJsonObject}?.filter{it.get("seq").asLong>was}.orEmpty()
        val hand=data.array("hand")?.map{it.asJsonObject}.orEmpty();val oldHand=old?.array("hand")?.map{it.asJsonObject}.orEmpty()
        if(changed)oldHand.forEachIndexed{i,c->flights.add(CardFlight(c,"HAND","DISCARD",i,0,now+i*15,190))}
        var draws=0;var discard=0;var shuffled=false
        val shuffle=motions.any{it.text("cause")=="RESHUFFLE"}
        for(m in motions){
            if(changed&&m.text("to")!="HAND"&&m.text("cause")!="RESHUFFLE")continue
            val c=m.getAsJsonObject("card");val uid=c.get("instance").asLong
            val targetIndex=hand.indexOfFirst{it.get("instance").asLong==uid}.coerceAtLeast(0)
            val fromIndex=oldHand.indexOfFirst{it.get("instance").asLong==uid}.takeIf{it>=0}?:m.number("index").coerceAtLeast(0)
            val from=m.text("from");val to=m.text("to")
            val delay:Long;val duration:Long
            if(from=="DISCARD"&&to=="DRAW"){
                if(shuffled)continue;shuffled=true;delay=180;duration=210
            }else if(to=="HAND") {delay=(if(shuffle)400 else if(old==null)0 else 100)+draws++*45L;duration=250}
            else if(to=="PLAY"){delay=0;duration=110}
            else if(from=="PLAY"){delay=130;duration=200}
            else{delay=discard++*18L;duration=190}
            flights.add(CardFlight(c,from,to,fromIndex,targetIndex,now+delay,duration))
        }
        until=flights.maxOfOrNull{it.start+it.duration}?:0
    }
}
