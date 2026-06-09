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
  weight: "生成 `weight:<grams>`。只填克数，适合称重二维码。",
  spool: "生成 `spool:<id>`。可直接填现成 ID，也可用材料、颜色、序号拼一个测试 ID。",
  part: "生成 `part:<id>`。可直接填现成零件 ID，也可根据名称和规格临时造一个测试 ID。",
  location: "生成 `location:<code>`。可直接填库位，也可按区域、排号、位号拼接。",
  raw: "不做前缀拼接，直接把你输入的内容拿去生成二维码。",
};

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
