const DEFAULT_BATCH = "TEST";
const DEFAULT_DATE = new Date();
const DEFAULT_NOTE = "虚拟货架测试";

let currentBatch = DEFAULT_BATCH;
let revealedId = "";

const els = {
  newBatch: document.querySelector("#newBatch"),
  batchName: document.querySelector("#batchName"),
  locationName: document.querySelector("#locationName"),
  locationQr: document.querySelector("#locationQr"),
  spoolCard: document.querySelector("#spoolCard"),
  partCard: document.querySelector("#partCard"),
  otherCard: document.querySelector("#otherCard"),
  weightGrid: document.querySelector("#weightGrid"),
  flowGrid: document.querySelector("#flowGrid"),
};

els.newBatch.addEventListener("click", () => {
  currentBatch = `${dateCode(DEFAULT_DATE)}-${Math.floor(100 + Math.random() * 900)}`;
  revealedId = "";
  render();
});

render();

function render() {
  const data = makeScenario(currentBatch);
  els.batchName.textContent = currentBatch;
  els.locationName.textContent = data.location.name;
  els.locationQr.innerHTML = qrLabel(data.location);
  els.spoolCard.innerHTML = itemCard(data.spool, "耗材卷", "PLA");
  els.partCard.innerHTML = itemCard(data.part, "零件盒", "M3");
  els.otherCard.innerHTML = itemCard(data.other, "其他物品", "工具");
  els.weightGrid.innerHTML = data.weights.map((item) => qrLabel(item, "weight-label")).join("");
  els.flowGrid.innerHTML = flowCards(data);
  bindReveal();
}

function makeScenario(batch) {
  const suffix = batch === DEFAULT_BATCH ? "TEST" : batch;
  const createdOn = dateCode(DEFAULT_DATE);
  const visibleDate = displayDate(DEFAULT_DATE);
  const note = DEFAULT_NOTE;
  const location = {
    key: "location",
    title: "测试库位",
    subtitle: `LOC-${suffix}-001`,
    lines: ["测试库位", visibleDate, note],
    payload: payload({
      type: "location",
      id: `LOC-${suffix}-001`,
      name: "测试库位",
      created_on: createdOn,
    }),
  };
  const spool = {
    key: "spool",
    title: "Bambu PLA white",
    subtitle: `FIL-${suffix}-001`,
    lines: ["PLA white Bambu", visibleDate, note],
    payload: payload({
      type: "spool",
      id: `FIL-${suffix}-001`,
      brand: "Bambu",
      material: "PLA",
      color: "white",
      tare_g: "200",
      created_on: createdOn,
    }),
  };
  const part = {
    key: "part",
    title: "M3x8黑色圆头螺丝",
    subtitle: `PART-${suffix}-001`,
    lines: ["M3x8黑色圆头螺丝", visibleDate, note],
    payload: payload({
      type: "part",
      id: `PART-${suffix}-001`,
      name: "M3x8黑色圆头螺丝",
      unit_weight_g: "0.42",
      created_on: createdOn,
    }),
  };
  const other = {
    key: "other",
    title: "热风枪",
    subtitle: `ITEM-${suffix}-001`,
    lines: ["热风枪", visibleDate, note],
    payload: payload({
      type: "other",
      id: `ITEM-${suffix}-001`,
      name: "热风枪",
      created_on: createdOn,
    }),
  };
  const weights = [
    weightCode("712.4", "耗材毛重"),
    weightCode("420", "零件总重"),
    weightCode("690", "盘点重量"),
  ];
  return { location, spool, part, other, weights };
}

function itemCard(item, kicker, badge) {
  return `
    <div class="shelf-item-head">
      <span>${escapeHtml(kicker)}</span>
      <strong>${escapeHtml(item.title)}</strong>
      <em>${escapeHtml(item.subtitle)}</em>
    </div>
    <div class="fake-label">
      <div class="fake-label-text">
        ${item.lines.map((line) => `<span>${escapeHtml(line)}</span>`).join("")}
      </div>
      ${qrLabel(item)}
    </div>
    <div class="shelf-badge">${escapeHtml(badge)}</div>
  `;
}

function qrLabel(item, extraClass = "") {
  const revealed = revealedId === item.key ? " revealed" : "";
  return `
    <button class="qr-label ${extraClass}${revealed}" type="button" data-reveal="${escapeHtml(item.key)}" aria-label="显示 ${escapeHtml(item.title)} 二维码">
      <span class="qr-art">${renderQr(item.payload)}</span>
      <span class="qr-cover">悬停 / 点击显示</span>
    </button>
  `;
}

function flowCards(data) {
  const flows = [
    {
      title: "入库耗材",
      steps: [
        ["扫耗材标签", data.spool],
        ["扫毛重 712.4g（或手输）", data.weights[0]],
        ["扫测试库位", data.location],
        ["手机上点入库", null],
      ],
    },
    {
      title: "入库零件",
      steps: [
        ["扫零件标签", data.part],
        ["扫总重 420g（或手输）", data.weights[1]],
        ["扫测试库位", data.location],
        ["手机上点入库", null],
      ],
    },
    {
      title: "整理库位",
      steps: [
        ["扫测试库位", data.location],
        ["手机上点整理该库位", null],
        ["连续扫耗材和零件", data.spool],
        ["手机上点完成整理", null],
      ],
    },
  ];

  return flows.map((flow) => `
    <article class="flow-card">
      <h2>${escapeHtml(flow.title)}</h2>
      <ol>
        ${flow.steps.map(([text, item]) => `
          <li>
            <span>${escapeHtml(text)}</span>
            ${item ? qrLabel(item, "flow-qr") : "<strong>App 动作</strong>"}
          </li>
        `).join("")}
      </ol>
    </article>
  `).join("");
}

function weightCode(value, title) {
  return {
    key: `weight-${value}`,
    title: `${title} ${value}g`,
    subtitle: `${value}g`,
    lines: [`${value}g`, displayDate(DEFAULT_DATE), "模拟重量码"],
    payload: payload({ type: "weight", value_g: value }),
  };
}

function payload(fields) {
  return `v1;${Object.entries(fields)
    .filter(([, value]) => String(value).trim() !== "")
    .map(([key, value]) => `${key}=${encodePayloadValue(String(value).trim())}`)
    .join(";")}`;
}

function encodePayloadValue(value) {
  return encodeURIComponent(value);
}

function renderQr(text) {
  const qr = globalThis.qrcode(0, "Q");
  qr.addData(text, "Byte");
  qr.make();
  return qr.createSvgTag({ cellSize: 3, margin: 4, scalable: true });
}

function bindReveal() {
  document.querySelectorAll("[data-reveal]").forEach((button) => {
    button.addEventListener("click", () => {
      const key = button.dataset.reveal;
      revealedId = revealedId === key ? "" : key;
      render();
    });
  });
}

function dateCode(date) {
  return `${String(date.getFullYear()).slice(-2)}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
}

function displayDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
