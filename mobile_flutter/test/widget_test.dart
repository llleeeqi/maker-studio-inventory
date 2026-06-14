import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:studio_inventory_mobile/main.dart';

void main() {
  testWidgets('starts on scan workspace with main navigation', (tester) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const StudioInventoryApp());
    await tester.pumpAndSettle();

    expect(find.text('工作室物品管理'), findsOneWidget);
    expect(find.text('扫码'), findsWidgets);
    expect(find.text('库存'), findsOneWidget);
    expect(find.text('新增'), findsOneWidget);
    expect(find.text('流水'), findsOneWidget);
    expect(find.text('预览扫码'), findsOneWidget);
    expect(find.byTooltip('原生扫码'), findsOneWidget);
  });

  test('parses readable msi spool payload', () {
    final parsed = parsePayload(
      'msi:v1;type=spool;id=PLA-BLK-001;name=黑色PLA;brand=Bambu;material=PLA;color=black;full_g=1200;tare_g=200;net_g=1000;created_on=260613',
    );

    expect(parsed.type, 'spool');
    expect(parsed.ref?.id, 'PLA-BLK-001');
    expect(parsed.profile?.name, '黑色PLA');
    expect(parsed.profile?.tareG, 200);
    expect(parsed.profile?.createdOn, '260613');
  });

  test('unknown msi profile does not count as stock until stock in', () {
    final store = InventoryStore();
    store.snapshot = InventorySnapshot(
      deviceId: 'test-phone',
      profiles: {},
      states: {},
      transactions: [],
      recentScanLog: [],
    );

    final message = store.handleScan(
      'msi:v1;type=other;id=TOOL-001;name=热风枪;note=喷嘴套装;created_on=260613',
    );

    expect(message, contains('尚未入库'));
    expect(store.snapshot.profiles.containsKey('TOOL-001'), isTrue);
    expect(store.snapshot.states.containsKey('TOOL-001'), isFalse);
  });

  test('weight then spool creates stock state', () {
    final store = InventoryStore();
    store.snapshot = InventorySnapshot(
      deviceId: 'test-phone',
      profiles: {},
      states: {},
      transactions: [],
      recentScanLog: [],
    );

    store.handleScan('msi:v1;type=weight;value_g=712.4');
    final message = store.handleScan(
      'msi:v1;type=spool;id=PLA-BLK-001;name=黑色PLA;brand=Bambu;material=PLA;color=black;full_g=1200;tare_g=200;net_g=1000;created_on=260613',
    );

    expect(message, contains('已入库'));
    expect(store.snapshot.states['PLA-BLK-001']?.currentG, 712.4);
    expect(store.snapshot.transactions.last.action, 'stock_in');
  });
}
