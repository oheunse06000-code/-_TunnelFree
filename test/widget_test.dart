import 'package:flutter/material.dart';
import 'package:flutter_inappwebview_platform_interface/flutter_inappwebview_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:tunnel_free/main.dart';

class _FakeInAppWebViewPlatform extends InAppWebViewPlatform {
  @override
  PlatformInAppWebViewWidget createPlatformInAppWebViewWidget(
    PlatformInAppWebViewWidgetCreationParams params,
  ) => _FakeInAppWebViewWidget(params);
}

class _FakeInAppWebViewWidget extends PlatformInAppWebViewWidget {
  _FakeInAppWebViewWidget(super.params) : super.implementation();

  @override
  Widget build(BuildContext context) => const SizedBox.expand();

  @override
  T controllerFromPlatform<T>(PlatformInAppWebViewController controller) =>
      throw UnimplementedError();

  @override
  void dispose() {}
}

void main() {
  setUp(() => InAppWebViewPlatform.instance = _FakeInAppWebViewPlatform());

  testWidgets('TunnelFreeApp 이 첫 화면을 그린다', (WidgetTester tester) async {
    await tester.pumpWidget(const TunnelFreeApp());
    await tester.pump();

    expect(find.byType(TunnelFreePage), findsOneWidget);
    expect(find.text('터널프리'), findsWidgets);
    expect(find.text('터널 회피'), findsOneWidget);
  });
}
