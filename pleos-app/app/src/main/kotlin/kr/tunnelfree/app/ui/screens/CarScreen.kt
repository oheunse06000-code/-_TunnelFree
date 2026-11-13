package kr.tunnelfree.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kr.tunnelfree.app.domain.DemoData
import kr.tunnelfree.app.domain.IncidentKind
import kr.tunnelfree.app.domain.MusicState
import kr.tunnelfree.app.domain.TurnType
import kr.tunnelfree.app.domain.formatDistance
import kr.tunnelfree.app.ui.IncidentState
import kr.tunnelfree.app.ui.TunnelFreeViewModel
import kr.tunnelfree.app.ui.UiState
import kr.tunnelfree.app.ui.NavScreen
import kr.tunnelfree.app.ui.theme.TfTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CarLayout(
    state: UiState,
    viewModel: TunnelFreeViewModel,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
) {
    val c = TfTheme.colors
    HideSystemBars()
    val mainMap = remember { MapHandle() }
    Box(Modifier.fillMaxSize().background(c.Background)) {
        Row(Modifier.fillMaxSize()) {
            ControlStrip(
                state, viewModel, onOpenSettings,
                onZoomIn = { mainMap.zoomIn() },
                onZoomOut = { mainMap.zoomOut() },
                onFit = { mainMap.fitRoute(state.route) },
            )
            Column(Modifier.weight(1.25f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    RoadMap(state, Modifier.fillMaxSize(), handle = mainMap, onLongPress = { viewModel.selectPoint(it.lat, it.lng) })
                    ClockPill(Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                    if (state.running) ManeuverCard(state, Modifier.align(Alignment.TopStart).padding(12.dp))
                    else RouteTooltip(state, Modifier.align(Alignment.TopEnd).padding(12.dp))
                    if (!state.roadsReady) {
                        Text(
                            "도로망 불러오는 중…",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                BottomInfoBar(state, viewModel, onOpenSettings)
            }
            RightPanel(state, viewModel, Modifier.weight(0.85f).fillMaxHeight())
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp))
    }
}

@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ControlStrip(
    state: UiState,
    viewModel: TunnelFreeViewModel,
    onOpenSettings: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
) {
    val c = TfTheme.colors
    Column(
        Modifier.width(76.dp).fillMaxHeight().background(c.Surface).padding(vertical = 10.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StripButton(Icons.Filled.Home, "메뉴", onClick = { viewModel.openNav(NavScreen.MENU) })
        StripButton(Icons.Filled.Explore, "전체", onClick = onFit)
        StripButton(
            if (state.musicState == MusicState.OFF) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
            "음악",
            active = state.musicState != MusicState.OFF,
            onClick = viewModel::toggleMusic,
        )
        StripButton(
            if (state.listening) Icons.Filled.MicOff else Icons.Filled.Mic,
            "음성",
            active = state.listening || state.navScreen == NavScreen.VOICE,
            onClick = { if (state.emergencyActive) viewModel.toggleListening() else viewModel.openNav(NavScreen.VOICE) },
        )
        StripButton(Icons.Filled.Add, "확대", onClick = onZoomIn)
        StripButton(Icons.Filled.Remove, "축소", onClick = onZoomOut)
        Spacer(Modifier.weight(1f))
        StripButton(Icons.Filled.Settings, "설정", onClick = onOpenSettings)
    }
}

@Composable
private fun StripButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val c = TfTheme.colors
    Column(
        Modifier
            .size(width = 60.dp, height = 58.dp)
            .background(if (active) c.Primary.copy(alpha = 0.25f) else c.SurfaceHigh, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(label, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ClockPill(modifier: Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000)
        }
    }
    Box(
        modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Text(now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ManeuverCard(state: UiState, modifier: Modifier) {
    val m = state.nextManeuver ?: return
    Row(
        modifier.background(Color(0xE6101418), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(turnIcon(m.type), contentDescription = m.label, tint = Color.White, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(formatDistance(state.nextManeuverDistanceM), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                m.label + (if (m.roadName.isNotEmpty()) " · ${m.roadName}" else ""),
                color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, maxLines = 1,
            )
        }
    }
}

internal fun turnIcon(type: TurnType): ImageVector = when (type) {
    TurnType.LEFT -> Icons.Filled.TurnLeft
    TurnType.RIGHT -> Icons.Filled.TurnRight
    TurnType.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    TurnType.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    TurnType.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    TurnType.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    TurnType.U_TURN -> Icons.Filled.UTurnLeft
    TurnType.ARRIVE -> Icons.Filled.Flag
    TurnType.DEPART, TurnType.STRAIGHT -> Icons.Filled.Straight
}

@Composable
private fun RouteTooltip(state: UiState, modifier: Modifier) {
    val route = state.route ?: return
    val extra = state.avoidanceExtraMinutes
    val lines = buildList {
        add("터널 ${route.tunnelCount}개 · ${formatDistance(route.lengthM)}")
        when {
            extra == null -> add("약 ${route.minutes}분")
            extra <= 0 -> add("약 ${route.minutes}분 · 시간 비슷")
            else -> add("약 ${route.minutes}분 · 회피로 +${extra}분")
        }
    }
    Box(modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(lines.joinToString("\n"), color = Color(0xFF14191E), fontSize = 16.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun BottomInfoBar(state: UiState, viewModel: TunnelFreeViewModel, onOpenSettings: () -> Unit) {
    val eta = remember(state.remainingMinutes) {
        LocalTime.now().plusMinutes(state.remainingMinutes.toLong()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1C2127)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton("메뉴", onClick = { viewModel.openNav(NavScreen.MENU) })
        when {
            state.running -> BarButton("경로\n취소", onClick = viewModel::endDrive)
            state.arrived -> BarButton("새\n목적지", onClick = viewModel::endDrive)
            else -> BarButton("안내\n시작", onClick = viewModel::startDrive)
        }
        if (state.destination != null) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Text("🏁 ${formatDistance(state.remainingDistance)}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (state.arrived) "도착" else "도착 $eta", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
            Text(
                "🏁 ${state.destinationName}" + (state.viaName?.let { " · 경유 $it" } ?: ""),
                color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f), maxLines = 1,
            )
        } else {
            Text(
                "목적지를 찾으세요. 오른쪽 메뉴, 지도 길게 누르기, 음성 모두 됩니다",
                color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp).weight(1f),
            )
        }
    }
}

@Composable
private fun BarButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 84.dp, height = 64.dp).background(Color(0xFF2A2F36)).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)) {
            Text(label, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun RightPanel(state: UiState, viewModel: TunnelFreeViewModel, modifier: Modifier) {
    val c = TfTheme.colors
    Column(modifier.background(c.Surface)) {
        when {
            state.emergencyActive || state.incident !is IncidentState.None -> EmergencyPanel(state, viewModel)
            state.navScreen == NavScreen.VOICE -> VoiceCommandPanel(state, viewModel)
            state.situation != null -> SituationPanel(state, viewModel)
            state.navScreen == NavScreen.PARKING -> ParkingPanel(state, viewModel)
            state.arrived -> ArrivalPanel(state, viewModel)
            state.running -> DriveStatusPanel(state, viewModel)
            state.navScreen == NavScreen.OPTIONS -> RouteOptionsPanel(state, viewModel)
            state.destination != null && state.navScreen == NavScreen.MENU -> ComparisonPanel(state, viewModel)
            state.navScreen == NavScreen.SEARCH -> SearchPanel(state, viewModel)
            state.navScreen == NavScreen.NEARBY -> NearbyPanel(state, viewModel)
            state.navScreen == NavScreen.EMERGENCY -> EmergencyPoiPanel(state, viewModel)
            state.navScreen == NavScreen.RECENT -> RecentPanel(state, viewModel)
            state.navScreen == NavScreen.STATUS -> DestinationChooser(state, viewModel)
            else -> NavMenuPanel(state, viewModel)
        }
    }
}

@Composable
private fun DestinationChooser(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("목적지", style = MaterialTheme.typography.titleLarge)
        for (index in DemoData.destinationIndices) {
            val place = DemoData.places[index]
            OutlinedButton(onClick = { viewModel.selectDestination(index) }, modifier = Modifier.fillMaxWidth(), enabled = state.roadsReady) { Text(place.name) }
        }
        Text(
            if (state.roadsReady) "지도를 길게 누르면 그 지점을 목적지로 정합니다 · 실제 도로망(OpenStreetMap) 기준"
            else "도로망을 불러오는 중입니다…",
            color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.fillMaxWidth().toggleable(value = state.avoidTunnel, role = Role.Switch, onValueChange = viewModel::setAvoidTunnel),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("터널 회피", style = MaterialTheme.typography.titleLarge)
                Text(state.avoidanceLevel.label, color = c.TextSecondary)
            }
            Switch(checked = state.avoidTunnel, onCheckedChange = null)
        }
        RealTunnelStatus(state, viewModel)
        MusicRow(state, viewModel)
        if (state.heard.isNotEmpty()) Text("들은 말: ${state.heard}", color = c.TextSecondary)
    }
}

@Composable
private fun DriveStatusPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.journeyStatus, style = MaterialTheme.typography.titleLarge)
        Text(
            "남은 ${formatDistance(state.remainingDistance)} · 약 ${state.remainingMinutes}분 · 남은 터널 ${state.tunnelCount}개" +
                (if (state.routeTunnelCount > 0) " · ${state.routeTunnelSummary}" else ""),
            color = c.TextSecondary, maxLines = 2,
        )
        state.nextManeuver?.let { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(turnIcon(m.type), contentDescription = null, tint = c.Primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${formatDistance(state.nextManeuverDistanceM)} 후 ${m.label}" + (if (m.roadName.isNotEmpty()) " · ${m.roadName}" else ""),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1,
                )
            }
        }

        if (state.breathing) Banner(state.breathText, "코로 들이쉬고 입으로 천천히 내쉬세요", c.Calm)

        val v = state.vehicle
        Text(
            buildString {
                append(if (v.connected) "차량 신호 연결됨" else "차량 신호 없음 (센서 추정)")
                state.effectiveSpeedKmh?.let { append(" · ${it.toInt()} km/h") }
                v.brakePressed?.let { append(if (it) " · 브레이크 ON" else " · 브레이크 OFF") }
                if (v.turnSignal != "OFF") append(" · 지시등 ${v.turnSignal}")
                v.seatBeltBuckled?.let { append(if (it) " · 벨트 착용" else " · 벨트 해제") }
            },
            color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        TunnelAheadCard(state, viewModel)
        RealTunnelStatus(state, viewModel)
        MusicRow(state, viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = viewModel::togglePause, modifier = Modifier.weight(1f)) { Text(if (state.paused) "다시 출발" else "일시정지") }
            FilledTonalButton(onClick = viewModel::toggleBreathing, modifier = Modifier.weight(1f)) { Text(if (state.breathing) "호흡 중지" else "호흡 안내") }
            Button(
                onClick = viewModel::openHelp,
                colors = ButtonDefaults.buttonColors(containerColor = c.Danger, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) { Text("도와줘") }
        }
        HelpPersonButtons(viewModel)
        OutlinedButton(onClick = { viewModel.openNav(NavScreen.PARKING) }, modifier = Modifier.fillMaxWidth()) { Text("목적지 주변 주차장") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::simulateHardStop, modifier = Modifier.weight(1f)) { Text("급정거 시연") }
            OutlinedButton(onClick = viewModel::simulateBrakedHardStop, modifier = Modifier.weight(1f)) { Text("브레이크 급정거 시연") }
        }
        if (state.heard.isNotEmpty()) Text("들은 말: ${state.heard}", color = c.TextSecondary)
    }
}

@Composable
private fun ArrivalPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    val route = state.route
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Banner(
            "목적지 도착 · ${state.destinationName.orEmpty()}",
            if (route != null) "${formatDistance(route.lengthM)} 주행 · 터널 ${route.tunnelCount}개 통과" +
                (if (route.tunnelCount > 0) " (${state.routeTunnelSummary})" else " · 터널 없이 도착했습니다") else "",
            c.Calm,
        )
        if (state.challengeMode) {
            Text("도전 모드 · 오늘 통과 ${state.challengeCountToday}개 · 최장 ${state.challengeLongestM} m", color = c.Calm)
        }
        TunnelRatingCard(state, viewModel)
        OutlinedButton(onClick = { viewModel.openNav(NavScreen.PARKING) }, modifier = Modifier.fillMaxWidth()) { Text("주변 주차장") }
        Button(onClick = viewModel::endDrive, modifier = Modifier.fillMaxWidth()) { Text("새 목적지") }
        MusicRow(state, viewModel)
    }
}

@Composable
private fun EmergencyPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (val incident = state.incident) {
            is IncidentState.Checking -> {
                val traffic = incident.kind == IncidentKind.TRAFFIC_LIKELY
                Banner(
                    if (traffic) "급정거 감지 · 교통 상황으로 보입니다" else "급정거 감지 · 괜찮으세요?",
                    "${incident.cause}\n응답이 없으면 안전 정차를 안내합니다.",
                    if (traffic) c.Warning else c.Danger,
                )
                IncidentButtons(viewModel)
            }
            is IncidentState.SafeStop -> {
                Banner(
                    "안전 정차 안내",
                    incident.steps.joinToString("\n") { "• $it" } + "\n앱은 제동·조향을 하지 않습니다. 정차가 확인되면 도움 화면을 엽니다.",
                    c.Danger,
                )
                IncidentButtons(viewModel)
            }
            IncidentState.None -> {
                Banner("도움 요청", "도움 화면이 열려 있습니다. 전화 앱 연결만 하며 자동 신고는 하지 않습니다.", c.Danger)
            }
        }
        Text(
            "안심 음악은 응급 흐름 동안 정지됩니다. \"괜찮아요\" 또는 \"도와줘\"라고 말해도 됩니다.",
            color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        if (state.heard.isNotEmpty()) Text("들은 말: ${state.heard}", color = c.TextSecondary)
    }
}
