# COBB-6 미반영 작업 조사 및 검토 가이드

조사일: 2026-11-14. 기준 소스는 등록된 in_place 개발 폴더이며 운영 설치본은 수정하지 않았다.

## 시작 상태와 누락 원인

- 로컬 `main` HEAD와 `git ls-remote origin refs/heads/main`은 모두 `37f0a2c07903573c4cbb16eaffd22fc80f6789d4`였다. 원격은 `https://github.com/arscabar/slaythecoblebus.git`이다. 로컬 작업 브랜치·태그·추가 worktree는 없었다.
- 추적 파일 diff는 없었고, 미추적 프로젝트 파일은 `build_slay.ps1`이었다. 런타임이 생성한 `AGENTS.md`, `.multica/`도 있었으며 소스가 아니므로 Git에서 제외했다.
- 그러나 `git stash list`에 `6a2481087908daeb1107ce60a500319ab81c4fe2` (`On main: pre-pull-backup`)가 남아 있었다. 기준 부모는 `bae5f32d8f30a83d6828f0eec73799fedb24c400`이며 변경된 추적 파일 15개와 세 번째 부모 `70f041cdb6b94f9f8e2e4868aa9b1b9aedd015b6`에 새 파일 12개가 있었다. clean 상태만으로 작업 누락이 없다고 결론 내릴 수 없는 실제 근거다.
- 이 stash에는 `0.6.0-dev`, 프로토콜 7, 일반 HUD·카메라 궤도/줌·입력 mixin, 실제 효과 피드백, 전원 항복 투표와 테스트가 있었다. 원본 stash를 pop/drop하지 않고 패치와 새 파일을 복구했다.
- 최신 main의 협동 세션·밸런스·피해 핑·음악·스킨을 유지하면서 6개 충돌 파일을 통합했다. 항복 투표는 탈퇴한 참가자를 분모와 투표에서 제거하며, 남은 참가자의 전원 동의가 성립하면 보상 없이 패배로 끝난다.

## 이미 Git에 반영된 큰 작업

| 커밋 | 포함 작업 |
| --- | --- |
| `a8efc22` | 5장 손패, 교체 새 드로우, 덱 이동 연출 |
| `9cc3200` | 독립 월드 전장과 실제 포켓몬 엔티티 |
| `fe2ceda`, `bae5f32` | 피해 예상·승리 기술카드 보상·역할 색상 |
| `b8886f8`~`10602a8` | 이벤트/시설/스토리/지도 아트와 명세 |
| `2406c62`, `533fe74` | 21개 음악/징글, UI 사운드·자막·아이콘·지도 텍스처·CI |
| `33ea909` | 1~4인 협동 방·서버 권한 전투·인원별 적 HP |
| `184c0ce`, `a0dc9d0`, `37f0a2c` | 피해 핑·동료 피해량·고딕 HUD·표시 문자 수정 |

작업이 모두 미커밋이었던 것은 아니다. 원격에 반영된 위 작업과 stash에만 있던 후속 작업이 공존했다.

## 관련 사본 비교

인접 작업 폴더와 문서의 과거 경로를 읽기만 조사했다. `compare-CobbleSpire`는 별도 웹 기획/체험판 저장소이며 HEAD `0d3498c`, `proto/`, `data/`, `docs/` 구조다. 현재 Fabric 모드의 다른 개발 브랜치나 복제본으로 취급해 덮어쓰지 않았다. 과거 문서의 `C:/gitsource`와 스크립트의 `W:` 작업 경로는 현재 환경에서 존재하지 않았다. 머신 전체나 다른 사용자의 저장소를 전수 조사했다는 뜻은 아니다.

## 포함·제외 및 원본 보존

- 포함: stash의 15개 변경 파일과 12개 새 파일, 일반화한 `build_slay.ps1`, 로컬 런타임 ignore, 현재 후보 안내와 릴리즈/조사 문서. `fabric.mod.json`의 저장소 URL 오타도 실제 origin에 맞췄다.
- QA 모드는 추가 피드백 검사로 revision이 바뀐 뒤 스냅샷을 다시 읽도록 수정했다. 별도 Gradle 프로젝트가 루트의 힙 설정을 상속하지 않아 발생한 `Java heap space` 실패는 `integration-tests/gradle.properties`의 3GB 설정으로 보완했다.
- 제외: `.gradle/`, `.kotlin/`, 모든 `build/`, `run-ui/`, 로그, 의존 JAR, 배포 ZIP/JAR, 실제 게임 데이터, `.multica/`, `AGENTS.md`. 산출물은 `dist/COBB-6/`와 Multica 첨부로 제공한다. 무차별 `git add -A`는 사용하지 않는다.
- `build_slay.ps1` 원본 SHA256: `22ba7257b87fbf7c6962012b61376c42a9e388255825030d7ae86f576b2b9bb7`. 내용은 JDK/캐시/작업 폴더의 머신 고정 경로와 Gradle 호출뿐이었고 비밀정보는 없었다. 원본 바이트는 `dist/COBB-6/preserved/build_slay.original.ps1`에 보존했다.
- 같은 보존 폴더의 `pre-pull-backup.patch`, `pre-pull-untracked.tar`는 stash 내용의 독립 사본이다. 원래 stash와 기존 브랜치/커밋 이력도 그대로 남는다. 생성된 보존 파일은 Git 소스에 포함하지 않는다.

## 검토 순서와 남은 검증

1. 기준 main 대비 diff에서 새 mixin 4개와 `cobblespire.client.mixins.json`, `BattleHudState`/렌더러/레이아웃을 함께 확인한다. `SpireClient`, `WorldBattleView`가 HUD·카메라·음악 생명주기를 연결한다.
2. `BattleKernel`, `TrialService`, `BattleFeedback`, `NativeEffects`를 함께 확인한다. 협동 참가/탈퇴, 전원 동의, 취소·오래된 revision·외부인 거부, 승리 보상과의 분리를 중점 검토한다.
3. `SurrenderTest`, `BattleFeedbackTest`, `BattleHudLayoutTest`, 기존 `CoopScalingTest` 및 전체 테스트를 실행한다. QA 모드 빌드 성공과 실제 시험 서버의 `spiresmoke` 실행은 다른 검증이다.
4. 격리된 실제 클라이언트에서 mixin 적용, F10/접기/숫자키, 카메라 복구·줌·창 크기, 2~4인 턴 대기·핑·동료 표시·탈퇴 중 투표·전원 항복, 종료 후 원래 위치/PC/인벤토리를 확인한다. 이 단계의 새 후보는 실제 게임에서 실행 검증하지 않았다.

버전과 프로토콜은 각각 `0.6.0-dev`, `7`이다. `design/data/current_rules.json`의 `protocol: 3`은 보존된 기획 데이터의 값이며 현재 전송 프로토콜을 뜻하지 않는다. GDD v4.2의 5장 손패·교체/버림·고정 Lv50·무효 ×0 규칙은 유지한다.
