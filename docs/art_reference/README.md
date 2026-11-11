# CobbleSpire 이벤트·시설·스토리 일러스트 자산 목록 및 설명

이 디렉터리는 [이벤트·스토리 이미지 제작 인계서](../EVENT_ART_HANDOFF.md) 및 [비전투 이벤트 전체 기획](../EVENT_ART_FULL_CATALOG.md) 기준에 따라 생성된 장면 삽화 및 레퍼런스 자산을 보관합니다.

- **공통 아트 스타일**: 굵은 잉크 윤곽선(bold hand-inked comic outlines), 각진 형태, 거친 회화적 음영과 명암 대비(rough painterly shading & dramatic chiaroscuro), 마인크래프트 블록형 지형·유적과 Cobblemon 포켓몬의 조화.
- **공통 제약 사항**: UI·글자·숫자·로고·버튼·테두리·워터마크·인간 트레이너·유혈/고어 배제, 16:9 가로 구도(1376×768 PNG), 주요 피사체 중앙 70% 배치.
- **생성 일자 / 도구**: 2026-11-11 / 내장 이미지 생성 도구 (`gemini-3.1-flash-image`)

---

## 1. 기본 컨셉 레퍼런스 (`docs/art_reference/`)

| 파일 | 장면 ID / 용도 | 설명 |
|---|---|---|
| [event_pokeball_concept.png](event_pokeball_concept.png) | `EVENT-MYSTERY-BALL` (`Q-DISCOVERY-01`) | 숲속 이끼 낀 블록형 석조 제단 위에서 불길한 빛을 내는 수상한 구형 몬스터볼과 이를 조심스럽게 바라보는 이브이. 어둠 속 두 눈빛이 긴장감을 더하는 기준 이벤트 샘플. |
| [map_background_concept.png](map_background_concept.png) | `MAP-BG-ACT1` | 1막 분기 지도용 양피지 배경 시안 (선·노드·글자 없는 순수 배경, 가장자리에 블록형 숲과 산맥 배치). |
| [map_ui_concept.png](map_ui_concept.png) | 지도 화면 무드 컨셉 | 1막 분기 지도 전체 레이아웃 참고용 무드 시안 (배경에 경로가 구워져 있으므로 실제 구현 자산으로는 `map_background_concept.png` + 동적 렌더링 사용). |

---

## 2. 물음표(`?`) 이벤트·시설·스토리 삽화 (`docs/art_reference/events/`)

일반 전투 조우가 아닌 **물음표(`?`) 이벤트 노드, 시설(상점·휴식), 막 시작 스토리 도입** 화면에서 사용되는 장면 삽화입니다.

| 파일 | 장면 ID (인계서 / 확장 카탈로그) | 구분 | 장면 설명 및 연출 의도 |
|---|---|---|---|
| [event_wild_meeting.png](events/event_wild_meeting.png) | `EVENT-WILD-MEETING` (`Q-MEET-01`) | 물음표 (만남/영입 후보) | 햇살이 비치는 블록형 자작나무·참나무 숲길에서 탐험 파티의 피카츄와 야생 이상해씨가 거리를 두고 서로를 조심스럽게 살피는 장면. 전투가 아닌 경계와 따뜻한 호기심을 표현. |
| [event_lost_supplies.png](events/event_lost_supplies.png) | `EVENT-LOST-SUPPLIES` (`Q-DISCOVERY-02`) | 물음표 (발견/탐험) | 해 질 녘 버려진 블록형 야영지에서 꺼진 모닥불과 찢어진 천막 옆, 닫힌 나무 보급 상자와 진흙 바닥에 찍힌 거대한 괴수의 발자국을 브케인이 조사하는 장면. 상자 내부 보상은 가려져 있음. |
| [event_rockfall.png](events/event_rockfall.png) | `EVENT-ROCKFALL` (`Q-HAZARD-01`) | 물음표 (위험/대가) | 좁고 높은 마인크래프트 블록형 협곡에서 머리 위 거대한 바위에 균열이 가며 작은 돌멩이가 떨어지기 시작하는 순간, 리아코가 위를 올려다보며 갈림길을 마주한 낙석 직전의 긴장 장면. |
| [event_shrine.png](events/event_shrine.png) | `EVENT-SHRINE` (`PROGRESS-EVOLUTION-01`) | 물음표 (사당/진화 후보) | 이끼와 덩굴로 덮인 지하 블록형 고대 성소에서 청록색·호박색 기하학 문양이 빛나는 석조 제단(비석)을 리오르가 올려다보는 장면. 특정 진화 결과나 텍스트 없이 신비로운 고대 힘을 암시. |
| [event_encounter.png](events/event_encounter.png) | `EVENT-ENCOUNTER` (`Q-COMBAT-01`) | 물음표 (전투 발생형 조우) | 어두운 블록형 동굴 입구 앞에서 방어 자세를 취한 나무지기와, 동굴 심연의 어둠 속에서 붉은 호박색으로 빛나는 정체불명의 두 쌍의 눈빛 및 날카로운 발톱 실루엣이 대치하는 장면. |
| [facility_shop.png](events/facility_shop.png) | `FACILITY-SHOP` (`FAC-SHOP-01`) | 시설 (상점) | 블록형 참나무와 조약돌로 지어진 숲속 쉼터 상점. 줄무늬 차양과 따뜻한 블록 랜턴 아래 글자나 가격표가 없는 물약병, 나무열매 바구니, 두루마리, 가죽 주머니가 진열되어 있고 물짱이가 이를 올려다보는 장면. |
| [facility_rest.png](events/facility_rest.png) | `FACILITY-REST` (`FAC-REST-01`) | 시설 (휴식처) | 밤하늘 아래 이끼 낀 블록형 돌담으로 둘러싸인 안전한 야영지. 따뜻한 모닥불 곁 침낭 위에서 잠든 파이리와 통나무에 앉아 불꽃을 바라보는 꼬부기, 그리고 배경 구석의 나무 수련 표적(휴식/기술 강화 선택 암시). |
| [story_act1.png](events/story_act1.png) | `STORY-ACT1` (`FLOW-ACT1-01`) | 막 도입 (1막 시작) | 거대한 블록형 숲과 고대 석조 아치문 앞에서 두 갈래로 갈라지는 자갈길을 마주한 작은 포켓몬(이브이)의 뒷모습 실루엣. 왼편의 그늘진 기둥 유적과 오른편의 먼 성채 탑이 첫 여정의 선택을 암시. |

---

## 3. 전투 발생형 이벤트 / 조우 일러스트 후보 (`docs/art_reference/encounters/`)

> **참고**: 일반 전투 노드는 인게임 3D 전장으로 바로 진입하므로 별도 정적 일러스트가 필수가 아니며, 아래 이미지들은 **전투 발생형 물음표 이벤트(`EVENT-ENCOUNTER`)의 세부 분기 연출, 엘리트/보스 조우 도입, 또는 도감/기록 화면**에서 선택적으로 활용할 수 있는 1막 포켓몬 조우 삽화입니다.

| 파일 | 조우 ID | 등장 포켓몬 | 장면 설명 |
|---|---|---|---|
| [encounter_act1_r01.png](encounters/encounter_act1_r01.png) | `ACT1-R01` | 꼬렛 (1) | 숲속 유적의 낡은 블록형 나무 보급 상자 옆에서 고개를 내밀며 경계하는 꼬렛과의 첫 조우. |
| [encounter_act1_r02.png](encounters/encounter_act1_r02.png) | `ACT1-R02` | 아보, 주뱃 (2) | 이끼 낀 블록형 동굴 입구 바위 위에 또아리를 튼 아보와 천장 그늘에서 날아 내려오는 주뱃이 길을 막는 장면. |
| [encounter_act1_r03.png](encounters/encounter_act1_r03.png) | `ACT1-R03` | 꼬렛, 아보, 주뱃 (3) | 무너진 지붕 틈으로 햇살이 들어오는 버려진 블록형 폐창고 내부에서 바닥(꼬렛)·중단 석조 단(아보)·상단 서까래(주뱃)의 서로 다른 높이로 모습을 드러낸 세 포켓몬. |
| [encounter_act1_r04.png](encounters/encounter_act1_r04.png) | `ACT1-R04` | 아보크, 또도가스 (2, 엘리트) | 나무뿌리가 침식한 어두운 지하 블록형 폐시설에서 바닥에 깔린 보랏빛 독 안개와 함께 위협적으로 후드를 펼친 아보크와 독가스를 뿜는 또도가스가 등장하는 엘리트 조우 장면. |

---

## 4. 생성 프롬프트 기록 (재현 및 후속 작업용)

### `EVENT-WILD-MEETING` (`events/event_wild_meeting.png`)
```text
A high quality narrative video game event illustration in a bold comic book art style. In a lush, blocky Minecraft forest featuring white birch trees with dark notches and blocky oak trees with cubic voxel foliage, a sunlit path of mossy cobblestones winds beside a natural cubic stone cliff overhang. On the left side of the path, an explorer's Pikachu is viewed from a three-quarter rear-and-side angle, sitting attentively on the grass, ears perked up with curiosity and lightning-bolt tail curved upward. On the right side across a short gap, a wild Bulbasaur stands on all fours beside blocky voxel ferns and a moss-covered boulder, looking back at Pikachu with gentle, cautious, expressive eyes. The atmosphere is quiet, warm, and full of mutual curiosity, capturing a peaceful encounter before an offer of friendship. Radiant golden sunbeams and amber god rays filter through the blocky canopy, casting dappled light across the mossy ground with dramatic chiaroscuro and rich charcoal shadows. Hand-inked bold black outlines, graphic novel illustration, rich painterly textures, cel shading, pure video game concept art. No text, no letters, no UI, no buttons, no borders, no watermark, no human characters.
```

### `EVENT-LOST-SUPPLIES` (`events/event_lost_supplies.png`)
```text
A cinematic digital concept illustration in a bold hand-inked comic art style with crisp black outlines, dramatic chiaroscuro lighting, and rough painterly shading, set in a blocky Minecraft world. An abandoned wilderness campsite in a blocky voxel forest clearing at dusk. In the center-left foreground, a small Cyndaquil Pokémon (cute echidna-like creature with cream-colored underbelly, dark teal-blue back, long tapered snout, and a small warm flame flickering from the spots on its back) cautiously lowers its nose to sniff deep, menacing monster claw footprints embedded in wet mud. The heavy beast tracks trail across the muddy dirt path toward a closed, weathered blocky wooden supply chest bound with rusted iron bands and a sturdy iron clasp. Beside the chest is an extinguished blocky stone campfire with cold grey ashes and blackened charred wood, and a torn canvas expedition tent flapping on square wooden posts. In the background, towering blocky voxel pine and oak trees with cubic leaf clusters loom in deep indigo and purple evening shadows, while warm golden amber sunset rays cut through the canopy in dramatic volumetric beams. Mysterious, suspenseful, cautious mood. Widescreen 16:9 composition, main subjects centered in the middle 70%. Pure illustration with no text, no words, no letters, no UI, no game borders, no watermarks, no human trainers.
```

### `EVENT-ROCKFALL` (`events/event_rockfall.png`)
```text
Cinematic narrative event illustration for a Minecraft Cobblemon game. Art style: bold black hand-inked comic outlines, rough painterly shading, dramatic chiaroscuro lighting, textured blocky Minecraft voxel environment combined with recognizable Pokémon design. Low-to-mid angle perspective inside a deep, towering, narrow Minecraft stone ravine canyon made of cubic stone and cobblestone blocks. In the foreground on the canyon floor, a small blue Totodile (bipedal crocodilian Pokémon with red dorsal crest spines along head and back, cream chevron pattern on its chest, large expressive eyes) freezes mid-step and gazes sharply upward in alarmed surprise. High above in the canyon chasm, a massive cracked blocky boulder hangs precariously wedged between the sheer cliff walls, spiderweb fracture cracks glowing faintly across its surface, with small voxel pebbles and trickles of dust beginning to tumble down through the air. Two paths lie ahead of Totodile: a dangerous direct passage directly underneath the fracturing overhang, and a narrow block ledge winding along the canyon wall. A dramatic shaft of warm amber golden sunlight pierces down through the shadowy gorge. High tension, split-second anticipation before a rockfall. Completely clean image with absolutely NO UI, no text, no letters, no numbers, no health bars, no dialog boxes, no borders, no watermarks, no human trainers.
```

### `EVENT-SHRINE` (`events/event_shrine.png`)
```text
A widescreen 16:9 narrative fantasy event illustration set deep inside a sunken, overgrown blocky Minecraft stone sanctuary. In the foreground slightly to the left, a small Riolu (bipedal blue and black canine jackal Pokémon with black mask and floppy head pendants) is seen from a three-quarter rear back-side view, standing at the base of short mossy stone block steps, gazing up in quiet awe. Facing Riolu at the top of the short stone dais stands an ancient blocky stone monolith altar constructed from weathered Minecraft stone blocks and mossy cobblestone draped with hanging vines. The stone monolith is intricately carved with glowing abstract geometric diamond and spiral motifs that emit a soft, mystical cyan and warm amber aura, illuminating the sanctuary floor with ethereal light and drifting glowing motes. Bold black hand-inked comic outlines, angular voxel stone architecture, rough painterly shading, dramatic chiaroscuro contrasting cavernous stone shadows with mystical cyan and amber luminescence. Sacred, mysterious, evocative atmosphere. Completely clean image with no text, no words, no letters, no numbers, no user interface, no frame borders, no watermark, no human trainers.
```

### `EVENT-ENCOUNTER` (`events/event_encounter.png`)
```text
A dramatic narrative comic illustration for a roguelike game. Art style features bold black hand-inked comic book outlines, rich painterly textured shading, and high-contrast dramatic chiaroscuro lighting, depicting a blocky Minecraft voxel world with Pokémon. In the foreground, a small Treecko Pokémon is seen from a rear three-quarter angle in a tense, defensive crouched stance, looking ahead. Ahead of Treecko lies the ominous gaping entrance of a dark overgrown cavern surrounded by angular blocky Minecraft stone cubes, mossy cobblestone, blocky dirt steps, hanging cubic vine blocks, and tangled voxel forest trees. Deep inside the pitch-black void of the cave mouth, two separate pairs of glowing amber-red predator eyes gleam menacingly from the darkness, accompanied by vague hints of sharp shadowy claws and silhouette horns lurking in the black shadows, keeping the enemy species mysterious. Atmospheric moonlight or faint dappled rays filter through the blocky tree canopy above, casting crisp rim light along Treecko's back and tail and catching the edges of the stone voxel blocks, while the cave interior remains pitch black with glowing eyes. Cinematic 16:9 landscape composition, central 70% focus. Clean artwork, no text, no UI, no letters, no numbers, no border, no buttons, no watermark, no human trainers, no pokeballs.
```

### `FACILITY-SHOP` (`events/facility_shop.png`)
```text
A 16:9 widescreen digital illustration in a stylized graphic novel comic art style with bold black hand-inked outlines and rich painterly shading. A cozy wilderness merchant stall in a blocky Minecraft voxel forest clearing at twilight. The merchant booth is built from blocky oak wood planks and cobblestone blocks, covered by an arched red-and-white striped fabric awning. Warm glowing cubic Minecraft lanterns hang from the wooden beams, casting bright amber light over the counter. Neatly displayed on the blocky counter and shelves are generic fantasy expedition supplies: blank unmarked round glass potion flasks with glowing red and blue liquid, small woven baskets filled with colorful berries, rolled blank parchment scrolls tied with twine, and a small closed leather pouch. There are no price tags, no text, no letters, no numbers, and no signs anywhere. In front of the counter, a curious Mudkip stands on the blocky grassy path, looking up with big expressive eyes at the travel goods. In the background, blocky cubic pine and oak trees fade into cool deep teal and navy shadows. Welcoming safe haven atmosphere, dramatic warm-and-cool lighting chiaroscuro, no humans, no UI, no borders, no watermark.
```

### `FACILITY-REST` (`events/facility_rest.png`)
```text
A warm, atmospheric concept art illustration of a campfire rest site at nightfall in a blocky Minecraft world. In a sheltered campsite hollow bordered by mossy Minecraft stone brick walls and dark blocky oak trees, a crackling campfire built inside a ring of cobblestone blocks illuminates the scene with radiant golden-amber light and floating embers. Curled up peacefully beside the fire on a soft bedroll atop a mossy stone block is a sleeping Charmander, its tail flame softly glowing. Next to it, a resting Squirtle sits calmly on a carved wooden log, looking warmly into the embers. In the background corner of the campsite stands a rustic wooden combat training dummy made of fence posts and hay blocks. Bold black hand-inked comic outlines, rough painterly shading, dramatic chiaroscuro lighting with rich amber firelight contrasting against deep indigo night shadows. Widescreen 16:9 landscape format, highly detailed, clean composition, completely free of any text, letters, numbers, UI, HUD elements, borders, or watermarks. No human trainers.
```

### `STORY-ACT1` (`events/story_act1.png`)
```text
A cinematic 16:9 widescreen illustration of the threshold of an ancient forest expedition in a blocky voxel Minecraft world. In the lower foreground, seen from behind in dark silhouette with subtle warm rim lighting, a small explorer Pokémon (an Eevee with distinct perked ears and fluffy tail) stands looking forward at a crucial crossroads. Ahead stands a weathered, ancient blocky stone archway draped in dangling vines and emerald moss. Through and beyond the archway, an overgrown cobblestone road clearly forks into two diverging paths: one path winding left deeper into shadowy ancient ruins with mossy stone columns, and the other path winding right towards distant derelict blocky stone towers silhouetted against the morning sky. Dramatic shafts of golden amber morning sunlight stream through the towering voxel trees with cubic foliage, illuminating floating pollen and mist, casting bold dramatic chiaroscuro shadows across the scene. Graphic novel art style with bold black hand-inked comic outlines, rough painterly shading, and crisp blocky voxel geometry. Color palette: earthy rusted browns, lush moss and forest greens, deep charcoal shadows, and radiant amber sunlight. Clean illustration, absolutely no text, no letters, no UI, no buttons, no borders, no watermarks, no humans.
```
