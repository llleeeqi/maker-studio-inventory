import {
  archiveCatalogItem,
  cloneCatalogForm,
  createEmptyCatalogForm,
  formFromItem,
  generateCatalogId,
  saveCatalogItem,
} from "../core/catalog.js";
import {
  getStockInfo,
  listItems,
} from "../core/inventory.js";
import { countLowStock, filterInventory } from "../core/filters.js";
import { renderQrSvg } from "./qr.js";
import { createScannerPort, registerScannerBridge } from "../core/scanner-port.js";
import { loadState, resetState, saveState } from "./storage.js";
import { exportSnapshot, importSnapshotText, makeSnapshotFilename } from "../core/snapshot.js";
import { mergeStates, previewMergeStates } from "../core/merge.js";
import {
  applyScanPayload,
  createScanSession,
  resetSession,
  SCAN_MODES,
  setSessionMode,
} from "../core/workflow.js";

let state = loadState();
let scanSession = createScanSession();
let scanLog = [];
const scannerPort = createScannerPort(handlePayload, {
  onError: (error) => setResult(error.message),
});

const els = {
  tabs: document.querySelectorAll(".tab"),
  views: document.querySelectorAll(".view"),
  modes: document.querySelectorAll(".mode"),
  resetDemo: document.querySelector("#resetDemo"),
  storageStatus: document.querySelector("#storageStatus"),
  scanText: document.querySelector("#scanText"),
  scanSubmit: document.querySelector("#scanSubmit"),
  scanModeLabel: document.querySelector("#scanModeLabel"),
  pendingWeight: document.querySelector("#pendingWeight"),
  pendingItem: document.querySelector("#pendingItem"),
  pendingLocation: document.querySelector("#pendingLocation"),
  scanResult: document.querySelector("#scanResult"),
  scanLog: document.querySelector("#scanLog"),
  searchText: document.querySelector("#searchText"),
  inventoryTypeFilter: document.querySelector("#inventoryTypeFilter"),
  archiveStatusFilter: document.querySelector("#archiveStatusFilter"),
  lowStockOnly: document.querySelector("#lowStockOnly"),
  inventorySummary: document.querySelector("#inventorySummary"),
  inventoryList: document.querySelector("#inventoryList"),
  exportData: document.querySelector("#exportData"),
  exportFilteredData: document.querySelector("#exportFilteredData"),
  mergeImportData: document.querySelector("#mergeImportData"),
  replaceImportData: document.querySelector("#replaceImportData"),
  importFile: document.querySelector("#importFile"),
  importPreview: document.querySelector("#importPreview"),
  catalogForm: document.querySelector("#catalogForm"),
  catalogFormTitle: document.querySelector("#catalogFormTitle"),
  catalogType: document.querySelector("#catalogType"),
  catalogId: document.querySelector("#catalogId"),
  catalogName: document.querySelector("#catalogName"),
  catalogMessage: document.querySelector("#catalogMessage"),
  newCatalogItem: document.querySelector("#newCatalogItem"),
  cloneCatalogItem: document.querySelector("#cloneCatalogItem"),
  archiveCatalogItem: document.querySelector("#archiveCatalogItem"),
  generateCatalogId: document.querySelector("#generateCatalogId"),
  labelItem: document.querySelector("#labelItem"),
  makeItemQr: document.querySelector("#makeItemQr"),
  labelQr: document.querySelector("#labelQr"),
  labelTitle: document.querySelector("#labelTitle"),
  labelMeta: document.querySelector("#labelMeta"),
  transactionList: document.querySelector("#transactionList"),
};

boot();

function boot() {
  bindEvents();
  registerScannerBridge(window, scannerPort);
  setCatalogForm(createEmptyCatalogForm("spool"));
  renderAll();
}

function bindEvents() {
  els.tabs.forEach((tab) => {
    tab.addEventListener("click", () => switchView(tab.dataset.view));
  });

  els.modes.forEach((button) => {
    button.addEventListener("click", () => setScanMode(button.dataset.mode));
  });

  els.resetDemo.addEventListener("click", () => {
    state = resetState();
    clearPending();
    renderAll();
    setResult("样例数据已重置。");
  });

  els.scanSubmit.addEventListener("click", () => consumeScanText());
  els.scanText.addEventListener("keydown", (event) => {
    if (event.key === "Enter") consumeScanText();
  });

  document.querySelectorAll("[data-payload]").forEach((button) => {
    button.addEventListener("click", () => scannerPort.push(button.dataset.payload));
  });

  els.searchText.addEventListener("input", renderInventory);
  els.inventoryTypeFilter.addEventListener("change", renderInventory);
  els.archiveStatusFilter.addEventListener("change", renderInventory);
  els.lowStockOnly.addEventListener("change", renderInventory);
  els.exportData.addEventListener("click", exportData);
  els.exportFilteredData.addEventListener("click", exportFilteredData);
  els.mergeImportData.addEventListener("click", () => chooseImportFile("merge"));
  els.replaceImportData.addEventListener("click", () => chooseImportFile("replace"));
  els.importFile.addEventListener("change", importData);
  els.catalogForm.addEventListener("submit", saveCatalogForm);
  els.catalogType.addEventListener("change", () => setCatalogForm(createEmptyCatalogForm(els.catalogType.value)));
  els.newCatalogItem.addEventListener("click", () => setCatalogForm(createEmptyCatalogForm(els.catalogType.value)));
  els.cloneCatalogItem.addEventListener("click", cloneCurrentCatalogItem);
  els.archiveCatalogItem.addEventListener("click", toggleArchiveCurrentCatalogItem);
  els.generateCatalogId.addEventListener("click", fillGeneratedCatalogId);
  els.makeItemQr.addEventListener("click", makeItemQr);
}

function switchView(viewId) {
  els.tabs.forEach((tab) => tab.classList.toggle("active", tab.dataset.view === viewId));
  els.views.forEach((view) => view.classList.toggle("active", view.id === viewId));
}

function setScanMode(mode) {
  const result = setSessionMode(scanSession, mode);
  els.modes.forEach((button) => button.classList.toggle("active", button.dataset.mode === mode));
  renderScanState();
  setResult(result.message);
}

function consumeScanText() {
  const text = els.scanText.value;
  els.scanText.value = "";
  scannerPort.push(text);
}

function handlePayload(text) {
  try {
    const result = applyScanPayload(state, scanSession, text);
    addScanLog(result.payload.raw || "空内容");
    setResult(result.message);
    if (result.changed) saveState(state);
    renderScanState();
    if (result.changed) renderAll();
  } catch (error) {
    setResult(error.message);
  }
}

function renderAll() {
  els.storageStatus.textContent = `本地 ${state.spools.length + state.parts.length} 个物品，${state.transactions.length} 条流水`;
  renderScanState();
  renderInventory();
  renderLabelOptions();
  renderTransactions();
}

function renderScanState() {
  els.scanModeLabel.textContent = SCAN_MODES[scanSession.mode].label;
  els.pendingWeight.textContent = scanSession.pendingWeight == null ? "未扫描" : `${scanSession.pendingWeight}g`;
  els.pendingItem.textContent = scanSession.pendingItem == null ? "未扫描" : `${scanSession.pendingItem.itemType}:${scanSession.pendingItem.id}`;
  els.pendingLocation.textContent = scanSession.pendingLocation == null ? "未扫描" : scanSession.pendingLocation;
  renderScanLog();
}

function renderInventory() {
  const entries = listItems(state);
  const filteredEntries = filterInventory(entries, {
    query: els.searchText.value,
    itemType: els.inventoryTypeFilter.value,
    lowOnly: els.lowStockOnly.checked,
    archiveStatus: els.archiveStatusFilter.value,
  });
  els.inventoryList.innerHTML = "";
  els.inventorySummary.textContent = `显示 ${filteredEntries.length} / ${entries.length} 个物品，低库存 ${countLowStock(entries)} 个。`;

  for (const entry of filteredEntries) {
    const { itemType, item } = entry;
    const row = document.querySelector("#itemTemplate").content.firstElementChild.cloneNode(true);
    row.dataset.itemType = itemType;
    row.dataset.itemId = item.id;
    row.tabIndex = 0;
    const stock = getStockInfo(itemType, item);
    row.querySelector(".item-title").textContent = `${item.id} · ${item.name}`;
    row.querySelector(".item-meta").textContent = `${makeMeta(itemType, item)}${item.archived_at ? " · 已归档" : ""}`;
    const stockEl = row.querySelector(".item-stock");
    stockEl.textContent = stock.low ? `${stock.text} 低库存` : stock.text;
    stockEl.className = `item-stock ${stock.low ? "low" : "ok"}`;
    row.addEventListener("click", () => loadCatalogItem(itemType, item.id));
    row.addEventListener("keydown", (event) => {
      if (event.key === "Enter") loadCatalogItem(itemType, item.id);
    });
    els.inventoryList.append(row);
  }

  if (!els.inventoryList.children.length) {
    els.inventoryList.innerHTML = `<div class="result">没有匹配的物品。</div>`;
  }
}

function renderLabelOptions() {
  els.labelItem.innerHTML = "";
  for (const { itemType, item } of listItems(state)) {
    const option = document.createElement("option");
    option.value = `${itemType}:${item.id}`;
    option.textContent = `${item.id} · ${item.name}`;
    els.labelItem.append(option);
  }
}

function setCatalogForm(form) {
  els.catalogType.value = form.itemType;
  els.catalogForm.dataset.mode = form.id ? "edit" : "create";
  els.catalogFormTitle.textContent = `${form.id ? "编辑" : "新增"}${form.itemType === "spool" ? "耗材卷" : "零件"}`;

  for (const element of els.catalogForm.elements) {
    if (!element.name) continue;
    element.value = form[element.name] ?? "";
  }

  document.querySelectorAll("[data-catalog-field]").forEach((field) => {
    field.hidden = field.dataset.catalogField !== form.itemType;
  });

  els.catalogMessage.textContent = form.id
    ? `正在编辑 ${form.itemType}:${form.id}${form.archived_at ? "（已归档）" : ""}。`
    : "点库存列表里的物品可载入编辑。";
  els.cloneCatalogItem.disabled = !form.id;
  els.archiveCatalogItem.disabled = !form.id;
  els.archiveCatalogItem.textContent = form.archived_at ? "恢复" : "归档";
}

function readCatalogForm() {
  return Object.fromEntries(new FormData(els.catalogForm).entries());
}

function saveCatalogForm(event) {
  event.preventDefault();

  try {
    const result = saveCatalogItem(state, readCatalogForm());
    saveState(state);
    setCatalogForm(formFromItem(result.itemType, result.item));
    els.catalogMessage.textContent = `${result.action === "create" ? "已新增" : "已更新"}：${result.item.id} · ${result.item.name}`;
    renderAll();
  } catch (error) {
    els.catalogMessage.textContent = error.message;
  }
}

function loadCatalogItem(itemType, id) {
  const entry = listItems(state).find((candidate) => candidate.itemType === itemType && candidate.item.id === id);
  if (!entry) return;
  setCatalogForm(formFromItem(itemType, entry.item));
  els.catalogMessage.textContent = `已载入 ${entry.item.id}，修改后点保存。`;
}

function cloneCurrentCatalogItem() {
  const form = readCatalogForm();
  if (!form.id) {
    els.catalogMessage.textContent = "先从库存列表载入一个物品，再克隆。";
    return;
  }

  try {
    const cloned = cloneCatalogForm(state, form.itemType, form.id);
    setCatalogForm(cloned);
    els.catalogForm.dataset.mode = "create";
    els.catalogFormTitle.textContent = `克隆${cloned.itemType === "spool" ? "耗材卷" : "零件"}`;
    els.archiveCatalogItem.disabled = true;
    els.catalogMessage.textContent = `已从 ${form.id} 克隆参数，新 ID 为 ${cloned.id}，确认后保存。`;
  } catch (error) {
    els.catalogMessage.textContent = error.message;
  }
}

function toggleArchiveCurrentCatalogItem() {
  const form = readCatalogForm();
  if (!form.id) {
    els.catalogMessage.textContent = "先从库存列表载入一个物品，再归档或恢复。";
    return;
  }

  try {
    const archived = !form.archived_at;
    const item = archiveCatalogItem(state, form.itemType, form.id, archived);
    saveState(state);
    setCatalogForm(formFromItem(form.itemType, item));
    els.catalogMessage.textContent = archived ? `${item.id} 已归档。` : `${item.id} 已恢复启用。`;
    renderAll();
  } catch (error) {
    els.catalogMessage.textContent = error.message;
  }
}

function fillGeneratedCatalogId() {
  try {
    const form = readCatalogForm();
    els.catalogId.value = generateCatalogId(state, form.itemType, form);
    els.catalogMessage.textContent = `已生成 ID：${els.catalogId.value}`;
  } catch (error) {
    els.catalogMessage.textContent = error.message;
  }
}

function renderTransactions() {
  els.transactionList.innerHTML = "";
  const transactions = [...state.transactions].reverse();

  for (const transaction of transactions) {
    const row = document.querySelector("#itemTemplate").content.firstElementChild.cloneNode(true);
    row.querySelector(".item-title").textContent = `${transaction.item_type}:${transaction.item_id} · ${transaction.field}`;
    row.querySelector(".item-meta").textContent = `${transaction.before_val ?? "空"} -> ${transaction.after_val} · ${transaction.source} · ${new Date(transaction.created_at).toLocaleString()}`;
    const action = row.querySelector(".item-stock");
    action.textContent = transaction.action;
    action.className = "item-stock ok";
    els.transactionList.append(row);
  }

  if (!transactions.length) {
    els.transactionList.innerHTML = `<div class="result">还没有流水。</div>`;
  }
}

function renderScanLog() {
  if (!scanLog.length) {
    els.scanLog.innerHTML = `<div>最近扫码会显示在这里。</div>`;
    return;
  }

  els.scanLog.innerHTML = scanLog
    .map((entry) => `<div class="log-row"><code>${escapeHtml(entry.payload)}</code><span>${entry.time.toLocaleTimeString()}</span></div>`)
    .join("");
}

function makeItemQr() {
  const payload = els.labelItem.value;
  const [itemType, id] = payload.split(":");
  const entry = listItems(state).find((candidate) => candidate.itemType === itemType && candidate.item.id === id);
  if (!entry) return;

  els.labelQr.innerHTML = renderQrSvg(payload);
  els.labelTitle.textContent = `${entry.item.id} · ${entry.item.name}`;
  els.labelMeta.textContent = `${payload} · ${makeMeta(entry.itemType, entry.item)}`;
}

function exportData() {
  downloadSnapshot(state, makeSnapshotFilename());
}

function exportFilteredData() {
  const entries = getFilteredEntries();
  const ids = new Set(entries.map((entry) => `${entry.itemType}:${entry.item.id}`));
  const filteredState = {
    spools: entries.filter((entry) => entry.itemType === "spool").map((entry) => entry.item),
    parts: entries.filter((entry) => entry.itemType === "part").map((entry) => entry.item),
    transactions: state.transactions.filter((transaction) => ids.has(`${transaction.item_type}:${transaction.item_id}`)),
  };
  downloadSnapshot(filteredState, makeSnapshotFilename(new Date()).replace(".json", "-filtered.json"));
}

function downloadSnapshot(snapshotState, filename) {
  const blob = new Blob([exportSnapshot(snapshotState)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function chooseImportFile(mode) {
  els.importFile.dataset.mode = mode;
  els.importFile.click();
}

async function importData(event) {
  const file = event.target.files[0];
  if (!file) return;
  const mode = event.target.dataset.mode || "merge";

  try {
    const importedState = importSnapshotText(await file.text());
    if (mode === "replace") {
      showImportPreview(makeReplacePreview(importedState, file.name));
      if (!window.confirm(`覆盖导入 ${file.name}？当前本地数据会被替换。`)) return;
      state = importedState;
      els.catalogMessage.textContent = `已覆盖导入快照：${file.name}`;
    } else {
      const preview = previewMergeStates(state, importedState);
      showImportPreview(formatMergePreview(preview, file.name));
      if (!window.confirm(`合并导入 ${file.name}？请先查看页面上的预览。`)) return;
      const result = mergeStates(state, importedState);
      state = result.state;
      els.catalogMessage.textContent = `已合并快照：${file.name}，物品 ${result.summary.spools.merged + result.summary.parts.merged} 个，流水 ${result.summary.transactions} 条。`;
    }
    saveState(state);
    clearPending();
    setCatalogForm(createEmptyCatalogForm("spool"));
    renderAll();
  } catch (error) {
    els.catalogMessage.textContent = error.message;
  } finally {
    event.target.value = "";
  }
}

function getFilteredEntries() {
  return filterInventory(listItems(state), {
    query: els.searchText.value,
    itemType: els.inventoryTypeFilter.value,
    lowOnly: els.lowStockOnly.checked,
    archiveStatus: els.archiveStatusFilter.value,
  });
}

function showImportPreview(lines) {
  els.importPreview.hidden = false;
  els.importPreview.innerHTML = lines.map((line) => `<div>${escapeHtml(line)}</div>`).join("");
}

function makeReplacePreview(importedState, filename) {
  return [
    `覆盖预览：${filename}`,
    `导入后将保留 ${importedState.spools.length} 个耗材卷、${importedState.parts.length} 个零件、${importedState.transactions.length} 条流水。`,
    `当前本地数据会被整体替换。`,
  ];
}

function formatMergePreview(preview, filename) {
  return [
    `合并预览：${filename}`,
    makeItemPreviewLine("耗材卷", preview.spools),
    makeItemPreviewLine("零件", preview.parts),
    `流水：远程 ${preview.transactions.remote} 条，本地 ${preview.transactions.local} 条，新增导入 ${preview.transactions.incoming} 条，合并后 ${preview.transactions.merged} 条。`,
  ];
}

function makeItemPreviewLine(label, preview) {
  return `${label}：新增 ${preview.addedFromRemote.length} 个（${preview.addedFromRemote.join(", ") || "无"}）；远程覆盖 ${preview.remoteOverrides.length} 个（${preview.remoteOverrides.join(", ") || "无"}）；本地保留 ${preview.localKeeps.length} 个；仅本地 ${preview.localOnly.length} 个。`;
}

function makeMeta(itemType, item) {
  if (itemType === "spool") {
    return `${item.brand || "未填品牌"} · ${item.material || "未填材料"} · ${item.color || "未填颜色"} · 空卷 ${item.empty_wt}g · ${item.location || "未填库位"}`;
  }
  return `单件 ${item.unit_wt}g · 容器 ${item.container_wt}g · ${item.location || "未填库位"}`;
}

function clearPending() {
  resetSession(scanSession);
  scanLog = [];
}

function addScanLog(payload) {
  scanLog = [{ payload, time: new Date() }, ...scanLog].slice(0, 6);
}

function setResult(text) {
  els.scanResult.textContent = text;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
