# 비전투 이벤트 전체 이미지 기획 및 누락 대조

이 문서는 [이미지 제작 인계서](EVENT_ART_HANDOFF.md)의 확장판이다. 공통 스타일·출력 조건·참고 그림은 그 문서를 따른다. 아래 장면은 생성 시스템이 사용할 **제작 제안**이며, 별도 표시가 없으면 게임에 이미 구현되거나 개별 사건으로 확정된 것이 아니다. 전투 조우 21개는 기존 인계서에 별도 수록되어 있다.

## 조사 결과와 범위

`design/GDD-v4.1.md`, `design/GDD-v4.2.md`, `design/data/registry_events_conditions_actions.json`, `design/data/relics_165_runtime.json`, `design/data/current_rules.json` 및 원본 데이터의 관련 참조를 대조했다. 현재 자료에서 확인되는 것은 물음표 노드, 낙하·낙석 피해, 이벤트 HP 대가, 금화 보상, 전투 발생형 이벤트, 영입, 포켓몬 교체, 진화 권한과 시설 등 **규칙과 유형**이다. 개별 물음표 사건의 완성된 제목·목록·분기·등장 포켓몬은 확인되지 않는다. 따라서 존재하지 않는 확정 목록을 모두 완성했다고 말할 수 없다.

아래는 확인된 유형별 누락을 막기 위한 전체 후보 장면 목록이다. 제안 ID는 이 문서에서만 쓰며 실제 게임 ID가 아니다. 예시 상황이나 보상을 자동으로 게임 규칙에 넣지 않는다. 같은 유형을 1개만 써야 한다는 의미도 아니다. 콘텐츠 기획자가 실제 이벤트 목록을 확정하면 각 사건을 이 표와 연결하거나 새 항목을 추가한다.

## 공통 전달 조건

기준 이미지는 [수상한 몬스터볼](art_reference/event_pokeball_concept.png). 장면만 그린다: UI, 버튼, 글자, 테두리, 수치, 인물 트레이너는 넣지 않는다. 블록형 마인크래프트 환경과 포켓몬, 굵은 잉크 윤곽, 각진 표현, 거친 그림자, 선명한 중심 사건을 조합한다. 16:9 가로 PNG, 1920×1080 이상을 제작 목표로 하고, 중심 피사체는 화면 중앙 70% 안에 둔다. 재미있는 모험과 선택 직전의 긴장을 유지하되 유혈과 고어를 피한다. 포켓몬의 특정 종이 미정이면 종을 추측하지 말고 기획 승인 후 지정한다. 결과·비용·참가 자격을 그림이 확정하지 않도록 한다.

각 표의 한 행을 독립 이미지 요청으로 쓰며 **공통 전달 조건 + 해당 행의 장면·구도·분위기**를 함께 제공한다. 명시된 원본 참조는 해당 유형이 기획에 언급되었다는 근거이고, 구체적인 장면의 확정 근거는 아니다.

## 물음표 사건: 발견과 탐험

| 제안 ID | 장면과 구도 | 감정·상황 | 생성된 시안 파일 |
|---|---|---|---|
| Q-DISCOVERY-01 | 이끼 낀 블록 제단 위 낡은 몬스터볼을 포켓몬이 살핀다 | 기준 샘플; 발견과 불확실성 | [event_pokeball_concept.png](art_reference/event_pokeball_concept.png) |
| Q-DISCOVERY-02 | 숲길의 오래된 상자와 주변 발자국을 바라보는 포켓몬, 상자 내부는 보이지 않음 | 주인을 알 수 없는 보급품 | [event_lost_supplies.png](art_reference/events/event_lost_supplies.png) |
| Q-DISCOVERY-03 | 절반쯤 묻힌 돌 문과 미세한 빛을 가까이 살피는 포켓몬 | 숨겨진 장소의 호기심 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-DISCOVERY-04 | 갈림길의 낡은 표지판과 서로 다른 두 통로를 보는 포켓몬 | 어디로 갈지 고민하는 순간; 글자 없는 표지판 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-DISCOVERY-05 | 어두운 블록형 동굴에서 희미한 빛이 나는 돌과 조심스러운 포켓몬 | 신비한 발견, 실제 아이템 여부 미정 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-DISCOVERY-06 | 버려진 야영지의 비어 있는 가방과 꺼져가는 등불 | 전 주인의 흔적, 고요한 의문 | 시안 대기 (`art_reference/README.md` 명세 수록) |

## 물음표 사건: 위험·대가·보상

낙석과 HP 대가, 금화 보상은 레지스트리 및 도구 규칙에 유형으로 언급된다. 아래 구체적 사건과 비용은 제안이다.

| 제안 ID | 장면과 구도 | 감정·상황 | 생성된 시안 파일 |
|---|---|---|---|
| Q-HAZARD-01 | 좁은 블록 계곡 위 갈라진 바위를 올려다보는 포켓몬 | 낙석 직전, 긴장; `EVENT_HAZARD_FALL_ROCK` 유형 참고 | [event_rockfall.png](art_reference/events/event_rockfall.png) |
| Q-HAZARD-02 | 깊은 틈과 흔들리는 나무다리 앞에 멈춘 포켓몬 | 위험한 길을 건널지 고민 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-HAZARD-03 | 가시 덤불 너머 희미한 빛을 바라보는 포켓몬 | 대가가 있을지 모를 접근, 상처 묘사 없음 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-HAZARD-04 | 무너지는 석조 문 앞에서 뒤로 물러선 포켓몬 | 돌이 떨어질 듯한 순간, 피해 확정 아님 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-REWARD-01 | 먼지 쌓인 상자 곁에 흩어진 동전과 조심하는 포켓몬 | 금화 가능성을 시각적으로 암시; 액수·실제 보상 미정 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-REWARD-02 | 수풀에 가려진 닫힌 보물볼과 주변을 살피는 포켓몬 | 보물을 열지 고민; 내용물 비공개 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-REWARD-03 | 제단 위 천으로 덮인 작은 물건과 호기심 어린 포켓몬 | 알 수 없는 도구; 특정 유물·효과 확정 금지 | 시안 대기 (`art_reference/README.md` 명세 수록) |

## 물음표 사건: 만남·전투 발생·영입

영입과 전투 발생형 물음표는 원본에 유형으로 언급된다. 출현 종, 선택 조건과 성공 결과는 미정이다.

| 제안 ID | 장면과 구도 | 감정·상황 | 생성된 시안 파일 |
|---|---|---|---|
| Q-MEET-01 | 숲길 양쪽에서 서로 거리를 두고 살펴보는 야생 포켓몬과 동료 | 만남과 경계, 영입 확정 아님 | [event_wild_meeting.png](art_reference/events/event_wild_meeting.png) |
| Q-MEET-02 | 나뭇잎 아래 몸을 숨긴 작은 야생 포켓몬과 이를 발견한 동료 | 도와줄지 지켜볼지 고민; 상처 없음 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-MEET-03 | 작은 공터에서 나란히 있는 야생 포켓몬과 탐험 파티 | 신뢰를 쌓는 가능성; 종과 결과 미정 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-COMBAT-01 | 동굴 그늘에서 두 눈이 나타나고 포켓몬이 경계 | 전투 가능성이 느껴지는 조우, 적 확정 전 | [event_encounter.png](art_reference/events/event_encounter.png) |
| Q-COMBAT-02 | 버려진 물건 앞에서 서로 오해하듯 대치하는 포켓몬 둘 | 갈등 직전, 실제 공격 장면 없음 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-RECRUIT-01 | 파티를 향해 조심스럽게 다가오는 야생 포켓몬 | 영입 선택 직전, 가입 확정 아님 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-RECRUIT-02 | 현재 파티와 새 후보가 서로 마주 보는 장면 | 파티가 가득 찼을 때의 교체 논의; 특정 멤버 제외 암시 금지 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| Q-SWAP-01 | 여행 중 서로 다른 길 앞에서 마주 보는 포켓몬들 | 상점·이벤트 교체의 감정적 표현, 규칙·결과 미정 | 시안 대기 (`art_reference/README.md` 명세 수록) |

## 진화·권한·성장

진화는 GDD v4.2의 서버 소유 이벤트/배지 권한, 원본 분기 확인, 실패 시 권한 보존을 따른다. 장면이 권한 발급이나 진화 성공을 확정하지 않는다. 기술 강화는 휴식처 선택으로 언급된다.

| 제안 ID | 장면과 구도 | 감정·상황 | 생성된 시안 파일 |
|---|---|---|---|
| PROGRESS-EVOLUTION-01 | 고대 문양이 새겨진 블록 제단을 포켓몬이 바라봄 | 진화 기회의 가능성, 분기·종 미정 | [event_shrine.png](art_reference/events/event_shrine.png) |
| PROGRESS-BADGE-01 | 작은 배지 모양 물건을 주의 깊게 보는 포켓몬 | 권한과 연결 가능한 상징 후보; 실제 배지 외형 미정 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| PROGRESS-BRANCH-01 | 갈라지는 빛 두 줄기 앞의 포켓몬 실루엣 | 진화 분기 선택 전, 특정 결과를 보여주지 않음 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| PROGRESS-UPGRADE-01 | 휴식처의 연습 표적을 바라보는 포켓몬 | 기술 강화 준비; 특정 기술·수치 확정 금지 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| PROGRESS-MEGA-01 | 전장에 나가기 전 희미한 빛을 바라보는 포켓몬 | 메가 관련 분위기 후보; 실제 각성·폼은 규칙 검증 후 | 시안 대기 (`art_reference/README.md` 명세 수록) |

## 시설·보상 및 모험 진행

시설과 3막 진행은 기획 유형이며 이미지 필요 여부와 최종 연출은 미구현이다. 하나의 시설에서 단계별 삽화가 필요한지 여부는 UI 확정 후 결정한다.

| 제안 ID | 이미지 위치 | 장면과 구도 | 생성된 시안 파일 |
|---|---|---|---|
| FLOW-START-01 | 모험 시작 | 블록형 길의 입구를 바라보는 대표 포켓몬; 출발의 기대 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FLOW-PARTY-01 | 시작 파티 준비 | 세 포켓몬이 같은 길을 바라봄; 실제 종은 선택 결과에 맞춰야 하므로 고정 그림 사용 여부 검토 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FLOW-ACT1-01 | 1막 도입 | 숲과 오래된 시설이 보이는 길 앞의 작은 포켓몬 | [story_act1.png](art_reference/events/story_act1.png) |
| FLOW-ACT2-M-01 | 2막 마그마 경로 도입 | 붉은 화산 능선과 블록형 바위 앞의 포켓몬 | [story_act2_m.png](art_reference/events/story_act2_m.png) |
| FLOW-ACT2-A-01 | 2막 아쿠아 경로 도입 | 물가와 잠긴 블록 유적을 바라보는 포켓몬 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FLOW-ACT3-01 | 3막 도입 | 차가운 유적과 먼 하늘의 빛을 보는 포켓몬 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FAC-SHOP-01 | 상점 진입 | 여행 물건이 진열된 블록형 가판대와 물건을 살피는 포켓몬 | [facility_shop.png](art_reference/events/facility_shop.png) |
| FAC-SHOP-02 | 기술 제거 선택 | 작업대 위 빈 카드 모양 소품을 살피는 포켓몬; 글자·구체 효과 없음 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FAC-REST-01 | 휴식처 진입·회복 선택 | 블록형 모닥불 곁에 모인 포켓몬, 편안하고 따뜻한 빛 | [facility_rest.png](art_reference/events/facility_rest.png) |
| FAC-REST-02 | 휴식처 기술 강화 선택 | 안전한 야영지 근처 연습 표적을 바라보는 포켓몬 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FAC-TREASURE-01 | 보물볼 진입 | 제단의 닫힌 보물볼, 주변을 살피는 포켓몬; 내용물 감춤 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FAC-TREASURE-02 | 보물 선택 포기 | 닫힌 보물볼을 뒤로하고 떠나는 실루엣; 실제 분기 필요 시만 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| REWARD-BATTLE-01 | 전투 후 보상 | 정돈된 여행 물품과 쉬고 있는 포켓몬; 특정 보상 확정 금지 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| REWARD-MOVE-01 | 기술 보상 선택 | 빛나는 무늬가 새겨진 훈련 장소와 포켓몬; 문자·카드 텍스트 금지 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| REWARD-CONSUMABLE-01 | 소모품 보상 | 여행 가방 옆에 놓인 일반적인 빈 용기; 실제 아이템 형상은 기획 후 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FLOW-BOSS-INTRO-01 | 보스 노드 진입 | 해당 막의 유적에서 보스 실루엣이 등장; 종·수는 조우 ID에 맞춤 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FLOW-ACT-CLEAR-01 | 막 완료 | 새 길을 바라보는 포켓몬 뒷모습, 지나온 막의 환경 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FLOW-VICTORY-01 | 최종 승리 | 새벽 풍경을 바라보는 포켓몬, 성취감과 여운 | 시안 대기 (`art_reference/README.md` 명세 수록) |
| FLOW-DEFEAT-01 | 패배·종료 | 안전한 길을 따라 돌아가는 포켓몬, 고요하고 비폭력적인 마무리 | 시안 대기 (`art_reference/README.md` 명세 수록) |

## 기술 이벤트와 장면 삽화의 관계

레지스트리의 54개는 전부 개별 스토리 사건이 아니다. 예를 들어 `BEFORE_MOVE_COST`, `MOVE_HIT`, `AFTER_DAMAGE_TAKEN`, `ROUND_START`, `DRAW_PILE_SHUFFLED`는 전투 규칙·애니메이션의 신호다. 별도의 정적 이야기 삽화를 각각 생성할 필요가 있다고 가정하지 않는다. `EVENT_ENTERED`, `REST_ENTERED`, `SHOP_STOCK_GENERATED`, `RELIC_CHEST_ENTERED` 등은 위 이벤트·시설 **화면 진입이나 상태**와 연결해 검토한다. 결과 보상 신호는 실제 UI가 별도의 결과 장면을 쓰기로 결정했을 때에만 대응한다. 이전 슬롯 기반 표현은 v4.2 손패 규칙보다 우선하지 않는다.

## 인계 후 남은 결정

- 실제 물음표 이벤트의 전체 목록, 각 사건의 제목·설명·선택·대상·효과·등장 조건과 참여 자격.
- 참가하지 않는 사람의 대기와 합류, 공동/개별 선택과 재접속 처리.
- 고정 포켓몬 그림과 실제 선택된 파티가 다른 경우의 표현. 포켓몬을 중립적 실루엣으로 둘지, 인물 레이어를 별도 조합할지 결정.
- 각 후보를 만들지, 통합·삭제할지, 결과별 별도 그림이 필요한지에 대한 콘텐츠 승인.
- 개별 파일의 실제 리소스 경로, 모델·생성일·프롬프트 기록 및 배포 권리 검토.

이 문서만으로 미정의 사건 수를 확정할 수 없다. 새 이벤트가 확정될 때마다 사건 ID와 장면을 추가하고, 이미지 없는 이벤트도 플레이할 수 있도록 대체 화면을 마련한다.
