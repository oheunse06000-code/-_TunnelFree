package kr.tunnelfree.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kr.tunnelfree.app.domain.AvoidanceLevel
import kr.tunnelfree.app.domain.formatDistance
import kr.tunnelfree.app.domain.DemoData
import kr.tunnelfree.app.domain.IncidentKind
import kr.tunnelfree.app.domain.MusicState
import kr.tunnelfree.app.ui.IncidentState
import kr.tunnelfree.app.ui.TunnelFreeViewModel
import kr.tunnelfree.app.ui.UiState
import kr.tunnelfree.app.ui.theme.TfTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelFreeScreen(viewModel: TunnelFreeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }
    val c = TfTheme.colors
    val config = LocalConfiguration.current
    val isCar = config.screenWidthDp >= 1000 && config.screenWidthDp > config.screenHeightDp

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    if (isCar) {
        CarLayout(state, viewModel, snackbar, onOpenSettings = { settingsOpen = true })
    } else Scaffold(
        containerColor = c.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("터널프리", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (state.platformName == "pleos") "Pleos Connect" else "체험 모드",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.TextSecondary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.Background),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth > 820.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    RoadMap(state, Modifier.weight(1.4f).fillMaxHeight(), onLongPress = { viewModel.selectPoint(it.lat, it.lng) })
                    Panel(state, viewModel, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    RoadMap(state, Modifier.weight(1f).fillMaxWidth(), onLongPress = { viewModel.selectPoint(it.lat, it.lng) })
                    Panel(state, viewModel, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    if (state.helpOpen) HelpDialog(state, viewModel)
    if (settingsOpen) SettingsSheet(state, viewModel, onDismiss = { settingsOpen = false })
}

@Composable
internal fun Panel(state: UiState, viewModel: TunnelFreeViewModel, modifier: Modifier) {
    val c = TfTheme.colors
    Column(
        modifier
            .background(c.Surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.running) DrivePanel(state, viewModel) else HomePanel(state, viewModel)

        RealTunnelStatus(state, viewModel)
        MusicRow(state, viewModel)

        if (state.heard.isNotEmpty()) {
            Text("들은 말: ${state.heard}", color = c.TextSecondary)
        }
    }
}

@Composable
internal fun HomePanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Text("터널을 피하는 길찾기", style = MaterialTheme.typography.headlineMedium)
    Text(
        "실제 도로망(OpenStreetMap) 기준 터널 회피 경로 · 지도를 길게 눌러 목적지 지정",
        color = c.TextSecondary,
    )

    Row(
        Modifier.fillMaxWidth().toggleable(value = state.avoidTunnel, role = Role.Switch, onValueChange = viewModel::setAvoidTunnel),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("터널 회피", style = MaterialTheme.typography.titleLarge)
            Text(
                "${state.avoidanceLevel.label} · 설정에서 강도 변경",
                color = c.TextSecondary,
            )
        }
        Switch(checked = state.avoidTunnel, onCheckedChange = null)
    }

    Text("목적지", style = MaterialTheme.typography.titleLarge)
    for (index in DemoData.destinationIndices) {
        val place = DemoData.places[index]
        val selected = state.destination?.name == place.name
        if (selected) {
            Button(onClick = { viewModel.selectDestination(index) }, modifier = Modifier.fillMaxWidth()) {
                Text(place.name)
            }
        } else {
            OutlinedButton(onClick = { viewModel.selectDestination(index) }, modifier = Modifier.fillMaxWidth()) {
                Text(place.name)
            }
        }
    }

    if (state.destination != null) {
        val count = state.tunnelCount
        Text(
            if (count == 0) "터널 없는 경로 · 약 ${state.remainingMinutes}분"
            else "경로에 터널 ${count}개 · 약 ${state.remainingMinutes}분",
            color = if (count == 0) c.Calm else c.Warning,
            style = MaterialTheme.typography.titleLarge,
        )
        state.viaName?.let { Text("경유: $it", color = c.TextSecondary) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MicButton(state, viewModel, Modifier.weight(1f))
        Button(
            onClick = viewModel::startDrive,
            enabled = state.destination != null,
            modifier = Modifier.weight(1.4f).heightIn(min = 56.dp),
        ) { Text("안내 시작") }
    }

    Text(
        "음성 예시: \"대전역으로 가 줘\" · \"짧은 터널은 괜찮아\" · \"음악 틀어 줘\" · \"도와줘\"",
        color = c.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun DrivePanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Text(state.journeyStatus, style = MaterialTheme.typography.titleLarge)
    state.destinationName?.let { Text("목적지: $it", color = c.TextSecondary) }

    Text(
        "남은 ${formatDistance(state.remainingDistance)} · 약 ${state.remainingMinutes}분 · 남은 터널 ${state.tunnelCount}개",
        style = MaterialTheme.typography.bodyLarge,
    )

    state.nextManeuver?.let { m ->
        Text(
            "${formatDistance(state.nextManeuverDistanceM)} 후 ${m.label}" + (if (m.roadName.isNotEmpty()) " · ${m.roadName}" else ""),
            style = MaterialTheme.typography.titleLarge,
        )
    }

    when (val incident = state.incident) {
        is IncidentState.Checking -> {
            val traffic = incident.kind == IncidentKind.TRAFFIC_LIKELY
            Banner(
                title = if (traffic) "급정거 감지 · 교통 상황으로 보입니다" else "급정거 감지 · 괜찮으세요?",
                body = "${incident.cause}\n응답이 없으면 안전 정차를 안내합니다.",
                color = if (traffic) c.Warning else c.Danger,
            )
            IncidentButtons(viewModel)
        }
        is IncidentState.SafeStop -> {
            Banner(
                title = "안전 정차 안내",
                body = incident.steps.joinToString("\n") { "• $it" } + "\n앱은 제동·조향을 하지 않습니다. 정차가 확인되면 도움 화면을 엽니다.",
                color = c.Danger,
            )
            IncidentButtons(viewModel)
        }
        IncidentState.None -> {}
    }

    if (state.breathing) {
        Banner(title = state.breathText, body = "코로 들이쉬고 입으로 천천히 내쉬세요", color = c.Calm)
    }

    val v = state.vehicle
    Text(
        buildString {
            append(if (v.connected) "차량 신호 연결됨" else "차량 신호 없음 (센서 추정)")
            state.effectiveSpeedKmh?.let { append(" · ${it.toInt()} km/h") }
            v.brakePressed?.let { append(if (it) " · 브레이크 ON" else " · 브레이크 OFF") }
            if (v.turnSignal != "OFF") append(" · 방향지시등 ${v.turnSignal}")
            v.seatBeltBuckled?.let { append(if (it) " · 벨트 착용" else " · 벨트 해제") }
        },
        color = c.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = viewModel::togglePause, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Text(if (state.paused) "다시 출발" else "일시정지")
        }
        FilledTonalButton(onClick = viewModel::toggleBreathing, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Text(if (state.breathing) "호흡 중지" else "호흡 안내")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MicButton(state, viewModel, Modifier.weight(1f))
        Button(
            onClick = viewModel::openHelp,
            colors = ButtonDefaults.buttonColors(containerColor = c.Danger, contentColor = Color.White),
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) { Text("도와줘") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::endDrive, modifier = Modifier.weight(1f)) { Text("안내 종료") }
        OutlinedButton(onClick = viewModel::simulateHardStop, modifier = Modifier.weight(1f)) { Text("급정거 시연") }
    }
    OutlinedButton(onClick = viewModel::simulateBrakedHardStop, modifier = Modifier.fillMaxWidth()) {
        Text("브레이크 급정거 시연 (교통 상황 판정)")
    }
}

@Composable
internal fun IncidentButtons(viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(onClick = viewModel::dismissIncident, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
            Text("괜찮아요")
        }
        Button(
            onClick = viewModel::openHelp,
            colors = ButtonDefaults.buttonColors(containerColor = c.Danger, contentColor = Color.White),
            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
        ) { Text("도움 요청") }
    }
}

@Composable
internal fun RealTunnelStatus(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    val ts = state.tunnelStatus
    val inside = ts.inside
    if (inside != null) {
        Banner(
            title = "${inside.name} 통과 중 · ${inside.lengthM} m",
            body = "실제 터널 데이터(OpenStreetMap) 기준입니다. 곧 끝납니다. 천천히 호흡하세요.",
            color = c.Warning,
        )
    } else if (ts.nearest != null && ts.nearestDistanceM != null && ts.nearestDistanceM < 20_000) {
        val d = ts.nearestDistanceM
        Text(
            if (ts.approaching) "전방 ${d} m · ${ts.nearest.name} (${ts.nearest.lengthM} m)"
            else "가까운 실제 터널: ${ts.nearest.name} · ${if (d >= 1000) String.format(Locale.ROOT, "%.1f km", d / 1000.0) else "$d m"}",
            color = if (ts.approaching) c.Warning else c.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (state.challengeMode) {
        Text(
            "도전 모드 · 오늘 통과 ${state.challengeCountToday}개 · 최장 ${state.challengeLongestM} m",
            color = c.Calm,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (!state.running) OutlinedButton(
        onClick = viewModel::simulateTunnelPass,
        enabled = !state.tunnelDemoRunning,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (state.tunnelDemoRunning) "실제 터널 통과 시연 중…" else "실제 터널 통과 시연 (OSM 데이터)") }
}

@Composable
internal fun MusicRow(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    val label = when (state.musicState) {
        MusicState.PLAYING -> "안심 음악 재생 중"
        MusicState.DUCKED -> "안심 음악 재생 중 (음성 안내로 볼륨 낮춤)"
        MusicState.OFF -> if (state.emergencyActive) "안심 음악 정지 (응급 흐름 우선)" else "안심 음악 꺼짐"
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (state.musicState == MusicState.OFF) c.TextSecondary else c.Calm,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        FilledTonalButton(onClick = viewModel::toggleMusic, enabled = !state.emergencyActive) {
            Icon(
                if (state.musicState == MusicState.OFF) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                contentDescription = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(if (state.musicState == MusicState.OFF) "음악 켜기" else "음악 끄기")
        }
    }
}

@Composable
internal fun MicButton(state: UiState, viewModel: TunnelFreeViewModel, modifier: Modifier) {
    FilledTonalButton(onClick = viewModel::toggleListening, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(
            if (state.listening) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(if (state.listening) "듣는 중" else "음성")
    }
}

@Composable
internal fun Banner(title: String, body: String, color: Color) {
    val c = TfTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(title, color = color, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(body, color = c.TextPrimary)
    }
}

@Composable
internal fun HelpDialog(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    AlertDialog(
        onDismissRequest = { },
        title = { Text("도움이 필요하신가요?") },
        text = {
            Text(
                "현재 위치는 GPS로 확인됩니다.\n\n" +
                    "아래 버튼은 전화 앱을 여는 기능입니다. 자동 신고나 문자 발송은 하지 않습니다." +
                    (if (state.guardianPhone.isEmpty()) "\n\n보호자 연락처는 설정에서 입력할 수 있습니다." else "")
            )
        },
        confirmButton = {
            Button(
                onClick = viewModel::call119,
                colors = ButtonDefaults.buttonColors(containerColor = c.Danger, contentColor = Color.White),
            ) { Text("119 전화 앱 열기") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::closeHelp) { Text("닫기") }
                OutlinedButton(onClick = viewModel::callGuardian, enabled = state.guardianPhone.isNotEmpty()) {
                    Text("보호자")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(state: UiState, viewModel: TunnelFreeViewModel, onDismiss: () -> Unit) {
    val c = TfTheme.colors
    var phone by remember { mutableStateOf(state.guardianPhone) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("설정", style = MaterialTheme.typography.headlineMedium)

            Text("터널 회피 강도", style = MaterialTheme.typography.titleLarge)
            Text("터널을 길이에 따라 피할지 지나갈지 정합니다. 운전을 돕는 기능이며 치료를 대신하지 않습니다.", color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            for (level in AvoidanceLevel.entries) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = state.avoidanceLevel == level, role = Role.RadioButton, onClick = { viewModel.setAvoidanceLevel(level) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.avoidanceLevel == level, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(level.label)
                        Text(level.description, color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SettingSwitch(
                title = "도전 모드 (선택적 노출)",
                body = "통과한 터널 수와 최장 길이를 기록합니다. 전문가 치료를 대체하지 않습니다.",
                checked = state.challengeMode,
                onChange = viewModel::setChallengeMode,
            )
            SettingSwitch(
                title = "터널 진입 시 안심 음악 자동 재생",
                body = "느린 템포의 음악을 호흡 주기(분당 6회)에 맞춰 재생합니다. 응급 상황과 음성 안내가 우선합니다.",
                checked = state.autoMusic,
                onChange = viewModel::setAutoMusic,
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("보호자 연락처") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "플랫폼: ${state.platformName}" +
                    (if (state.naviAvailable) " · 기본 내비 연동" else " · 내비 미연동(체험 경로)"),
                color = c.TextSecondary,
            )
            Text(
                "경로·터널·주행 위치는 체험 데이터입니다. 실시간 길찾기·교통 데이터는 아직 연결하지 않았습니다. 실제 터널 위치는 OpenStreetMap 자료입니다.",
                color = c.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = {
                    viewModel.saveGuardianPhone(phone)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = TfTheme.colors
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
