import assert from "node:assert/strict";

import { getQrInfo, renderQrSvg } from "../app/qr.js";

testReadableV1PayloadOverLegacyLimit();
testCapacityLimitMessage();

console.log("qr tests passed");

function testReadableV1PayloadOverLegacyLimit() {
  const payload =
    "v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200;created_on=260613;note=虚拟货架测试";
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
