package dev.cobblespire

data class LootCard(val owner:Int,val card:MoveInstance)
/** One settlement per victory; reopening the result never rerolls or duplicates an award. */
class VictoryLoot(val battleNumber:Int,val offers:List<LootCard>){
    var selected:Int?=null;private set
    val settled get()=selected!=null
    fun validate(index:Int){require(!settled){"이미 정산한 전리품입니다."};require(index==-1||index in offers.indices){"전리품 후보가 변경되었습니다."}}
    fun claim(index:Int):LootCard?{validate(index);selected=index;return offers.getOrNull(index)}
}
