# CobbleSpire BGM 및 오디오 제작 인계서

이 문서는 CobbleSpire(마인크래프트 Cobblemon 기반 3막 로그라이크)에서 필요한 **배경음악(BGM) 및 결과 징글(Jingle/Stinger) 전체 목록**과 구간별 연출·전환 명세를 정의한다.

- **현재 구현 상태**: `src/main/resources/assets/cobblespire/sounds.json` 및 오디오 에셋(`.ogg`)은 아직 미구현 상태이며, 아래 목록은 사운드 제작 시스템 또는 작곡가에게 전달할 **전체 제작 명세**다.
- **포맷 기준**: 마인크래프트 Fabric 리소스팩 표준인 **Ogg Vorbis (`.ogg`), 44.1kHz, 16-bit, Stereo(BGM은 `stream: true`, 짧은 징글/UI 효과음은 Mono/Stereo `stream: false`)**.
- **저작권 원칙**: 원작 포켓몬스터 본가 게임이나 타 게임(Slay the Spire, Darkest Dungeon 등)의 음원을 복제·샘플링하지 않고, 각 막의 환경(블록형 숲 폐허 / 화산·해안 / 고산 성채와 우주적 균열)에 맞춘 독자적인 오리지널 트랙으로 제작한다.

---

## 1. BGM이 필요한 전체 구간 요약 (총 19트랙: 루프 BGM 15종 + 징글 4종)

게임의 플레이 흐름([INTERACTION_AND_STORY_DESIGN.md](INTERACTION_AND_STORY_DESIGN.md), [UI_ARCHITECTURE.md](UI_ARCHITECTURE.md), `design/data/encounter_21.json`)에 따라 BGM이 필요한 지점은 크게 **5개 영역**으로 나뉜다.

1. **도입·로비·분기 지도 탐색 (5종)**: 런 시작/파티 편성 화면 + 막별(1막, 2막 마그마, 2막 아쿠아, 3막) 지도 및 스토리 화면
2. **일반·엘리트 전투 (6종)**: 막·경로별 일반 전투 4종 + 엘리트 위협 전투 2종(또는 막별 변주)
3. **보스 전투 (4종)**: 1막 보스(`ACT1-R06/R07`), 2막 마그마 보스(`ACT2-M04` 메가폭타), 2막 아쿠아 보스(`ACT2-A04` 메가샤크니아), 3막 최종 보스(`ACT3-G05/G06`)
4. **비전투 노드·시설 (3종)**: 물음표(`?`) 미스터리 이벤트, 상점(`FACILITY-SHOP`), 모닥불 휴식처(`FACILITY-REST`)
5. **결과 및 전환 징글 (4종)**: 전투 승리/보상 징글, 보스 격파/막 클리어 징글, 최종 런 클리어 테마, 런 패배/귀환 징글

---

## 2. 영역별 상세 BGM 트랙 명세

### 2-1. 로비 및 막별 지도·스토리 탐색 BGM (Loop, 5종)

지도 화면(`MAP-BG-*`), 막 도입 스토리 화면(`STORY-ACT*`), 보물볼/일반 탐색 중에 흐르는 기본 테마. 전투 진입 시 페이드아웃 후 전투 BGM으로 전환되며, 비전투 시 자연스럽게 무한 루프되어야 한다.

| 트랙 ID (`sounds.json` 키) | 예정 파일 경로 (`assets/cobblespire/sounds/`) | 재생 구간 및 연결 화면 | 분위기 · 악기 구성 · 권장 BPM | 루프 / 길이 |
|---|---|---|---|---|
| `BGM-TITLE-PREP` (`cobblespire:bgm.lobby_prep`) | `bgm/bgm_lobby_prep.ogg` | 모험 시작(`FLOW-START-01`), 시작 파티 준비(`FLOW-PARTY-01`), 도감/런 준비 화면 | 설렘과 결의가 공존하는 출발 전 테마. 어쿠스틱 기타, 따뜻한 목관(플룻/오보에), 가벼운 스네어 마칭 리듬. (95~105 BPM) | 무한 루프 / 1분 30초~2분 |
| `BGM-MAP-ACT1` (`cobblespire:bgm.map_act1`) | `bgm/bgm_map_act1.ogg` | 1막 도입(`STORY-ACT1`) 및 1막 분기 지도(`MAP-BG-ACT1`) 탐색 | 이끼 낀 고대 숲과 버려진 폐시설을 탐색하는 호기심·은밀한 긴장감. 피치카토 현악, 마림바/실로폰, 낮은 첼로 드론과 숲 바람 질감. (85~95 BPM) | 무한 루프 / 2분 내외 |
| `BGM-MAP-ACT2-M` (`cobblespire:bgm.map_act2_magma`) | `bgm/bgm_map_act2_magma.ogg` | 2막 마그마 경로 도입(`STORY-ACT2-M`) 및 2막 마그마 지도(`MAP-BG-ACT2-M`) 탐색 | 뜨거운 화산 협곡과 지하 용암 유적의 묵직한 열기. 저음 브라스, 타악기(탐탐·모루 타격음), 일렉트릭 베이스와 불길한 현악 트레몰로. (90~100 BPM) | 무한 루프 / 2분 내외 |
| `BGM-MAP-ACT2-A` (`cobblespire:bgm.map_act2_aqua`) | `bgm/bgm_map_act2_aqua.ogg` | 2막 아쿠아 경로 도입(`STORY-ACT2-A`) 및 2막 아쿠아 지도(`MAP-BG-ACT2-A`) 탐색 | 침수된 해안 신전과 심해 수로의 신비롭고 차가운 수압감. 하프 아르페지오, 글로켄슈필, 잔향 깊은 신스 패드와 수중 퍼커션. (80~92 BPM) | 무한 루프 / 2분 내외 |
| `BGM-MAP-ACT3` (`cobblespire:bgm.map_act3_galaxy`) | `bgm/bgm_map_act3_galaxy.ogg` | 3막 도입(`STORY-ACT3`) 및 3막 고산 성채 지도(`MAP-BG-ACT3`) 탐색 | 눈 덮인 고대 성채 정상과 보랏빛 하늘 균열이 주는 우주적 고독·결전 직전의 고조감. 차가운 아날로그 신스 아르페지오, 파이프 오르간, 장중한 현악 앙상블. (95~108 BPM) | 무한 루프 / 2분 내외 |

---

### 2-2. 일반 전투 및 엘리트 전투 BGM (Loop, 5종)

전용 월드 아레나([WORLD_ARENA.md](WORLD_ARENA.md))에서 진행되는 턴제 카드 전투용 BGM. 사고(카드 계산·타깃 선택)를 방해하지 않으면서도 박진감 있는 리듬을 유지해야 한다.

| 트랙 ID (`sounds.json` 키) | 예정 파일 경로 (`assets/cobblespire/sounds/`) | 재생 구간 (`encounter_21.json` 매핑) | 분위기 · 악기 구성 · 권장 BPM | 루프 / 길이 |
|---|---|---|---|---|
| `BGM-BATTLE-ACT1` (`cobblespire:bgm.battle_act1`) | `bgm/bgm_battle_act1.ogg` | 1막 일반 전투 (`ACT1-R01` 꼬렛, `ACT1-R02` 아보·주뱃, `ACT1-R03` 3마리 조우) | 경쾌하면서도 그늘진 숲속 폐허 조우전. 스타카토 스트링, 신스 리드, 탄력 있는 드럼 비트와 베이스라인. (128~136 BPM) | 무한 루프 / 2분 내외 |
| `BGM-BATTLE-ACT2-M` (`cobblespire:bgm.battle_act2_magma`) | `bgm/bgm_battle_act2_magma.ogg` | 2막 마그마 일반 전투 (`ACT2-M01` 둔타·그라에나, `ACT2-M02` 골뱃·또가스·둔타) | 거칠고 공격적인 화산 지대 전투. 오버드라이브 기타 리프, 묵직한 금관악기(트롬본·호른), 강렬한 타악 리듬. (132~140 BPM) | 무한 루프 / 2분 내외 |
| `BGM-BATTLE-ACT2-A` (`cobblespire:bgm.battle_act2_aqua`) | `bgm/bgm_battle_act2_aqua.ogg` | 2막 아쿠아 일반 전투 (`ACT2-A01` 샤프니아·그라에나, `ACT2-A02` 샤프니아·골뱃·고래왕자) | 몰아치는 파도와 급류처럼 속도감 있는 수중 유적 전투. 빠른 피아노/하프시코드 패시지, 플룻/바이올린 선율, 질주하는 브레이크비트. (135~144 BPM) | 무한 루프 / 2분 내외 |
| `BGM-BATTLE-ACT3` (`cobblespire:bgm.battle_act3_galaxy`) | `bgm/bgm_battle_act3_galaxy.ogg` | 3막 일반 전투 (`ACT3-G01` 스컹탱크·몬냥이, `ACT3-G02` 독개굴·동탁군·골뱃) | 차갑고 날카로운 고대 성채 수비대와의 교전. 변박 리듬(7/8 또는 빠른 4/4), 사이버네틱 신스 베이스, 긴박한 오케스트라 히트. (140~150 BPM) | 무한 루프 / 2분 내외 |
| `BGM-BATTLE-ELITE` (`cobblespire:bgm.battle_elite`) | `bgm/bgm_battle_elite.ogg` | 전 막 엘리트 전투 (`ACT1-R04` 아보크·또도가스, `ACT1-R05` 헬가, `ACT2-M03` 폭타, `ACT2-A03` 샤크니아, `ACT3-G03/G04`) | 일반전보다 한 단계 높은 위압감과 치명적인 위기감. 불협화음 브라스 팡파르로 시작해 묵직한 더블 킥 드럼과 격렬한 현악 오스티나토로 전개. (145~155 BPM) | 무한 루프 / 2분~2분 30초 |

---

### 2-3. 막별 보스 전투 BGM (Loop, 4종)

각 막의 정점인 보스전(`FLOW-BOSS-INTRO-01` 및 실제 보스 전투)에서 재생되는 하이라이트 트랙.

| 트랙 ID (`sounds.json` 키) | 예정 파일 경로 (`assets/cobblespire/sounds/`) | 재생 구간 (`encounter_21.json` 보스 ID) | 분위기 · 악기 구성 · 권장 BPM | 루프 / 길이 |
|---|---|---|---|---|
| `BGM-BOSS-ACT1` (`cobblespire:bgm.boss_act1`) | `bgm/bgm_boss_act1.ogg` | 1막 보스전 (`ACT1-R06` 니드킹 단독, `ACT1-R07` 니드킹·니드퀸 듀오) | 무너진 석조 창고를 지배하는 거대 괴수 보스의 압도적 무력. 무거운 팀파니와 저음 튜바, 공격적인 신스 브라스와 박력 있는 오케스트라. (148~156 BPM) | 무한 루프 / 2분 30초 |
| `BGM-BOSS-ACT2-M` (`cobblespire:bgm.boss_act2_magma`) | `bgm/bgm_boss_act2_magma.ogg` | 2막 마그마 보스전 (`ACT2-M04` 메가폭타) | 분화하는 화산 칼데라와 메가진화 보스의 폭발적인 열기. 헤비메탈 기타, 합창(Choir) 스탭, 웅장한 금관과 몰아치는 타악기. (150~160 BPM) | 무한 루프 / 2분 30초 |
| `BGM-BOSS-ACT2-A` (`cobblespire:bgm.boss_act2_aqua`) | `bgm/bgm_boss_act2_aqua.ogg` | 2막 아쿠아 보스전 (`ACT2-A04` 메가샤크니아) | 폭풍우 치는 심해 신전을 가르는 초고속 포식자 메가샤크니아의 맹공. 날카로운 현악 속주, 폭풍 같은 신스 리드와 숨 가쁜 드럼 비트. (158~168 BPM) | 무한 루프 / 2분 30초 |
| `BGM-BOSS-ACT3` (`cobblespire:bgm.boss_act3_finale`) | `bgm/bgm_boss_act3_finale.ogg` | 3막 최종 보스전 (`ACT3-G05` 포푸니라·돈크로우, `ACT3-G06` 포푸니라·돈크로우·크로뱃) | 하늘의 균열 아래 펼쳐지는 모험의 최종 결전. 파이프 오르간, 풀 오케스트라, 전자음 리드와 장엄한 코러스가 결합된 클라이맥스 곡. (155~165 BPM) | 무한 루프 / 2분 45초~3분 |

---

### 2-4. 비전투 이벤트 및 시설 BGM (Loop, 3종)

물음표(`?`) 노드, 상점, 모닥불 휴식처 진입 시 지도 BGM에서 크로스페이드(또는 덕킹)되어 재생되는 전용 테마.

| 트랙 ID (`sounds.json` 키) | 예정 파일 경로 (`assets/cobblespire/sounds/`) | 재생 구간 (연결 화면/삽화) | 분위기 · 악기 구성 · 권장 BPM | 루프 / 길이 |
|---|---|---|---|---|
| `BGM-EVENT-MYSTERY` (`cobblespire:bgm.event_mystery`) | `bgm/bgm_event_mystery.ogg` | 물음표(`?`) 이벤트 전체 (`Q-DISCOVERY-*`, `Q-HAZARD-*`, `Q-REWARD-*`, `Q-MEET-*`, `Q-RECRUIT-*`, `Q-SWAP-*`, `PROGRESS-*`) 및 보물볼(`FAC-TREASURE-*`) | 수상한 몬스터볼·고대 사당·버려진 보급품을 마주했을 때의 기묘한 호기심과 선택 직전의 정적. 첼레스타(오르골 톤), 클라리넷 독주, 나지막한 현악 피치카토. (76~86 BPM) | 무한 루프 / 1분 30초 |
| `BGM-FACILITY-SHOP` (`cobblespire:bgm.facility_shop`) | `bgm/bgm_facility_shop.ogg` | 상점 시설 (`FACILITY-SHOP` / `FAC-SHOP-01`, `FAC-SHOP-02`) | 위험한 여정 중 만난 반갑고 아늑한 숲속 가판대. 어쿠스틱 기타 스윙/보사노바 리듬, 아코디언 또는 리코더, 가벼운 탬버린·셰이커. (105~115 BPM) | 무한 루프 / 1분 30초 |
| `BGM-FACILITY-REST` (`cobblespire:bgm.facility_rest`) | `bgm/bgm_facility_rest.ogg` | 모닥불 휴식처 (`FACILITY-REST` / `FAC-REST-01`, `FAC-REST-02` / `PROGRESS-UPGRADE-01`) | 밤하늘 아래 타오르는 모닥불의 온기와 평온한 회복. 잔잔한 핑거스타일 어쿠스틱 기타, 따뜻한 피아노 화음, 은은한 장작 타는 앰비언스. (68~76 BPM) | 무한 루프 / 1분 30초 |

---

### 2-5. 결과 화면 및 전환 징글 / 단발 테마 (Jingle & Stinger, 4종)

전투 종료 및 런 종료 결과 화면(`BattleResultScreen`, `RESULT-*`)에서 재생되는 음악.

| 트랙 ID (`sounds.json` 키) | 예정 파일 경로 (`assets/cobblespire/sounds/`) | 재생 구간 (연결 화면/삽화) | 분위기 · 구성 · 길이 | 루프 여부 |
|---|---|---|---|---|
| `JINGLE-BATTLE-WIN` (`cobblespire:jingle.battle_win`) | `jingle/jingle_battle_win.ogg` | 일반/엘리트 전투 승리 및 전리품 선택 화면 (`BattleResultScreen`, `REWARD-BATTLE-01`, `REWARD-MOVE-01`, `REWARD-CONSUMABLE-01`) | 첫 4초간 경쾌한 브라스 승리 팡파르 후, 전리품/기술카드를 고르는 동안 차분하고 기분 좋은 잔잔한 루프 비트로 이어짐. | 도입 4초 + 루프 30초 (또는 단발 징글 후 지도 BGM 복귀) |
| `JINGLE-ACT-CLEAR` (`cobblespire:jingle.act_clear`) | `jingle/jingle_act_clear.ogg` | 막 보스 격파 및 막 완료 화면 (`RESULT-ACT-CLEAR` / `FLOW-ACT-CLEAR-01`) | 보스를 넘어서고 다음 지역 능선을 바라보는 성취감과 여운의 팡파르. 호른과 현악이 넓게 펼쳐지는 웅장한 화음. | 단발 (8~12초, `stream: false`) |
| `BGM-RUN-VICTORY` (`cobblespire:bgm.run_victory`) | `bgm/bgm_run_victory.ogg` | 3막 최종 승리 화면 (`RESULT-RUN-CLEAR` / `FLOW-VICTORY-01`) | 새벽빛 산 정상에서 지나온 모든 여정을 내려다보는 벅찬 감동과 희망의 엔딩 테마. 로비 테마(`BGM-TITLE-PREP`)의 메인 멜로디를 오케스트라로 장대하게 변주. | 무한 루프 / 2분 |
| `JINGLE-RUN-DEFEAT` (`cobblespire:jingle.run_defeat`) | `jingle/jingle_run_defeat.ogg` | 전투 패배 및 런 종료 귀환 화면 (`RESULT-RUN-DEFEAT` / `FLOW-DEFEAT-01`) | 공포나 절망이 아닌, 저녁노을 속 안전한 숲 쉼터로 돌아가 다음 모험을 기약하는 차분하고 서정적인 피아노·목관 솔로. | 단발 후 잔향 페이드아웃 (10~15초) |

---

## 3. 인게임 사운드 전환 규칙 및 구현 체크리스트

1. **마인크래프트 기본 BGM과의 충돌 방지**:
   - 전용 아레나 차원(`cobblespire:arena`) 및 CobbleSpire UI 화면(`SpireBattleScreen`, `BattleResultScreen`, 지도/이벤트 화면) 활성화 중에는 마인크래프트 기본 배경음악(`SoundSource.MUSIC`)을 일시 정지하거나 음소거 처리한다.
2. **크로스페이드 및 상태 유지**:
   - 지도 화면(`BGM-MAP-ACT*`)에서 상점·휴식처·물음표 이벤트 진입 시 0.8~1.2초 크로스페이드로 전환하고, 이벤트 종료 후 다시 해당 막의 지도 BGM으로 복귀한다.
   - 물음표 이벤트에서 전투(`Q-COMBAT-01/02`)가 발생한 경우 즉시 해당 막의 전투 BGM(`BGM-BATTLE-ACT*`)으로 전환한다.
3. **핵심 UI 효과음(SFX) 보조 목록 (필요 시 바닐라/Cobblemon 사운드 재활용 가능)**:
   - 카드 호버 / 카드 선택 / 카드 발동(공격·방어·지원)
   - 턴 종료 버튼 클릭 / 드로우 더미 셔플
   - 지도 양피지 펼침·접힘(`MAP-SCROLL`) / 지도 노드 선택 및 공동 투표 핑(`MAP-COOP`)
   - 상점 금화 소비 / 휴식처 모닥불 회복 / 기술 강화 타격음 / 배지·진화 공명음
