# 폐기용 전용 서버 검사

일반 배포/친구용 설치에 포함하지 않습니다. 별도 시험 서버에만 설치하고 `-Dcobblespire.integration=true`로 실행한 후 운영자 콘솔에서 `spiresmoke`를 호출합니다. SpireQA 가상 플레이어를 만들고 검사 후 제거합니다. 실제 플레이어를 조작하지 않습니다.

루트 JAR 빌드 뒤 `../gradlew -p . build`로 검사 모드만 별도로 빌드합니다.
