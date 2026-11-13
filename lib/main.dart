import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:speech_to_text/speech_to_text.dart';
import 'package:url_launcher/url_launcher.dart';

import 'kakao_config.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const TunnelFreeApp());
}

class TunnelFreeApp extends StatelessWidget {
  const TunnelFreeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF16785F)),
        scaffoldBackgroundColor: const Color(0xFFF6F8F7),
      ),
      home: const TunnelFreePage(),
    );
  }
}

class DemoPlace {
  final String name;
  final double lat;
  final double lng;

  const DemoPlace(this.name, this.lat, this.lng);

  List<double> get point => [lat, lng];
}

class DemoRoad {
  final int from;
  final int to;
  final bool tunnel;

  const DemoRoad(this.from, this.to, {this.tunnel = false});

  bool connects(int a, int b) =>
      (from == a && to == b) || (from == b && to == a);
}

const places = <DemoPlace>[
  DemoPlace('건양대학교 대전캠퍼스 · 출발 기준', 36.3061, 127.3446),
  DemoPlace('가수원 방면 · 체험 지점', 36.3100, 127.3600),
  DemoPlace('서쪽 우회 · 체험 지점', 36.3390, 127.3490),
  DemoPlace('둔산 방면 · 체험 지점', 36.3480, 127.3820),
  DemoPlace('대전시청', 36.3504, 127.3845),
  DemoPlace('대전역', 36.3320, 127.4342),
  DemoPlace('한밭수목원', 36.3690, 127.3880),
  DemoPlace('유성온천역', 36.3540, 127.3415),
  DemoPlace('화장실 · 체험 시설', 36.3410, 127.3510),
  DemoPlace('휴게시설 · 체험 시설', 36.3530, 127.3780),
];

const roads = <DemoRoad>[
  DemoRoad(0, 1),
  DemoRoad(0, 2),
  DemoRoad(1, 3, tunnel: true),
  DemoRoad(2, 3),
  DemoRoad(2, 7),
  DemoRoad(3, 4),
  DemoRoad(3, 5),
  DemoRoad(4, 6),
  DemoRoad(7, 6),
  DemoRoad(2, 8),
  DemoRoad(3, 9),
];

double distanceBetween(DemoPlace a, DemoPlace b) {
  const rad = math.pi / 180;
  final lat = (b.lat - a.lat) * rad;
  final lng = (b.lng - a.lng) * rad;

  final value =
      math.sin(lat / 2) * math.sin(lat / 2) +
      math.cos(a.lat * rad) *
          math.cos(b.lat * rad) *
          math.sin(lng / 2) *
          math.sin(lng / 2);

  return 6371000 *
      2 *
      math.atan2(
        math.sqrt(value.clamp(0.0, 1.0)),
        math.sqrt((1 - value).clamp(0.0, 1.0)),
      );
}

class TunnelFreePage extends StatefulWidget {
  const TunnelFreePage({super.key});

  @override
  State<TunnelFreePage> createState() => _TunnelFreePageState();
}

class _TunnelFreePageState extends State<TunnelFreePage>
    with WidgetsBindingObserver {
  final _speech = SpeechToText();
  final _tts = FlutterTts();

  final _search = TextEditingController();
  final _guardian = TextEditingController();

  InAppWebViewController? _map;
  Position? _currentPosition;
  StreamSubscription<Position>? _positionSubscription;
  Timer? _driveTimer;
  Timer? _breathTimer;

  late final String _html;

  bool _mapReady = false;
  bool _avoidTunnel = true;
  bool _running = false;
  bool _paused = false;
  bool _listening = false;
  bool _micBusy = false;
  bool _breathing = false;
  bool _helpOpen = false;
  bool _inBackground = false;

  bool? _speechAvailable;

  String _mapStatus = '지도 불러오는 중';
  String _heard = '';
  String _breathText = '';
  String _journeyStatus = '목적지를 선택하세요';

  int? _destination;
  int? _via;
  int _currentNode = 0;

  List<int> _route = [];

  double _fraction = 0;
  int _breathTick = 0;
  int _speechGeneration = 0;

  String _lastCommand = '';
  DateTime _lastCommandAt = DateTime.fromMillisecondsSinceEpoch(0);

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addObserver(this);
    _html = _buildHtml();
    _prepareVoice();
    _getCurrentLocation();
  }

  Future<void> _getCurrentLocation() async {
    try {
      final serviceEnabled = await Geolocator.isLocationServiceEnabled();

      if (!serviceEnabled) {
        if (mounted) {
          setState(() => _mapStatus = '위치 서비스가 꺼져 있습니다.');
        }
        return;
      }

      var permission = await Geolocator.checkPermission();

      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }

      if (permission == LocationPermission.denied ||
          permission == LocationPermission.deniedForever) {
        if (mounted) {
          setState(() => _mapStatus = '위치 권한이 필요합니다.');
        }
        return;
      }

      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
        ),
      );

      if (!mounted) return;

      setState(() {
        _currentPosition = position;
        if (_mapReady) _mapStatus = '';
      });

      _syncCurrentLocation();

      await _positionSubscription?.cancel();
      _positionSubscription =
          Geolocator.getPositionStream(
            locationSettings: const LocationSettings(
              accuracy: LocationAccuracy.high,
              distanceFilter: 5,
            ),
          ).listen((position) {
            if (!mounted) return;

            setState(() => _currentPosition = position);
            _syncCurrentLocation();
          });
    } catch (e) {
      if (mounted) {
        setState(() => _mapStatus = '현재 위치를 가져오지 못했습니다.');
      }
      debugPrint('LOCATION >>> 오류: $e');
    }
  }

  Future<void> _syncCurrentLocation() async {
    final controller = _map;
    final position = _currentPosition;

    if (!_mapReady || controller == null || position == null) return;

    try {
      await controller.evaluateJavascript(
        source:
            'window.updateCurrentLocation(${position.latitude}, ${position.longitude});',
      );
    } catch (_) {}
  }

  Future<void> _prepareVoice() async {
    try {
      await _tts.setLanguage('ko-KR');
      await _tts.setSpeechRate(0.45);
      await _tts.awaitSpeakCompletion(true);
    } catch (_) {
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _inBackground = state != AppLifecycleState.resumed;

    if (_inBackground) {
      _driveTimer?.cancel();
      _breathTimer?.cancel();

      _speechGeneration++;
      _speech.cancel();
      _tts.stop();

      if (mounted) {
        setState(() {
          if (_running) _paused = true;
          _listening = false;
          _breathing = false;
          _breathText = '';
        });
      }
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);

    _driveTimer?.cancel();
    _breathTimer?.cancel();
    _positionSubscription?.cancel();

    _speechGeneration++;
    _speech.cancel();
    _tts.stop();

    _search.dispose();
    _guardian.dispose();

    super.dispose();
  }

  void _message(String text) {
    if (!mounted) return;

    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _say(String text) async {
    final generation = ++_speechGeneration;

    try {
      await _speech.cancel();

      if (!mounted || _inBackground || generation != _speechGeneration) {
        return;
      }

      setState(() => _listening = false);

      await _tts.stop();

      if (!mounted || _inBackground || generation != _speechGeneration) {
        return;
      }

      await _tts.speak(text);
    } catch (_) {
    }
  }

  DemoRoad? _road(int a, int b) {
    for (final road in roads) {
      if (road.connects(a, b)) return road;
    }
    return null;
  }

  List<int>? _findPath(int start, int end) {
    final distances = List<double>.filled(places.length, double.infinity);

    final previous = List<int>.filled(places.length, -1);
    final visited = List<bool>.filled(places.length, false);

    distances[start] = 0;

    for (var count = 0; count < places.length; count++) {
      var current = -1;

      for (var i = 0; i < places.length; i++) {
        if (!visited[i] &&
            (current == -1 || distances[i] < distances[current])) {
          current = i;
        }
      }

      if (current == -1 || distances[current].isInfinite) break;
      if (current == end) break;

      visited[current] = true;

      for (final road in roads) {
        if (_avoidTunnel && road.tunnel) continue;

        int next;

        if (road.from == current) {
          next = road.to;
        } else if (road.to == current) {
          next = road.from;
        } else {
          continue;
        }

        final candidate =
            distances[current] + distanceBetween(places[current], places[next]);

        if (candidate < distances[next]) {
          distances[next] = candidate;
          previous[next] = current;
        }
      }
    }

    if (distances[end].isInfinite) return null;

    final result = <int>[];

    for (var cursor = end; cursor != -1; cursor = previous[cursor]) {
      result.add(cursor);
    }

    return result.reversed.toList();
  }

  List<int>? _calculateRoute(int destination, int? via) {
    final movingOnEdge = _running && _route.length >= 2;

    final prefix = movingOnEdge
        ? <int>[_route[0], _route[1]]
        : <int>[_currentNode];

    final start = prefix.last;

    if (via != null) {
      final first = _findPath(start, via);
      final second = _findPath(via, destination);

      if (first == null || second == null) return null;

      return [...prefix, ...first.skip(1), ...second.skip(1)];
    }

    final remaining = _findPath(start, destination);
    if (remaining == null) return null;

    return [...prefix, ...remaining.skip(1)];
  }

  DemoPlace get _position {
    if (_route.length < 2) return places[_currentNode];

    final a = places[_route[0]];
    final b = places[_route[1]];

    return DemoPlace(
      '모의 주행 위치',
      a.lat + (b.lat - a.lat) * _fraction,
      a.lng + (b.lng - a.lng) * _fraction,
    );
  }

  double get _remainingDistance {
    var result = 0.0;

    for (var i = 0; i < _route.length - 1; i++) {
      final length = distanceBetween(places[_route[i]], places[_route[i + 1]]);

      result += i == 0 ? length * (1 - _fraction) : length;
    }

    return result;
  }

  int get _tunnelCount {
    var count = 0;

    for (var i = 0; i < _route.length - 1; i++) {
      if (_road(_route[i], _route[i + 1])?.tunnel == true) {
        count++;
      }
    }

    return count;
  }

  bool get _insideDemoTunnel =>
      _running &&
      _route.length >= 2 &&
      _road(_route[0], _route[1])?.tunnel == true;

  int get _remainingMinutes {
    return (_remainingDistance / (40000 / 60)).ceil();
  }

  void _selectDestination(int index) {
    if (_running) {
      _message('목적지를 바꾸려면 먼저 모의 주행을 종료하세요.');
      return;
    }

    final candidate = _calculateRoute(index, null);

    if (candidate == null) {
      _message('선택한 조건을 만족하는 체험 경로가 없습니다.');
      return;
    }

    setState(() {
      _destination = index;
      _via = null;
      _route = candidate;
      _fraction = 0;
      _journeyStatus = '경로를 확인하고 모의 주행을 시작하세요';
      _search.clear();
    });

    _syncMap(fit: true);

    _say(
      '${places[index].name} 경로입니다. '
      '체험 경로에 터널이 $_tunnelCount개 있습니다.',
    );
  }

  void _changeAvoidance(bool value) {
    if (_running) {
      _message('이 코드에서는 출발 전에 회피 조건을 선택합니다.');
      return;
    }

    final oldValue = _avoidTunnel;
    _avoidTunnel = value;

    final destination = _destination;
    final candidate = destination == null
        ? null
        : _calculateRoute(destination, _via);

    if (destination != null && candidate == null) {
      _avoidTunnel = oldValue;
      _message('해당 조건의 체험 경로가 없습니다. 기존 조건을 유지합니다.');
      return;
    }

    setState(() {
      if (candidate != null) _route = candidate;
    });

    _syncMap(fit: true);
  }

  void _startDrive() {
    if (_destination == null || _route.length < 2) {
      _message('먼저 목적지를 선택하세요.');
      return;
    }

    if (_running) {
      if (_paused) _togglePause();
      return;
    }

    setState(() {
      _running = true;
      _paused = false;
      _journeyStatus = '모의 경로 주행 중 · 현재 위치 GPS는 별도 표시';
    });

    _startTimer();
    _say('모의 주행을 시작합니다.');
  }

  void _startTimer() {
    _driveTimer?.cancel();

    _driveTimer = Timer.periodic(
      const Duration(milliseconds: 250),
      (_) => _advance(),
    );
  }

  void _advance() {
    if (!mounted || !_running || _paused || _helpOpen || _route.length < 2) {
      return;
    }

    var movement = (40 / 3.6) * 0.25 * 10;
    var reachedVia = false;
    var arrived = false;

    setState(() {
      while (movement > 0 && _route.length >= 2) {
        final length = distanceBetween(places[_route[0]], places[_route[1]]);

        final remaining = length * (1 - _fraction);

        if (length > 0 && movement < remaining) {
          _fraction += movement / length;
          movement = 0;
          break;
        }

        movement -= remaining;
        _currentNode = _route[1];
        _route.removeAt(0);
        _fraction = 0;

        if (_via == _currentNode) {
          _via = null;
          _paused = true;
          reachedVia = true;
          _journeyStatus = '체험 경유지 도착 · 다시 출발을 눌러주세요';
          break;
        }

        if (_route.length < 2) {
          arrived = true;
          _running = false;
          _paused = false;
          _journeyStatus = '목적지에 도착했습니다';
          break;
        }
      }
    });

    _syncMap();

    if (reachedVia) {
      _driveTimer?.cancel();
      _say('체험 경유지에 도착했습니다. 준비되면 다시 출발하세요.');
    }

    if (arrived) {
      _driveTimer?.cancel();
      _stopBreathing();
      _say('목적지에 도착했습니다.');
    }
  }

  void _togglePause() {
    if (!_running) return;

    setState(() {
      _paused = !_paused;
      _journeyStatus = _paused ? '모의 주행 일시정지' : '모의 경로 주행 중 · 현재 위치 GPS는 별도 표시';
    });

    if (_paused) {
      _driveTimer?.cancel();
    } else {
      _startTimer();
    }
  }

  void _endDrive() {
    _driveTimer?.cancel();
    _stopBreathing();

    _speechGeneration++;
    _speech.cancel();
    _tts.stop();

    setState(() {
      _running = false;
      _paused = false;
      _listening = false;
      _currentNode = 0;
      _destination = null;
      _via = null;
      _route = [];
      _fraction = 0;
      _journeyStatus = '목적지를 선택하세요';
    });

    _syncMap(fit: true);
  }

  void _addVia(int index) {
    final destination = _destination;

    if (destination == null) {
      _message('먼저 최종 목적지를 선택하세요.');
      return;
    }

    if (index == destination) {
      _message('이미 목적지로 선택된 장소입니다.');
      return;
    }

    final candidate = _calculateRoute(destination, index);

    if (candidate == null) {
      _message('회피 조건을 유지하면서 갈 수 있는 체험 경로가 없습니다.');
      return;
    }

    final oldDistance = _remainingDistance;

    setState(() {
      _via = index;
      _route = candidate;
    });

    final extra = _remainingDistance - oldDistance;

    _syncMap(fit: !_running);

    _message(
      '${places[index].name} 경유 반영\n'
      '체험 경로 ${(extra / 1000).toStringAsFixed(1)}km 변경',
    );

    _say('${places[index].name}을 거치는 체험 경로로 변경했습니다.');
  }

  void _removeVia() {
    final destination = _destination;
    if (destination == null) return;

    final candidate = _calculateRoute(destination, null);
    if (candidate == null) return;

    setState(() {
      _via = null;
      _route = candidate;
    });

    _syncMap(fit: !_running);
  }

  void _toggleBreathing() {
    if (_breathing) {
      _stopBreathing();
      return;
    }

    setState(() {
      _breathing = true;
      _breathTick = 0;
      _breathText = '들이쉬기 4초';
    });

    _say('호흡 안내를 켰습니다. 불편하면 중지하세요.');

    _breathTimer?.cancel();
    _breathTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;

      setState(() {
        _breathTick = (_breathTick + 1) % 10;

        _breathText = _breathTick < 4
            ? '들이쉬기 ${4 - _breathTick}초'
            : '내쉬기 ${10 - _breathTick}초';
      });
    });
  }

  void _stopBreathing() {
    _breathTimer?.cancel();

    if (!mounted) return;

    setState(() {
      _breathing = false;
      _breathText = '';
    });
  }

  Future<void> _listen() async {
    if (_micBusy) return;

    if (_speech.isListening) {
      await _speech.stop();
      if (mounted) setState(() => _listening = false);
      return;
    }

    _micBusy = true;

    try {
      _speechGeneration++;
      await _tts.stop();

      _speechAvailable ??= await _speech.initialize(
        onStatus: (_) {
          if (mounted) {
            setState(() => _listening = _speech.isListening);
          }
        },
        onError: (error) {
          if (!mounted) return;

          setState(() => _listening = false);
          _message('음성 인식: ${error.errorMsg}');
        },
      );

      if (_speechAvailable != true) {
        _message('마이크 권한 또는 기기의 음성 인식 지원을 확인하세요.');
        return;
      }

      if (!mounted || _inBackground) return;

      await _speech.listen(
        listenOptions: SpeechListenOptions(
          localeId: 'ko-KR',
          listenFor: const Duration(seconds: 30),
          pauseFor: const Duration(seconds: 4),
        ),
        onResult: (result) {
          if (!mounted) return;

          setState(() => _heard = result.recognizedWords);

          if (result.finalResult) {
            _handleCommand(result.recognizedWords);
          }
        },
      );

      if (mounted) {
        setState(() => _listening = _speech.isListening);
      }
    } catch (error) {
      _message('음성 인식을 시작하지 못했습니다: $error');
    } finally {
      _micBusy = false;
    }
  }

  void _handleCommand(String raw) {
    final text = raw.replaceAll(RegExp(r'\s+'), '');
    if (text.isEmpty) return;

    final now = DateTime.now();

    if (text == _lastCommand &&
        now.difference(_lastCommandAt).inMilliseconds < 2000) {
      return;
    }

    _lastCommand = text;
    _lastCommandAt = now;

    if (RegExp(r'안괜찮|괜찮지않|못견디|도와|도움|무서워|숨이막|숨쉬기힘').hasMatch(text)) {
      _openHelp();
      return;
    }

    if (_helpOpen) {
      return;
    }

    if (text.contains('안내종료') || text.contains('주행종료')) {
      _endDrive();
      return;
    }

    if (text.contains('일시정지') || text.contains('주행멈춰')) {
      if (_running && !_paused) _togglePause();
      return;
    }

    if (text.contains('다시출발') ||
        text.contains('안내시작') ||
        text.contains('주행시작')) {
      _startDrive();
      return;
    }

    if (text.contains('터널피') ||
        text.contains('터널회피') ||
        text.contains('터널없는')) {
      _changeAvoidance(true);
      return;
    }

    if (text.contains('터널허용')) {
      _changeAvoidance(false);
      return;
    }

    if (text.contains('화장실') || text.contains('똥마려')) {
      _addVia(8);
      return;
    }

    if (text.contains('휴게소') || text.contains('쉬고싶')) {
      _addVia(9);
      return;
    }

    if (text.contains('경유취소') || text.contains('경유삭제')) {
      _removeVia();
      return;
    }

    if (text.contains('호흡')) {
      if (text.contains('꺼') || text.contains('중지') || text.contains('멈춰')) {
        _stopBreathing();
      } else if (!_breathing) {
        _toggleBreathing();
      }
      return;
    }

    for (final index in [4, 5, 6, 7]) {
      final name = places[index].name.replaceAll(' ', '');

      if (text.contains(name)) {
        _selectDestination(index);
        return;
      }
    }

    _message(
      '명령을 확인하지 못했습니다.\n'
      '예: 대전역으로 가 줘 / 안내 시작 / 화장실 / 도와줘',
    );
  }

  Future<void> _openHelp() async {
    if (_helpOpen || !mounted) return;

    _driveTimer?.cancel();
    _stopBreathing();

    setState(() {
      _helpOpen = true;
      if (_running) _paused = true;
    });

    _say('도움 요청 화면을 열었습니다. 자동 신고는 실행되지 않습니다.');

    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) {
        return AlertDialog(
          title: const Text('도움이 필요하신가요?'),
          content: const Text(
            '현재 위치는 GPS로 확인됩니다.\n\n'
            '아래 버튼은 전화 앱을 여는 기능입니다. '
            '자동 신고나 문자 발송은 하지 않습니다.',
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(dialogContext).pop();
              },
              child: const Text('닫기'),
            ),
            OutlinedButton(
              onPressed: () => _call(_guardian.text),
              child: const Text('보호자'),
            ),
            FilledButton(
              onPressed: () => _call('119'),
              child: const Text('119 전화 앱 열기'),
            ),
          ],
        );
      },
    );

    if (!mounted) return;

    setState(() => _helpOpen = false);
  }

  Future<void> _call(String raw) async {
    final number = raw.replaceAll(RegExp(r'[^0-9+]'), '');

    if (number.isEmpty) {
      _message('설정에서 보호자 연락처를 입력하세요.');
      return;
    }

    try {
      final opened = await launchUrl(
        Uri(scheme: 'tel', path: number),
        mode: LaunchMode.externalApplication,
      );

      if (!opened) _message('이 기기에서 전화 앱을 열 수 없습니다.');
    } catch (_) {
      _message('전화 앱 연결을 지원하지 않는 환경입니다.');
    }
  }

  Future<void> _openSettings() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (sheetContext) {
        return Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            20,
            20,
            MediaQuery.of(sheetContext).viewInsets.bottom + 24,
          ),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  '설정',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _guardian,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                    labelText: '보호자 연락처',
                    helperText: '이 예제에서는 앱 실행 중에만 유지됩니다.',
                    border: OutlineInputBorder(),
                  ),
                ),
                const SizedBox(height: 20),
                const Text(
                  '음성 명령 예시\n\n'
                  '• 대전역으로 가 줘\n'
                  '• 터널 피해서 가 줘 — 출발 전 설정\n'
                  '• 안내 시작 / 일시정지 / 다시 출발\n'
                  '• 화장실 / 똥마려\n'
                  '• 휴게소 / 쉬고 싶어\n'
                  '• 호흡 켜 줘 / 호흡 꺼 줘\n'
                  '• 도와줘 / 안내 종료',
                ),
                const SizedBox(height: 20),
                const Text(
                  '지도는 카카오 지도를 사용합니다.\n'
                  '경로·터널·휴게시설·주행 위치는 체험 데이터입니다.\n'
                  '실제 GPS는 연결되어 있지만, 실시간 길찾기/교통/터널 데이터는 아직 연결하지 않았습니다.',
                  style: TextStyle(color: Colors.black54),
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => Navigator.pop(sheetContext),
                  child: const Text('확인'),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _syncMap({bool fit = false}) async {
    final controller = _map;
    if (!_mapReady || controller == null) return;

    final path = <List<double>>[];

    if (_route.isNotEmpty) {
      path.add(_position.point);

      for (final node in _route.skip(1)) {
        path.add(places[node].point);
      }
    }

    final payload = jsonEncode({
      'position': _position.point,
      'path': path,
      'fit': fit,
      'follow': _running && !_paused,
      'dark': _insideDemoTunnel,
    });

    try {
      await controller.evaluateJavascript(
        source: 'window.updateDemo($payload);',
      );
    } catch (_) {
    }
  }

  String _buildHtml() {
    final key = Uri.encodeQueryComponent(kakaoJavaScriptKey);

    return '''
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport"
 content="width=device-width,initial-scale=1.0">
<style>
html,body,#map{
  width:100%;height:100%;margin:0;padding:0;
}
body{background:#e8eeeb}
#error{
  position:absolute;inset:0;
  display:none;align-items:center;justify-content:center;
  padding:24px;text-align:center;font:15px sans-serif;
}
#badge{
  position:absolute;left:10px;bottom:24px;z-index:3;
  padding:6px 9px;border-radius:7px;background:white;
  font:12px sans-serif;color:#43534c;
  pointer-events:none;
}
</style>
</head>
<body>
<div id="map"></div>
<div id="badge">점선: 기능 확인용 체험 경로</div>
<div id="error"></div>

<script>
let map=null;
let marker=null;
let gpsMarker=null;
let line=null;
let follow=true;
let pending=null;
let initStarted=false;

function notify(name,value){
  if(window.flutter_inappwebview){
    window.flutter_inappwebview.callHandler(name,value);
  }
}

function fail(message){
  const el=document.getElementById("error");
  el.style.display="flex";
  el.textContent=message;
  notify("mapError",message);
}

window.updateDemo=function(data){
  if(!map){
    pending=data;
    return;
  }

  const point=new kakao.maps.LatLng(
    data.position[0],data.position[1]
  );

  if(!marker){
    marker=new kakao.maps.Marker({
      map:map,position:point
    });
  }else{
    marker.setPosition(point);
  }

  if(line){
    line.setMap(null);
    line=null;
  }

  if(data.path.length>1){
    line=new kakao.maps.Polyline({
      map:map,
      path:data.path.map(
        p=>new kakao.maps.LatLng(p[0],p[1])
      ),
      strokeWeight:6,
      strokeColor:"#16785F",
      strokeOpacity:0.85,
      strokeStyle:"shortdash"
    });
  }

  document.getElementById("map").style.filter=
    data.dark?"brightness(.55)":"none";

  if(data.fit){
    follow=true;

    if(data.path.length>1){
      const bounds=new kakao.maps.LatLngBounds();
      data.path.forEach(
        p=>bounds.extend(
          new kakao.maps.LatLng(p[0],p[1])
        )
      );
      map.setBounds(bounds,45,30,45,30);
    }else{
      map.setCenter(point);
      map.setLevel(6);
    }
  }else if(data.follow&&follow){
    map.setCenter(point);
  }
};


window.updateCurrentLocation=function(latitude,longitude){
  if(!map){
    return;
  }

  const point=new kakao.maps.LatLng(latitude,longitude);

  if(!gpsMarker){
    gpsMarker=new kakao.maps.Marker({
      map:map,
      position:point,
      title:"현재 위치"
    });
  }else{
    gpsMarker.setPosition(point);
  }
};

window.recenterCurrent=function(){
  follow=true;
  if(gpsMarker){
    map.setCenter(gpsMarker.getPosition());
  }else if(marker){
    map.setCenter(marker.getPosition());
  }
};

window.recenterDemo=function(){
  follow=true;
  if(marker)map.setCenter(marker.getPosition());
};

function boot(){
  if(initStarted)return;
  initStarted=true;

  const sdk=document.createElement("script");
  sdk.src=
    "https://dapi.kakao.com/v2/maps/sdk.js"
    +"?autoload=false&appkey=$key";

  sdk.onerror=function(){
    fail("지도를 불러오지 못했습니다. 앱키와 네트워크를 확인하세요.");
  };

  sdk.onload=function(){
    if(!window.kakao||!kakao.maps){
      fail("카카오 지도 SDK를 확인할 수 없습니다.");
      return;
    }

    kakao.maps.load(function(){
      try{
        map=new kakao.maps.Map(
          document.getElementById("map"),
          {
            center:new kakao.maps.LatLng(36.3061,127.3446),
            level:6
          }
        );

        kakao.maps.event.addListener(
          map,"dragstart",function(){follow=false;}
        );

        window.addEventListener("resize",function(){
          if(map)map.relayout();
        });

        notify("mapReady",true);

        if(pending){
          window.updateDemo(pending);
          pending=null;
        }
      }catch(error){
        fail("지도 생성 실패: "+error.message);
      }
    });
  };

  document.head.appendChild(sdk);
}

window.addEventListener(
  "flutterInAppWebViewPlatformReady",boot
);

if(window.flutter_inappwebview)boot();
</script>
</body>
</html>
''';
  }

  Widget _mapView() {
    return Stack(
      children: [
        Positioned.fill(
          child: InAppWebView(
            initialSettings: InAppWebViewSettings(javaScriptEnabled: true),
            initialData: InAppWebViewInitialData(
              data: _html,
              baseUrl: WebUri('http://localhost/'),
              mimeType: 'text/html',
              encoding: 'utf-8',
            ),
            onWebViewCreated: (controller) {
              _map = controller;

              controller.addJavaScriptHandler(
                handlerName: 'mapReady',
                callback: (_) {
                  if (!mounted) return null;

                  setState(() {
                    _mapReady = true;
                    _mapStatus = '';
                  });

                  _syncMap(fit: true);
                  _syncCurrentLocation();
                  return null;
                },
              );

              controller.addJavaScriptHandler(
                handlerName: 'mapError',
                callback: (arguments) {
                  if (!mounted) return null;

                  setState(() {
                    _mapReady = false;
                    _mapStatus = arguments.isEmpty
                        ? '지도 연결 실패'
                        : arguments.first.toString();
                  });

                  return null;
                },
              );
            },
            onLoadStart: (_, _) {
              _mapReady = false;
            },
            onReceivedError: (_, request, error) {
              if (request.isForMainFrame != true || !mounted) {
                return;
              }

              setState(() {
                _mapStatus = '지도 연결 실패: ${error.description}';
              });
            },
          ),
        ),
        if (_mapStatus.isNotEmpty)
          Positioned(
            top: 12,
            left: 12,
            right: 12,
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(10),
                child: Text(_mapStatus),
              ),
            ),
          ),
        Positioned(
          right: 12,
          bottom: 18,
          child: FloatingActionButton.small(
            heroTag: 'recenter',
            tooltip: '체험 위치로 이동',
            onPressed: () {
              _map?.evaluateJavascript(source: 'window.recenterCurrent?.();');
            },
            child: const Icon(Icons.my_location),
          ),
        ),
      ],
    );
  }

  Widget _panel() {
    final query = _search.text.trim();
    final results = [
      4,
      5,
      6,
      7,
    ].where((index) => places[index].name.contains(query)).toList();

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        if (!_running) ...[
          const Text(
            '터널을 피하는 길찾기',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 4),
          const Text(
            '현재 위치는 실제 GPS · 경로는 기능 확인용 체험 데이터',
            style: TextStyle(color: Colors.black54),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _search,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              hintText: '대전역, 대전시청, 한밭수목원, 유성온천역',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: IconButton(
                onPressed: _listen,
                icon: Icon(_listening ? Icons.mic : Icons.mic_none),
              ),
              border: const OutlineInputBorder(),
              isDense: true,
            ),
          ),
          SwitchListTile.adaptive(
            contentPadding: EdgeInsets.zero,
            title: const Text('터널 회피'),
            subtitle: const Text('체험 도로망의 터널 구간을 제외합니다'),
            value: _avoidTunnel,
            onChanged: _changeAvoidance,
          ),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: results.map((index) {
              return ActionChip(
                label: Text(places[index].name),
                onPressed: () => _selectDestination(index),
              );
            }).toList(),
          ),
          if (results.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Text('체험 장소에 없는 검색어입니다.'),
            ),
        ],
        if (_destination != null) ...[
          const Divider(height: 24),
          Text(
            places[_destination!].name,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 6),
          Text(
            '약 $_remainingMinutes분 · '
            '${(_remainingDistance / 1000).toStringAsFixed(1)}km',
            style: const TextStyle(fontSize: 23, fontWeight: FontWeight.bold),
          ),
          Text(
            '남은 체험 터널 $_tunnelCount개 · '
            '40km/h 가정으로 계산',
          ),
          const SizedBox(height: 8),
          Text(
            _journeyStatus,
            style: TextStyle(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
          if (_insideDemoTunnel)
            const Card(
              color: Color(0xFFFFEBC4),
              child: Padding(
                padding: EdgeInsets.all(12),
                child: Text(
                  '체험 터널 통과 중\n'
                  '실제 터널 위치나 GPS 판정이 아닙니다.',
                ),
              ),
            ),
          if (_via != null)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.flag_outlined),
              title: Text('경유: ${places[_via!].name}'),
              trailing: IconButton(
                onPressed: _removeVia,
                icon: const Icon(Icons.close),
              ),
            ),
          const SizedBox(height: 12),
          if (!_running)
            FilledButton.icon(
              onPressed: _startDrive,
              icon: const Icon(Icons.navigation),
              label: const Text('모의 주행 시작'),
            )
          else
            Row(
              children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _togglePause,
                    icon: Icon(_paused ? Icons.play_arrow : Icons.pause),
                    label: Text(_paused ? '다시 출발' : '일시정지'),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(onPressed: _endDrive, child: const Text('종료')),
              ],
            ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              ActionChip(
                avatar: const Icon(Icons.wc, size: 18),
                label: const Text('화장실 경유'),
                onPressed: () => _addVia(8),
              ),
              ActionChip(
                avatar: const Icon(Icons.local_cafe, size: 18),
                label: const Text('휴게시설 경유'),
                onPressed: () => _addVia(9),
              ),
              ActionChip(
                avatar: const Icon(Icons.air, size: 18),
                label: Text(_breathing ? '호흡 중지' : '호흡 안내'),
                onPressed: _toggleBreathing,
              ),
            ],
          ),
        ],
        if (_breathing)
          Card(
            child: ListTile(
              leading: const Icon(Icons.air),
              title: Text(_breathText),
              subtitle: const Text('선택형 안내 · 불편하면 중지하세요'),
              trailing: IconButton(
                onPressed: _stopBreathing,
                icon: const Icon(Icons.close),
              ),
            ),
          ),
        const SizedBox(height: 12),
        FilledButton.tonalIcon(
          onPressed: _listen,
          icon: Icon(_listening ? Icons.mic : Icons.mic_none),
          label: Text(_listening ? '듣고 있어요 · 중지' : '음성 명령'),
        ),
        if (_heard.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text('인식: $_heard'),
          ),
        const SizedBox(height: 12),
        const Text(
          '기능 체험용입니다. 지도 위 점선은 실제 도로 경로가 아닙니다.',
          style: TextStyle(fontSize: 12, color: Colors.black54),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        title: const Text('터널프리'),
        actions: [
          TextButton.icon(
            onPressed: _openHelp,
            icon: const Icon(Icons.sos, color: Colors.red),
            label: const Text('도움'),
          ),
          IconButton(
            tooltip: '설정',
            onPressed: _openSettings,
            icon: const Icon(Icons.settings_outlined),
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: LayoutBuilder(
          builder: (context, constraints) {
            if (constraints.maxWidth >= 720) {
              return Row(
                children: [
                  SizedBox(width: 350, child: _panel()),
                  Expanded(child: _mapView()),
                ],
              );
            }

            final keyboardOpen = MediaQuery.of(context).viewInsets.bottom > 0;

            final panelHeight =
                constraints.maxHeight *
                (keyboardOpen ? 0.75 : (_running ? 0.52 : 0.58));

            return Column(
              children: [
                Expanded(child: _mapView()),
                SizedBox(
                  height: panelHeight,
                  child: Material(
                    elevation: 8,
                    color: Colors.white,
                    borderRadius: const BorderRadius.vertical(
                      top: Radius.circular(22),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: _panel(),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
