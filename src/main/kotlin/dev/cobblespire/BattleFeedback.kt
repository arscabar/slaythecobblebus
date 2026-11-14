package dev.cobblespire

object BattleFeedback {
    private val stats=mapOf("atk" to "공격","def" to "방어","spa" to "특공","spd" to "특방","spe" to "속도","accuracy" to "명중","evasion" to "회피")
    private val statuses=mapOf("burn" to "화상","poison" to "독","toxic" to "맹독","paralysis" to "마비","sleep" to "수면","confusion" to "혼란","flinch" to "풀죽음")
    private val types=mapOf("normal" to "노말","fire" to "불꽃","water" to "물","grass" to "풀","electric" to "전기","ice" to "얼음","fighting" to "격투","poison" to "독","ground" to "땅","flying" to "비행","psychic" to "에스퍼","bug" to "벌레","rock" to "바위","ghost" to "고스트","dragon" to "드래곤","dark" to "악","steel" to "강철","fairy" to "페어리")
    fun text(e:SpireBattleEvent,own:Set<String>,names:Map<String,String>):String? {
        val target=if(e.target in own)"내"else names[e.target].orEmpty().ifBlank{"상대"}
        return when(e.name){
            "STAGE_CHANGED"->"$target ${stats[e.detail]?:e.detail} ${kotlin.math.abs(e.amount)}단계 ${if(e.amount>0)"상승"else "하락"}"
            "STATUS_APPLIED"->"${if(e.target in own)"내 포켓몬"else target}: ${statuses[e.detail]?:e.detail}"
            "HEALED"->if(e.amount>0)"$target HP ${e.amount} 회복"else null
            "CARDS_DRAWN"->if(e.amount>0)"카드 ${e.amount}장을 뽑았습니다"else "추가로 뽑은 카드가 없습니다"
            "MOVE_HIT","AFTER_DAMAGE_TAKEN"->if(e.amount>0)"${if(e.target in own)"내 포켓몬:"else target+"에게"} ${types[e.moveType]?:e.moveType} 피해 ${e.amount}"else null
            "MOVE_MISS"->if(e.multiplier==0.0)"${if(e.target in own)"내 포켓몬"else target}에게는 통하지 않습니다"else "기술이 빗나가거나 차단됐습니다"
            "NORMAL_SWITCH","FORCED_SWITCH"->"${names[e.target].orEmpty()} 출전"
            "BATTLE_SURRENDERED"->"전원 동의로 항복했습니다"
            else->null
        }
    }
}
