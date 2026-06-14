import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const StudioInventoryApp());
}

class NativeScanner {
  static const MethodChannel _channel = MethodChannel(
    'studio.inventory.mobile/native_scanner',
  );

  static Future<String?> scanQr() {
    return _channel.invokeMethod<String>('scanQr');
  }
}

class StudioInventoryApp extends StatelessWidget {
  const StudioInventoryApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF0F766E);
    return MaterialApp(
      title: '工作室物品管理',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: seed),
        scaffoldBackgroundColor: const Color(0xFFF6F7F8),
        navigationBarTheme: const NavigationBarThemeData(
          labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        ),
      ),
      home: const InventoryHome(),
    );
  }
}

class InventoryHome extends StatefulWidget {
  const InventoryHome({super.key});

  @override
  State<InventoryHome> createState() => _InventoryHomeState();
}

class _InventoryHomeState extends State<InventoryHome> {
  final InventoryStore store = InventoryStore();
  int page = 0;

  @override
  void initState() {
    super.initState();
    store.load();
  }

  @override
  void dispose() {
    store.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      ScanPage(store: store),
      InventoryPage(store: store, onEdit: _openEditor),
      CatalogPage(store: store),
      TransactionsPage(store: store),
    ];

    return AnimatedBuilder(
      animation: store,
      builder: (context, _) {
        return Scaffold(
          appBar: AppBar(
            title: const Text('工作室物品管理'),
            actions: [
              IconButton(
                tooltip: '重置样例',
                icon: const Icon(Icons.restart_alt),
                onPressed: store.resetDemo,
              ),
            ],
          ),
          body: SafeArea(
            child: store.loaded
                ? IndexedStack(index: page, children: pages)
                : const Center(child: CircularProgressIndicator()),
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: page,
            onDestinationSelected: (value) => setState(() => page = value),
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.qr_code_scanner),
                label: '扫码',
              ),
              NavigationDestination(icon: Icon(Icons.inventory_2), label: '库存'),
              NavigationDestination(icon: Icon(Icons.add_box), label: '新增'),
              NavigationDestination(
                icon: Icon(Icons.receipt_long),
                label: '流水',
              ),
            ],
          ),
        );
      },
    );
  }

  void _openEditor(ItemRef ref) {
    setState(() => page = 2);
    store.editingRef = ref;
    store.notify();
  }
}

class ScanPage extends StatefulWidget {
  const ScanPage({required this.store, super.key});

  final InventoryStore store;

  @override
  State<ScanPage> createState() => _ScanPageState();
}

class _ScanPageState extends State<ScanPage> with WidgetsBindingObserver {
  late final MobileScannerController scanner;
  String message = '扫 msi:v1 标签恢复档案；入库、盘点、移库都由扫码上下文决定。';
  String lastPayload = '';
  DateTime lastPayloadAt = DateTime.fromMillisecondsSinceEpoch(0);
  bool scannerBusy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    scanner = MobileScannerController(
      autoStart: false,
      detectionSpeed: DetectionSpeed.normal,
      formats: const [BarcodeFormat.qrCode],
      facing: CameraFacing.back,
    );
    scanner.addListener(_handleScannerState);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    scanner.removeListener(_handleScannerState);
    unawaited(scanner.dispose());
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden) {
      unawaited(_stopScanner(showMessage: false));
    }
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.store.snapshot;
    final active = widget.store.activeEntry;
    return LayoutBuilder(
      builder: (context, constraints) {
        final compact = constraints.maxHeight < 560;
        final previewHeight = compact ? 124.0 : 168.0;
        final scanFrameSize = compact ? 82.0 : 104.0;

        return Padding(
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
          child: Column(
            children: [
              _StatusGrid(
                cells: [
                  StatusCell('当前', active?.profile.id ?? '无'),
                  StatusCell(
                    '重量',
                    widget.store.pendingWeight == null
                        ? '无'
                        : '${widget.store.pendingWeight!.g}g',
                  ),
                  StatusCell(
                    '库位',
                    widget.store.pendingLocation?.isEmpty ?? true
                        ? '无'
                        : widget.store.pendingLocation!,
                  ),
                  StatusCell('在库', '${snapshot.inStockCount}'),
                ],
              ),
              const SizedBox(height: 8),
              Material(
                color: Theme.of(context).colorScheme.surface,
                borderRadius: BorderRadius.circular(8),
                clipBehavior: Clip.antiAlias,
                child: SizedBox(
                  height: previewHeight,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      MobileScanner(
                        controller: scanner,
                        fit: BoxFit.cover,
                        onDetect: _onDetect,
                        errorBuilder: (context, error) => Center(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Text(
                              '相机不可用：${error.errorDetails?.message ?? error.errorCode.name}',
                            ),
                          ),
                        ),
                      ),
                      IgnorePointer(
                        child: Center(
                          child: Container(
                            width: scanFrameSize,
                            height: scanFrameSize,
                            decoration: BoxDecoration(
                              border: Border.all(color: Colors.white, width: 2),
                              borderRadius: BorderRadius.circular(8),
                              boxShadow: const [
                                BoxShadow(
                                  color: Colors.black26,
                                  spreadRadius: 80,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: scannerBusy ? null : _startScanner,
                      icon: const Icon(Icons.play_arrow),
                      label: Text(scannerBusy ? '启动中' : '预览扫码'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filledTonal(
                    tooltip: '停止',
                    onPressed: scannerBusy ? null : _stopScanner,
                    icon: const Icon(Icons.stop),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filledTonal(
                    tooltip: '手电筒',
                    onPressed: scannerBusy ? null : _toggleTorch,
                    icon: const Icon(Icons.flashlight_on),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filledTonal(
                    tooltip: '原生扫码',
                    onPressed: scannerBusy ? null : _scanWithNativeScanner,
                    icon: const Icon(Icons.document_scanner),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filledTonal(
                    tooltip: '手动补录',
                    onPressed: _openManualInput,
                    icon: const Icon(Icons.keyboard),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              _ResultCard(text: message, compact: compact),
              if (active != null) ...[
                const SizedBox(height: 8),
                _ActiveEntryCard(
                  entry: active,
                  pendingWeight: widget.store.pendingWeight,
                  onStockIn: () => _runAction(widget.store.stockInActive),
                  onCheckout: () => _runAction(widget.store.checkoutActive),
                  onRestore: () => _runAction(widget.store.restoreActive),
                ),
              ],
              const SizedBox(height: 8),
              Expanded(
                child: ListView(
                  padding: EdgeInsets.zero,
                  children: snapshot.recentScanLog.map((entry) {
                    return ListTile(
                      dense: true,
                      leading: const Icon(Icons.history, size: 20),
                      title: Text(entry.payload),
                      subtitle: Text(
                        entry.createdAt.toLocal().toIso8601String(),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _onDetect(BarcodeCapture capture) {
    final payload = capture.barcodes
        .map((barcode) => barcode.rawValue)
        .whereType<String>()
        .firstOrNull;
    if (payload == null || payload.trim().isEmpty) return;
    final now = DateTime.now();
    if (payload == lastPayload &&
        now.difference(lastPayloadAt).inMilliseconds < 1400) {
      return;
    }
    lastPayload = payload;
    lastPayloadAt = now;
    _handlePayload(payload);
  }

  void _handlePayload(String payload) {
    final result = widget.store.handleScan(payload);
    HapticFeedback.mediumImpact();
    SystemSound.play(SystemSoundType.click);
    setState(() => message = result);
  }

  Future<void> _startScanner() async {
    if (scannerBusy || scanner.value.isRunning) return;
    setState(() {
      scannerBusy = true;
      message = '正在打开相机...';
    });
    try {
      await scanner.start(cameraDirection: CameraFacing.back);
      final error = scanner.value.error;
      if (!mounted) return;
      setState(() {
        message = error == null ? '扫码中：对准二维码。' : _cameraErrorText(error);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => message = _cameraErrorText(error));
    } finally {
      if (mounted) setState(() => scannerBusy = false);
    }
  }

  Future<void> _scanWithNativeScanner() async {
    if (scannerBusy) return;
    setState(() {
      scannerBusy = true;
      message = '正在打开原生扫码...';
    });
    try {
      await _stopScannerForNativeLaunch();
      final payload = await NativeScanner.scanQr();
      if (!mounted) return;
      if (payload == null || payload.trim().isEmpty) {
        setState(() => message = '已取消扫码。');
        return;
      }
      _handlePayload(payload);
    } catch (error) {
      if (mounted) setState(() => message = _cameraErrorText(error));
    } finally {
      if (mounted) setState(() => scannerBusy = false);
    }
  }

  Future<void> _stopScannerForNativeLaunch() async {
    try {
      if (scanner.value.isRunning) await scanner.stop();
    } catch (_) {
      // Launching the native scanner is still useful if inline stop fails.
    }
  }

  Future<void> _stopScanner({bool showMessage = true}) async {
    if (scannerBusy) return;
    setState(() => scannerBusy = true);
    try {
      await scanner.stop();
      if (mounted && showMessage) {
        setState(() => message = '扫码已停止。');
      }
    } catch (error) {
      if (mounted) setState(() => message = _cameraErrorText(error));
    } finally {
      if (mounted) setState(() => scannerBusy = false);
    }
  }

  Future<void> _toggleTorch() async {
    if (scannerBusy || !scanner.value.isRunning) {
      setState(() => message = '先点“开始扫码”，相机启动后才能打开手电筒。');
      return;
    }
    try {
      await scanner.toggleTorch();
    } catch (error) {
      if (mounted) setState(() => message = _cameraErrorText(error));
    }
  }

  void _handleScannerState() {
    final error = scanner.value.error;
    if (error == null || !mounted) return;
    setState(() => message = _cameraErrorText(error));
  }

  void _runAction(String Function() action) {
    final result = action();
    HapticFeedback.mediumImpact();
    SystemSound.play(SystemSoundType.click);
    setState(() => message = result);
  }

  Future<void> _openManualInput() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: EdgeInsets.only(
              left: 16,
              right: 16,
              top: 16,
              bottom: MediaQuery.viewInsetsOf(sheetContext).bottom + 16,
            ),
            child: _ManualPayloadBar(
              onSubmit: (payload) {
                Navigator.of(sheetContext).pop();
                _handlePayload(payload);
              },
            ),
          ),
        );
      },
    );
  }
}

class _ActiveEntryCard extends StatelessWidget {
  const _ActiveEntryCard({
    required this.entry,
    required this.pendingWeight,
    required this.onStockIn,
    required this.onCheckout,
    required this.onRestore,
  });

  final InventoryEntry entry;
  final double? pendingWeight;
  final VoidCallback onStockIn;
  final VoidCallback onCheckout;
  final VoidCallback onRestore;

  @override
  Widget build(BuildContext context) {
    final state = entry.state;
    final isMissingState = state == null;
    final isCheckedOut = state?.status == StockStatus.checkedOut;
    final canStockIn = switch (entry.profile.kind) {
      ItemKind.spool =>
        pendingWeight != null || (isMissingState && entry.currentG != null),
      ItemKind.part =>
        pendingWeight != null ||
            (isMissingState &&
                (entry.currentQty != null || entry.currentG != null)),
      ItemKind.other => true,
      ItemKind.location => false,
    };

    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${entry.profile.id} · ${entry.profile.name}',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                _StockBadge(entry: entry),
              ],
            ),
            const SizedBox(height: 4),
            Text(entry.detailText),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (isMissingState || isCheckedOut)
                  FilledButton.icon(
                    onPressed: canStockIn ? onStockIn : null,
                    icon: const Icon(Icons.input),
                    label: Text(isCheckedOut ? '重新入库' : '入库'),
                  ),
                if (state?.status == StockStatus.inStock)
                  FilledButton.tonalIcon(
                    onPressed: onCheckout,
                    icon: const Icon(Icons.output),
                    label: const Text('出库'),
                  ),
                if (state?.status == StockStatus.archived)
                  FilledButton.tonalIcon(
                    onPressed: onRestore,
                    icon: const Icon(Icons.unarchive),
                    label: const Text('恢复'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class InventoryPage extends StatefulWidget {
  const InventoryPage({required this.store, required this.onEdit, super.key});

  final InventoryStore store;
  final void Function(ItemRef ref) onEdit;

  @override
  State<InventoryPage> createState() => _InventoryPageState();
}

class _InventoryPageState extends State<InventoryPage> {
  String query = '';
  String kindFilter = 'all';
  bool missingLocationOnly = false;

  @override
  Widget build(BuildContext context) {
    final entries = widget.store.snapshot.entries.where(_matches).toList();
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        Row(
          children: [
            Expanded(
              child: SearchBar(
                hintText: '搜索 ID、名称、品牌、规格、库位',
                leading: const Icon(Icons.search),
                onChanged: (value) => setState(() => query = value),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filledTonal(
              tooltip: '导入/导出',
              onPressed: _openSnapshotSheet,
              icon: const Icon(Icons.import_export),
            ),
          ],
        ),
        const SizedBox(height: 8),
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'all', label: Text('全部')),
            ButtonSegment(value: 'spool', label: Text('耗材')),
            ButtonSegment(value: 'part', label: Text('零件')),
            ButtonSegment(value: 'other', label: Text('其他')),
          ],
          selected: {kindFilter},
          onSelectionChanged: (value) =>
              setState(() => kindFilter = value.first),
        ),
        const SizedBox(height: 8),
        FilterChip(
          label: const Text('未绑定库位'),
          selected: missingLocationOnly,
          onSelected: (value) => setState(() => missingLocationOnly = value),
        ),
        const SizedBox(height: 8),
        Text(
          '显示 ${entries.length} / ${widget.store.snapshot.profiles.length} 个档案',
        ),
        const SizedBox(height: 8),
        for (final entry in entries)
          Card(
            margin: const EdgeInsets.only(bottom: 8),
            child: ListTile(
              onTap: () => widget.onEdit(entry.ref),
              leading: Icon(entry.profile.kind.icon),
              title: Text('${entry.profile.id} · ${entry.profile.name}'),
              subtitle: Text(entry.detailText),
              trailing: _StockBadge(entry: entry),
            ),
          ),
      ],
    );
  }

  bool _matches(InventoryEntry entry) {
    if (kindFilter != 'all' && entry.profile.kind.payloadType != kindFilter) {
      return false;
    }
    final text = query.trim().toLowerCase();
    if (text.isNotEmpty && !entry.searchText.contains(text)) return false;
    if (missingLocationOnly && entry.locationText != '未绑定') return false;
    return true;
  }

  Future<void> _openSnapshotSheet() async {
    final controller = TextEditingController(text: widget.store.exportJson());
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) {
        return SafeArea(
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              16,
              8,
              16,
              16 + MediaQuery.viewInsetsOf(context).bottom,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('JSON 快照', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 8),
                SizedBox(
                  height: 260,
                  child: TextField(
                    controller: controller,
                    maxLines: null,
                    expands: true,
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      hintText: '导出后可复制；粘贴 JSON 后可导入。',
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: FilledButton.icon(
                        onPressed: () {
                          final result = widget.store.importJson(
                            controller.text,
                          );
                          Navigator.of(context).pop();
                          ScaffoldMessenger.of(
                            context,
                          ).showSnackBar(SnackBar(content: Text(result)));
                        },
                        icon: const Icon(Icons.upload_file),
                        label: const Text('导入'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    FilledButton.tonalIcon(
                      onPressed: () {
                        Clipboard.setData(
                          ClipboardData(text: widget.store.exportJson()),
                        );
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('已复制快照 JSON')),
                        );
                      },
                      icon: const Icon(Icons.copy),
                      label: const Text('复制'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
    controller.dispose();
  }
}

class CatalogPage extends StatefulWidget {
  const CatalogPage({required this.store, super.key});

  final InventoryStore store;

  @override
  State<CatalogPage> createState() => _CatalogPageState();
}

class _CatalogPageState extends State<CatalogPage> {
  final formKey = GlobalKey<FormState>();
  final id = TextEditingController();
  final name = TextEditingController();
  final brand = TextEditingController();
  final material = TextEditingController();
  final color = TextEditingController();
  final fullG = TextEditingController();
  final tareG = TextEditingController();
  final netG = TextEditingController();
  final category = TextEditingController();
  final spec = TextEditingController();
  final unitWeightG = TextEditingController();
  final packageQty = TextEditingController();
  final note = TextEditingController();
  final createdOn = TextEditingController();
  final currentG = TextEditingController();
  final currentQty = TextEditingController();
  final location = TextEditingController();
  final stockedOn = TextEditingController();
  ItemKind kind = ItemKind.spool;

  @override
  void initState() {
    super.initState();
    _newProfile();
  }

  @override
  void didUpdateWidget(covariant CatalogPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    final ref = widget.store.editingRef;
    if (ref != null) {
      widget.store.editingRef = null;
      _load(ref);
    }
  }

  @override
  void dispose() {
    for (final controller in [
      id,
      name,
      brand,
      material,
      color,
      fullG,
      tareG,
      netG,
      category,
      spec,
      unitWeightG,
      packageQty,
      note,
      createdOn,
      currentG,
      currentQty,
      location,
      stockedOn,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final profile = _tryReadProfile();
    return Form(
      key: formKey,
      child: ListView(
        padding: const EdgeInsets.all(12),
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(
                id.text.isEmpty ? '新增${kind.label}' : '编辑${kind.label}',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              FilledButton.tonalIcon(
                onPressed: _newProfile,
                icon: const Icon(Icons.add),
                label: const Text('新建'),
              ),
              FilledButton.tonalIcon(
                onPressed: _openFillScanner,
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('扫码录入'),
              ),
              FilledButton.tonalIcon(
                onPressed: _clone,
                icon: const Icon(Icons.copy),
                label: const Text('克隆'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          SegmentedButton<ItemKind>(
            segments: const [
              ButtonSegment(value: ItemKind.spool, label: Text('耗材')),
              ButtonSegment(value: ItemKind.part, label: Text('零件')),
              ButtonSegment(value: ItemKind.other, label: Text('其他')),
            ],
            selected: {kind},
            onSelectionChanged: (value) => setState(() {
              kind = value.first;
              _applyKindDefaults();
            }),
          ),
          const SizedBox(height: 12),
          _TextField(controller: id, label: 'ID', requiredText: true),
          _TextField(controller: name, label: '名称', requiredText: true),
          _TextField(
            controller: createdOn,
            label: '建档日期 YYMMDD',
            requiredText: true,
          ),
          if (kind == ItemKind.spool) ...[
            _TextField(controller: brand, label: '品牌', requiredText: true),
            _TextField(controller: material, label: '材料', requiredText: true),
            _TextField(controller: color, label: '颜色', requiredText: true),
            _TextField(controller: fullG, label: '满卷总重 full_g', number: true),
            _TextField(
              controller: tareG,
              label: '空盘重量 tare_g',
              number: true,
              requiredText: true,
            ),
            _TextField(controller: netG, label: '标称净重 net_g', number: true),
            _TextField(
              controller: currentG,
              label: '入库/盘点当前毛重 current_g',
              number: true,
            ),
          ] else if (kind == ItemKind.part) ...[
            _TextField(
              controller: category,
              label: '类别 category',
              requiredText: true,
            ),
            _TextField(controller: spec, label: '规格 spec', requiredText: true),
            _TextField(controller: color, label: '颜色/表面处理 color'),
            _TextField(
              controller: unitWeightG,
              label: '单件重量 unit_weight_g',
              number: true,
            ),
            _TextField(
              controller: packageQty,
              label: '包装数量 package_qty',
              number: true,
            ),
            _TextField(
              controller: currentQty,
              label: '入库数量 current_qty',
              number: true,
            ),
            _TextField(
              controller: currentG,
              label: '或总重量 current_g',
              number: true,
            ),
          ] else ...[
            _TextField(controller: note, label: '备注 note（短）'),
          ],
          _TextField(controller: location, label: '库位（可选，不进二维码）'),
          _TextField(controller: stockedOn, label: '入库日期 YYMMDD（可选）'),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: FilledButton.icon(
                  onPressed: _saveProfile,
                  icon: const Icon(Icons.save),
                  label: const Text('保存档案'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton.tonalIcon(
                  onPressed: _saveAndStockIn,
                  icon: const Icon(Icons.input),
                  label: const Text('保存并入库'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          _LabelPreview(profile: profile),
        ],
      ),
    );
  }

  void _newProfile() {
    setState(() {
      kind = ItemKind.spool;
      id.clear();
      name.clear();
      brand.text = 'Bambu';
      material.text = 'PLA';
      color.text = 'black';
      fullG.text = '1200';
      tareG.text = '200';
      netG.text = '1000';
      category.text = 'screw';
      spec.text = 'M3x8';
      unitWeightG.text = '0.42';
      packageQty.text = '100';
      note.clear();
      createdOn.text = todayYyMmDd();
      currentG.clear();
      currentQty.clear();
      location.clear();
      stockedOn.text = todayYyMmDd();
    });
  }

  void _applyKindDefaults() {
    if (createdOn.text.isEmpty) createdOn.text = todayYyMmDd();
    if (stockedOn.text.isEmpty) stockedOn.text = todayYyMmDd();
  }

  void _load(ItemRef ref) {
    final entry = widget.store.snapshot.entryFor(ref);
    if (entry == null) return;
    final profile = entry.profile;
    final state = entry.state;
    setState(() {
      kind = profile.kind;
      id.text = profile.id;
      name.text = profile.name;
      brand.text = profile.brand;
      material.text = profile.material;
      color.text = profile.color;
      fullG.text = profile.fullG?.g ?? '';
      tareG.text = profile.tareG?.g ?? '';
      netG.text = profile.netG?.g ?? '';
      category.text = profile.category;
      spec.text = profile.spec;
      unitWeightG.text = profile.unitWeightG?.g ?? '';
      packageQty.text = profile.packageQty?.toString() ?? '';
      note.text = profile.note;
      createdOn.text = profile.createdOn;
      currentG.text = state?.currentG?.g ?? '';
      currentQty.text = state?.currentQty?.toString() ?? '';
      location.text = state?.locationId ?? '';
      stockedOn.text = state?.stockedOn ?? todayYyMmDd();
    });
  }

  void _saveProfile() {
    if (!formKey.currentState!.validate()) return;
    final result = widget.store.saveProfile(_readProfile());
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(result)));
    setState(() {});
  }

  void _saveAndStockIn() {
    if (!formKey.currentState!.validate()) return;
    final profile = _readProfile();
    widget.store.saveProfile(profile);
    final result = widget.store.stockIn(
      profile.ref,
      currentG: double.tryParse(currentG.text),
      currentQty: int.tryParse(currentQty.text),
      locationId: location.text.trim(),
      stockedOn: stockedOn.text.trim().isEmpty
          ? todayYyMmDd()
          : stockedOn.text.trim(),
    );
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(result)));
    setState(() {});
  }

  void _clone() {
    final ref = ItemRef(kind, id.text.trim().toUpperCase());
    final entry = widget.store.snapshot.entryFor(ref);
    if (entry == null) {
      _openCloneScanner();
      return;
    }
    final cloned = widget.store.cloneProfile(ref);
    _load(cloned.ref);
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text('已从 ${entry.profile.id} 克隆')));
  }

  Future<void> _openFillScanner() async {
    await showCatalogScannerSheet(
      context,
      title: '扫码录入',
      onPayload: _applyCatalogPayload,
    );
  }

  Future<void> _openCloneScanner() async {
    await showCatalogScannerSheet(
      context,
      title: '扫码克隆来源',
      onPayload: (payload) {
        final parsed = parsePayload(payload);
        final ref = parsed.ref;
        if (ref == null) return '只支持扫物品码作为克隆来源。';
        final entry = widget.store.snapshot.entryFor(ref);
        if (entry == null) return '找不到可克隆物品：$payload';
        final cloned = widget.store.cloneProfile(ref);
        _load(cloned.ref);
        Navigator.of(context).maybePop();
        return '已从 ${entry.profile.id} 克隆';
      },
    );
  }

  String _applyCatalogPayload(String payload) {
    final parsed = parsePayload(payload);
    setState(() {
      if (parsed.profile != null) {
        _fillProfile(parsed.profile!);
        return;
      }
      switch (parsed.type) {
        case 'spool':
        case 'part':
        case 'other':
          kind = parsed.kind!;
          id.text = parsed.value.toUpperCase();
          _applyKindDefaults();
        case 'weight':
          final weight = parsed.weightG;
          if (weight == null || weight <= 0) break;
          currentG.text = weight.g;
          if (kind == ItemKind.part) {
            final unit = double.tryParse(unitWeightG.text);
            if (unit != null && unit > 0) {
              currentQty.text = '${(weight / unit).floor()}';
            }
          }
        case 'location':
          location.text = parsed.value.toUpperCase();
        default:
      }
    });
    return '已处理：$payload';
  }

  void _fillProfile(ItemProfile profile) {
    kind = profile.kind;
    id.text = profile.id;
    name.text = profile.name;
    brand.text = profile.brand;
    material.text = profile.material;
    color.text = profile.color;
    fullG.text = profile.fullG?.g ?? '';
    tareG.text = profile.tareG?.g ?? '';
    netG.text = profile.netG?.g ?? '';
    category.text = profile.category;
    spec.text = profile.spec;
    unitWeightG.text = profile.unitWeightG?.g ?? '';
    packageQty.text = profile.packageQty?.toString() ?? '';
    note.text = profile.note;
    createdOn.text = profile.createdOn;
  }

  ItemProfile? _tryReadProfile() {
    if (id.text.trim().isEmpty || name.text.trim().isEmpty) return null;
    return _readProfile();
  }

  ItemProfile _readProfile() {
    return ItemProfile(
      id: id.text.trim().toUpperCase(),
      kind: kind,
      name: name.text.trim(),
      brand: brand.text.trim(),
      material: material.text.trim(),
      color: color.text.trim(),
      fullG: double.tryParse(fullG.text),
      tareG: double.tryParse(tareG.text),
      netG: double.tryParse(netG.text),
      category: category.text.trim(),
      spec: spec.text.trim(),
      unitWeightG: double.tryParse(unitWeightG.text),
      packageQty: int.tryParse(packageQty.text),
      note: note.text.trim(),
      createdOn: createdOn.text.trim().isEmpty
          ? todayYyMmDd()
          : createdOn.text.trim(),
      profileUpdatedAt: DateTime.now(),
    );
  }
}

class TransactionsPage extends StatelessWidget {
  const TransactionsPage({required this.store, super.key});

  final InventoryStore store;

  @override
  Widget build(BuildContext context) {
    final transactions = store.snapshot.transactions.reversed.toList();
    if (transactions.isEmpty) {
      return const Center(child: Text('还没有流水'));
    }
    return ListView.builder(
      padding: const EdgeInsets.all(12),
      itemCount: transactions.length,
      itemBuilder: (context, index) {
        final tx = transactions[index];
        return Card(
          child: ListTile(
            leading: const Icon(Icons.receipt_long),
            title: Text('${tx.itemId} · ${tx.action}'),
            subtitle: Text('${tx.createdAt} · ${tx.deviceId}'),
            trailing: Text(tx.createdOn),
          ),
        );
      },
    );
  }
}

class InventoryStore extends ChangeNotifier {
  static const storageKey = 'studio_inventory_snapshot_v2';
  static const legacyStorageKey = 'studio_inventory_state_v1';

  InventorySnapshot snapshot = InventorySnapshot.demo();
  bool loaded = false;
  ItemRef? activeRef;
  ItemRef? editingRef;
  double? pendingWeight;
  String? pendingLocation;

  InventoryEntry? get activeEntry =>
      activeRef == null ? null : snapshot.entryFor(activeRef!);

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(storageKey);
    final legacyRaw = prefs.getString(legacyStorageKey);
    if (raw != null) {
      snapshot = InventorySnapshot.fromJson(
        jsonDecode(raw) as Map<String, Object?>,
      );
    } else if (legacyRaw != null) {
      snapshot = InventorySnapshot.fromLegacy(
        jsonDecode(legacyRaw) as Map<String, Object?>,
      );
    }
    loaded = true;
    notifyListeners();
  }

  void notify() => notifyListeners();

  Future<void> persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(storageKey, jsonEncode(snapshot.toJson()));
  }

  Future<void> resetDemo() async {
    snapshot = InventorySnapshot.demo();
    activeRef = null;
    pendingWeight = null;
    pendingLocation = null;
    await persist();
    notifyListeners();
  }

  String handleScan(String raw) {
    final parsed = parsePayload(raw);
    snapshot.addScanLog(raw);

    String message;
    switch (parsed.type) {
      case 'weight':
        final weight = parsed.weightG;
        if (weight == null || weight <= 0) return '重量格式错误：$raw';
        message = _handleWeight(round1(weight));
      case 'location':
        if (parsed.value.isEmpty) return '库位码为空。';
        message = _handleLocation(parsed.value.toUpperCase());
      case 'spool':
      case 'part':
      case 'other':
        message = _handleItemPayload(parsed);
      default:
        message = '无法识别：$raw';
    }

    unawaited(persist());
    notifyListeners();
    return message;
  }

  String saveProfile(ItemProfile profile) {
    final existed = snapshot.profiles.containsKey(profile.id);
    snapshot.profiles[profile.id] = profile;
    snapshot.addTransaction(
      action: existed ? 'edit_profile' : 'create_profile',
      ref: profile.ref,
      before: existed ? {'id': profile.id} : null,
      after: profile.toJson(),
    );
    unawaited(persist());
    notifyListeners();
    return '${existed ? '已更新档案' : '已保存档案'}：${profile.id} · ${profile.name}';
  }

  ItemProfile cloneProfile(ItemRef ref) {
    final profile = snapshot.profiles[ref.id];
    if (profile == null) throw StateError('找不到档案：${ref.id}');
    final clone = profile.cloneWithId(snapshot.nextIdFor(profile));
    snapshot.profiles[clone.id] = clone;
    snapshot.addTransaction(
      action: 'clone_profile',
      ref: clone.ref,
      before: null,
      after: clone.toJson(),
    );
    unawaited(persist());
    notifyListeners();
    return clone;
  }

  String stockInActive() {
    final ref = activeRef;
    if (ref == null) return '没有当前物品。';
    return stockIn(
      ref,
      currentG: pendingWeight,
      locationId: pendingLocation ?? '',
      stockedOn: todayYyMmDd(),
    );
  }

  String stockIn(
    ItemRef ref, {
    double? currentG,
    int? currentQty,
    required String locationId,
    required String stockedOn,
  }) {
    final profile = snapshot.profiles[ref.id];
    if (profile == null) return '没有固定档案：${ref.id}';

    final before = snapshot.states[ref.id]?.toJson();
    final previous = snapshot.states[ref.id];
    final state = ItemState(
      itemId: ref.id,
      kind: ref.kind,
      status: StockStatus.inStock,
      locationId: locationId.trim().isNotEmpty
          ? locationId.trim().toUpperCase()
          : previous?.locationId ?? '',
      stockedOn: stockedOn.trim().isEmpty ? todayYyMmDd() : stockedOn.trim(),
      checkedOutOn: null,
      countedOn: todayYyMmDd(),
      stateUpdatedOn: todayYyMmDd(),
      stateUpdatedAt: DateTime.now(),
      currentG: currentG ?? previous?.currentG,
      currentQty: currentQty ?? previous?.currentQty,
    );

    if (profile.kind == ItemKind.spool && state.currentG == null) {
      return '耗材入库必须先扫重量或输入当前毛重。';
    }
    if (profile.kind == ItemKind.part &&
        state.currentQty == null &&
        state.currentG == null) {
      return '零件入库必须输入数量或总重量。';
    }

    final normalized = _normalizePartQuantity(profile, state);
    snapshot.states[ref.id] = normalized;
    pendingWeight = null;
    pendingLocation = null;
    snapshot.addTransaction(
      action: 'stock_in',
      ref: ref,
      before: before,
      after: normalized.toJson(),
    );
    unawaited(persist());
    notifyListeners();
    return '${profile.name} 已入库：${InventoryEntry(profile, normalized).stockText}。';
  }

  String checkoutActive() {
    final entry = activeEntry;
    if (entry == null || entry.state == null) return '当前物品尚未入库。';
    final before = entry.state!.toJson();
    final state = entry.state!.copyWith(
      status: StockStatus.checkedOut,
      checkedOutOn: todayYyMmDd(),
      stateUpdatedOn: todayYyMmDd(),
      stateUpdatedAt: DateTime.now(),
    );
    snapshot.states[entry.profile.id] = state;
    snapshot.addTransaction(
      action: 'checkout',
      ref: entry.ref,
      before: before,
      after: state.toJson(),
    );
    unawaited(persist());
    notifyListeners();
    return '${entry.profile.name} 已出库。';
  }

  String restoreActive() {
    final entry = activeEntry;
    if (entry == null || entry.state == null) return '当前物品没有库存状态。';
    final before = entry.state!.toJson();
    final state = entry.state!.copyWith(
      status: StockStatus.inStock,
      stateUpdatedOn: todayYyMmDd(),
      stateUpdatedAt: DateTime.now(),
    );
    snapshot.states[entry.profile.id] = state;
    snapshot.addTransaction(
      action: 'restore',
      ref: entry.ref,
      before: before,
      after: state.toJson(),
    );
    unawaited(persist());
    notifyListeners();
    return '${entry.profile.name} 已恢复在库。';
  }

  String importJson(String raw) {
    try {
      snapshot = InventorySnapshot.fromJson(
        jsonDecode(raw) as Map<String, Object?>,
      );
      activeRef = null;
      pendingWeight = null;
      pendingLocation = null;
      unawaited(persist());
      notifyListeners();
      return '已导入 ${snapshot.profiles.length} 个档案。';
    } catch (error) {
      return '导入失败：$error';
    }
  }

  String exportJson() {
    const encoder = JsonEncoder.withIndent('  ');
    return encoder.convert(snapshot.toJson());
  }

  String _handleWeight(double weight) {
    final entry = activeEntry;
    if (entry == null) {
      pendingWeight = weight;
      return '已收到重量 ${weight.g}g，继续扫物品码。';
    }
    if (entry.profile.kind == ItemKind.other) {
      pendingWeight = weight;
      return '已暂存重量 ${weight.g}g。其他物品不做余量计算。';
    }
    return stockIn(
      entry.ref,
      currentG: weight,
      locationId: entry.state?.locationId ?? pendingLocation ?? '',
      stockedOn:
          entry.state == null || entry.state!.status == StockStatus.checkedOut
          ? todayYyMmDd()
          : entry.state!.stockedOn,
    );
  }

  String _handleLocation(String location) {
    final entry = activeEntry;
    if (entry == null) {
      pendingLocation = location;
      return '已收到库位 $location，继续扫物品码。';
    }
    if (entry.state == null) {
      pendingLocation = location;
      return '${entry.profile.name} 尚未入库；库位 $location 会在入库时写入。';
    }
    final before = entry.state!.toJson();
    final state = entry.state!.copyWith(
      locationId: location,
      stateUpdatedOn: todayYyMmDd(),
      stateUpdatedAt: DateTime.now(),
    );
    snapshot.states[entry.profile.id] = state;
    snapshot.addTransaction(
      action: 'move',
      ref: entry.ref,
      before: before,
      after: state.toJson(),
    );
    pendingLocation = null;
    return '${entry.profile.name} 已绑定库位：$location。';
  }

  String _handleItemPayload(ParsedPayload parsed) {
    if (parsed.profile != null) {
      final existed = snapshot.profiles.containsKey(parsed.profile!.id);
      snapshot.profiles[parsed.profile!.id] = parsed.profile!;
      if (!existed) {
        snapshot.addTransaction(
          action: 'create_profile',
          ref: parsed.profile!.ref,
          before: null,
          after: parsed.profile!.toJson(),
        );
      }
    }

    final ref = parsed.ref;
    if (ref == null) return '无法识别物品码。';
    final profile = snapshot.profiles[ref.id];
    if (profile == null) return '找不到档案：${ref.payload}';
    activeRef = ref;

    if (pendingWeight != null && profile.kind != ItemKind.other) {
      final weight = pendingWeight!;
      return stockIn(
        ref,
        currentG: weight,
        locationId:
            pendingLocation ?? snapshot.states[ref.id]?.locationId ?? '',
        stockedOn: snapshot.states[ref.id]?.stockedOn ?? todayYyMmDd(),
      );
    }
    if (pendingLocation != null && snapshot.states[ref.id] != null) {
      final location = pendingLocation!;
      return _handleLocation(location);
    }

    final entry = snapshot.entryFor(ref)!;
    if (entry.state == null) {
      return '${profile.id} · ${profile.name} 已建档，尚未入库。';
    }
    return '${profile.id} · ${profile.name}：${entry.stockText}，库位 ${entry.locationText}。';
  }

  ItemState _normalizePartQuantity(ItemProfile profile, ItemState state) {
    if (profile.kind != ItemKind.part) return state;
    if (state.currentQty != null) return state;
    final unit = profile.unitWeightG;
    final total = state.currentG;
    if (unit == null || unit <= 0 || total == null) return state;
    return state.copyWith(currentQty: (total / unit).floor());
  }
}

class InventorySnapshot {
  InventorySnapshot({
    required this.deviceId,
    required this.profiles,
    required this.states,
    required this.transactions,
    required this.recentScanLog,
  });

  final String deviceId;
  final Map<String, ItemProfile> profiles;
  final Map<String, ItemState> states;
  final List<InventoryTransaction> transactions;
  final List<ScanLogEntry> recentScanLog;

  int get inStockCount => states.values
      .where((state) => state.status == StockStatus.inStock)
      .length;

  List<InventoryEntry> get entries {
    final result = profiles.values
        .where((profile) => profile.kind != ItemKind.location)
        .map((profile) => InventoryEntry(profile, states[profile.id]))
        .toList();
    result.sort((a, b) => a.profile.id.compareTo(b.profile.id));
    return result;
  }

  InventoryEntry? entryFor(ItemRef ref) {
    final profile = profiles[ref.id];
    if (profile == null) return null;
    return InventoryEntry(profile, states[ref.id]);
  }

  factory InventorySnapshot.demo() {
    final now = DateTime.now();
    final profiles = <String, ItemProfile>{};
    final states = <String, ItemState>{};

    void add(ItemProfile profile, ItemState? state) {
      profiles[profile.id] = profile;
      if (state != null) states[profile.id] = state;
    }

    add(
      ItemProfile(
        id: 'PLA-BLK-001',
        kind: ItemKind.spool,
        name: '黑色PLA',
        brand: 'Bambu',
        material: 'PLA',
        color: 'black',
        fullG: 1200,
        tareG: 200,
        netG: 1000,
        createdOn: '260613',
        profileUpdatedAt: now,
      ),
      ItemState(
        itemId: 'PLA-BLK-001',
        kind: ItemKind.spool,
        status: StockStatus.inStock,
        locationId: 'RACK-A01',
        stockedOn: '260613',
        stateUpdatedOn: '260613',
        stateUpdatedAt: now,
        currentG: 712.4,
      ),
    );
    add(
      ItemProfile(
        id: 'PETG-CLR-001',
        kind: ItemKind.spool,
        name: '透明PETG',
        brand: 'eSUN',
        material: 'PETG',
        color: 'clear',
        fullG: 1200,
        tareG: 185,
        netG: 1000,
        createdOn: '260613',
        profileUpdatedAt: now,
      ),
      ItemState(
        itemId: 'PETG-CLR-001',
        kind: ItemKind.spool,
        status: StockStatus.inStock,
        locationId: '',
        stockedOn: '260613',
        stateUpdatedOn: '260613',
        stateUpdatedAt: now,
        currentG: 244,
      ),
    );
    add(
      ItemProfile(
        id: 'M3-SCREW-8-BLK',
        kind: ItemKind.part,
        name: 'M3x8黑色圆头螺丝',
        category: 'screw',
        spec: 'M3x8',
        color: 'black',
        unitWeightG: 0.42,
        packageQty: 100,
        createdOn: '260613',
        profileUpdatedAt: now,
      ),
      ItemState(
        itemId: 'M3-SCREW-8-BLK',
        kind: ItemKind.part,
        status: StockStatus.inStock,
        locationId: 'BOX-B03',
        stockedOn: '260613',
        stateUpdatedOn: '260613',
        stateUpdatedAt: now,
        currentQty: 800,
      ),
    );
    add(
      ItemProfile(
        id: 'TOOL-001',
        kind: ItemKind.other,
        name: '热风枪',
        note: '喷嘴套装',
        createdOn: '260613',
        profileUpdatedAt: now,
      ),
      null,
    );

    return InventorySnapshot(
      deviceId: 'phone-a',
      profiles: profiles,
      states: states,
      transactions: [],
      recentScanLog: [],
    );
  }

  factory InventorySnapshot.fromJson(Map<String, Object?> json) {
    if (json['schema'] == 2) {
      return InventorySnapshot(
        deviceId: json['device_id'] as String? ?? 'phone-a',
        profiles: (json['profiles'] as Map? ?? {}).map(
          (key, value) => MapEntry(
            key as String,
            ItemProfile.fromJson(value as Map<String, Object?>),
          ),
        ),
        states: (json['states'] as Map? ?? {}).map(
          (key, value) => MapEntry(
            key as String,
            ItemState.fromJson(value as Map<String, Object?>),
          ),
        ),
        transactions: (json['transactions'] as List? ?? [])
            .map(
              (item) =>
                  InventoryTransaction.fromJson(item as Map<String, Object?>),
            )
            .toList(),
        recentScanLog: (json['recentScanLog'] as List? ?? [])
            .map((item) => ScanLogEntry.fromJson(item as Map<String, Object?>))
            .toList(),
      );
    }
    return InventorySnapshot.fromLegacy(json);
  }

  factory InventorySnapshot.fromLegacy(Map<String, Object?> json) {
    final snapshot = InventorySnapshot.demo();
    snapshot.profiles.clear();
    snapshot.states.clear();
    for (final item in (json['spools'] as List? ?? [])) {
      final map = item as Map<String, Object?>;
      final id = map['id'] as String? ?? '';
      if (id.isEmpty) continue;
      snapshot.profiles[id] = ItemProfile(
        id: id,
        kind: ItemKind.spool,
        name: map['name'] as String? ?? id,
        brand: map['brand'] as String? ?? '',
        material: map['material'] as String? ?? '',
        color: map['color'] as String? ?? '',
        tareG: (map['emptyWeight'] as num?)?.toDouble(),
        createdOn: todayYyMmDd(),
        profileUpdatedAt: DateTime.now(),
      );
      snapshot.states[id] = ItemState(
        itemId: id,
        kind: ItemKind.spool,
        status: StockStatus.inStock,
        locationId: map['location'] as String? ?? '',
        stockedOn: todayYyMmDd(),
        stateUpdatedOn: todayYyMmDd(),
        stateUpdatedAt: DateTime.now(),
        currentG: (map['currentWeight'] as num?)?.toDouble(),
      );
    }
    for (final item in (json['parts'] as List? ?? [])) {
      final map = item as Map<String, Object?>;
      final id = map['id'] as String? ?? '';
      if (id.isEmpty) continue;
      snapshot.profiles[id] = ItemProfile(
        id: id,
        kind: ItemKind.part,
        name: map['name'] as String? ?? id,
        unitWeightG: (map['unitWeight'] as num?)?.toDouble(),
        createdOn: todayYyMmDd(),
        profileUpdatedAt: DateTime.now(),
      );
      snapshot.states[id] = ItemState(
        itemId: id,
        kind: ItemKind.part,
        status: StockStatus.inStock,
        locationId: map['location'] as String? ?? '',
        stockedOn: todayYyMmDd(),
        stateUpdatedOn: todayYyMmDd(),
        stateUpdatedAt: DateTime.now(),
        currentQty: (map['quantity'] as num?)?.toInt(),
      );
    }
    return snapshot;
  }

  Map<String, Object?> toJson() => {
    'schema': 2,
    'device_id': deviceId,
    'profiles': profiles.map((key, value) => MapEntry(key, value.toJson())),
    'states': states.map((key, value) => MapEntry(key, value.toJson())),
    'transactions': transactions.map((item) => item.toJson()).toList(),
    'recentScanLog': recentScanLog.map((item) => item.toJson()).toList(),
  };

  void addTransaction({
    required String action,
    required ItemRef ref,
    required Object? before,
    required Object? after,
  }) {
    transactions.add(
      InventoryTransaction.create(
        deviceId: deviceId,
        action: action,
        ref: ref,
        before: before,
        after: after,
      ),
    );
  }

  void addScanLog(String payload) {
    recentScanLog.insert(
      0,
      ScanLogEntry(payload: payload, createdAt: DateTime.now()),
    );
    if (recentScanLog.length > 6) {
      recentScanLog.removeRange(6, recentScanLog.length);
    }
  }

  String nextIdFor(ItemProfile profile) {
    final prefix = profile.id.replaceFirst(RegExp(r'-\d+$'), '');
    for (var i = 1; i < 10000; i += 1) {
      final id = '$prefix-${i.toString().padLeft(3, '0')}';
      if (!profiles.containsKey(id)) return id;
    }
    throw StateError('无法生成可用 ID');
  }
}

class InventoryEntry {
  InventoryEntry(this.profile, this.state);

  final ItemProfile profile;
  final ItemState? state;

  ItemRef get ref => profile.ref;
  double? get currentG => state?.currentG;
  int? get currentQty => state?.currentQty;

  String get statusText {
    if (state == null) return '未入库';
    return state!.status.label;
  }

  String get locationText {
    final location = state?.locationId ?? '';
    return location.isEmpty ? '未绑定' : location;
  }

  String get stockText {
    if (state == null) return '未入库';
    if (state!.status == StockStatus.checkedOut) return '已出库';
    if (state!.status == StockStatus.archived) return '已归档';
    return switch (profile.kind) {
      ItemKind.spool => usableWeight == null ? '未称重' : '${usableWeight!.g}g',
      ItemKind.part => currentQty == null ? '未记录数量' : '$currentQty 件',
      ItemKind.other => '在库',
      ItemKind.location => '库位',
    };
  }

  double? get usableWeight {
    final current = state?.currentG;
    final tare = profile.tareG;
    if (current == null || tare == null) return null;
    return round1(current - tare);
  }

  bool get isLowStock {
    if (state?.status != StockStatus.inStock) return false;
    return switch (profile.kind) {
      ItemKind.spool => usableWeight != null && usableWeight! < 100,
      ItemKind.part => currentQty != null && currentQty! < 10,
      ItemKind.other || ItemKind.location => false,
    };
  }

  String get detailText {
    final fixed = profile.fixedText;
    final stocked = state?.stockedOn ?? '未入库';
    return '$fixed · $statusText · 库位 $locationText · 入库 $stocked';
  }

  String get searchText =>
      '${profile.id} ${profile.name} ${profile.fixedText} $locationText $statusText'
          .toLowerCase();
}

enum ItemKind {
  spool('耗材', 'spool', Icons.category),
  part('零件', 'part', Icons.hardware),
  other('其他', 'other', Icons.extension),
  location('库位', 'location', Icons.place);

  const ItemKind(this.label, this.payloadType, this.icon);

  final String label;
  final String payloadType;
  final IconData icon;
}

enum StockStatus {
  inStock('在库', 'in_stock'),
  checkedOut('已出库', 'checked_out'),
  archived('已归档', 'archived');

  const StockStatus(this.label, this.value);

  final String label;
  final String value;
}

class ItemRef {
  const ItemRef(this.kind, this.id);

  final ItemKind kind;
  final String id;
  String get payload => '${kind.payloadType}:$id';
}

class ItemProfile {
  ItemProfile({
    required this.id,
    required this.kind,
    required this.name,
    this.brand = '',
    this.material = '',
    this.color = '',
    this.fullG,
    this.tareG,
    this.netG,
    this.category = '',
    this.spec = '',
    this.unitWeightG,
    this.packageQty,
    this.note = '',
    required this.createdOn,
    required this.profileUpdatedAt,
  });

  final String id;
  final ItemKind kind;
  final String name;
  final String brand;
  final String material;
  final String color;
  final double? fullG;
  final double? tareG;
  final double? netG;
  final String category;
  final String spec;
  final double? unitWeightG;
  final int? packageQty;
  final String note;
  final String createdOn;
  final DateTime profileUpdatedAt;

  ItemRef get ref => ItemRef(kind, id);

  String get fixedText {
    return switch (kind) {
      ItemKind.spool =>
        '${brandText(brand)} · ${emptyFallback(material, '未填材料')} · ${emptyFallback(color, '未填颜色')} · 空盘 ${tareG?.g ?? '?'}g',
      ItemKind.part =>
        '${emptyFallback(category, '未分类')} · ${emptyFallback(spec, '未填规格')} · 单件 ${unitWeightG?.g ?? '?'}g',
      ItemKind.other => note.isEmpty ? '简单登记' : note,
      ItemKind.location => note.isEmpty ? '库位' : note,
    };
  }

  String get labelPayload {
    final fields = <String, String>{
      'type': kind.payloadType,
      'id': id,
      'name': name,
    };
    if (kind == ItemKind.spool) {
      fields.addAll({
        'brand': brand,
        'material': material,
        'color': color,
        if (fullG != null) 'full_g': fullG!.g,
        if (tareG != null) 'tare_g': tareG!.g,
        if (netG != null) 'net_g': netG!.g,
      });
    } else if (kind == ItemKind.part) {
      fields.addAll({
        'category': category,
        'spec': spec,
        if (color.isNotEmpty) 'color': color,
        if (unitWeightG != null) 'unit_weight_g': unitWeightG!.g,
        if (packageQty != null) 'package_qty': '$packageQty',
      });
    } else if (kind == ItemKind.other) {
      if (note.isNotEmpty) fields['note'] = note;
    } else if (kind == ItemKind.location) {
      if (note.isNotEmpty) fields['note'] = note;
    }
    fields['created_on'] = createdOn;
    return buildMsiPayload(fields);
  }

  ItemProfile cloneWithId(String newId) {
    return ItemProfile(
      id: newId,
      kind: kind,
      name: name,
      brand: brand,
      material: material,
      color: color,
      fullG: fullG,
      tareG: tareG,
      netG: netG,
      category: category,
      spec: spec,
      unitWeightG: unitWeightG,
      packageQty: packageQty,
      note: note,
      createdOn: todayYyMmDd(),
      profileUpdatedAt: DateTime.now(),
    );
  }

  factory ItemProfile.fromJson(Map<String, Object?> json) {
    return ItemProfile(
      id: json['id'] as String? ?? '',
      kind: itemKindFromPayload(json['type'] as String? ?? 'spool'),
      name: json['name'] as String? ?? '',
      brand: json['brand'] as String? ?? '',
      material: json['material'] as String? ?? '',
      color: json['color'] as String? ?? '',
      fullG: (json['full_g'] as num?)?.toDouble(),
      tareG: (json['tare_g'] as num?)?.toDouble(),
      netG: (json['net_g'] as num?)?.toDouble(),
      category: json['category'] as String? ?? '',
      spec: json['spec'] as String? ?? '',
      unitWeightG: (json['unit_weight_g'] as num?)?.toDouble(),
      packageQty: (json['package_qty'] as num?)?.toInt(),
      note: json['note'] as String? ?? '',
      createdOn: json['created_on'] as String? ?? todayYyMmDd(),
      profileUpdatedAt:
          DateTime.tryParse(json['profile_updated_at'] as String? ?? '') ??
          DateTime.now(),
    );
  }

  Map<String, Object?> toJson() => {
    'id': id,
    'type': kind.payloadType,
    'name': name,
    'brand': brand,
    'material': material,
    'color': color,
    'full_g': fullG,
    'tare_g': tareG,
    'net_g': netG,
    'category': category,
    'spec': spec,
    'unit_weight_g': unitWeightG,
    'package_qty': packageQty,
    'note': note,
    'created_on': createdOn,
    'profile_updated_at': profileUpdatedAt.toIso8601String(),
  };
}

class ItemState {
  ItemState({
    required this.itemId,
    required this.kind,
    required this.status,
    required this.locationId,
    required this.stockedOn,
    this.checkedOutOn,
    this.countedOn,
    required this.stateUpdatedOn,
    required this.stateUpdatedAt,
    this.currentG,
    this.currentQty,
  });

  final String itemId;
  final ItemKind kind;
  final StockStatus status;
  final String locationId;
  final String stockedOn;
  final String? checkedOutOn;
  final String? countedOn;
  final String stateUpdatedOn;
  final DateTime stateUpdatedAt;
  final double? currentG;
  final int? currentQty;

  ItemState copyWith({
    StockStatus? status,
    String? locationId,
    String? stockedOn,
    String? checkedOutOn,
    String? countedOn,
    String? stateUpdatedOn,
    DateTime? stateUpdatedAt,
    double? currentG,
    int? currentQty,
  }) {
    return ItemState(
      itemId: itemId,
      kind: kind,
      status: status ?? this.status,
      locationId: locationId ?? this.locationId,
      stockedOn: stockedOn ?? this.stockedOn,
      checkedOutOn: checkedOutOn,
      countedOn: countedOn ?? this.countedOn,
      stateUpdatedOn: stateUpdatedOn ?? this.stateUpdatedOn,
      stateUpdatedAt: stateUpdatedAt ?? this.stateUpdatedAt,
      currentG: currentG ?? this.currentG,
      currentQty: currentQty ?? this.currentQty,
    );
  }

  factory ItemState.fromJson(Map<String, Object?> json) {
    return ItemState(
      itemId: json['item_id'] as String? ?? '',
      kind: itemKindFromPayload(json['type'] as String? ?? 'spool'),
      status: stockStatusFromValue(json['status'] as String? ?? 'in_stock'),
      locationId: json['location_id'] as String? ?? '',
      stockedOn: json['stocked_on'] as String? ?? todayYyMmDd(),
      checkedOutOn: json['checked_out_on'] as String?,
      countedOn: json['counted_on'] as String?,
      stateUpdatedOn: json['state_updated_on'] as String? ?? todayYyMmDd(),
      stateUpdatedAt:
          DateTime.tryParse(json['state_updated_at'] as String? ?? '') ??
          DateTime.now(),
      currentG: (json['current_g'] as num?)?.toDouble(),
      currentQty: (json['current_qty'] as num?)?.toInt(),
    );
  }

  Map<String, Object?> toJson() => {
    'item_id': itemId,
    'type': kind.payloadType,
    'status': status.value,
    'location_id': locationId,
    'stocked_on': stockedOn,
    'checked_out_on': checkedOutOn,
    'counted_on': countedOn,
    'state_updated_on': stateUpdatedOn,
    'state_updated_at': stateUpdatedAt.toIso8601String(),
    'current_g': currentG,
    'current_qty': currentQty,
  };
}

class InventoryTransaction {
  InventoryTransaction({
    required this.txId,
    required this.deviceId,
    required this.action,
    required this.itemId,
    required this.itemKind,
    required this.createdOn,
    required this.createdAt,
    required this.timezone,
    required this.before,
    required this.after,
  });

  final String txId;
  final String deviceId;
  final String action;
  final String itemId;
  final ItemKind itemKind;
  final String createdOn;
  final String createdAt;
  final String timezone;
  final Object? before;
  final Object? after;

  factory InventoryTransaction.create({
    required String deviceId,
    required String action,
    required ItemRef ref,
    required Object? before,
    required Object? after,
  }) {
    final now = DateTime.now();
    final stamp = now.microsecondsSinceEpoch.toRadixString(36);
    return InventoryTransaction(
      txId: '$deviceId-$stamp',
      deviceId: deviceId,
      action: action,
      itemId: ref.id,
      itemKind: ref.kind,
      createdOn: yyMmDd(now),
      createdAt: isoWithOffset(now),
      timezone: now.timeZoneName,
      before: before,
      after: after,
    );
  }

  factory InventoryTransaction.fromJson(Map<String, Object?> json) {
    return InventoryTransaction(
      txId: json['tx_id'] as String? ?? '',
      deviceId: json['device_id'] as String? ?? '',
      action: json['action'] as String? ?? '',
      itemId: json['item_id'] as String? ?? '',
      itemKind: itemKindFromPayload(json['type'] as String? ?? 'spool'),
      createdOn: json['created_on'] as String? ?? todayYyMmDd(),
      createdAt: json['created_at'] as String? ?? isoWithOffset(DateTime.now()),
      timezone: json['timezone'] as String? ?? DateTime.now().timeZoneName,
      before: json['before'],
      after: json['after'],
    );
  }

  Map<String, Object?> toJson() => {
    'tx_id': txId,
    'device_id': deviceId,
    'action': action,
    'item_id': itemId,
    'type': itemKind.payloadType,
    'created_on': createdOn,
    'created_at': createdAt,
    'timezone': timezone,
    'before': before,
    'after': after,
  };
}

class ScanLogEntry {
  ScanLogEntry({required this.payload, required this.createdAt});

  final String payload;
  final DateTime createdAt;

  factory ScanLogEntry.fromJson(Map<String, Object?> json) {
    return ScanLogEntry(
      payload: json['payload'] as String? ?? '',
      createdAt:
          DateTime.tryParse(json['createdAt'] as String? ?? '') ??
          DateTime.now(),
    );
  }

  Map<String, Object?> toJson() => {
    'payload': payload,
    'createdAt': createdAt.toIso8601String(),
  };
}

class ParsedPayload {
  ParsedPayload({
    required this.type,
    required this.value,
    required this.fields,
    this.profile,
    this.isMsi = false,
  });

  final String type;
  final String value;
  final Map<String, String> fields;
  final ItemProfile? profile;
  final bool isMsi;

  ItemKind? get kind {
    if (type == 'spool' || type == 'part' || type == 'other') {
      return itemKindFromPayload(type);
    }
    return null;
  }

  ItemRef? get ref {
    final payloadKind = kind;
    if (payloadKind == null || value.isEmpty) return null;
    return ItemRef(payloadKind, value.toUpperCase());
  }

  double? get weightG {
    if (type == 'weight') {
      return double.tryParse(fields['value_g'] ?? value);
    }
    return null;
  }
}

ParsedPayload parsePayload(String raw) {
  final text = raw.trim();
  if (text.toLowerCase().startsWith('msi:v1;')) {
    final fields = <String, String>{};
    for (final token in text.split(';').skip(1)) {
      final index = token.indexOf('=');
      if (index < 1) continue;
      fields[token.substring(0, index).trim().toLowerCase()] = token
          .substring(index + 1)
          .trim();
    }
    final type = fields['type']?.toLowerCase() ?? 'unknown';
    final value = switch (type) {
      'weight' => fields['value_g'] ?? '',
      _ => fields['id'] ?? '',
    };
    final profile = _profileFromMsi(type, fields);
    return ParsedPayload(
      type: type,
      value: value,
      fields: fields,
      profile: profile,
      isMsi: true,
    );
  }

  final index = text.indexOf(':');
  if (index < 1) {
    return ParsedPayload(type: 'unknown', value: text, fields: const {});
  }
  final type = text.substring(0, index).trim().toLowerCase();
  final value = text.substring(index + 1).trim();
  return ParsedPayload(
    type: type,
    value: value,
    fields: type == 'weight' ? {'value_g': value} : {'id': value},
  );
}

ItemProfile? _profileFromMsi(String type, Map<String, String> fields) {
  if (type != 'spool' &&
      type != 'part' &&
      type != 'other' &&
      type != 'location') {
    return null;
  }
  final id = fields['id']?.trim().toUpperCase() ?? '';
  if (id.isEmpty) return null;
  return ItemProfile(
    id: id,
    kind: itemKindFromPayload(type),
    name: fields['name']?.trim() ?? id,
    brand: fields['brand']?.trim() ?? '',
    material: fields['material']?.trim() ?? '',
    color: fields['color']?.trim() ?? '',
    fullG: double.tryParse(fields['full_g'] ?? ''),
    tareG: double.tryParse(fields['tare_g'] ?? ''),
    netG: double.tryParse(fields['net_g'] ?? ''),
    category: fields['category']?.trim() ?? '',
    spec: fields['spec']?.trim() ?? '',
    unitWeightG: double.tryParse(fields['unit_weight_g'] ?? ''),
    packageQty: int.tryParse(fields['package_qty'] ?? ''),
    note: fields['note']?.trim() ?? '',
    createdOn: fields['created_on']?.trim() ?? todayYyMmDd(),
    profileUpdatedAt: DateTime.now(),
  );
}

class StatusCell {
  const StatusCell(this.label, this.value);

  final String label;
  final String value;
}

class _StatusGrid extends StatelessWidget {
  const _StatusGrid({required this.cells});

  final List<StatusCell> cells;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 58,
      child: Row(
        children: [
          for (final cell in cells) ...[
            Expanded(child: _StatusTile(cell: cell)),
            if (cell != cells.last) const SizedBox(width: 6),
          ],
        ],
      ),
    );
  }
}

class _StatusTile extends StatelessWidget {
  const _StatusTile({required this.cell});

  final StatusCell cell;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(cell.label, style: Theme.of(context).textTheme.labelSmall),
            const SizedBox(height: 2),
            Text(
              cell.value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800),
            ),
          ],
        ),
      ),
    );
  }
}

class _ManualPayloadBar extends StatefulWidget {
  const _ManualPayloadBar({required this.onSubmit});

  final void Function(String payload) onSubmit;

  @override
  State<_ManualPayloadBar> createState() => _ManualPayloadBarState();
}

class _ManualPayloadBarState extends State<_ManualPayloadBar> {
  final controller = TextEditingController();

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: controller,
            decoration: const InputDecoration(
              labelText: '手动补录',
              hintText: 'msi:v1;type=spool;... / weight:712.4',
              border: OutlineInputBorder(),
              isDense: true,
            ),
            onSubmitted: _submit,
          ),
        ),
        const SizedBox(width: 8),
        FilledButton(
          onPressed: () => _submit(controller.text),
          child: const Text('处理'),
        ),
      ],
    );
  }

  void _submit(String payload) {
    if (payload.trim().isEmpty) return;
    widget.onSubmit(payload);
    controller.clear();
  }
}

class _ResultCard extends StatelessWidget {
  const _ResultCard({required this.text, this.compact = false});

  final String text;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: EdgeInsets.all(compact ? 10 : 12),
        child: Row(
          children: [
            Icon(
              Icons.info_outline,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                text,
                maxLines: compact ? 2 : 3,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StockBadge extends StatelessWidget {
  const _StockBadge({required this.entry});

  final InventoryEntry entry;

  @override
  Widget build(BuildContext context) {
    return Badge(
      label: Text(entry.isLowStock ? '${entry.stockText} 低' : entry.stockText),
      backgroundColor: entry.state?.status == StockStatus.checkedOut
          ? Theme.of(context).colorScheme.secondary
          : entry.isLowStock
          ? Theme.of(context).colorScheme.error
          : Theme.of(context).colorScheme.primary,
    );
  }
}

class _TextField extends StatelessWidget {
  const _TextField({
    required this.controller,
    required this.label,
    this.number = false,
    this.requiredText = false,
  });

  final TextEditingController controller;
  final String label;
  final bool number;
  final bool requiredText;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextFormField(
        controller: controller,
        keyboardType: number
            ? const TextInputType.numberWithOptions(decimal: true)
            : TextInputType.text,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
        validator: (value) {
          final text = value?.trim() ?? '';
          if (requiredText && text.isEmpty) return '$label 不能为空';
          if (number && text.isNotEmpty && double.tryParse(text) == null) {
            return '$label 必须是数字';
          }
          return null;
        },
      ),
    );
  }
}

class _LabelPreview extends StatelessWidget {
  const _LabelPreview({required this.profile});

  final ItemProfile? profile;

  @override
  Widget build(BuildContext context) {
    final payload = profile?.labelPayload ?? '填写 ID 和名称后生成标签';
    return Card(
      child: ListTile(
        leading: const Icon(Icons.qr_code_2),
        title: const Text('标签内容'),
        subtitle: Text(payload),
      ),
    );
  }
}

Future<void> showCatalogScannerSheet(
  BuildContext context, {
  required String title,
  required String Function(String payload) onPayload,
}) {
  return showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) =>
        CatalogScannerSheet(title: title, onPayload: onPayload),
  );
}

class CatalogScannerSheet extends StatefulWidget {
  const CatalogScannerSheet({
    required this.title,
    required this.onPayload,
    super.key,
  });

  final String title;
  final String Function(String payload) onPayload;

  @override
  State<CatalogScannerSheet> createState() => _CatalogScannerSheetState();
}

class _CatalogScannerSheetState extends State<CatalogScannerSheet>
    with WidgetsBindingObserver {
  late final MobileScannerController scanner;
  String status = '扫 msi:v1 档案、重量码或库位码填当前表单。';
  String lastPayload = '';
  DateTime lastPayloadAt = DateTime.fromMillisecondsSinceEpoch(0);
  bool scannerBusy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    scanner = MobileScannerController(
      autoStart: false,
      detectionSpeed: DetectionSpeed.normal,
      formats: const [BarcodeFormat.qrCode],
      facing: CameraFacing.back,
    );
    scanner.addListener(_handleScannerState);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    scanner.removeListener(_handleScannerState);
    unawaited(scanner.dispose());
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden) {
      unawaited(_stopScanner(showMessage: false));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        bottom: 16 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(widget.title, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: SizedBox(
              height: 150,
              child: MobileScanner(
                controller: scanner,
                fit: BoxFit.cover,
                onDetect: _onDetect,
                errorBuilder: (context, error) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(_cameraErrorText(error)),
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(child: Text(status)),
              TextButton.icon(
                onPressed: scannerBusy ? null : _startScanner,
                icon: const Icon(Icons.play_arrow),
                label: Text(scannerBusy ? '启动中' : '预览扫码'),
              ),
              IconButton.filledTonal(
                tooltip: '手电筒',
                onPressed: scannerBusy ? null : _toggleTorch,
                icon: const Icon(Icons.flashlight_on),
              ),
              IconButton.filledTonal(
                tooltip: '原生扫码',
                onPressed: scannerBusy ? null : _scanWithNativeScanner,
                icon: const Icon(Icons.document_scanner),
              ),
            ],
          ),
          _ManualPayloadBar(onSubmit: _handle),
        ],
      ),
    );
  }

  void _onDetect(BarcodeCapture capture) {
    final payload = capture.barcodes
        .map((barcode) => barcode.rawValue)
        .whereType<String>()
        .firstOrNull;
    if (payload == null || payload.trim().isEmpty) return;
    final now = DateTime.now();
    if (payload == lastPayload &&
        now.difference(lastPayloadAt).inMilliseconds < 1400) {
      return;
    }
    lastPayload = payload;
    lastPayloadAt = now;
    _handle(payload);
  }

  void _handle(String payload) {
    final result = widget.onPayload(payload);
    HapticFeedback.mediumImpact();
    SystemSound.play(SystemSoundType.click);
    setState(() => status = result);
  }

  Future<void> _startScanner() async {
    if (scannerBusy || scanner.value.isRunning) return;
    setState(() {
      scannerBusy = true;
      status = '正在打开相机...';
    });
    try {
      await scanner.start(cameraDirection: CameraFacing.back);
      final error = scanner.value.error;
      if (!mounted) return;
      setState(() {
        status = error == null ? '扫码中：对准二维码。' : _cameraErrorText(error);
      });
    } catch (error) {
      if (mounted) setState(() => status = _cameraErrorText(error));
    } finally {
      if (mounted) setState(() => scannerBusy = false);
    }
  }

  Future<void> _scanWithNativeScanner() async {
    if (scannerBusy) return;
    setState(() {
      scannerBusy = true;
      status = '正在打开原生扫码...';
    });
    try {
      await _stopScannerForNativeLaunch();
      final payload = await NativeScanner.scanQr();
      if (!mounted) return;
      if (payload == null || payload.trim().isEmpty) {
        setState(() => status = '已取消扫码。');
        return;
      }
      _handle(payload);
    } catch (error) {
      if (mounted) setState(() => status = _cameraErrorText(error));
    } finally {
      if (mounted) setState(() => scannerBusy = false);
    }
  }

  Future<void> _stopScannerForNativeLaunch() async {
    try {
      if (scanner.value.isRunning) await scanner.stop();
    } catch (_) {
      // Launching the native scanner is still useful if inline stop fails.
    }
  }

  Future<void> _stopScanner({bool showMessage = true}) async {
    if (scannerBusy) return;
    setState(() => scannerBusy = true);
    try {
      await scanner.stop();
      if (mounted && showMessage) {
        setState(() => status = '扫码已停止。');
      }
    } catch (error) {
      if (mounted) setState(() => status = _cameraErrorText(error));
    } finally {
      if (mounted) setState(() => scannerBusy = false);
    }
  }

  Future<void> _toggleTorch() async {
    if (scannerBusy || !scanner.value.isRunning) {
      setState(() => status = '先点“开始扫码”，相机启动后才能打开手电筒。');
      return;
    }
    try {
      await scanner.toggleTorch();
    } catch (error) {
      if (mounted) setState(() => status = _cameraErrorText(error));
    }
  }

  void _handleScannerState() {
    final error = scanner.value.error;
    if (error == null || !mounted) return;
    setState(() => status = _cameraErrorText(error));
  }
}

String _cameraErrorText(Object error) {
  if (error is MobileScannerException) {
    final detail = error.errorDetails?.message;
    if (detail != null && detail.trim().isNotEmpty) {
      return '相机不可用：$detail';
    }
    return '相机不可用：${error.errorCode.name}';
  }
  if (error is PlatformException) {
    return '相机不可用：${error.message ?? error.code}';
  }
  return '相机不可用：$error';
}

String buildMsiPayload(Map<String, String> fields) {
  final buffer = StringBuffer('msi:v1');
  fields.forEach((key, value) {
    if (value.trim().isEmpty) return;
    buffer.write(';');
    buffer.write(key);
    buffer.write('=');
    buffer.write(value.trim());
  });
  return buffer.toString();
}

ItemKind itemKindFromPayload(String value) {
  return ItemKind.values.firstWhere(
    (kind) => kind.payloadType == value,
    orElse: () => ItemKind.spool,
  );
}

StockStatus stockStatusFromValue(String value) {
  return StockStatus.values.firstWhere(
    (status) => status.value == value,
    orElse: () => StockStatus.inStock,
  );
}

String todayYyMmDd() => yyMmDd(DateTime.now());

String yyMmDd(DateTime date) {
  final year = (date.year % 100).toString().padLeft(2, '0');
  final month = date.month.toString().padLeft(2, '0');
  final day = date.day.toString().padLeft(2, '0');
  return '$year$month$day';
}

String isoWithOffset(DateTime date) {
  final local = date.toLocal();
  final offset = local.timeZoneOffset;
  final sign = offset.isNegative ? '-' : '+';
  final absolute = offset.abs();
  final hours = absolute.inHours.toString().padLeft(2, '0');
  final minutes = (absolute.inMinutes % 60).toString().padLeft(2, '0');
  final y = local.year.toString().padLeft(4, '0');
  final m = local.month.toString().padLeft(2, '0');
  final d = local.day.toString().padLeft(2, '0');
  final hh = local.hour.toString().padLeft(2, '0');
  final mm = local.minute.toString().padLeft(2, '0');
  final ss = local.second.toString().padLeft(2, '0');
  return '$y-$m-${d}T$hh:$mm:$ss$sign$hours:$minutes';
}

double round1(double value) => (value * 10).round() / 10;

String emptyFallback(String value, String fallback) =>
    value.trim().isEmpty ? fallback : value.trim();

String brandText(String value) => emptyFallback(value, '未填品牌');

extension NullableDoubleParse on double? {
  double get orZero => this ?? 0;
}

extension CompactNumberText on num {
  String get g => this == round() ? round().toString() : toStringAsFixed(1);
}

extension FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
