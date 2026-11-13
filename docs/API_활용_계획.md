# 터널프리: 심사 기준 기반 API 활용 계획

> 11월 4일 문서입니다. 2장의 호출 목록은 공식 문서를 보고 적은 계획이고, 5장은 2026-11-10에 SDK 사용 근거 표로 바꿨습니다. 그 뒤로 외부 연동을 공모전 SDK 5종으로 한정했으니, Hyundai Developers API 같은 확장안은 참고만 해 주세요.

작성일 2026-11-04. 출처: 공모전 심사 기준 페이지, Pleos Vehicle SDK v2.3.2 문서(document.pleos.ai), Hyundai Developers(developers.hyundai.com) 규격서.

---

## 0. 결론 먼저

| 소스 | 공모전 점수 반영 | 터널프리에서의 역할 |
|---|---|---|
| Pleos Connect SDK (Vehicle, NaviHelper, FusedLocation, TTS, STT) | 기술 활용성 항목의 유일한 대상 ("Pleos Playground에서 제공하는 SDK·API") | 아래 2장의 메서드를 실제 코드에서 호출해야 하는 대상이다 |
| Hyundai Developers 데이터 API (커넥티드카 클라우드 REST) | 심사 기준에 명시 없음. 가점 근거 아님 | 보조·확장 계획으로만 언급. 차량 소유자 OAuth 동의와 실차가 필요해 에뮬레이터 시연 불가, 데이터는 시동 OFF 시점 갱신이라 실시간 주행 판단에 못 씀 |

공식 문서의 Vehicle 도메인 목록에는 차량 속도와 기어가 없다. 급정거 판정은 Brake(페달) + Odometer(주행거리 변화율로 속도 추정) + 가속도 센서 조합으로 구현한다.

---

## 1. 심사 기준 (원문 요지)

세부 배점 비공개. 1차 서류 4항목 모두 필수 평가.

| 항목 | 평가 내용 | 터널프리가 보여줄 것 |
|---|---|---|
| 차량특화성 | 모바일 대비 차량 환경만의 가치·우위 / 주행·정차 맥락 및 차량 기능과 유기적 결합 | 차량 신호로 급정거·정차를 판단, 공조·조명·창문 제어(2-6)는 계획이고 현재 코드에는 없다 |
| 창의성 | 독창성 / Pain point·Needs 정의와 해결 방식의 명확성 | 공황·폐소공포 운전자의 터널 회피 + 발작 vs 교통상황 판별 |
| 기술 활용성 | Pleos SDK·API를 핵심 기능과 사용 경험에 적절히 활용했는가 | 5개 SDK 전부를 핵심 흐름에 사용 (2장) |
| 사업성 | 타깃, MAU, 리텐션, 확장 계획 | 기획서 담당. 4장에 API 관점 확장안만 제시 |
| 가산점 | 시연 영상, GitHub(소스+APK) | 제출 가이드 참고 |

2차 PT: "실제 동작 가능한 앱" 시연 필수(에뮬레이터 또는 기기). 2차 기준은 추후 공지.

---

## 2. Pleos Connect SDK: 기능별 호출 목록

사용 순서는 모든 SDK 공통: 생성 → `initialize()` → 리스너 등록 → 호출(`onComplete`/`onFailed`) → 해제 → `release()`.
Manifest 권한: 조회 `pleos.car.permission.CAR_*`, 제어 `pleos.car.permission.CONTROL_CAR_*`. 제어 권한은 App Market 사전 승인 범위 내에서만 사용 가능.
Vehicle 도메인 객체는 `Vehicle(context).getXxx()` 로만 얻는다. 각 도메인에는 지원 여부를 묻는 `check…Capabilities` 함수가 있다. 현재 코드는 이 함수를 부르지 않고, 등록이 실패하면 로그를 남기고 그 신호를 뺀다.

### 2-1. 터널 회피 경로: NaviHelper SDK (바로 사용 가능)

| 목적 | 호출 | 비고 |
|---|---|---|
| 목적지 설정 | `naviHelper.requestRoute(RouteInfo(latitude, longitude, displayLatitude, displayLongitude, poiName, poiId, poiSubId, address))` | 콜백 `onRouteStarted`, `onRoutingCompleted`, `onDestinationChanged`, `onError` |
| 터널 회피 유도 | `naviHelper.addWaypoint(RequestWaypointInfo(latitude, longitude, waypointIndex = WaypointIndex.FIRST/SECOND, …))` | RouteOption에 "터널 회피"가 없다(RECOMMENDED/SHORT_DISTANCE/SHORT_TIME/FREE/HIGHWAY/COMFORTABLE/SCHOOL_ZONE). 앱이 터널 우회 경유지(최대 2개)를 계산해 넣는 방식(계획. 현재 코드는 우회 경유지를 계산하지 않고, 사용자가 고른 경유지 하나만 `WaypointIndex.FIRST` 로 넘긴다) |
| 경로 옵션 | (호출하지 않음) | 2026-11-10에 뺐다. `RouteOption.FREE`는 무료 도로 우선이라 터널 회피와 관계가 없다 |
| 재탐색 | (호출하지 않음) | 경로 옵션 호출과 함께 뺐다 |
| 경유지 제거 | `naviHelper.removeWaypoint(WaypointIndex.FIRST)` / `getWaypointInfo()` → `onWaypointInfo(List<WaypointInfo>)` | |
| 경유지 도착 | (처리하지 않음) | `onWayPointArrived()`는 문서에 이름이 있지만 인자 타입을 확인하지 못해 2026-11-10에 뺐다(5장) |
| 취소 | `naviHelper.cancelRoute()` | 안내 종료 |

현재 코드(2026-11-10): `PleosNaviGateway.kt` 에 requestRoute/addWaypoint/removeWaypoint/cancelRoute와 이벤트 4종(onRoutingCompleted, onRouteOptionChanged, onDestinationChanged, onError) 구현. 터널 회피 경로는 앱 내장 도로망으로 계산하고, 이벤트 4종은 로그만 남긴다. NaviHelper는 "차량 내비" 버튼을 누를 때 목적지와 경유지를 넘기는 데 쓴다. 넘긴 뒤 앱에서 경유지를 더하거나 빼면 `addWaypoint`/`removeWaypoint`로 차량 내비에도 반영하고, 안내를 끝내면 `cancelRoute`를 부른다.

### 2-2. 터널 진입·이탈 판정: FusedLocation SDK (문서상 "준비 중")

| 목적 | 호출 |
|---|---|
| 위치 스트림 | `fusedLocation.isSupported()` → `initialize()` → `registerFusedLocationCallback(FusedLocationListener { onChanged(LocationData?), onError(Exception) })` |
| 터널 입구 지오펜스 | `addProximityAlert(...)` / `removeProximityAlert(...)`, 터널 입구 좌표 반경 진입 시 안심 안내 시작, 출구 좌표 진입 시 종료 |
| 1회 조회 | `getLastKnownLocation()` (null 가능) |

`LocationData` = 위도·경도·고도·진행 방향·신뢰도·타임스탬프. 속도 필드 없음. 속도는 Odometer 주행거리 변화로 추정한다(2-3).
권한 `pleos.car.permission.FUSED_LOCATION` (dangerous).
현재 코드(2026-11-10): `PleosLocationSource.kt` 구현됨. 현재 위치 5 km 안의 터널 입구 최대 10곳에 반경 300 m 근접 알림을 `addProximityAlert`로 걸고, 2 km 넘게 움직이면 `removeProximityAlert`로 지운 뒤 다시 건다. 진입 알림이 오면 터널 접근 안내를 한다. 진입·이탈 판정은 위치를 터널 DB와 대조하는 `TunnelMonitor`가 한다.

### 2-3. 급정거 감지 및 공황 vs 교통상황 판별: Vehicle SDK (Vehicle State는 바로 사용 가능)

속도 API가 없으므로 아래 신호를 조합한다.

| 신호 | 도메인 / 메서드 | 권한 | 판정 역할 |
|---|---|---|---|
| 브레이크 페달 | `Brake`: `checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus`, `registerBrakePedalStatus(BrakePedalStatusListener)` / `unregister…` | CAR_DRIVE | 페달 ON + 급감속 → 운전자가 의도한 제동(교통상황)일 가능성이 높다 |
| 주행거리 변화율 → 속도 추정 | `Odometer`: `getTripCurrentDrivingDistance`, `registerTripCurrentDrivingDistance`, `getTripCurrentDrivingTime`, `registerTripCurrentDrivingTime` | CAR_DRIVE | Δ거리/Δ시간으로 속도·감속도 계산. 현재 코드는 `registerDistanceDriven`을 쓴다. 가속도 센서와 함께 쓴다 |
| 조향 입력 | `SteeringWheel`: `getCurrentSteeringWheelAngle`, `registerSteeringWheelAngle(SteeringWheelAngleListener)` | (문서 권한 표에 행이 없음. 에뮬레이터에서는 추가 권한 없이 등록됨) | 급정거 전후 조향 변화와 응답이 없으면 공황 가능성이 높다 |
| 방향지시등 | `TurnSignal`: `getCurrentTurnSignalState`, `registerTurnSignalState(TurnSignalStateListener)` | CAR_LIGHTS | 차선 변경·정차 의도 파악. 비상등(hazard) API는 없음 |
| 안전벨트 | `Seat`: `getCurrentSeatBeltBuckledState`, `registerSeatBeltBuckledState` / `Safety`: `hasSeatBeltNotBuckledWarning`, `registerSeatBeltNotBuckledWarning` | CAR_SEATS / CAR_SAFETY | 벨트 해제 = 하차 시도 → 도움 화면 즉시 |
| 경고등·에어백 | `Safety`: `getTelltalesInfo`, `registerTelltales(TelltaleInfoListener)`, `hasSeatAirbagBeenDeployed`, `registerSeatAirbagDeployed` | CAR_SAFETY | 에어백 전개 = 사고 → 공황 질문 생략, 바로 긴급 흐름 |
| 크루즈 상태 | `DrivingMode`: `getCruiseControlState`, `registerCruiseControlState`, `getCruiseControlTargetSpeed` | CAR_DRIVE | 크루즈 해제 직후 급감속은 교통상황 가능성 |

판정 규칙(초안):
1. 감속도 ≥ 4.5 m/s² (Odometer 추정 또는 센서) → 이벤트 발생
2. 에어백 전개 → 즉시 긴급(질문 생략)
3. 브레이크 ON 또는 방향지시등 ON 또는 조향 변화 → "교통상황 가능성" 라벨, 음성 질문 1회
4. 브레이크 OFF + 조향 변화 없음 → "상태 확인 필요" 라벨, 음성 질문 + 무응답 12초 → 도움 화면
5. 안전벨트 해제 → 도움 화면
현재 코드(2026-11-10): `PleosVehicleSignals.kt` 에 Brake, Odometer, SteeringWheel, TurnSignal, Seat(벨트), Safety(에어백) 리스너 6개 구현. 속도와 감속도는 `domain/OdometerSpeed.kt` 가 주행거리로 추정한다. Odometer는 `registerDistanceDriven`을 쓴다. 앞 값과 0.5초가 안 지난 값은 다음 값과 합치고, 5초 동안 새 값이 없으면 속도를 0으로 본다. 감속도 4.5 m/s² 이상이 두 번 연속 나오거나, 2초 이상 벌어진 구간에서 한 번 나오면 급정거로 본다. 가속도 센서 경로(`SensorVehicleSignals.kt`: 3회 연속, 알린 뒤 15초 쉼)도 같이 돈다. 위 판정 규칙 1~5는 초안이다. 현재 코드는 무응답 12초 뒤 안전 정차 안내를 먼저 하고, 20초 더 응답이 없거나 정차가 확인되면 도움 화면을 연다. 벨트 해제는 시속 5 km를 넘게 달릴 때만 도움 화면을 연다.

### 2-4. 안심 오디오 가이드: TextToSpeech SDK ("준비 중")

`TextToSpeech(context, Mode.ON_DEVICE)` → `initialize()` → `addEventListener(...)` → `setLocale(...)` → `speak(text)` / `speak(text, isFinal=false)` 분할 → `stop()` → `release()`. ON_DEVICE는 앱 등록·네트워크 불필요.
Toll 도메인의 `setEtcsVoiceGuidanceVolume` 으로 하이패스 안내 음량을 낮춰 안심 안내가 묻히지 않게 하는 것도 가능(CONTROL_CAR_TOLL 필요).
현재 코드(2026-11-10): `PleosSpeechOutput.kt` 구현. 한 문장이 끝난 것은 `EventListener`의 완료 콜백으로 받는다.

### 2-5. 음성 제어: SpeechToText SDK ("준비 중")

`SpeechToText(context, Mode.ON_DEVICE)` → `initialize()` → `addListener(ResultListener)` → `setLocale()` → `request()` → `stop()` → `removeListener()` → `release()`. 인식 텍스트만 반환하므로 `CommandParser` 로 명령 매핑.
현재 코드(2026-11-10): `PleosSpeechInput.kt` 구현. 인식 결과는 `ResultListener`로 받는다.

### 2-6. 차량특화성 강화: "안심 모드" 차량 제어 (CONTROL 권한, 승인 필요)

차량 제어 API로 공조·조명·창문을 바꾸는 기능이라 모바일 앱으로는 할 수 없다. 터널에 들어가거나 불안이 감지되면 아래 제어를 한 번에 실행하는 계획이다. 제어 권한 이름은 공식 문서의 `CONTROL_CAR_*` 규칙에서 짐작한 것이니 API Reference에서 확인한다.

| 제어 | 메서드 | 권한 | 근거 |
|---|---|---|---|
| 외기 순환·풍량 | `HVAC`: `setHvacRecirculation(off)`, `setHvacFanSpeed`, `setHvacTemperature`, `setHvacAc` | CONTROL_CAR_CLIMATE | 답답함·호흡 곤란 완화 |
| 앰비언트 라이트 | `Light`: `setAmbientLightBrightnessColor`, `setAmbientLightBrightness`, `setAmbientLightSwitch` | CONTROL_CAR_LIGHTS | 터널 내 어두움 보상, 진정 색상 |
| 실내등 | `Light`: `setCabinLightSwitch` | CONTROL_CAR_LIGHTS | 폐소감 완화 |
| 창문 소폭 개방 | `Window`: `setWindowPosition`, `getCurrentWindowPosition` | CONTROL_CAR_WINDOWS | 정차 시에만. 주행 중 안전 고려 |
| 스마트글라스 투명도 | `Window`: `setSmartGlassState`, `setSmartGlassLevel`, `getSmartGlassConfig` | CONTROL_CAR_WINDOWS | 지원 차종 한정 |
| 시트 통풍 | `Seat`: `setHvacSeatVentilation` | CONTROL_CAR_SEATS | 발한 완화 |
| 디스플레이 온도 단위 등 | `Display` | CAR_INFO | 해당 없음 |

제어 API는 "Vehicle Control SDK 준비 중"이므로 2차 PT에서는 에뮬레이터 Virtual Console로 상태 변화를 보여주는 수준이 현실적. 기획서에는 "승인 후 적용" 으로 기재.

### 2-7. 기획서·PT용 한 줄 매핑

| 앱 기능 | 사용 SDK·메서드 |
|---|---|
| 터널 회피 경로 | 앱 내장 도로망으로 계산. NaviHelper `requestRoute`·`addWaypoint`·`removeWaypoint`·`cancelRoute`는 "차량 내비" 버튼으로 넘긴 뒤 목적지와 경유지를 차량 내비와 맞추는 데만 사용 |
| 터널 진입/이탈 안내 | 위치(FusedLocation `registerFusedLocationCallback`)와 터널 DB 대조(`TunnelMonitor`). 입구 접근은 `addProximityAlert`로도 받음. 안내는 TTS `speak` |
| 음성 제어 | STT `request` → CommandParser → NaviHelper/앱 상태 |
| 급정거 판별 | Vehicle Brake `registerBrakePedalStatus` + Odometer `registerDistanceDriven` + SteeringWheel `registerSteeringWheelAngle` + TurnSignal `registerTurnSignalState` + Safety `registerSeatAirbagDeployed` + 가속도 센서 |
| 하차 시도 감지 | Seat `registerSeatBeltBuckledState` (운전석, 시속 5 km 초과일 때만) |
| 안심 모드 | HVAC/Light/Window/Seat `set*` |
| 긴급 전화 | Android `ACTION_DIAL` (Pleos 전용 API 없음) |

---

## 3. Hyundai Developers 데이터 API (참고용)

- 성격: 커넥티드카(CCS) 클라우드 REST API. 차량 소유자가 현대 통합계정으로 로그인해 차량 접근 권한과 개인정보 제3자 제공에 동의해야 함. 데이터는 시동 OFF 시점 기준 갱신 → 실시간 주행 판단 불가.
- 인증: OAuth2 Authorization Code. `GET https://prd.kr-ccapi.hyundai.com/api/v1/user/oauth2/authorize?client_id&redirect_uri&response_type=code&state` → `POST …/oauth2/token` (Basic client_id:secret, grant_type=authorization_code|refresh_token|delete) → `GET …/user/profile`.
- 동의: `…/car-service/terms/agreement` (Bearer token, state) → redirect에 userId.
- 데이터 API (dev.kr-ccapi.hyundai.com/api/v1, Bearer):
  - `GET /car/profile/carlist` → cars[carId, carNickname, carType(GN/EV/HEV/PHEV/FCEV), carName, carSellname]
  - `GET /car/profile/:carId/contract` 가입일/무료 종료일
  - `GET /car/status/:carId/dte` 주행 가능 거리, `/odometer` 누적 거리(value, unit 0 feet/1 km/2 m/3 miles, timestamp)
  - `GET /car/status/:carId/evCharging`, `/evBattery`
  - `GET /car/status/warning/:carId/lowFuel`, tire pressure, lamp wire, smart key battery, washer fluid, brake oil, engine oil
  - 파트너 콜백: 데이터 조회 불가 상태 알림
- 소개 페이지의 "운행정보 API(경로/시간/속도)", "운전습관 API(안전운전점수)"는 규격서에 없고 "일부 API 개발 중" 표기.
- 에러 코드 예: 4045 No data(장기 미운행), 4046 차량 미보유, 5005 미동의, 5031 원격제어 불가.

터널프리 적용 가능성: 보호자용 웹/알림 서비스(사업성 확장안)에서 차량 상태·누적 거리·경고등을 조회하는 용도 정도. 공모전 핵심 기능과 점수에는 기여하지 않으므로 구현 우선순위 최하.

---

## 4. 구현 우선순위 (2차 PT 12/3 기준)

진행 현황(2026-11-11 코드 기준): 1번 완료(TurnSignal 포함 리스너 6개). 2번은 NaviHelper로 목적지와 사용자가 고른 경유지 하나를 넘기는 데까지다. 터널 우회 경유지는 계산하지 않고, 앱 경로는 내장 도로망으로 계산한다. 경로 옵션 호출은 2026-11-10에 뺐다. 3번 완료(OSM 터널 자료 137구간, 같은 터널 조각을 이어 135개 + TunnelMonitor + 입구 반경 300 m 근접 알림 + 통과 시연). 4번은 Pleos 어댑터와 Android 대체 구현까지 했다. 5번 안심 모드 제어는 코드에 없다.

1. Vehicle SDK 리스너 완성, Brake, Odometer(속도 추정), SteeringWheel, Seat belt, Safety airbag. 에뮬레이터 Virtual Console로 신호 주입해 급정거 판별 시연. (기술 활용성·차량특화성 직결, 신청 없이 사용 가능)
2. NaviHelper 실경로, requestRoute + 터널 우회 경유지 addWaypoint. (처음 계획에 있던 changeRouteOption(FREE)는 2026-11-10에 뺐다. 2-1 참고)
3. FusedLocation ProximityAlert, 터널 입구/출구 좌표 10곳 정도 DB화(대전 시내 실제 터널) → 진입 안내.
4. TTS/STT Pleos 어댑터 리스너 완성 (준비 중이면 Android 폴백 유지, 코드상 호출 경로는 보여줄 것).
5. 안심 모드 제어(HVAC/Light), 권한 승인 전제. 코드와 UI는 넣고 에뮬레이터에서 상태값 변화로 시연.
6. Hyundai Developers API, 기획서 확장 계획 문단만.

## 5. SDK 사용 근거 (2026-11-10 정리)

### 있었던 일

이 장에는 원래 SDK 파일을 풀어서 만든 함수 표가 있었습니다. 2026년 11월 4일, 5일, 9일에 SDK AAR을 풀고 javap으로 함수 형태와 권한 이름을 봤고(비공개 멤버와 바이트코드 출력 포함), 9일에는 에뮬레이터의 음성 서비스 APK를 PC로 복사해 파일 목록과 문자열을 봤습니다. 9일 작업은 SDK 라이선스에 동의한 뒤였습니다. SDK 라이선스 3.4조는 디컴파일과 역공학을 금지합니다.

11월 10일에 풀어 둔 파일과 그 표를 지웠습니다. pleos 어댑터 코드는 처음에 그렇게 알게 된 내용으로 짠 것입니다. 그래서 같은 날, 앱이 쓰는 SDK 호출을 하나씩 다른 경로로 다시 확인했고 확인되지 않는 것은 뺐습니다. 아래 표가 그 결과입니다. 다시 확인한 것과 별개로, 처음에 SDK 파일을 풀어 본 일은 그대로 남아 있습니다.

### 확인에 쓴 것

- 공식 문서 기록: document.pleos.ai의 SDK 가이드와 API Reference를 11월 3~4일에 받아 둔 글입니다. 당시 자동 도구로 받았습니다. 11월 9일에도 자동 도구로 문서 사이트 주소 하나를 열었는데 없는 페이지(Page Not Found)여서 받은 내용은 없습니다. Pleos Playground 이용약관이 자동화 프로그램 접속을 금지한다는 것은 11월 10일에 알았고, 그 뒤로는 자동으로 접속하지 않습니다. 새로 볼 때는 사람이 브라우저로 직접 엽니다. Vehicle API Reference는 목차와 함수 이름만 받아졌습니다. 함수 상세 페이지는 1건(`getCurrentBrakePedalStatus`)만 받아 봤는데 본문이 비어 있었고, 나머지 함수의 상세 페이지는 받아 보지 않았습니다.
- 컴파일러 진단: SDK 파일을 열지 않고, 임시 소스 파일을 평소처럼 컴파일해서 Kotlin 컴파일러가 내는 오류 문구를 읽었습니다. 인터페이스를 구현하지 않으면 빠진 콜백 이름을, 인자를 잘못 주면 기대하는 타입을, 열거형을 when으로 받으면 빠진 상수 이름을 알려 줍니다. 기본 구현이 있는 콜백은 이 방법으로 드러나지 않으므로, 아래 표의 콜백 개수는 반드시 구현해야 하는 콜백의 수입니다. 문서 기록에 이름이 있는 클래스와 함수에서 시작했고, 그다음 이름은 앞선 오류 문구에 나온 것만 썼습니다. 예외가 둘 있습니다. Vehicle의 getter 5개(`getOdometer`, `getSteeringWheel`, `getTurnSignal`, `getSeat`, `getSafety`)와 `FusedLocation`·`NaviHelper` 클래스의 패키지 경로는 문서 기록에 글자 그대로는 없습니다. 문서 기록에는 `getXxx()` 규칙과 하위 패키지 이름(`…location.fused.model`, `…navi.helper.listener`)만 있고, 이 이름들은 첫 컴파일에 넣어 컴파일러가 받아들이는 것으로 확인했습니다. 18번 컴파일했고, 오류 문구 원문은 저장소 밖 개발 노트북의 `D:\hmg_contest\SDK_확인_기록`에 있습니다.
- 실행 로그: Pleos Connect Emulator v2.1.0에서 앱을 돌렸을 때 logcat에 찍힌 내용입니다.

### SDK별 근거

| SDK | 문서 기록에 이름이 있는 것 | 컴파일러 진단으로 확인한 것 | 실행 로그로 확인한 것 |
|---|---|---|---|
| Vehicle | `Vehicle(context)`, `initialize()`, `release()`, `getBrake()`, 도메인 6개(Brake, Odometer, SteeringWheel, TurnSignal, Seat, Safety)와 그 `register…`·`unregister…` 함수 이름, 리스너 6개 이름, 권한 CAR_DRIVE·CAR_LIGHTS·CAR_SAFETY·CAR_SEATS | 나머지 getter 5개, register·unregister의 인자, 리스너마다 콜백 2개(값이 바뀔 때와 실패했을 때)와 인자 타입, 좌석을 가리키는 객체의 생성자와 좌석 상수, 방향지시등 상수 | 6개 모두 등록 호출이 예외 없이 끝났고 앱의 실패 콜백 로그는 없음. Vehicle 서비스 로그에서는 5개가 등록됐고(result = true), 에어백(SEAT_AIRBAGS_DEPLOYED)은 에뮬레이터에서 등록되지 않음(result = false) |
| FusedLocation | `isSupported()`, `initialize()`, `registerFusedLocationCallback`·`unregisterFusedLocationCallback`, `FusedLocationListener`의 `onChanged`·`onError`(예제 코드), `release()`, `addProximityAlert`·`removeProximityAlert` 이름, 권한 FUSED_LOCATION | 생성자, `addProximityAlert`·`removeProximityAlert`의 인자, 근접 알림 요청 객체의 인자, 근접 알림 리스너의 콜백 3개, 이벤트가 담는 값과 진입·이탈 상수, 위치 객체의 위도·경도 속성 | 초기화 로그 |
| NaviHelper | `initialize`, `addListener`·`removeListener`, `release`(소개 페이지의 사용 순서 설명), `requestRoute(RouteInfo(…))`, `addWaypoint(RequestWaypointInfo(…))`, `removeWaypoint(WaypointIndex.FIRST)`, `cancelRoute`(예제 코드), 이벤트 `onRoutingCompleted`·`onRouteOptionChanged`·`onDestinationChanged`·`onError` | 생성자, 이벤트 인자 타입 | 권한 NAVI_ROUTE_SEARCH(권한 없이 호출하면 SDK가 오류 문구에 이 이름을 적어 줌), 차량 내비 화면으로 넘어가는 것 |
| TextToSpeech | `TextToSpeech(context, Mode)`와 ON_DEVICE, `initialize`, `addEventListener`·`removeEventListener`, `setLocale`, `speak(text, isFinal)`, `stop`, `release`, `EventListener`의 `onStop` | `initialize`에 넘기는 리스너와 그 콜백 2개, `EventListener`의 콜백 6개와 인자 타입 | 권한 TTS_SERVICE(오류 문구), `Locale.KOREA`(ko_KR)를 받는 것, 문장 시작과 끝 콜백이 오는 순서 |
| SpeechToText | `SpeechToText(context, Mode)`와 ON_DEVICE, `initialize`, `addListener`·`removeListener`, `setLocale`, `request`, `stop`, `release`, `ResultListener`의 `onEndedRecognition` | `initialize`에 넘기는 리스너와 그 콜백 2개, `ResultListener`의 콜백 7개와 인자 타입 | 권한 STT_SERVICE(오류 문구), `request` 뒤 인식 준비 알림(ReadyToStartRecognition)이 오는 것. 11월 10일 회귀 로그에는 인식한 글자가 실린 콜백이 없음(마이크 입력이 없었음). 합성 음성을 넣은 별도 시험에서는 4개 문구가 명령으로 처리됨 |

### 문서로도 컴파일러로도 알 수 없어 가정한 것

- 운전석이 1열 왼쪽 좌석이라는 것.
- Odometer 주행거리 값의 단위. 앱은 km로 가정합니다. 에뮬레이터에서는 값이 0.0으로만 왔습니다.
- SteeringWheel에 필요한 권한. 문서 권한 표에 행이 없습니다. 에뮬레이터에서는 Vehicle 권한 4개만 선언한 상태로 등록됐습니다.
- 좌석을 지정해 등록한 벨트 콜백에 그 좌석의 값만 온다는 것. 콜백 인자가 `Boolean?` 하나라 어느 좌석 값인지 알 수 없습니다. 에뮬레이터 로그에서는 벨트 이벤트가 좌석 영역 5개로 앱 프로세스의 SDK 계층까지 왔습니다.
- 에어백 신호가 실제 차량에서 등록된다는 것. 에뮬레이터에서는 등록되지 않아 콜백을 한 번도 받지 못했습니다.

### 2026-11-10에 빼거나 고친 것

- 기어 신호. 공식 문서의 Vehicle 도메인 목록 19개에 없어서 관련 코드를 모두 지웠습니다. 차가 섰는지는 주행거리로 추정한 속도로 봅니다(`domain/OdometerSpeed.kt`).
- 내비 이벤트 2종의 로그. `onReRouteEnded`는 문서 기록에 이름이 없습니다. `onWayPointArrived`는 문서 기록에 이름이 있지만 인자 타입을 문서 기록으로도 컴파일러 진단으로도 확인하지 못했습니다.
- 터널 회피 설정을 차량 내비의 경로 옵션으로 넘기던 호출. 문서상 그 옵션은 무료 도로 우선이라 터널 회피와 관계가 없습니다.

### 사람이 공식 문서에서 확인해 줄 것

- 컴파일러 진단으로만 확인한 항목(리스너 콜백, `initialize`에 넘기는 리스너, 근접 알림 객체, 좌석과 방향지시등 상수)이 API Reference의 해당 페이지와 같은지.
- 각 SDK의 권한 선언 페이지에 적힌 권한 이름.
- 위에 가정이라고 적은 다섯 가지.
- `FusedLocation`·`NaviHelper` 클래스의 패키지 경로와 Vehicle getter 5개 이름.

GitHub에 올라가 있는 예전 커밋에는 지운 표와 javap을 썼다는 기록이 그대로 있습니다. 이 정리를 올려도 지난 커밋에는 남습니다.
