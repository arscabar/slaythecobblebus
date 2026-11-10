# CobbleSpire / slaythecoblebus

COBBLEVERSE 1.7.42 / Minecraft 1.21.1 / Fabric / Cobblemon 1.7.3용 독립 협동 로그라이크 애드온입니다. CobbleLeague·CobbleSynergy 의존성은 없습니다.

기본 진입 키는 **F10**이며 게임 키 설정에서 변경합니다. 설치된 기본 모드팩에서 F4·F6·F7·F8·F9·U는 다른 기능에 쓰이고 있어 피했습니다.

## 개발 단계

- [x] 빈 원격 저장소 복제와 독립 Gradle/Fabric 프로젝트 초기화
- [x] 원본 GDD와 JSON 정의 보존
- [ ] 설치된 모든 애드온의 포켓몬·폼·기술 레지스트리 조사 및 호환성 보고
- [ ] 독립 기술 덱·에너지·협동 페이즈 전투와 검증
- [ ] 3막 지도·보상·상점·휴식·영입·보스·모험 도구
- [ ] 독립 설치 및 기존 애드온과의 공존 검증

런 상태와 포켓몬은 별도 메모리 데이터로 관리하며 기존 PC·인벤토리를 변경하지 않습니다. 콘텐츠를 조용히 흉내 내거나 미지원 규칙을 작동한다고 표시하지 않습니다. 검증되지 않은 규칙은 이유와 함께 활성화에서 제외합니다.

## 원본 자료

`design/GDD-v4.1.md`와 `design/data`가 제공된 기획의 보존 사본입니다. `design/Settings.json`, `LevelProgression.json`, `MoveUpgrades.json`은 원본 엑셀의 셀 주소를 보존한 읽기 전용 발췌입니다. 최신 규칙 우선순위는 GDD v4.1과 정규 JSON입니다.

빌드: JDK 21에서 `./gradlew test build`.
