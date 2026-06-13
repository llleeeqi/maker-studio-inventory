import assert from "node:assert/strict";

import { parsePayload } from "../core/inventory.js";

testMsiSpool();
testMsiWeight();
testMsiLocation();
testLegacyShortCode();

console.log("msi payload tests passed");

function testMsiSpool() {
  const payload = parsePayload(
    "msi:v1;type=spool;id=PLA-BLK-001;name=黑色PLA;brand=Bambu;material=PLA;color=black;full_g=1200;tare_g=200;net_g=1000;created_on=260613",
  );

  assert.equal(payload.type, "spool");
  assert.equal(payload.value, "PLA-BLK-001");
  assert.equal(payload.fields.material, "PLA");
  assert.equal(payload.fields.created_on, "260613");
}

function testMsiWeight() {
  const payload = parsePayload("msi:v1;type=weight;value_g=712.4");

  assert.equal(payload.type, "weight");
  assert.equal(payload.value, "712.4");
}

function testMsiLocation() {
  const payload = parsePayload("msi:v1;type=location;id=RACK-A01;name=A架01格;created_on=260613");

  assert.equal(payload.type, "location");
  assert.equal(payload.value, "RACK-A01");
}

function testLegacyShortCode() {
  const payload = parsePayload("part:M3-INSERT");

  assert.equal(payload.type, "part");
  assert.equal(payload.value, "M3-INSERT");
}
