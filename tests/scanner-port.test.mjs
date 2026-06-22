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
  assert.equal(
    normalizeScanResult(" v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200 "),
    "v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200",
  );
}

function testNormalizeScannerObject() {
  assert.equal(normalizeScanResult({ rawValue: "v1;type=part;id=M3-INSERT;name=M3 热熔螺母" }), "v1;type=part;id=M3-INSERT;name=M3 热熔螺母");
  assert.equal(normalizeScanResult({ text: "v1;type=weight;value_g=712.4" }), "v1;type=weight;value_g=712.4");
  assert.equal(normalizeScanResult({ payload: "v1;type=location;id=RACK-A01;name=A01 库位" }), "v1;type=location;id=RACK-A01;name=A01 库位");
}

function testRejectInvalidResult() {
  assert.throws(() => normalizeScanResult(" "), /不能为空/);
  assert.throws(() => normalizeScanResult({ code: "nope" }), /扫码结果必须/);
}

function testPortCallback() {
  const seen = [];
  const port = createScannerPort((payload) => seen.push(payload));

  const payload = port.push({ value: "v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200" });

  assert.equal(payload, "v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200");
  assert.deepEqual(seen, ["v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200"]);
}

function testBridgeRegistration() {
  const fakeWindow = {};
  const seen = [];
  const port = createScannerPort((payload) => seen.push(payload));

  registerScannerBridge(fakeWindow, port);
  fakeWindow.StudioInventoryScanner.push({ data: "v1;type=weight;value_g=256" });
  fakeWindow.StudioInventory.handleScanPayload("v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200");

  assert.deepEqual(seen, ["v1;type=weight;value_g=256", "v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200"]);
}
