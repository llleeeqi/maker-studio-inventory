import { getQrInfo, renderQrSvg } from "../app/qr.js";

const CREATED_ON = yyMmDd(new Date());

const TYPE_HINTS = {
  spool:
    "耗材卷固定档案。二维码存品牌、材料、颜色和固定重量；当前重量和库位存在 app 本地状态。",
  part:
    "五金/零件固定档案。单件重量用于称重估算数量；当前数量存在 app 本地状态。",
  other: "其他实体只做简单登记：名称、短备注、建档日期；不做余量计算。",
  location: "库位标签用于绑定物品状态。库位不写进实体二维码。",
  weight: "重量码统一使用克，字段为 value_g。",
  raw: "直接生成输入内容，用于边界和兼容性测试。",
};

const SAMPLE_DATA = {
  spools: [
    { id: "PLA-BLK-001", name: "黑色PLA", brand: "Bambu", material: "PLA", color: "black" },
    { id: "PETG-CLR-001", name: "透明PETG", brand: "eSUN", material: "PETG", color: "clear" },
    { id: "PLA-WHT-002", name: "白色PLA", brand: "Bambu", material: "PLA", color: "white" },
  ],
  parts: [
    { id: "M3-SCREW-8-BLK", name: "M3x8黑色圆头螺丝", category: "screw", spec: "M3x8", color: "black", unitWeightG: "0.42", packageQty: "100" },
    { id: "M3-INSERT-OD42", name: "M3热熔螺母", category: "insert", spec: "M3 OD4.2", color: "brass", unitWeightG: "0.27", packageQty: "100" },
    { id: "608-BEARING-ZZ", name: "608ZZ轴承", category: "bearing", spec: "608ZZ", color: "steel", unitWeightG: "12", packageQty: "10" },
  ],
  others: [
    { id: "TOOL-001", name: "热风枪", note: "喷嘴套装" },
    { id: "MOD-ESP8266-001", name: "ESP8266模块", note: "打印桥测试" },
    { id: "BOX-SPARE-001", name: "备件盒", note: "杂项" },
  ],
};

const WORKBENCH_SCENARIOS = [
  {
    title: "扫未知耗材标签建档",
    goal: "本地没有档案时，扫完整 msi 标签只恢复 Profile，不自动入库。",
    steps: [
      { label: "扫耗材档案", payload: spoolPayload(SAMPLE_DATA.spools[0]) },
    ],
  },
  {
    title: "耗材称重入库",
    goal: "先扫重量，再扫耗材档案，满足 current_g 后创建 State。",
    steps: [
      { label: "扫重量", payload: weightPayload("712.4") },
      { label: "扫耗材档案", payload: spoolPayload(SAMPLE_DATA.spools[0]) },
    ],
  },
  {
    title: "零件称重估算",
    goal: "零件有码内单件重量，扫总重量后估算数量。",
    steps: [
      { label: "扫零件档案", payload: partPayload(SAMPLE_DATA.parts[0]) },
      { label: "扫总重量", payload: weightPayload("420") },
    ],
  },
  {
    title: "其他物品简单入库",
    goal: "other 不做余量计算，扫档案后在 app 点入库即可。",
    steps: [
      { label: "扫其他档案", payload: otherPayload(SAMPLE_DATA.others[0]) },
      { label: "app 动作", action: "点“入库”" },
    ],
  },
  {
    title: "无库位入库后移库",
    goal: "库位可选。后续扫物品再扫库位，写本地状态。",
    steps: [
      { label: "扫耗材档案", payload: spoolPayload(SAMPLE_DATA.spools[1]) },
      { label: "扫重量", payload: weightPayload("388.5") },
      { label: "扫库位", payload: locationPayload({ id: "RACK-C04", name: "C架04格" }) },
    ],
  },
  {
    title: "出库和重新入库",
    goal: "出库是 app 动作。重新入库必须再次提供重量。",
    steps: [
      { label: "扫耗材档案", payload: spoolPayload(SAMPLE_DATA.spools[0]) },
      { label: "app 动作", action: "点“出库”" },
      { label: "扫新重量", payload: weightPayload("690") },
      { label: "扫同一耗材", payload: spoolPayload(SAMPLE_DATA.spools[0]) },
    ],
  },
];

const els = {
  qrType: document.querySelector("#qrType"),
  legacyMode: document.querySelector("#legacyMode"),
  qrOutput: document.querySelector("#qrOutput"),
  qrMeta: document.querySelector("#qrMeta"),
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
    spool: document.querySelector("#fieldsSpool"),
    part: document.querySelector("#fieldsPart"),
    other: document.querySelector("#fieldsOther"),
    location: document.querySelector("#fieldsLocation"),
    weight: document.querySelector("#fieldsWeight"),
    raw: document.querySelector("#fieldsRaw"),
  },
  spoolId: document.querySelector("#spoolId"),
  spoolName: document.querySelector("#spoolName"),
  spoolBrand: document.querySelector("#spoolBrand"),
  spoolMaterial: document.querySelector("#spoolMaterial"),
  spoolColor: document.querySelector("#spoolColor"),
  spoolFullG: document.querySelector("#spoolFullG"),
  spoolTareG: document.querySelector("#spoolTareG"),
  spoolNetG: document.querySelector("#spoolNetG"),
  spoolCreatedOn: document.querySelector("#spoolCreatedOn"),
  partId: document.querySelector("#partId"),
  partName: document.querySelector("#partName"),
  partCategory: document.querySelector("#partCategory"),
  partSpec: document.querySelector("#partSpec"),
  partColor: document.querySelector("#partColor"),
  partUnitWeightG: document.querySelector("#partUnitWeightG"),
  partPackageQty: document.querySelector("#partPackageQty"),
  partCreatedOn: document.querySelector("#partCreatedOn"),
  otherId: document.querySelector("#otherId"),
  otherName: document.querySelector("#otherName"),
  otherNote: document.querySelector("#otherNote"),
  otherCreatedOn: document.querySelector("#otherCreatedOn"),
  locationId: document.querySelector("#locationId"),
  locationName: document.querySelector("#locationName"),
  locationArea: document.querySelector("#locationArea"),
  locationRow: document.querySelector("#locationRow"),
  locationSlot: document.querySelector("#locationSlot"),
  locationCreatedOn: document.querySelector("#locationCreatedOn"),
  weightGrams: document.querySelector("#weightGrams"),
  rawPayload: document.querySelector("#rawPayload"),
};

bindEvents();
setDefaultDates();
applyRandomValues("spool");
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
    renderAllSamples();
    renderWorkbenchScenarios();
    makeQr();
  });
  els.copyPayload.addEventListener("click", copyPayload);
  els.qrType.addEventListener("change", () => {
    updateTypeUi();
    makeQr();
  });
  els.legacyMode.addEventListener("change", makeQr);

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

  document
    .querySelectorAll("input")
    .forEach((input) => input.addEventListener("input", makeQr));
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
  els.copyStatus.textContent = "复制后可直接贴到手机 app 手动补录里。";
}

function makeQr() {
  const payload = buildPayload();
  els.qrPayload.textContent = payload;
  renderQrMeta(payload);
  try {
    els.qrOutput.innerHTML = renderQrSvg(payload);
  } catch (error) {
    els.qrOutput.innerHTML = `<div class="result">${escapeHtml(error.message)}</div>`;
  }
}

function renderQrMeta(payload) {
  const info = getQrInfo(payload);
  const density =
    info.version == null
      ? "超出当前生成器"
      : info.version <= 4
        ? "低密度"
        : info.version <= 7
          ? "中密度"
          : "高密度，小标签需实测";

  els.qrMeta.textContent = info.fits
    ? `${info.bytes} 字节 / QR V${info.version} / 上限 ${info.maxBytes} 字节 / ${density}`
    : `${info.bytes} 字节 / 超出当前 ${info.maxSupportedBytes} 字节上限`;
}

function buildPayload() {
  const type = els.qrType.value;
  if (type === "raw") return clean(els.rawPayload.value) || "raw:empty";

  if (type === "weight") {
    const value = clean(els.weightGrams.value) || "0";
    return els.legacyMode.checked ? `weight:${value}` : weightPayload(value);
  }

  if (type === "spool") {
    const data = readSpool();
    return els.legacyMode.checked ? `spool:${data.id}` : spoolPayload(data);
  }

  if (type === "part") {
    const data = readPart();
    return els.legacyMode.checked ? `part:${data.id}` : partPayload(data);
  }

  if (type === "other") {
    const data = readOther();
    return els.legacyMode.checked ? `other:${data.id}` : otherPayload(data);
  }

  const data = readLocation();
  return els.legacyMode.checked ? `location:${data.id}` : locationPayload(data);
}

function readSpool() {
  const fallback = pick(SAMPLE_DATA.spools);
  return {
    id: cleanUpper(els.spoolId.value) || fallback.id,
    name: clean(els.spoolName.value) || fallback.name,
    brand: clean(els.spoolBrand.value) || fallback.brand,
    material: cleanUpper(els.spoolMaterial.value) || fallback.material,
    color: clean(els.spoolColor.value) || fallback.color,
    fullG: clean(els.spoolFullG.value) || "1200",
    tareG: clean(els.spoolTareG.value) || "200",
    netG: clean(els.spoolNetG.value) || "1000",
    createdOn: clean(els.spoolCreatedOn.value) || CREATED_ON,
  };
}

function readPart() {
  const fallback = pick(SAMPLE_DATA.parts);
  return {
    id: cleanUpper(els.partId.value) || fallback.id,
    name: clean(els.partName.value) || fallback.name,
    category: clean(els.partCategory.value) || fallback.category,
    spec: clean(els.partSpec.value) || fallback.spec,
    color: clean(els.partColor.value) || fallback.color,
    unitWeightG: clean(els.partUnitWeightG.value) || fallback.unitWeightG,
    packageQty: clean(els.partPackageQty.value) || fallback.packageQty,
    createdOn: clean(els.partCreatedOn.value) || CREATED_ON,
  };
}

function readOther() {
  const fallback = pick(SAMPLE_DATA.others);
  return {
    id: cleanUpper(els.otherId.value) || fallback.id,
    name: clean(els.otherName.value) || fallback.name,
    note: limitNote(clean(els.otherNote.value) || fallback.note),
    createdOn: clean(els.otherCreatedOn.value) || CREATED_ON,
  };
}

function readLocation() {
  const direct = cleanUpper(els.locationId.value);
  const area = slug(cleanUpper(els.locationArea.value) || "RACK");
  const row = slug(cleanUpper(els.locationRow.value) || "A");
  const slot = pad2(Number(clean(els.locationSlot.value) || 1));
  const id = direct || `${area}-${row}${slot}`;
  return {
    id,
    name: clean(els.locationName.value) || `${row}架${slot}格`,
    createdOn: clean(els.locationCreatedOn.value) || CREATED_ON,
  };
}

function applyRandomValues(type) {
  if (type === "spool") {
    const data = { ...pick(SAMPLE_DATA.spools), id: randomSpoolId() };
    els.spoolId.value = data.id;
    els.spoolName.value = data.name;
    els.spoolBrand.value = data.brand;
    els.spoolMaterial.value = data.material;
    els.spoolColor.value = data.color;
    els.spoolFullG.value = String(pick([1150, 1200, 1250]));
    els.spoolTareG.value = String(pick([178, 185, 200]));
    els.spoolNetG.value = "1000";
    els.spoolCreatedOn.value = CREATED_ON;
    return;
  }

  if (type === "part") {
    const data = pick(SAMPLE_DATA.parts);
    els.partId.value = data.id;
    els.partName.value = data.name;
    els.partCategory.value = data.category;
    els.partSpec.value = data.spec;
    els.partColor.value = data.color;
    els.partUnitWeightG.value = data.unitWeightG;
    els.partPackageQty.value = data.packageQty;
    els.partCreatedOn.value = CREATED_ON;
    return;
  }

  if (type === "other") {
    const data = pick(SAMPLE_DATA.others);
    els.otherId.value = data.id;
    els.otherName.value = data.name;
    els.otherNote.value = data.note;
    els.otherCreatedOn.value = CREATED_ON;
    return;
  }

  if (type === "location") {
    const area = pick(["RACK", "BOX", "BIN", "DRAWER"]);
    const row = pick(["A", "B", "C", "D"]);
    const slot = pad2(randomInt(1, 24));
    els.locationId.value = `${area}-${row}${slot}`;
    els.locationName.value = `${row}架${slot}格`;
    els.locationArea.value = area;
    els.locationRow.value = row;
    els.locationSlot.value = slot;
    els.locationCreatedOn.value = CREATED_ON;
    return;
  }

  if (type === "weight") {
    els.weightGrams.value = randomWeight();
    return;
  }

  els.rawPayload.value = weightPayload("712.4");
}

function renderAllSamples() {
  const samples = [
    { type: "spool", label: "耗材档案", payload: spoolPayload(SAMPLE_DATA.spools[0]), note: "扫码只恢复固定档案，不自动入库" },
    { type: "part", label: "零件档案", payload: partPayload(SAMPLE_DATA.parts[0]), note: "带单件重量，可配合重量码估算数量" },
    { type: "other", label: "其他档案", payload: otherPayload(SAMPLE_DATA.others[0]), note: "简单登记，不做余量计算" },
    { type: "location", label: "库位", payload: locationPayload({ id: "RACK-A01", name: "A架01格" }), note: "绑定到本地状态，不进入实体二维码" },
    { type: "weight", label: "重量", payload: weightPayload(randomWeight()), note: "统一克，字段 value_g" },
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
  els.scenarioGrid.innerHTML = WORKBENCH_SCENARIOS.map(
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
  ).join("");
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

function spoolPayload(data) {
  return msi({
    type: "spool",
    id: data.id,
    name: data.name,
    brand: data.brand,
    material: data.material,
    color: data.color,
    full_g: data.fullG || "1200",
    tare_g: data.tareG || "200",
    net_g: data.netG || "1000",
    created_on: data.createdOn || CREATED_ON,
  });
}

function partPayload(data) {
  return msi({
    type: "part",
    id: data.id,
    name: data.name,
    category: data.category,
    spec: data.spec,
    color: data.color,
    unit_weight_g: data.unitWeightG,
    package_qty: data.packageQty,
    created_on: data.createdOn || CREATED_ON,
  });
}

function otherPayload(data) {
  return msi({
    type: "other",
    id: data.id,
    name: data.name,
    note: limitNote(data.note || ""),
    created_on: data.createdOn || CREATED_ON,
  });
}

function locationPayload(data) {
  return msi({
    type: "location",
    id: data.id,
    name: data.name,
    created_on: data.createdOn || CREATED_ON,
  });
}

function weightPayload(value) {
  return msi({ type: "weight", value_g: value });
}

function msi(fields) {
  return `msi:v1;${Object.entries(fields)
    .filter(([, value]) => clean(value) !== "")
    .map(([key, value]) => `${key}=${clean(value)}`)
    .join(";")}`;
}

function setDefaultDates() {
  [
    els.spoolCreatedOn,
    els.partCreatedOn,
    els.otherCreatedOn,
    els.locationCreatedOn,
  ].forEach((input) => {
    input.value = CREATED_ON;
  });
}

function randomSpoolId() {
  const material = pick(["PLA", "PETG", "ABS", "TPU"]);
  const color = pick(["BLK", "WHT", "CLR", "RED", "BLU"]);
  return `${material}-${color}-${pad3(randomInt(1, 240))}`;
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

function slug(value) {
  return cleanUpper(value)
    .replace(/[^A-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "ITEM";
}

function yyMmDd(date) {
  return `${String(date.getFullYear() % 100).padStart(2, "0")}${String(
    date.getMonth() + 1,
  ).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
}

function limitNote(value) {
  const text = clean(value);
  if (/^[\x00-\x7F]*$/.test(text)) return text.slice(0, 40);
  return Array.from(text).slice(0, 20).join("");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
