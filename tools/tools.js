import { renderQrSvg } from "../app/qr.js";

const els = {
  qrType: document.querySelector("#qrType"),
  qrValue: document.querySelector("#qrValue"),
  makeQr: document.querySelector("#makeQr"),
  qrOutput: document.querySelector("#qrOutput"),
  qrPayload: document.querySelector("#qrPayload"),
};

els.makeQr.addEventListener("click", makeQr);
els.qrType.addEventListener("change", makeQr);
els.qrValue.addEventListener("input", makeQr);

document.querySelectorAll("[data-type]").forEach((button) => {
  button.addEventListener("click", () => {
    els.qrType.value = button.dataset.type;
    els.qrValue.value = button.dataset.value;
    makeQr();
  });
});

makeQr();

function makeQr() {
  const type = els.qrType.value;
  const value = els.qrValue.value.trim();
  const payload = type === "raw" ? value : `${type}:${value}`;
  els.qrPayload.textContent = payload;

  try {
    els.qrOutput.innerHTML = renderQrSvg(payload);
  } catch (error) {
    els.qrOutput.innerHTML = `<div class="result">${escapeHtml(error.message)}</div>`;
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
