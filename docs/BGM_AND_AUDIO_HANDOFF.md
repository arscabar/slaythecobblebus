# CobbleSpire BGM 및 오디오 제작 인계서

이 문서는 CobbleSpire(마인크래프트 Cobblemon 기반 3막 로그라이크)에서 필요한 **배경음악(BGM) 및 결과 징글(Jingle/Stinger) 전체 목록**과 구간별 연출·전환 명세를 정의한다.

- **현재 구현 상태**: `src/main/resources/assets/cobblespire/sounds.json`과 21개 실제 포켓몬 기반 `.ogg` 오디오 에셋(`bgm/*.ogg` 18종 + `jingle/*.ogg` 3종)이 번들 완료되었으며, 클라이언트 상태 제어기([SpireMusic.kt](file:///c:/gitsource/slaythecobblebus/src/main/kotlin/dev/cobblespire/client/SpireMusic.kt))가 로비/전투(막·티어별)/승리·패배·포켓몬센터 회복 효과음을 재생하도록 연동되었다. (지도/상점/휴식처/이벤트 UI가 추가되는 Stage 8+에서는 `SpireMusic.enterFacility(...)` / `exitFacility()` 호출만 연결하면 즉시 재생된다.)
- **포맷 기준**: 마인크래프트 Fabric 표준 **Ogg Vorbis (`.ogg`)** (`bgm/*` 및 긴 징글은 `stream: true`, 단발 `jingle/run_defeat`는 `stream: false`).
- **원음 대체 및 추후 교체 가능성**: Modrinth 커뮤니티 포켓몬 리소스팩(Karasus Gen3 Songpack / Original Pokemon Battle Music / Cobblemon Music Pack / Pokemusic-Enviroment / jor's Cobblemon Music Pack)의 실제 포켓몬 BGM을 Ogg Vorbis로 변환·경량 번들(총 21트랙)해 즉시 재생 가능하게 구성했으며, 추후 상업적 배포나 오리지널 사운드로 교체할 때는 `assets/cobblespire/sounds/` 파일만 동일 경로로 덮어쓰면 된다.

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

| 트랙 ID (`sounds.json` 키) | 번들 파일 경로 (`assets/cobblespire/sounds/`) | 적용된 포켓몬 원곡 및 재생 구간 | 분위기 · 연출 의도 | 루프 / 상태 |
|---|---|---|---|---|
| `BGM-TITLE-PREP` (`cobblespire:bgm.lobby_prep`) | `bgm/lobby_prep.ogg` | **RSE Main Theme** · 모험 시작(`SpireMenuScreen`), 도감(`RegistryScreen`), 런 준비 화면 | 설렘과 결의가 공존하는 출발 전 테마. | 무한 루프 / **구현·연동 완료** |
| `BGM-MAP-ACT1` (`cobblespire:bgm.map_act1`) | `bgm/map_act1.ogg` | **FRLG Viridian Forest** · 1막 도입(`STORY-ACT1`) 및 1막 분기 지도(`MAP-BG-ACT1`) | 고대 숲과 폐시설을 탐색하는 호기심·긴장감. | 무한 루프 / **에셋 번들 완료 (Stage 8 지도 연결 대기)** |
| `BGM-MAP-ACT2-M` (`cobblespire:bgm.map_act2_magma`) | `bgm/map_act2_magma.ogg` | **RSE Mt. Chimney** · 2막 마그마 경로 도입(`STORY-ACT2-M`) 및 지도(`MAP-BG-ACT2-M`) | 화산 협곡과 지하 용암 유적의 열기. | 무한 루프 / **에셋 번들 완료 (Stage 8 지도 연결 대기)** |
| `BGM-MAP-ACT2-A` (`cobblespire:bgm.map_act2_aqua`) | `bgm/map_act2_aqua.ogg` | **RSE Dive** · 2막 아쿠아 경로 도입(`STORY-ACT2-A`) 및 지도(`MAP-BG-ACT2-A`) | 침수된 해안 신전과 심해 수로의 수압감. | 무한 루프 / **에셋 번들 완료 (Stage 8 지도 연결 대기)** |
| `BGM-MAP-ACT3` (`cobblespire:bgm.map_act3_galaxy`) | `bgm/map_act3_galaxy.ogg` | **BDSP Mt. Coronet** · 3막 도입(`STORY-ACT3`) 및 고산 성채 지도(`MAP-BG-ACT3`) | 천관산 정상과 우주적 균열의 고조감. | 무한 루프 / **에셋 번들 완료 (Stage 8 지도 연결 대기)** |

---

### 2-2. 일반 전투 및 엘리트 전투 BGM (Loop, 5종)

전용 월드 아레나([WORLD_ARENA.md](WORLD_ARENA.md))에서 진행되는 턴제 카드 전투용 BGM. 사고(카드 계산·타깃 선택)를 방해하지 않으면서도 박진감 있는 리듬을 유지해야 한다.

| 트랙 ID (`sounds.json` 키) | 번들 파일 경로 (`assets/cobblespire/sounds/`) | 적용된 포켓몬 원곡 및 조우 매핑 | 상태 |
|---|---|---|---|
| `BGM-BATTLE-ACT1` (`cobblespire:bgm.battle_act1`) | `bgm/battle_act1.ogg` | **FRLG Wild Battle** · 1막 일반 전투 (`ACT1-R01`, `ACT1-R02`, `ACT1-R03`) | 무한 루프 / **구현·연동 완료** |
| `BGM-BATTLE-ACT2-M` (`cobblespire:bgm.battle_act2_magma`) | `bgm/battle_act2_magma.ogg` | **PBR Magma Colosseum** · 2막 마그마 일반 전투 (`ACT2-M01`, `ACT2-M02`) | 무한 루프 / **구현·연동 완료** |
| `BGM-BATTLE-ACT2-A` (`cobblespire:bgm.battle_act2_aqua`) | `bgm/battle_act2_aqua.ogg` | **PBR Lagoon Colosseum** · 2막 아쿠아 일반 전투 (`ACT2-A01`, `ACT2-A02`) | 무한 루프 / **구현·연동 완료** |
| `BGM-BATTLE-ACT3` (`cobblespire:bgm.battle_act3_galaxy`) | `bgm/battle_act3_galaxy.ogg` | **DP Battle! Team Galactic** · 3막 일반 전투 (`ACT3-G01`, `ACT3-G02`) | 무한 루프 / **구현·연동 완료** |
| `BGM-BATTLE-ELITE` (`cobblespire:bgm.battle_elite`) | `bgm/battle_elite.ogg` | **BW Battle! Elite Four** · 전 막 엘리트 전투 (`ACT1-R04/R05`, `ACT2-M03`, `ACT2-A03`, `ACT3-G03/G04`) | 무한 루프 / **구현·연동 완료** |

---

### 2-3. 막별 보스 전투 BGM (Loop, 4종)

각 막의 정점인 보스전(`FLOW-BOSS-INTRO-01` 및 실제 보스 전투)에서 재생되는 하이라이트 트랙.

| 트랙 ID (`sounds.json` 키) | 번들 파일 경로 (`assets/cobblespire/sounds/`) | 적용된 포켓몬 원곡 및 보스 ID | 상태 |
|---|---|---|---|
| `BGM-BOSS-ACT1` (`cobblespire:bgm.boss_act1`) | `bgm/boss_act1.ogg` | **FRLG Trainer Battle** · 1막 보스전 (`ACT1-R06`, `ACT1-R07`) | 무한 루프 / **구현·연동 완료** |
| `BGM-BOSS-ACT2-M` (`cobblespire:bgm.boss_act2_magma`) | `bgm/boss_act2_magma.ogg` | **RSE Battle! Super-Ancient Pokémon** · 2막 마그마 보스전 (`ACT2-M04` 메가폭타) | 무한 루프 / **구현·연동 완료** |
| `BGM-BOSS-ACT2-A` (`cobblespire:bgm.boss_act2_aqua`) | `bgm/boss_act2_aqua.ogg` | **PBR Waterfall Colosseum** · 2막 아쿠아 보스전 (`ACT2-A04` 메가샤크니아) | 무한 루프 / **구현·연동 완료** |
| `BGM-BOSS-ACT3` (`cobblespire:bgm.boss_act3_finale`) | `bgm/boss_act3_finale.ogg` | **DP Battle! Team Galactic Boss (Cyrus)** · 3막 최종 보스전 (`ACT3-G05`, `ACT3-G06`) | 무한 루프 / **구현·연동 완료** |

---

### 2-4. 비전투 이벤트 및 시설 BGM (Loop, 3종)

물음표(`?`) 노드, 상점, 포켓몬센터/휴식처 진입 시 지도 BGM에서 전환되어 재생되는 전용 테마.

| 트랙 ID (`sounds.json` 키) | 번들 파일 경로 (`assets/cobblespire/sounds/`) | 적용된 포켓몬 원곡 및 연결 시설/화면 | 상태 |
|---|---|---|---|
| `BGM-EVENT-MYSTERY` (`cobblespire:bgm.event_mystery`) | `bgm/event_mystery.ogg` | **HGSS Ruins of Alph** · 물음표(`?`) 이벤트 전체 및 보물볼(`FAC-TREASURE-*`) | 무한 루프 / **에셋 번들 완료 (`enterFacility(EVENT_MYSTERY)` 연결 대기)** |
| `BGM-FACILITY-SHOP` (`cobblespire:bgm.facility_shop`) | `bgm/facility_shop.ogg` | **RSE/FRLG Friendly Shop (Poké Mart)** · 상점 시설(`FACILITY-SHOP`) | 무한 루프 / **에셋 번들 완료 (`enterFacility(FACILITY_SHOP)` 연결 대기)** |
| `BGM-FACILITY-REST` (`cobblespire:bgm.facility_rest`) | `bgm/facility_rest.ogg` (+ `cobblemon:block.healing_machine.active` SFX) | **RSE Pokémon Center** + Cobblemon 내장 회복음 · 포켓몬센터/휴식처(`FACILITY-REST`) 및 전투 부활 회복 시 | 무한 루프 + 회복음 / **에셋 번들 + 부활 회복음 연동 완료** |

---

### 2-5. 결과 화면 및 전환 징글 / 단발 테마 (Jingle & Stinger, 4종)

전투 종료 및 런 종료 결과 화면(`BattleResultScreen`, `RESULT-*`)에서 재생되는 음악.

| 트랙 ID (`sounds.json` 키) | 번들 파일 경로 (`assets/cobblespire/sounds/`) | 적용된 포켓몬 원곡 및 재생 구간 | 상태 |
|---|---|---|---|
| `JINGLE-BATTLE-WIN` (`cobblespire:jingle.battle_win`) | `jingle/battle_win.ogg` (+ `cobblemon:gui.levelup` SFX) | **Sun & Moon Victory!** · 일반/엘리트 전투 승리(`BattleResultScreen`) | 20초 페이드아웃 / **구현·연동 완료** |
| `JINGLE-ACT-CLEAR` (`cobblespire:jingle.act_clear`) | `jingle/act_clear.ogg` | **LGPE Congratulations!** · Act 1/2 보스 격파 시(`BattleResultScreen`) | 20초 페이드아웃 / **구현·연동 완료** |
| `BGM-RUN-VICTORY` (`cobblespire:bgm.run_victory`) | `bgm/run_victory.ogg` | **DP Hall of Fame** · 3막 최종 보스 격파 및 전당등록(`BattleResultScreen`) | 무한 루프 / **구현·연동 완료** |
| `JINGLE-RUN-DEFEAT` (`cobblespire:jingle.run_defeat`) | `jingle/run_defeat.ogg` | **RSE Regret** · 전투 패배 시(`BattleResultScreen`) | 단발 / **구현·연동 완료** |

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
