package kr.tunnelfree.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.tunnelfree.app.domain.AvoidanceLevel
import kr.tunnelfree.app.domain.DayCondition
import kr.tunnelfree.app.domain.HelpAction
import kr.tunnelfree.app.domain.HelpPerson
import kr.tunnelfree.app.domain.PoiCategory
import kr.tunnelfree.app.domain.RoadRoute
import kr.tunnelfree.app.domain.RouteVerdict
import kr.tunnelfree.app.domain.SearchHit
import kr.tunnelfree.app.domain.TripUrgency
import kr.tunnelfree.app.domain.TunnelExposure
import kr.tunnelfree.app.domain.VoiceShortcuts
import kr.tunnelfree.app.domain.formatDistance
import kr.tunnelfree.app.ui.NavScreen
import kr.tunnelfree.app.ui.TunnelFreeViewModel
import kr.tunnelfree.app.ui.UiState
import kr.tunnelfree.app.ui.theme.TfTheme

@Composable
internal fun NavMenuPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HelpPersonButtons(viewModel)
        Text("내비게이션 메뉴", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavTile(Icons.Filled.Search, "검색", Modifier.weight(1f)) { viewModel.openNav(NavScreen.SEARCH) }
            NavTile(Icons.Filled.Place, "주변 시설", Modifier.weight(1f)) { viewModel.openNav(NavScreen.NEARBY) }
            NavTile(Icons.Filled.History, "이전 목적지", Modifier.weight(1f)) { viewModel.openNav(NavScreen.RECENT) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavTile(Icons.Filled.LocalParking, "주차장", Modifier.weight(1f)) { viewModel.openNav(NavScreen.PARKING) }
            NavTile(Icons.Filled.LocalHospital, "응급 시설", Modifier.weight(1f)) {
                viewModel.showNearby(null)
                viewModel.openNav(NavScreen.EMERGENCY)
            }
            NavTile(Icons.Filled.Tune, "경로 옵션", Modifier.weight(1f)) { viewModel.openNav(NavScreen.OPTIONS) }
        }
        Text(
            if (state.roadsReady) "지도를 길게 누르면 그 지점을 목적지로 정합니다" else "도로망을 불러오는 중입니다…",
            color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { viewModel.openNav(NavScreen.STATUS) }, modifier = Modifier.fillMaxWidth()) { Text("시연 목적지·터널 상태") }
    }
}

@Composable
internal fun VoiceCommandPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelHeader("음성 명령", viewModel)
        Text("누르면 말로 명령한 것과 똑같이 동작합니다", color = c.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        for (group in VoiceShortcuts.groups) {
            Text(group.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            for (row in group.phrases.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (phrase in row) {
                        OutlinedButton(onClick = { viewModel.runCommandButton(phrase) }, modifier = Modifier.weight(1f)) {
                            Text(phrase, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        OutlinedButton(onClick = viewModel::toggleListening, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(if (state.listening) "듣기 중지" else "마이크로 말하기")
        }
    }
}

@Composable
private fun NavTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = TfTheme.colors
    Column(
        modifier.height(116.dp).background(c.SurfaceHigh, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.Primary, modifier = Modifier.height(36.dp))
        Text(label, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PanelHeader(title: String, viewModel: TunnelFreeViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.openNav(NavScreen.MENU) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HitRow(hit: SearchHit, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { viewModel.selectDestination(hit.place) }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(hit.place.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hit.categoryLabel, color = c.TextSecondary, fontSize = 13.sp)
        }
        Text(formatDistance(hit.distanceM), color = c.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
internal fun SearchPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        PanelHeader("검색", viewModel)
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::search,
            label = { Text("장소 이름") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (state.searchQuery.isNotBlank() && state.searchResults.isEmpty()) {
                Text("대전 권역 시설 목록에 없습니다. 지도를 길게 눌러 고를 수 있습니다.", color = c.TextSecondary, modifier = Modifier.padding(10.dp))
            }
            state.searchResults.forEach { HitRow(it, viewModel) }
        }
    }
}

@Composable
internal fun NearbyPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        PanelHeader("주변 시설", viewModel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (category in listOf(PoiCategory.REST_AREA, PoiCategory.FUEL, PoiCategory.RESTAURANT, PoiCategory.HOSPITAL)) {
                FilterChip(selected = state.nearbyCategory == category, onClick = { viewModel.showNearby(category) }, label = { Text(category.label) })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (state.nearbyCategory != null) state.searchResults.forEach { HitRow(it, viewModel) }
        }
    }
}

@Composable
internal fun EmergencyPoiPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        PanelHeader("응급 시설", viewModel)
        Text("위급하면 직접 운전하지 말고 119에 연락하세요. 응급실 수용 여부는 전화로 확인하세요.", color = c.Danger, modifier = Modifier.padding(6.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            state.searchResults.forEach { HitRow(it, viewModel) }
        }
    }
}

@Composable
internal fun RecentPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        PanelHeader("이전 목적지", viewModel)
        if (state.recentPlaces.isEmpty()) Text("아직 안내한 목적지가 없습니다.", color = c.TextSecondary, modifier = Modifier.padding(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            state.recentPlaces.forEach { place ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        place.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable { viewModel.selectDestination(place) }.padding(10.dp),
                    )
                    OutlinedButton(onClick = { viewModel.removeRecent(place) }) { Text("삭제") }
                }
            }
        }
    }
}

@Composable
internal fun RouteOptionsPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelHeader("경로 옵션", viewModel)
        OptionLabel("터널 회피")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (level in AvoidanceLevel.entries) {
                FilterChip(selected = state.avoidanceLevel == level, onClick = { viewModel.setAvoidanceLevel(level) }, label = { Text(level.label, fontSize = 12.sp) })
            }
        }
        OptionLabel("오늘 상태")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (value in DayCondition.entries) {
                FilterChip(selected = state.dayCondition == value, onClick = { viewModel.setDayCondition(value) }, label = { Text(value.label) })
            }
        }
        OptionLabel("시간 조건")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (value in TripUrgency.entries) {
                FilterChip(selected = state.urgency == value, onClick = { viewModel.setUrgency(value) }, label = { Text(value.label) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("동승자 있음", modifier = Modifier.weight(1f))
            Switch(checked = state.hasPassenger, onCheckedChange = viewModel::setPassenger)
        }
        Text(
            "오늘 터널 한도 ${limitText(state.tunnelLimitM)} · 통과 평가 ${state.ratingCount}건. 한도는 임상 기준이 아니라 개인 설정값입니다.",
            color = c.TextSecondary, fontSize = 13.sp,
        )
    }
}

@Composable
private fun OptionLabel(text: String) = Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

private fun limitText(limitM: Double): String = when {
    limitM >= 1e9 -> "제한 없음"
    limitM <= 0.0 -> "모든 터널 피함"
    else -> formatDistance(limitM)
}

@Composable
internal fun ComparisonPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    val route = state.route
    val alt = state.comparison
    val advice = state.routeAdvice
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RoadMap(state, Modifier.fillMaxSize(), comparisonOnly = true, interactive = false)
        }
        Column(Modifier.fillMaxWidth().background(c.Background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.routing || route == null || advice == null) {
                Text("경로 계산 중…", fontSize = 16.sp)
                return@Column
            }
            val tone = when (advice.verdict) {
                RouteVerdict.MUST_AVOID -> c.Danger
                RouteVerdict.MUST_PASS -> c.Warning
                else -> c.Calm
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    advice.verdict.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(tone, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(advice.headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(advice.reasons.joinToString(" "), color = c.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteCard(route, selected = true, Modifier.weight(1f)) {}
                if (alt != null) RouteCard(alt, selected = false, Modifier.weight(1f), onClick = viewModel::selectAlternativeRoute)
            }
            state.restBeforeTunnel?.let { rest ->
                OutlinedButton(onClick = viewModel::addRestBeforeTunnel, modifier = Modifier.fillMaxWidth()) {
                    Text("터널 앞 ${rest.name} 들러 가기", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::startDrive, modifier = Modifier.weight(1.4f)) { Text("안내 시작") }
                OutlinedButton(onClick = { viewModel.openNav(NavScreen.PARKING) }, modifier = Modifier.weight(1f)) { Text("주차장") }
                OutlinedButton(onClick = { viewModel.openNav(NavScreen.OPTIONS) }, modifier = Modifier.weight(1f)) { Text("경로 옵션") }
                if (state.naviAvailable) {
                    OutlinedButton(onClick = viewModel::sendToCarNavi, modifier = Modifier.weight(1f)) { Text("차량 내비", maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(route: RoadRoute, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = TfTheme.colors
    val exposure = TunnelExposure.of(route)
    Column(
        modifier
            .background(c.SurfaceHigh, RoundedCornerShape(8.dp))
            .border(2.dp, if (selected) c.Primary else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(if (exposure.hasTunnel) "터널 ${exposure.count}개" else "터널 없음", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (exposure.hasTunnel) c.Warning else c.Calm)
        Text("약 ${route.minutes}분 · ${formatDistance(route.lengthM)}", fontSize = 14.sp)
        Text(
            if (exposure.hasTunnel) "최장 ${formatDistance(exposure.longestM)} · 약 ${exposure.longestSec}초" else "터널 구간 없음",
            color = c.TextSecondary, fontSize = 12.sp, maxLines = 1,
        )
    }
}

@Composable
internal fun ParkingPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    val p = state.parking
    val best = p.candidates.firstOrNull { it.lot.id == p.advice?.lotId }
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelHeader("${p.destinationName} 주변 주차장", viewModel)
        if (best != null && p.advice != null) {
            Row(
                Modifier.fillMaxWidth().background(c.SurfaceHigh, RoundedCornerShape(8.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text("추천 · ${best.lot.kind.label}", color = c.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(best.lot.displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.advice.reason, color = c.TextSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = { viewModel.goToParking(best.lot) }) { Text("안내") }
            }
        } else {
            Text("반경 800 m 안에서 찾은 주차장이 없습니다.", color = c.TextSecondary)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            p.candidates.filter { it !== best }.forEach { candidate ->
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.goToParking(candidate.lot) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(candidate.lot.displayName, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(candidate.lot.kind.label, color = if (candidate.lot.kind.enclosed) c.Warning else c.TextSecondary, fontSize = 13.sp)
                    }
                    Text(formatDistance(candidate.distanceM), color = c.TextSecondary, fontSize = 14.sp)
                }
            }
        }
        Text(p.note, color = c.TextSecondary, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
internal fun TunnelAheadCard(state: UiState, viewModel: TunnelFreeViewModel) {
    val ahead = state.tunnelAhead ?: return
    val c = TfTheme.colors
    Column(
        Modifier.fillMaxWidth().background(c.SurfaceHigh, RoundedCornerShape(8.dp)).border(2.dp, c.Warning, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(ahead.title, color = c.Warning, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(ahead.message, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ahead.detour != null) {
                Button(onClick = viewModel::acceptTunnelDetour, modifier = Modifier.weight(1f)) { Text("우회하기") }
                OutlinedButton(onClick = viewModel::dismissTunnelAhead, modifier = Modifier.weight(1f)) { Text("그대로 가기") }
            } else {
                OutlinedButton(onClick = viewModel::dismissTunnelAhead, modifier = Modifier.fillMaxWidth()) { Text("확인") }
            }
        }
    }
}

@Composable
internal fun TunnelRatingCard(state: UiState, viewModel: TunnelFreeViewModel) {
    val length = state.pendingRatingM ?: return
    val c = TfTheme.colors
    Column(Modifier.fillMaxWidth().background(c.SurfaceHigh, RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${formatDistance(length)} 터널은 어땠나요?", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { viewModel.rateTunnel(2) }, modifier = Modifier.weight(1f)) { Text("괜찮음") }
            OutlinedButton(onClick = { viewModel.rateTunnel(5) }, modifier = Modifier.weight(1f)) { Text("긴장됨") }
            OutlinedButton(onClick = { viewModel.rateTunnel(8) }, modifier = Modifier.weight(1f)) { Text("힘들었음") }
            OutlinedButton(onClick = viewModel::skipTunnelRating, modifier = Modifier.weight(1f)) { Text("건너뛰기") }
        }
    }
}

@Composable
internal fun HelpPersonButtons(viewModel: TunnelFreeViewModel) {
    val c = TfTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("지금 도움이 필요한가요?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        HelpPerson.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { person ->
                    Column(
                        Modifier.weight(1f).height(64.dp)
                            .background(c.SurfaceHigh, RoundedCornerShape(8.dp))
                            .clickable { viewModel.openSituation(person) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(person.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(person.hint, color = c.TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SituationPanel(state: UiState, viewModel: TunnelFreeViewModel) {
    val plan = state.situation ?: return
    val c = TfTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(plan.person.label, color = c.TextSecondary, fontSize = 14.sp)
        Text(plan.title, style = MaterialTheme.typography.titleLarge)
        plan.steps.forEach { Text("• $it", fontSize = 16.sp) }
        plan.actions.forEachIndexed { index, action ->
            val urgent = action == HelpAction.CALL_119
            Button(
                onClick = { viewModel.runHelpAction(action) },
                modifier = Modifier.fillMaxWidth().height(if (index == 0) 60.dp else 50.dp),
                colors = if (urgent) ButtonDefaults.buttonColors(containerColor = c.Danger, contentColor = Color.White) else ButtonDefaults.buttonColors(),
            ) { Text(action.label, fontSize = 17.sp) }
        }
        OutlinedButton(onClick = viewModel::closeSituation, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
    }
}
