import assert from "node:assert/strict";

import { getQrInfo, renderQrSvg } from "../app/qr.js";

testReadableMsiPayloadOverLegacyLimit();
testCapacityLimitMessage();

console.log("qr tests passed");

function testReadableMsiPayloadOverLegacyLimit() {
  const payload =
    "msi:v1;type=spool;id=PLA-BLK-001;name=黑色PLA;brand=Bambu;material=PLA;color=black;full_g=1200;tare_g=200;net_g=1000;created_on=260613";
  const info = getQrInfo(payload);

  assert.equal(info.fits, true);
  assert.ok(info.bytes > 78);
  assert.ok(info.version > 4);
  assert.match(renderQrSvg(payload), /^<svg /);
}

function testCapacityLimitMessage() {
  const payload = `raw:${"x".repeat(272)}`;
  const info = getQrInfo(payload);

  assert.equal(info.fits, false);
  assert.equal(info.maxSupportedBytes, 271);
  assert.throws(() => renderQrSvg(payload), /最多支持 271 字节/);
}
