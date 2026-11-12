<div align="center">

# ION

**차가 아는 것으로 가족의 시간을 챙기는 차량용 AI Bot 앱**

현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Connect · Android Automotive

[![Pleos Connect Emulator 2.1.0](https://img.shields.io/badge/Pleos_Connect_Emulator-2.1.0-1f1f1f)](#테스트-환경)
[![Android Automotive](https://img.shields.io/badge/Android_Automotive-API_34-3ddc84)](#테스트-환경)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7f52ff)](#테스트-환경)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285f4)](#테스트-환경)
[![Unit tests](https://img.shields.io/badge/unit_tests-367_passed-2ea44f)](#품질)

**[📄 기획서 · prd.moonwave.kr](https://prd.moonwave.kr)** · **[🎨 ION Studio · ion-studio.moonwave.kr](https://ion-studio.moonwave.kr)** · **[⬇️ APK](https://github.com/hersouls/ION/releases/tag/v0.1.0)**

[화면 설명서](docs/SCREENS.md) · [앱 구조](docs/ARCHITECTURE.md) · [시나리오 아홉 장면](https://prd.moonwave.kr/scenarios) · [심사 기준 대응](https://prd.moonwave.kr/judging) · [만든 사람](#만든-사람)

<img src="docs/screens/app-car.webp" alt="Pleos Connect 에뮬레이터에서 ION이 열린 차 화면 전체: 왼쪽은 계기판, 가운데는 ION 대화창, 오른쪽은 ION 홈" width="100%">

<sub>Pleos Connect 에뮬레이터 2.1.0(2560 × 1440)에서 찍은 실제 화면이에요. 왼쪽 계기판과 아래 공조 막대는 차 시스템이 그리고, ION은 오른쪽 앱 창에 있어요. 기획서의 같은 화면 → <a href="https://prd.moonwave.kr/ion-app#app-screens">ION App 실제 화면</a></sub>

</div>

---

## ION은

전기차는 가족이 함께 보내는 가장 긴 시간에 **충전이라는 새로운 멈춤**을 더했어요. ION은 그 시간을 비워 두지 않아요. ([기획 배경 › 누구의 불편을 어떻게 풀어요](https://prd.moonwave.kr/background#pain-solve))

ION(이온)은 정해진 모양 없이 221개의 작은 조각으로 숨 쉬는 **차 안의 AI Bot**이에요([ION은 누구인가](https://prd.moonwave.kr/#ion)). 차가 알려 주는 배터리 · 기어 · 좌석 · 안전벨트 · 내비 경로를 지켜보다가, 필요한 순간에만 한 장의 카드로 말을 걸어요. 그리고 ION Studio와 마이현대에서 오는 **서비스 여덟 가지**로 차 안의 시간을 채워요. 말로 차를 다루는 Gleo AI와는 시작점이 달라요([Gleo AI와 ION](https://prd.moonwave.kr/#gleo-vs-ion)).

> **이 앱의 약속: 차가 주지 않는 값은 그리지 않아요.**
> 차에서 읽히지 않는 값은 지어내지 않고 "—"나 "아직 몰라요"로 보여 줘요. 차가 아닌 기기에서는 "차와 이어지지 않았어요"라고 정직하게 말해요.

## 세 곳이 함께 움직여요

이 출품작은 저장소 하나가 아니라 **세 곳**이에요. 차 안의 앱, 창작자의 공간, 그리고 둘을 설명하는 기획서예요.

| | 무엇 | 주소 | 여기서 볼 것 |
|---|---|---|---|
| 🚗 **ION App** | 차 안의 앱(이 저장소) | [github.com/hersouls/ION](https://github.com/hersouls/ION) | 소스 · APK · [화면 설명서](docs/SCREENS.md) |
| 🎨 **ION Studio** | 창작자가 올리고, 심사받고, 정산받는 곳(시연 사이트) | [ion-studio.moonwave.kr](https://ion-studio.moonwave.kr) | [스토어](https://ion-studio.moonwave.kr/#/store) · [작가 소개](https://ion-studio.moonwave.kr/#/creators) · [안내서](https://ion-studio.moonwave.kr/#/guide) |
| 📄 **기획서** | 기획 전체(배경 · Brain · Studio · 서비스 · 시나리오 · 사업성) | [prd.moonwave.kr](https://prd.moonwave.kr) | [ION App](https://prd.moonwave.kr/ion-app) · [ION Service](https://prd.moonwave.kr/services) · [ION Studio](https://prd.moonwave.kr/ion-studio) |
| 🌙 **만든 사람** | 제안자 문유 Moonyou와 Moonwave | [www.moonwave.kr](https://www.moonwave.kr) | [만든 사람](#만든-사람) · [이 기획서를 쓴 사람](https://prd.moonwave.kr/#h-about) |

```mermaid
flowchart LR
  C["창작자"] -->|올리기 · 심사 · 정산| S["🎨 ION Studio<br/>ion-studio.moonwave.kr"]
  S -->|"스토어 → (앞으로) 블루링크 스토어"| A["🚗 ION App<br/>이 저장소"]
  S -.->|"시연: API 키로 바로"| A
  M["마이현대"] -->|사진 · 곡 · 일정 · 가족 위치| A
  P["차 · Pleos SDK"] -->|배터리 · 기어 · 좌석 · 경로| A
  D["📄 기획서<br/>prd.moonwave.kr"] -.- S
  D -.- A
```

본래 길은 ION Studio → 블루링크 스토어 → 차예요. 시연에서는 ION Studio가 낸 **API 키로 ION App이 바로** 받아요([잇는 방법](https://prd.moonwave.kr/services#ion-connect), 아래 [ION Studio와 이어 보기](#ion-studio와-이어-보기)).

> 두 사이트가 여섯 자리 **접속 PIN**을 물으면 접수양식에 적은 PIN을 넣어 주세요. ION Studio는 시연용 기획 프로토타입이고, 현대자동차 · 블루링크의 공식 서비스가 아니에요.

## 심사위원을 위한 5분 체험

APK를 설치하고 아래 순서대로 해 보면 ION의 세 가지 모드([세 가지 모드](https://prd.moonwave.kr/features#modes))를 모두 볼 수 있어요. 차 값은 Pleos **Virtual Console**로 바꿔요. 이 저장소의 `scripts/pleos_console.sh`가 터미널에서 대신해 줘요.

```bash
./scripts/pleos_console.sh start               # adb forward + 콘솔 하트비트(켜 둬야 값이 바뀌어요)
alias put='./scripts/pleos_console.sh put'     # 아래 표의 put 명령을 그대로 쓰려고
```

| # | 해 볼 것 | 이렇게 해요 | 보이는 것 | 기획서의 장면 |
|---|---|---|---|---|
| 1 | 앱 열기 | 앱 목록의 **ION** | 인트로 막 → "안녕하세요!" → ION 대화창과 ION 홈 | [A · 처음 이어지는 날](https://prd.moonwave.kr/scenarios#scenario-a) |
| 2 | 홈 둘러보기 | ION 홈을 아래로 스크롤 | 배터리 · 외기온 · 기어, ION이 챙기는 것, 서비스 여덟 가지 | [ION 한눈에](https://prd.moonwave.kr/ion-app#h-glance) |
| 3 | 말 걸기 | 왼쪽 칩 **"충전 몇 번 했어?"** · **"지난 여행 보여 줘"** | 기록을 읽고 답한 뒤 → 화면으로 가는 링크 칩 | [ION과 말하기](https://prd.moonwave.kr/features#ion-talk) |
| 4 | 달리기 | `put "gear?gear=Drive"` | **주행 모드**: 카드 한 장, 단추 두 개 | [주행 중 안전](https://prd.moonwave.kr/highend#driving-safety) |
| 5 | 서기 | `put "gear?gear=Park"` | 도착 요약 → 작업 화면으로 돌아와요 | [I · 1년 전 오늘, 같은 길](https://prd.moonwave.kr/scenarios#scenario-i) |
| 6 | 충전하기 | `put "ev-battery?field=charge_port_open&value=true"` · `put "ev-battery?field=soc&value=62"` | **충전 모드**: 충전 상태와 "충전하는 사이" 슬라이드쇼 | [C · 충전하는 25분](https://prd.moonwave.kr/scenarios#scenario-c) |
| 7 | 테마 입히기 | 서비스 › **디스플레이 테마** › 내려받기 → 적용 | 앱 창 바탕과 홈 카드 14장이 테마 그림을 입어요 | [F · 창작자의 한 달](https://prd.moonwave.kr/scenarios#scenario-f) |
| 8 | 뒷좌석 | `put "seat?kind=occupied&area=rear_left&value=true"` | 가족 · 자리 화면과 ION이 뒷좌석 탑승을 알아요 | [차량 특화 요소](https://prd.moonwave.kr/features#vehicle-only) |
| 9 | ION Studio와 잇기 | [아래 순서](#ion-studio와-이어-보기) | 서비스 목록이 ION Studio에 올라온 것으로 바뀌어요 | [H · 남이 다녀온 길을 따라](https://prd.moonwave.kr/scenarios#scenario-h) |

## 서비스 여덟 가지

위 넷은 **ION Studio**, 아래 넷은 **마이현대**에서 와요. 칸마다 기획서의 설명과 ION Studio의 실제 상품으로 이어져요.

<table>
<tr>
<td width="25%" align="center"><img src="docs/screens/app-svc-song.webp" alt="시그니처 송"><br><b>시그니처 송</b><br><sub>차의 순간(탑승 · 출발 · 도착 · 충전 80%)에 산 목소리 묶음이 말해요</sub><br><sub><a href="https://prd.moonwave.kr/ion-studio#ion-song">기획서</a> · <a href="https://ion-studio.moonwave.kr/#/store">스토어</a> · <a href="https://prd.moonwave.kr/scenarios#scenario-g">장면 G</a></sub></td>
<td width="25%" align="center"><img src="docs/screens/app-svc-theme.webp" alt="디스플레이 테마"><br><b>디스플레이 테마</b><br><sub>앱 창 바탕 · 색 · 홈 카드 14장을 테마 그림으로</sub><br><sub><a href="https://prd.moonwave.kr/services#ion-svc-studio">기획서</a> · <a href="https://ion-studio.moonwave.kr/#/store">스토어</a> · <a href="https://prd.moonwave.kr/scenarios#scenario-f">장면 F</a></sub></td>
<td width="25%" align="center"><img src="docs/screens/app-svc-art.webp" alt="디지털 아트"><br><b>디지털 아트</b><br><sub>작품을 ION 칸과 충전 화면에 걸어요</sub><br><sub><a href="https://prd.moonwave.kr/ion-studio#ion-art">기획서</a> · <a href="https://ion-studio.moonwave.kr/#/creators">작가 소개</a></sub></td>
<td width="25%" align="center"><img src="docs/screens/app-svc-trip.webp" alt="마이 트립"><br><b>마이 트립</b><br><sub>정류장마다 "여기로 안내" → 내비 경로 → 도착하면 다음 정류장</sub><br><sub><a href="https://prd.moonwave.kr/services#ion-trip">기획서</a> · <a href="https://ion-studio.moonwave.kr/#/store?tab=trips">여행 일정</a> · <a href="https://prd.moonwave.kr/scenarios#scenario-h">장면 H</a></sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screens/app-svc-album.webp" alt="디지털 앨범"><br><b>디지털 앨범</b><br><sub>마이현대 사진을 차에서 크게 넘겨 보고 슬라이드쇼로</sub><br><sub><a href="https://prd.moonwave.kr/services#ion-myhyundai">기획서</a> · <a href="https://prd.moonwave.kr/scenarios#scenario-c">장면 C</a></sub></td>
<td align="center"><img src="docs/screens/app-svc-music.webp" alt="디지털 뮤직"><br><b>디지털 뮤직</b><br><sub>지금 어울리는 곡 · 여행과 이어진 곡, 이전 · 다음 · MediaSession</sub><br><sub><a href="https://prd.moonwave.kr/services#ion-myhyundai">기획서</a> · <a href="https://prd.moonwave.kr/scenarios#scenario-i">장면 I</a></sub></td>
<td align="center"><img src="docs/screens/app-svc-cal.webp" alt="캘린더 동기화"><br><b>캘린더 동기화</b><br><sub>다음 약속 · 두 주 띠, 내비 남은 시간으로 늦을지 견줘요</sub><br><sub><a href="https://prd.moonwave.kr/services#ion-myhyundai">기획서</a> · <a href="https://prd.moonwave.kr/scenarios#scenario-d">장면 D</a></sub></td>
<td align="center"><img src="docs/screens/app-svc-loc.webp" alt="위치 동기화"><br><b>위치 동기화</b><br><sub>가족 위치를 별칭으로, 누르면 경로 안내</sub><br><sub><a href="https://prd.moonwave.kr/services#ion-location">기획서</a> · <a href="https://prd.moonwave.kr/scenarios#scenario-e">장면 E</a></sub></td>
</tr>
</table>

서비스가 받기부터 쓰기까지 어떻게 흐르는지는 [서비스 workflow](https://prd.moonwave.kr/services#svc-workflow), 어디까지 확인했는지는 [확인한 것과 확인 전인 것](https://prd.moonwave.kr/services#ion-check)에 있어요. 여행 기록 · 기억은 서비스가 아니라 앱의 기본 기능이에요([기억](https://prd.moonwave.kr/services#ion-road)).

## 세 가지 모드: 차 상태가 화면을 정해요

사용자가 화면을 고르지 않아요. 기어 · 충전구 · 플랫폼의 주행 규제(`CarUxRestrictions`)가 모드를 정해요.

<table>
<tr>
<td width="33%" align="center"><img src="docs/screens/app-home-top.webp" alt="서 있을 때: ION 홈"><br><b>서 있을 때</b><br><sub>ION 대화창 + 작업 화면(황금비 1 : 1.618). 홈 · 충전 · 가족 · 점검 · 여행 기록 · 데이터 · 설정</sub><br><sub><a href="https://prd.moonwave.kr/features#six">기본 기능 여섯 가지</a></sub></td>
<td width="33%" align="center"><img src="docs/screens/app-mode-drive.webp" alt="주행 중: 카드 한 장"><br><b>주행 중</b><br><sub>카드 한 장과 단추 두 개. 입력 없음, 답은 첫 토막만, 화면은 열리지 않아요</sub><br><sub><a href="https://prd.moonwave.kr/highend#driving-safety">주행 중 안전</a></sub></td>
<td width="33%" align="center"><img src="docs/screens/app-charge-show.webp" alt="충전 중: 슬라이드쇼"><br><b>충전 중</b><br><sub>배터리 · 오르는 속도로 어림한 남은 시간 · 충전하는 사이 슬라이드쇼</sub><br><sub><a href="https://prd.moonwave.kr/scenarios#scenario-c">장면 C · 충전하는 25분</a></sub></td>
</tr>
</table>

<details>
<summary><b>화면 더 보기</b> (낮 · 밤, 대화, 도착, 점검, 여행 기록, 설정, 테마 카드 세트)</summary>
<br>

| ION 홈 · 밤 | 서비스 앞 넷 | 서비스 뒤 넷 |
|---|---|---|
| <img src="docs/screens/app-home-night.webp" width="280"> | <img src="docs/screens/app-home-svc1.webp" width="280"> | <img src="docs/screens/app-home-svc2.webp" width="280"> |
| **충전 중 홈** | **충전 화면** | **충전 기록 카드** |
| <img src="docs/screens/app-home-charging.webp" width="280"> | <img src="docs/screens/app-scr-charge.webp" width="280"> | <img src="docs/screens/app-scr-charge-card.webp" width="280"> |
| **점검** | **여행 기록** | **설정** |
| <img src="docs/screens/app-scr-check.webp" width="280"> | <img src="docs/screens/app-scr-trips.webp" width="280"> | <img src="docs/screens/app-scr-settings.webp" width="280"> |
| **말하기** | **도착(밤)** | |
| <img src="docs/screens/app-chat.webp" width="280"> | <img src="docs/screens/app-mode-arrive.webp" width="280"> | |

**테마 카드 세트**: 같은 홈이 테마 세 개를 입은 모습이에요. 이 카드 그림은 ION Studio [스토어](https://ion-studio.moonwave.kr/#/store)의 디스플레이 테마와 같은 것이에요.

<img src="docs/screens/app-theme-sets.webp" alt="테마 세 개를 입은 ION 홈 카드" width="100%">

화면마다 무엇이 어디서 오는지는 **[화면 설명서](docs/SCREENS.md)**, 시안과 실제 화면의 비교는 기획서 [시안과 실제 앱 화면](https://prd.moonwave.kr/ux#mock-real)에 있어요.
</details>

## ION Studio와 이어 보기

APK 안에도 시연 데이터가 있어서 잇지 않아도 모든 서비스를 써 볼 수 있어요. 이으면 **ION Studio에 지금 올라와 있는 것**을 차가 받아요.

1. [ion-studio.moonwave.kr](https://ion-studio.moonwave.kr)에 가입하고 [연동](https://ion-studio.moonwave.kr/#/keys)에서 **새 키 만들기**를 눌러요. 키는 `ion_test_`로 시작하고, 만들 때 한 번만 보여요.
2. ION App의 **설정 › ION Studio 연결**에서 주소(`ion-studio.moonwave.kr`, 기본값)와 API 키를 넣고 **연결하기**를 눌러요.
3. 시그니처 송 · 디스플레이 테마 · 디지털 아트 · 여행 일정 목록이 Studio의 것으로 바뀌어요. 같은 화면의 **새로 받기**로 다시 받아요.

- 차가 보내는 것은 **키뿐**이에요. 차의 값이나 위치는 보내지 않아요.
- 앱은 `GET /api/v1/car/catalog`(`ion-car/1`)로 목록을 받고, 고른 것만 내려받아요. 키를 실은 요청은 PIN 없이 지나가요.
- 잇지 않으면 네트워크를 쓰지 않아요.
- 창작자 쪽 흐름은 ION Studio [안내서](https://ion-studio.moonwave.kr/#/guide)(제안 → 심사 → 계약 → 제작 → 출시 준비 → 출시 → 정산)와 기획서 [창작자가 지나가는 길](https://prd.moonwave.kr/ion-studio#ion-creator)에 있어요.

## Pleos Connect를 이렇게 써요

| 영역 | 쓰는 것 | ION에서 하는 일 |
|---|---|---|
| **Vehicle SDK** 2.3.2 | 배터리 SoC · 주행 가능 거리 · 충전구 · 기어 · 좌석 착좌 · 안전벨트 · 문 · 트렁크 · 후드 · 와이퍼 | 모드 전환, 안전 카드, 점검, 충전 속도 재기, 날씨 신호로 [화면 톤](https://prd.moonwave.kr/features#tone) 고르기 |
| **NaviHelper SDK** 3.0.5 | `requestRoute` · `requestRouteWithWaypoints` · 목적지 · 도착 예상 배터리(`requestTrip`) · 경유지/목적지 도착 | 마이 트립 정류장 안내, 약속 장소 안내, 도착 배터리 답, 도착하면 다음 정류장 |
| **SpeechToText SDK** 3.3.1 | 온디바이스 음성 인식(ko-KR) | 말하기 → 낱말 규칙으로 ION의 일에 연결 |
| **Gleo Interface** | `assets/schema.json` 함수 3개 · `APP_ACTION_RESPONSE`(7초 안 응답) | "길의 기억 보여 줘" · "충전 화면 열어 줘" · "충전소 들를까?" ([Gleo 음성 데이터](https://prd.moonwave.kr/data#gleo-voice)) |
| **AAOS** | `CarUxRestrictions` · 외기온 · 충전구 | 플랫폼이 "제한 필요"라고 하면 기어보다 먼저 주행 모드, 글 길이 제한을 따라요 |

Vehicle SDK의 읽기 API 104개를 에뮬레이터에서 모두 불러 보고, 값이 오는 것만 화면에 썼어요. 앱은 Pleos API 정책대로 값을 읽기 전에 도메인마다 지원 여부(`checkXxxCapabilities`)를 먼저 묻고, 지원한다고 답한 구역만 읽고 구독해요. 전체 표는 기획서 [데이터 사전](https://prd.moonwave.kr/data), SDK별 계획은 [SDK · API 사용 계획](https://prd.moonwave.kr/dev#sdk-plan), 선언한 권한은 [이 앱이 선언할 권한](https://prd.moonwave.kr/data#permissions)에 있어요.

## 앱 구조

```mermaid
flowchart LR
  subgraph Car["차 · Pleos Connect"]
    V[Vehicle SDK]
    N[NaviHelper SDK]
    S[SpeechToText]
    G[Gleo AI]
    U[CarUxRestrictions]
  end
  V & N & U --> CS["CarSource<br/>PleosCarSource / NoCarSource"]
  CS --> E["Engine<br/>세 가지 모드"]
  CS --> B["ION Brain(규칙)<br/>Advisor · Agenda · TripPlan · Memories"]
  S --> VC[VoiceCommands]
  G --> GB[GleoBridge]
  VC & GB --> DC[DriveController]
  E & B --> DC
  DC --> UI["Compose UI<br/>ION 대화창 · 작업 화면 · 주행 카드"]
  B <--> L["ION-Link<br/>ontology.json"]
  B <--> DB[("기기 안 저장<br/>SQLite · prefs")]
  ST["ION Studio API<br/>(선택)"] -.-> B
```

- **차 신호는 한 모양으로**: `CarSource`가 SDK 값을 `CarSnapshot` 하나로 모아요. 차가 아니면 `NoCarSource`가 빈 값을 줘요. 가짜 값은 만들지 않아요.
- **ION Brain은 규칙이에요**: 권하기 · 일정 · 충전 계획 · 기억 · 대화는 모두 순수 함수이고, 권할 때마다 쓴 값으로 **이유**를 붙여요. 같은 종류를 두 번 거절하면 7일 동안 쉬어요. 주행 중에는 권하지 않아요. 하이엔드에서 더할 AI 모델 · 벡터 DB는 기획서 [ION Brain](https://prd.moonwave.kr/brain)에 있어요.
- **ION-Link**: 출처 · 권한 · 읽기 API · 개념 · 기능 30개의 관계(노드 262 · 관계 423)를 APK 안 그래프로 두고, "이 기능엔 뭐가 필요해?" 같은 질문에 답해요([아는 쪽: ION-Link](https://prd.moonwave.kr/brain#brain-ontology)).
- **기기 안에서 끝나요**: 여행 기록은 이 기기의 SQLite에, 들은 말은 이 기기에만(끌 수 있어요) 남아요. 위치를 계속 읽지 않아요([데이터와 사생활](https://prd.moonwave.kr/highend#privacy)).

자세한 패키지 구성과 데이터 흐름은 **[앱 구조](docs/ARCHITECTURE.md)** 에 있어요.

## 품질

| 항목 | 값 |
|---|---|
| 단위 테스트 | **386개**(테스트 클래스 52개): 385개 통과, 1개는 기획 저장소에만 있는 약속 파일을 확인하는 것이라 이 저장소에서는 건너뛰어요 |
| 질문 은행 | 사람 말투 질문 261개를 실제 답변 순서로 돌려 답을 점검해요(`QnaBankTest`) |
| 주행 규제 | `UxRulesTest` · `WorkspaceTest`: 주행 중 화면이 열리지 않고 글 길이 제한을 지키는지 |
| 화면 글 | `NoDemoTextOnScreenTest` · `NoPlanningSpeakOnScreenTest`: 개발자 말투가 화면에 나오지 않게 |
| 코드 | Kotlin 약 14,400줄(소스 102파일) · Jetpack Compose |

```bash
./gradlew testDebugUnitTest
```

화면별로 무엇을 근거로 만들었는지는 기획서 [화면별 구현 근거](https://prd.moonwave.kr/dev#impl-basis)에 있어요.

## 테스트 환경

| 항목 | 내용 |
|---|---|
| **에뮬레이터** | **Pleos Connect Emulator 2.1.0** (Android 14 · API 34 · Android Automotive · arm64-v8a 이미지) |
| **화면 규격** | **2560 × 1440 px · 160 dpi** (가로). 왼쪽 계기판 · 위 상태 막대 · 아래 공조와 앱 메뉴는 시스템이 그리고, ION은 오른쪽 **앱 창(약 1700 × 1210 dp)** 에 그려요 |
| 다른 창 크기 | 1080 × 1920(세로) · 1280 × 720에서도 화면이 맞춰져요 |
| 개발 기기 | Apple Silicon Mac, 에뮬레이터 `-gpu host` |
| 앱 | `app.roadmemory.car` · 버전 0.1.0(1) · minSdk 28 · targetSdk 35 · compileSdk 35 |
| 빌드 | Kotlin 2.0.0 · AGP 8.8.2 · Compose BOM 2024.04.01 · JDK 17 이상(21 확인) |
| Pleos SDK | Vehicle 2.3.2 · NaviHelper 3.0.5 · SpeechToText 3.3.1<br>저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/` (계정 필요 없음) |

### 필요한 설정

- **따로 넣을 키 · 계정 · 서버가 없어요.** 설치하면 바로 열려요. 인터넷이 없어도 모든 화면이 동작해요(ION Studio 연결은 선택이에요).
- 차 값은 Pleos **Virtual Console**로 바꿔요. 터미널에서는:
  ```bash
  ./scripts/pleos_console.sh start                           # adb forward 8088 + 콘솔 하트비트
  ./scripts/pleos_console.sh put "gear?gear=Drive"           # 주행
  ./scripts/pleos_console.sh put "ev-battery?field=soc&value=22"   # 배터리 22%
  ./scripts/pleos_console.sh put "gear?gear=Park"            # 주차
  ./scripts/pleos_console.sh stop
  ```
- 위치 권한(선택): `adb shell pm grant --user 10 app.roadmemory.car android.permission.ACCESS_FINE_LOCATION`
- 권한은 차 값 **읽기**와 내비 경로 요청이에요(`CAR_ENERGY` · `CAR_SEATS` · `CAR_POWERTRAIN` · `CAR_DRIVE` · `CAR_DOORS` · `CAR_SAFETY` · `NAVI_ROUTE` · `NAVI_ROUTE_SEARCH` · `STT_SERVICE` 등 19개, 목록은 `AndroidManifest.xml`). 앱 마켓 승인이 필요한 `PRIVATE_` 권한은 선언하지 않아서, 경고등과 바퀴별 공기압은 읽지 않아요(점검 화면은 서비스센터 기록으로 보여 줘요).
- Pleos 에뮬레이터가 아닌 기기(폰 · 일반 에뮬레이터)에서는 차 값이 비어요.

## 실행 방법

### 1. 받은 APK로 바로 실행

1. **[Releases › v0.1.0](https://github.com/hersouls/ION/releases/tag/v0.1.0)** 에서 `Moonyou_ION_release.apk`를 받아요(서명된 release 빌드, 약 155MB).
2. Pleos Connect 에뮬레이터 2.1.0을 켜고 설치해요.
   ```bash
   adb install -r Moonyou_ION_release.apk
   adb shell am start -n app.roadmemory.car/.MainActivity
   ```
3. 앱 목록의 **ION**을 눌러도 열려요.

> APK가 GitHub 파일 한도(100MB)를 넘어서 `apk/` 폴더 대신 Releases에 올렸어요([`apk/README.md`](apk/README.md)).

### 2. 소스에서 빌드

```bash
git clone https://github.com/hersouls/ION.git && cd ION
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # 또는 ANDROID_HOME
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # 켜 둔 에뮬레이터에 설치
```

release 빌드(`./gradlew assembleRelease`)는 서명 키(`keystore.properties` · `release.keystore`)가 있을 때만 서명돼요. 키는 저장소에 올리지 않아서, 키 없이 빌드하면 서명 없는 APK가 나와요.

## 데이터에 대해 밝혀 둘 것

- **차 값은 진짜예요**: 배터리 · 기어 · 좌석 · 안전벨트 · 충전구 · 문 · 외기온 · 내비 경로는 Pleos SDK와 AAOS에서 읽은 값이에요.
- **서비스 내용은 시연용이에요**: 블루링크 스토어와 마이현대 연동이 아직 없어서, 테마 23 · 작품 17 · 일정 13 · 캘린더 5 · 여행 기록 12건 · 서비스센터 점검 기록은 APK 안에 넣은 **가상 데이터**예요. 창작자와 작품은 지어낸 것이고, 일정의 장소만 실제 장소예요. 가상 사용자 이름은 "문유"예요. 무엇이 차 값이고 무엇이 시연 값인지는 기획서 [접수 앱의 값](https://prd.moonwave.kr/dev#demo-scope)과 ION Studio의 [시연 값과 정해지지 않은 것](https://prd.moonwave.kr/ion-studio#ion-studio-demo)에 표로 있어요.
- **앨범 사진 100장**은 사람 · 글자가 없는 풍경 사진이고 위치 정보(EXIF)를 지웠어요. **곡** 22곡은 제안자가 AI로 만든 노래 16곡과 연주곡 6곡이고, **시그니처 송의 목소리**는 합성 음성이에요(실제 선수 · 연예인의 목소리가 아니에요).

## 저장소 구성

```
ION/
├─ README.md                  ← 지금 이 문서
├─ docs/
│  ├─ SCREENS.md              화면 설명서(화면마다 무엇이 어디서 오는지)
│  ├─ ARCHITECTURE.md         앱 구조 · 패키지 · 데이터 흐름 · 테스트
│  └─ screens/                에뮬레이터에서 찍은 화면 24장
├─ apk/README.md              APK 위치 안내(파일은 Releases)
├─ app/
│  ├─ build.gradle.kts
│  └─ src/
│     ├─ main/java/app/roadmemory/car/   앱 소스(Kotlin · Compose)
│     ├─ main/assets/                    Gleo schema.json · ION-Link · 시연 데이터 · 시그니처 송 팩
│     ├─ debug/                          디버그 빌드에만 있는 진단 도구
│     └─ test/                           단위 테스트
└─ scripts/                   Virtual Console 도우미 · 디버그 설정
```

## 만든 사람

<table>
<tr>
<td width="62%" valign="top">

### 문유 Moonyou

**이 출품작의 제안자이자 개발자예요.** 가족의 여행 · 사진 · 음악 기록을 하나의 이야기로 묶어 직접 써 온 사람이에요. 숫자 앞에서는 냉철하게, 기록 앞에서는 솔직하게 하자는 것이 문유의 태도예요.

ION도 같은 태도로 만들었어요. 차가 주지 않는 값은 그리지 않고, 모르는 것은 모른다고 말하고, 권할 때는 이유를 밝혀요.

</td>
<td width="38%" valign="top">

### Moonwave

**문유의 세계**예요. 문유가 살아가는 방식에 붙인 이름이고, 달처럼 고요하게 존재하고 파동처럼 유연하게 이어지는 것을 뜻해요.

> 파동으로 존재하고,<br>시스템으로 실현하며,<br>본질로 전진하다

**[🌙 www.moonwave.kr](https://www.moonwave.kr)**

</td>
</tr>
</table>

이 저장소와 [기획서](https://prd.moonwave.kr), [ION Studio](https://ion-studio.moonwave.kr)는 공모전을 위해 따로 만든 출품작이에요. 문유와 Moonwave의 더 많은 이야기는 **[www.moonwave.kr](https://www.moonwave.kr)** 에서 볼 수 있어요. 기획서의 소개는 [이 기획서를 쓴 사람](https://prd.moonwave.kr/#h-about)에 있어요.

## 더 보기

### 📄 기획서 · [prd.moonwave.kr](https://prd.moonwave.kr)

이 앱은 기획서의 **"지금 가능한 것"** 을 실제로 동작하게 만든 것이에요.

| 기획 소개 | ION은 이렇게 움직여요 | ION이 하는 일 | 개발 · 데이터 | 마무리 |
|---|---|---|---|---|
| [개요](https://prd.moonwave.kr/) | [ION App](https://prd.moonwave.kr/ion-app) | [ION Service](https://prd.moonwave.kr/services) | [개발 계획](https://prd.moonwave.kr/dev) | [ION Insight](https://prd.moonwave.kr/insight) |
| [기획 배경](https://prd.moonwave.kr/background) | [ION Brain](https://prd.moonwave.kr/brain) | [ION Feature](https://prd.moonwave.kr/features) | [데이터 사전](https://prd.moonwave.kr/data) | [기대효과](https://prd.moonwave.kr/impact) |
| | [ION Studio](https://prd.moonwave.kr/ion-studio) | [ION Future](https://prd.moonwave.kr/highend) | [Lessons Learned](https://prd.moonwave.kr/lessons) | [심사 기준](https://prd.moonwave.kr/judging) |
| | | [시안](https://prd.moonwave.kr/ux) · [시나리오](https://prd.moonwave.kr/scenarios) | | |

**시나리오 아홉 장면**: 앱에서 직접 해 볼 수 있는 장면이에요.

| 장면 | 앱에서 해 보기 |
|---|---|
| [A · 처음 이어지는 날, ION은 차가 주는 값만 알아요](https://prd.moonwave.kr/scenarios#scenario-a) | 앱을 처음 열고 ION 홈 보기 |
| [B · 비 오는 날의 퇴근길, 화면이 운전자를 따라와요](https://prd.moonwave.kr/scenarios#scenario-b) | 콘솔로 와이퍼를 켜고 설정 › 화면 톤 보기 |
| [C · 충전하는 25분, 마이현대의 사진과 곡이 차 안에 와요](https://prd.moonwave.kr/scenarios#scenario-c) | 충전구를 열고 충전하는 사이 슬라이드쇼 |
| [D · 금요일 저녁 가족 식사, 폰의 약속이 내비의 목적지가 돼요](https://prd.moonwave.kr/scenarios#scenario-d) | 캘린더 동기화 › 약속 장소로 안내 |
| [E · 엄마가 있는 곳으로, 가족이 보여 준 위치로 길을 안내해요](https://prd.moonwave.kr/scenarios#scenario-e) | 위치 동기화 › 경로 안내 |
| [F · 창작자의 한 달, 올린 테마가 누군가의 차 화면이 되기까지](https://prd.moonwave.kr/scenarios#scenario-f) | 디스플레이 테마 내려받기 → 적용 |
| [G · 응원하는 선수와 출근길, 차의 순간마다 그 목소리가 나와요](https://prd.moonwave.kr/scenarios#scenario-g) | 시그니처 송 들어 보기 → 적용 → 기어 D |
| [H · 남이 다녀온 길을 따라, 마이 트립의 정류장마다 내비가 이어져요](https://prd.moonwave.kr/scenarios#scenario-h) | 마이 트립 › 여기로 안내 |
| [I · 1년 전 오늘, 같은 길, 쌓인 기록이 먼저 말을 걸어요](https://prd.moonwave.kr/scenarios#scenario-i) | ION 홈의 "ION이 권해요" › 그때 곡 틀기 |

### 🎨 ION Studio · [ion-studio.moonwave.kr](https://ion-studio.moonwave.kr)

ION App이 받는 시그니처 송 · 디스플레이 테마 · 디지털 아트 · 여행 일정을 **창작자가 올리고, 심사받고, 정산받는 곳**이에요(시연 사이트).

| 화면 | 주소 | 기획서의 설명 |
|---|---|---|
| 홈 | [ion-studio.moonwave.kr](https://ion-studio.moonwave.kr) | [ION Studio](https://prd.moonwave.kr/ion-studio) |
| 스토어(출시된 상품 미리보기) | [/#/store](https://ion-studio.moonwave.kr/#/store) | [스토어 · 블루링크 스토어 통합](https://prd.moonwave.kr/ion-studio#ion-store) |
| 여행 일정(마이 트립) | [/#/store?tab=trips](https://ion-studio.moonwave.kr/#/store?tab=trips) | [마이 트립 일정 · 지도와 전기차 정보](https://prd.moonwave.kr/ion-studio#ion-trip-ev) |
| 작가 소개 | [/#/creators](https://ion-studio.moonwave.kr/#/creators) | [작가 소개](https://prd.moonwave.kr/ion-studio#ion-creators) |
| 안내서(제안부터 정산까지 여덟 걸음) | [/#/guide](https://ion-studio.moonwave.kr/#/guide) | [창작자가 지나가는 길](https://prd.moonwave.kr/ion-studio#ion-creator) · [심사](https://prd.moonwave.kr/ion-studio#ion-review) · [수익 정산](https://prd.moonwave.kr/ion-studio#ion-settle) |
| 찾기 | [/#/search](https://ion-studio.moonwave.kr/#/search) | |
| 연동(API 키 · 가입 필요) | [/#/keys](https://ion-studio.moonwave.kr/#/keys) | [연동 · API 키](https://prd.moonwave.kr/ion-studio#ion-api) · [ION Studio와 ION App 잇기](https://prd.moonwave.kr/services#ion-connect) |

---

<div align="center"><sub>ION · 현대자동차그룹 차량용 앱 개발 공모전 2026 출품작 · Team Moonyou<br><a href="https://prd.moonwave.kr">기획서</a> · <a href="https://ion-studio.moonwave.kr">ION Studio</a> · <a href="https://github.com/hersouls/ION/releases/tag/v0.1.0">APK</a> · <a href="https://www.moonwave.kr">www.moonwave.kr</a></sub></div>
