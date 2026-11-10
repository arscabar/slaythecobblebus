package dev.cobblespire
import kotlin.random.Random

data class DeckTransfer(val sequence:Long,val card:MoveInstance,val from:String,val to:String,val index:Int,val round:Int,val cause:String)

/** A per-Pokemon deck. The playing card is outside every pile until its effect resolves. */
class BattleDeck(instances:List<MoveInstance>,private val random:Random,val handLimit:Int=10){
    val original=instances.toList()
    val draw=ArrayDeque(instances.shuffled(random))
    val discard=mutableListOf<MoveInstance>()
    val exhausted=mutableListOf<MoveInstance>()
    val hand=mutableListOf<MoveInstance>()
    val transfers=ArrayDeque<DeckTransfer>()
    private val playing=mutableMapOf<Long,MoveInstance>()
    var sequence=0L;private set
    var lastDrawRound=-1;private set
    init{require(original.map{it.uid}.distinct().size==original.size);require(handLimit>=5)}
    private fun motion(c:MoveInstance,from:String,to:String,index:Int,round:Int,cause:String){
        transfers.addLast(DeckTransfer(++sequence,c,from,to,index,round,cause));while(transfers.size>128)transfers.removeFirst()
    }
    fun beginTurn(round:Int){if(lastDrawRound==round)return;lastDrawRound=round;drawCards(5,round,"START_TURN")}
    fun drawCards(count:Int,round:Int,cause:String="EFFECT"):List<MoveInstance>{
        require(count in 0..100);val result=mutableListOf<MoveInstance>()
        repeat(count){
            if(hand.size>=handLimit)return result
            if(draw.isEmpty()&&discard.isNotEmpty()){
                val cards=discard.shuffled(random);discard.clear();draw.addAll(cards)
                cards.forEach{motion(it,"DISCARD","DRAW",-1,round,"RESHUFFLE")}
            }
            val c=draw.removeFirstOrNull()?:return result
            hand.add(c);result.add(c);motion(c,"DRAW","HAND",hand.lastIndex,round,cause)
        }
        return result
    }
    fun take(index:Int,round:Int):MoveInstance{
        require(index in hand.indices);val c=hand.removeAt(index);check(playing.put(c.uid,c)==null)
        motion(c,"HAND","PLAY",index,round,"PLAY_CARD");return c
    }
    fun resolve(card:MoveInstance,round:Int){
        check(playing.remove(card.uid)===card)
        if(card.move.exhaust)exhausted.add(card)else discard.add(card)
        motion(card,"PLAY",if(card.move.exhaust)"EXHAUST" else "DISCARD",-1,round,"PLAY_CARD")
    }
    fun discardHand(round:Int,cause:String="SWITCH"){
        check(playing.isEmpty());val cards=hand.toList();hand.clear()
        cards.forEachIndexed{i,c->discard.add(c);motion(c,"HAND","DISCARD",i,round,cause)}
    }
    fun switchIn(round:Int){discardHand(round,"SWITCH_RESET");lastDrawRound=round;drawCards(5,round,"SWITCH_DRAW")}
    fun finishTurn(round:Int){
        check(playing.isEmpty())
        val list=hand.withIndex().filter{!it.value.retain}
        list.forEach{(index,c)->hand.remove(c);discard.add(c);motion(c,"HAND","DISCARD",index,round,"END_TURN")}
    }
}
