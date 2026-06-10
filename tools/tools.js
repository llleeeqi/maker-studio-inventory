import { renderQrSvg } from "../app/qr.js";

const COLOR_CODES = {
  黑: "BLK",
  黑色: "BLK",
  白: "WHT",
  白色: "WHT",
  透明: "CLR",
  红: "RED",
  红色: "RED",
  蓝: "BLU",
  蓝色: "BLU",
  绿: "GRN",
  绿色: "GRN",
  黄: "YLW",
  黄色: "YLW",
  灰: "GRY",
  灰色: "GRY",
  银: "SLV",
  银色: "SLV",
};

const SAMPLE_LIBRARY = {
  weight: () => ({
    type: "weight",
    payload: `weight:${randomWeight()}`,
    label: "随机重量",
    note: "盘点称重时先扫或后扫都行",
  }),
  spool: () => {
    const material = pick(["PLA", "PETG", "ABS", "TPU"]);
    const color = pick(["黑色", "白色", "透明", "红色", "蓝色"]);
    const serial = pad3(randomInt(1, 240));
    const id = `${material}-${colorCode(color)}-${serial}`;
    return {
      type: "spool",
      payload: `spool:${id}`,
      label: `${material} ${color}`,
      note: "耗材卷物品码",
    };
  },
  part: () => {
    const item = pick([
      { id: "M3-INSERT", name: "M3 热熔螺母" },
      { id: "M2X8-SCREW", name: "M2x8 螺丝" },
      { id: "608-BEARING", name: "608 轴承" },
      { id: "M5-TNUT", name: "M5 T 型螺母" },
      { id: "XT60-CONNECTOR", name: "XT60 接头" },
    ]);
    return {
      type: "part",
      payload: `part:${item.id}`,
      label: item.name,
      note: "零件物品码",
    };
  },
  location: () => {
    const area = pick(["RACK", "BOX", "BIN", "DRAWER"]);
    const row = pick(["A", "B", "C", "D"]);
    const slot = pad2(randomInt(1, 24));
    return {
      type: "location",
      payload: `location:${area}-${row}${slot}`,
      label: `${area} ${row}${slot}`,
      note: "库位码",
    };
  },
  raw: () => {
    const payload = pick([
      "weight:712.4",
      "spool:PLA-BLK-001",
      "part:M3-INSERT",
      "location:RACK-A01",
      "custom:test-scan-001",
    ]);
    return {
      type: "raw",
      payload,
      label: "原始 payload",
      note: "给兼容性测试用",
    };
  },
};

const TYPE_HINTS = {
  weight: "生成 `weight:<grams>`。新版扫码台会按当前物品或待处理物品自动判断盘点/重新入库。",
  spool: "生成 `spool:<id>`。扫物品码默认展示详情；遇到待处理重量或库位时自动写入。",
  part: "生成 `part:<id>`。扫零件码默认展示详情；遇到重量码时可估算数量。",
  location: "生成 `location:<code>`。有当前物品就绑定库位，没有当前物品就等待物品码。",
  raw: "不做前缀拼接，直接把你输入的内容拿去生成二维码，用于未知码和兼容性测试。",
};

const WORKBENCH_SCENARIOS = [
  {
    title: "查耗材详情",
    goal: "扫物品码后展示库存、库位、入库时间和同类卷数。",
    steps: [
      { label: "扫耗材卷", payload: "spool:PLA-BLK-001" },
    ],
  },
  {
    title: "查零件详情",
    goal: "扫零件码后展示数量、库位和报警值。",
    steps: [
      { label: "扫零件", payload: "part:M3-INSERT" },
    ],
  },
  {
    title: "先称重再扫物品",
    goal: "先扫重量，扫码台进入待处理重量；再扫物品后写入库存。",
    steps: [
      { label: "扫重量", payload: "weight:700.0" },
      { label: "扫耗材卷", payload: "spool:PLA-BLK-001" },
    ],
  },
  {
    title: "先物品再称重",
    goal: "先设当前物品，再扫重量直接更新当前物品。",
    steps: [
      { label: "扫耗材卷", payload: "spool:PETG-CLR-001" },
      { label: "扫重量", payload: "weight:388.5" },
    ],
  },
  {
    title: "先库位再扫物品",
    goal: "先扫库位，扫码台等待物品；再扫物品后绑定库位。",
    steps: [
      { label: "扫库位", payload: "location:RACK-C04" },
      { label: "扫耗材卷", payload: "spool:PLA-BLK-001" },
    ],
  },
  {
    title: "先物品再移库",
    goal: "当前物品存在时，扫库位码直接绑定新库位。",
    steps: [
      { label: "扫零件", payload: "part:M3-INSERT" },
      { label: "扫库位", payload: "location:BOX-D08" },
    ],
  },
  {
    title: "出库当前卷",
    goal: "出库是 app 按钮动作，不是新二维码。先扫卷，再在详情卡点出库。",
    steps: [
      { label: "扫耗材卷", payload: "spool:PLA-BLK-001" },
      { label: "app 动作", action: "点“出库当前卷”" },
    ],
  },
  {
    title: "出库卷重新入库",
    goal: "已出库卷再次扫码后，必须补重量；重量和物品顺序都应支持。",
    steps: [
      { label: "扫已出库卷", payload: "spool:PLA-BLK-001" },
      { label: "扫新重量", payload: "weight:690.0" },
    ],
  },
  {
    title: "先重量后重新入库",
    goal: "先扫重量，再扫已出库卷，也应按重新入库处理。",
    steps: [
      { label: "扫新重量", payload: "weight:702.5" },
      { label: "扫已出库卷", payload: "spool:PLA-BLK-001" },
    ],
  },
  {
    title: "同类耗材卷数",
    goal: "用于测同类卷数。若 app 里还没有 002，先在新增页克隆/新增同类卷。",
    steps: [
      { label: "扫第一卷", payload: "spool:PLA-BLK-001" },
      { label: "扫同类第二卷", payload: "spool:PLA-BLK-002" },
    ],
  },
];

const els = {
  qrType: document.querySelector("#qrType"),
  qrOutput: document.querySelector("#qrOutput"),
  qrPayload: document.querySelector("#qrPayload"),
  makeQr: document.querySelector("#makeQr"),
  randomCurrent: document.querySelector("#randomCurrent"),
  randomAll: document.querySelector("#randomAll"),
  copyPayload: document.querySelector("#copyPayload"),
  copyStatus: document.querySelector("#copyStatus"),
  fieldHint: document.querySelector("#fieldHint"),
  sampleGrid: document.querySelector("#sampleGrid"),
  scenarioGrid: document.querySelector("#scenarioGrid"),
  typeButtons: document.querySelectorAll("[data-type-select]"),
  sampleButtons: document.querySelectorAll("[data-sample]"),
  fieldGroups: {
    weight: document.querySelector("#fieldsWeight"),
    spool: document.querySelector("#fieldsSpool"),
    part: document.querySelector("#fieldsPart"),
    location: document.querySelector("#fieldsLocation"),
    raw: document.querySelector("#fieldsRaw"),
  },
  weightGrams: document.querySelector("#weightGrams"),
  spoolId: document.querySelector("#spoolId"),
  spoolMaterial: document.querySelector("#spoolMaterial"),
  spoolColor: document.querySelector("#spoolColor"),
  spoolSerial: document.querySelector("#spoolSerial"),
  partId: document.querySelector("#partId"),
  partName: document.querySelector("#partName"),
  partSpec: document.querySelector("#partSpec"),
  locationId: document.querySelector("#locationId"),
  locationArea: document.querySelector("#locationArea"),
  locationRow: document.querySelector("#locationRow"),
  locationSlot: document.querySelector("#locationSlot"),
  rawPayload: document.querySelector("#rawPayload"),
};

bindEvents();
applyRandomValues("weight");
renderWorkbenchScenarios();
renderAllSamples();
updateTypeUi();
makeQr();

function bindEvents() {
  els.makeQr.addEventListener("click", makeQr);
  els.randomCurrent.addEventListener("click", () => {
    applyRandomValues(els.qrType.value);
    makeQr();
  });
  els.randomAll.addEventListener("click", () => {
    applyRandomValues(els.qrType.value);
    makeQr();
    renderAllSamples();
  });
  els.copyPayload.addEventListener("click", copyPayload);
  els.qrType.addEventListener("change", () => {
    updateTypeUi();
    makeQr();
  });

  els.typeButtons.forEach((button) => {
    button.addEventListener("click", () => {
      els.qrType.value = button.dataset.typeSelect;
      updateTypeUi();
      makeQr();
    });
  });

  els.sampleButtons.forEach((button) => {
    button.addEventListener("click", () => {
      const type = button.dataset.sample;
      els.qrType.value = type;
      applyRandomValues(type);
      updateTypeUi();
      makeQr();
    });
  });

  [
    els.weightGrams,
    els.spoolId,
    els.spoolMaterial,
    els.spoolColor,
    els.spoolSerial,
    els.partId,
    els.partName,
    els.partSpec,
    els.locationId,
    els.locationArea,
    els.locationRow,
    els.locationSlot,
    els.rawPayload,
  ].forEach((input) => input.addEventListener("input", makeQr));
}

function updateTypeUi() {
  const type = els.qrType.value;
  els.typeButtons.forEach((button) => {
    button.classList.toggle("active", button.dataset.typeSelect === type);
  });
  Object.entries(els.fieldGroups).forEach(([name, group]) => {
    group.classList.toggle("active", name === type);
  });
  els.fieldHint.textContent = TYPE_HINTS[type];
  els.copyStatus.textContent = "复制后可直接贴到扫码测试里。";
}

function makeQr() {
  const payload = buildPayload();
  els.qrPayload.textContent = payload;

  try {
    els.qrOutput.innerHTML = renderQrSvg(payload);
  } catch (error) {
    els.qrOutput.innerHTML = `<div class="result">${escapeHtml(error.message)}</div>`;
  }
}

function buildPayload() {
  const type = els.qrType.value;

  if (type === "weight") {
    const grams = clean(els.weightGrams.value) || "0";
    return `weight:${grams}`;
  }

  if (type === "spool") {
    const direct = cleanUpper(els.spoolId.value);
    if (direct) return `spool:${direct}`;
    const material = slug(cleanUpper(els.spoolMaterial.value) || "PLA");
    const color = colorCode(clean(els.spoolColor.value) || "黑色");
    const serial = pad3(Number(clean(els.spoolSerial.value) || 1));
    return `spool:${material}-${color}-${serial}`;
  }

  if (type === "part") {
    const direct = cleanUpper(els.partId.value);
    if (direct) return `part:${direct}`;
    const spec = slug(cleanUpper(els.partSpec.value) || "PART");
    const name = slug(cleanUpper(els.partName.value) || "ITEM");
    return `part:${spec}-${name}`.replace(/-+/g, "-");
  }

  if (type === "location") {
    const direct = cleanUpper(els.locationId.value);
    if (direct) return `location:${direct}`;
    const area = slug(cleanUpper(els.locationArea.value) || "RACK");
    const row = slug(cleanUpper(els.locationRow.value) || "A");
    const slot = pad2(Number(clean(els.locationSlot.value) || 1));
    return `location:${area}-${row}${slot}`;
  }

  return clean(els.rawPayload.value) || "raw:empty";
}

function applyRandomValues(type) {
  const sample = SAMPLE_LIBRARY[type]();
  const payload = sample.payload;

  if (type === "weight") {
    els.weightGrams.value = payload.replace("weight:", "");
    return;
  }

  if (type === "spool") {
    const id = payload.replace("spool:", "");
    const [material, color, serial] = id.split("-");
    els.spoolId.value = "";
    els.spoolMaterial.value = material || "PLA";
    els.spoolColor.value = colorName(color);
    els.spoolSerial.value = serial || "001";
    return;
  }

  if (type === "part") {
    const id = payload.replace("part:", "");
    const parts = id.split("-");
    els.partId.value = id;
    els.partSpec.value = parts[0] || "M3";
    els.partName.value = sample.label;
    return;
  }

  if (type === "location") {
    const id = payload.replace("location:", "");
    const [area, rowSlot = "A01"] = id.split("-");
    els.locationId.value = "";
    els.locationArea.value = area || "RACK";
    els.locationRow.value = rowSlot.slice(0, 1) || "A";
    els.locationSlot.value = rowSlot.slice(1) || "01";
    return;
  }

  els.rawPayload.value = payload;
}

function renderAllSamples() {
  const samples = [
    SAMPLE_LIBRARY.spool(),
    SAMPLE_LIBRARY.part(),
    SAMPLE_LIBRARY.weight(),
    SAMPLE_LIBRARY.location(),
    SAMPLE_LIBRARY.raw(),
  ];

  els.sampleGrid.innerHTML = samples
    .map(
      (sample) => `
        <article class="sample-card">
          <div class="sample-card-head">
            <strong>${escapeHtml(sample.label)}</strong>
            <span>${escapeHtml(sample.type)}</span>
          </div>
          <div class="sample-card-qr">${renderQrSvg(sample.payload, 5, 3)}</div>
          <code>${escapeHtml(sample.payload)}</code>
          <div class="sample-note">${escapeHtml(sample.note)}</div>
        </article>
      `,
    )
    .join("");
}

function renderWorkbenchScenarios() {
  els.scenarioGrid.innerHTML = WORKBENCH_SCENARIOS
    .map(
      (scenario) => `
        <article class="scenario-card">
          <div class="scenario-head">
            <strong>${escapeHtml(scenario.title)}</strong>
            <span>${escapeHtml(scenario.goal)}</span>
          </div>
          <div class="scenario-steps">
            ${scenario.steps.map((step, index) => renderScenarioStep(step, index)).join("")}
          </div>
        </article>
      `,
    )
    .join("");
}

function renderScenarioStep(step, index) {
  if (step.action) {
    return `
      <div class="scenario-step app-action-step">
        <span class="scenario-index">${index + 1}</span>
        <div>
          <strong>${escapeHtml(step.label)}</strong>
          <p>${escapeHtml(step.action)}</p>
        </div>
      </div>
    `;
  }

  return `
    <div class="scenario-step">
      <span class="scenario-index">${index + 1}</span>
      <div class="scenario-step-qr">${renderQrSvg(step.payload, 4, 2)}</div>
      <div>
        <strong>${escapeHtml(step.label)}</strong>
        <code>${escapeHtml(step.payload)}</code>
      </div>
    </div>
  `;
}

async function copyPayload() {
  const payload = els.qrPayload.textContent;
  try {
    await navigator.clipboard.writeText(payload);
    els.copyStatus.textContent = `已复制：${payload}`;
  } catch {
    els.copyStatus.textContent = "复制失败，请手动复制下面的 payload。";
  }
}

function randomWeight() {
  return (randomInt(80, 1200) + randomInt(0, 9) / 10).toFixed(1);
}

function pick(list) {
  return list[randomInt(0, list.length - 1)];
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function clean(value) {
  return String(value ?? "").trim();
}

function cleanUpper(value) {
  return clean(value).toUpperCase();
}

function pad2(value) {
  return String(Math.max(1, Math.floor(value || 1))).padStart(2, "0");
}

function pad3(value) {
  return String(Math.max(1, Math.floor(value || 1))).padStart(3, "0");
}

function colorCode(value) {
  const text = clean(value);
  return COLOR_CODES[text] || COLOR_CODES[text.replace(/色$/, "")] || slug(text || "MIX");
}

function colorName(code) {
  const entry = Object.entries(COLOR_CODES).find(([, value]) => value === code);
  return entry ? entry[0] : code;
}

function slug(value) {
  return cleanUpper(value)
    .replace(/[^A-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "ITEM";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
