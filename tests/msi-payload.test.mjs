import assert from "node:assert/strict";

import { parsePayload } from "../core/inventory.js";

testV1Spool();
testV1Weight();
testV1Location();
testV1EncodedLocation();
testRejectLegacyShortCode();

console.log("v1 payload tests passed");

function testV1Spool() {
  const payload = parsePayload(
    "v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200;created_on=260613",
  );

  assert.equal(payload.type, "spool");
  assert.equal(payload.value, "PLA-BLK-001");
  assert.equal(payload.fields.material, "PLA");
  assert.equal(payload.fields.created_on, "260613");
}

function testV1Weight() {
  const payload = parsePayload("v1;type=weight;value_g=712.4");

  assert.equal(payload.type, "weight");
  assert.equal(payload.value, "712.4");
}

function testV1Location() {
  const payload = parsePayload("v1;type=location;id=RACK-A01;name=A架01格;created_on=260613");

  assert.equal(payload.type, "location");
  assert.equal(payload.value, "RACK-A01");
}

function testV1EncodedLocation() {
  const payload = parsePayload("v1;type=location;id=LOC-TEST-001;name=%E6%B5%8B%E8%AF%95%E5%BA%93%E4%BD%8D;created_on=260622");

  assert.equal(payload.type, "location");
  assert.equal(payload.value, "LOC-TEST-001");
  assert.equal(payload.fields.name, "测试库位");
}

function testRejectLegacyShortCode() {
  const payload = parsePayload("part:M3-INSERT");

  assert.equal(payload.type, "unknown");
  assert.equal(payload.value, "part:M3-INSERT");
}
