import assert from "node:assert/strict";

import {
  createScannerPort,
  normalizeScanResult,
  registerScannerBridge,
} from "../core/scanner-port.js";

testNormalizeString();
testNormalizeScannerObject();
testRejectInvalidResult();
testPortCallback();
testBridgeRegistration();

console.log("scanner-port tests passed");

function testNormalizeString() {
  assert.equal(normalizeScanResult(" spool:PLA-BLK-001 "), "spool:PLA-BLK-001");
}

function testNormalizeScannerObject() {
  assert.equal(normalizeScanResult({ rawValue: "part:M3-INSERT" }), "part:M3-INSERT");
  assert.equal(normalizeScanResult({ text: "weight:712.4" }), "weight:712.4");
  assert.equal(normalizeScanResult({ payload: "location:RACK-A01" }), "location:RACK-A01");
}

function testRejectInvalidResult() {
  assert.throws(() => normalizeScanResult(" "), /不能为空/);
  assert.throws(() => normalizeScanResult({ code: "nope" }), /扫码结果必须/);
}

function testPortCallback() {
  const seen = [];
  const port = createScannerPort((payload) => seen.push(payload));

  const payload = port.push({ value: "spool:PLA-BLK-001" });

  assert.equal(payload, "spool:PLA-BLK-001");
  assert.deepEqual(seen, ["spool:PLA-BLK-001"]);
}

function testBridgeRegistration() {
  const fakeWindow = {};
  const seen = [];
  const port = createScannerPort((payload) => seen.push(payload));

  registerScannerBridge(fakeWindow, port);
  fakeWindow.StudioInventoryScanner.push({ data: "weight:256" });
  fakeWindow.StudioInventory.handleScanPayload("spool:PLA-BLK-001");

  assert.deepEqual(seen, ["weight:256", "spool:PLA-BLK-001"]);
}
