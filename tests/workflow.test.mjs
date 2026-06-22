import assert from "node:assert/strict";

import { createDemoState, getStockInfo } from "../core/inventory.js";
import { applyScanPayload, createScanSession, setSessionMode } from "../core/workflow.js";

const SPOOL_QR = "v1;type=spool;id=PLA-BLK-001;brand=Bambu;material=PLA;color=black;tare_g=200;created_on=260613";
const PART_QR = "v1;type=part;id=M3-INSERT;name=M3 热熔螺母;unit_weight_g=0.27;created_on=260613";
const WEIGHT_700_QR = "v1;type=weight;value_g=700";
const WEIGHT_69_QR = "v1;type=weight;value_g=69";
const LOCATION_A01_QR = "v1;type=location;id=RACK-A01;name=A01 库位;created_on=260613";
const LOCATION_Z09_QR = "v1;type=location;id=RACK-Z09;name=Z09 库位;created_on=260613";
const LOCATION_BOX_Z10_QR = "v1;type=location;id=BOX-Z10;name=BOX Z10;created_on=260613";

testLookup();
testV1Lookup();
testWeightAutoSwitchesToStocktake();
testStocktakeAnyOrder();
testMoveAnyOrder();
testUnknownItemDoesNotComplete();

console.log("workflow tests passed");

function testLookup() {
  const state = createDemoState();
  const session = createScanSession("lookup");
  const result = applyScanPayload(state, session, SPOOL_QR);

  assert.equal(result.changed, false);
  assert.match(result.message, /PLA-BLK-001/);
  assert.match(result.message, /RACK-A01/);
  assert.equal(state.transactions.length, 0);
}

function testV1Lookup() {
  const state = createDemoState();
  const session = createScanSession("lookup");
  const result = applyScanPayload(state, session, SPOOL_QR);

  assert.equal(result.changed, false);
  assert.match(result.message, /PLA-BLK-001/);
  assert.match(result.message, /RACK-A01/);
  assert.equal(state.transactions.length, 0);
}

function testWeightAutoSwitchesToStocktake() {
  const state = createDemoState();
  const session = createScanSession("lookup");
  const weightResult = applyScanPayload(state, session, WEIGHT_700_QR);

  assert.equal(weightResult.changed, false);
  assert.equal(session.mode, "stocktake");
  assert.match(weightResult.message, /已切到盘点称重/);

  const itemResult = applyScanPayload(state, session, SPOOL_QR);

  assert.equal(itemResult.changed, true);
  assert.equal(getStockInfo("spool", state.spools[0]).text, "522g");
}

function testStocktakeAnyOrder() {
  const state = createDemoState();
  const session = createScanSession();
  setSessionMode(session, "stocktake");

  applyScanPayload(state, session, WEIGHT_700_QR);
  const result = applyScanPayload(state, session, SPOOL_QR);

  assert.equal(result.changed, true);
  assert.equal(getStockInfo("spool", state.spools[0]).text, "522g");
  assert.equal(state.transactions.length, 1);

  const reverse = createScanSession("stocktake");
  applyScanPayload(state, reverse, PART_QR);
  const partResult = applyScanPayload(state, reverse, WEIGHT_69_QR);

  assert.equal(partResult.changed, true);
  assert.equal(state.parts[0].estimated_qty, 100);
  assert.equal(state.transactions.length, 2);
}

function testMoveAnyOrder() {
  const state = createDemoState();
  const session = createScanSession("move");

  applyScanPayload(state, session, LOCATION_Z09_QR);
  const result = applyScanPayload(state, session, SPOOL_QR);

  assert.equal(result.changed, true);
  assert.equal(state.spools[0].location, "RACK-Z09");

  const reverse = createScanSession("move");
  applyScanPayload(state, reverse, PART_QR);
  const reverseResult = applyScanPayload(state, reverse, LOCATION_BOX_Z10_QR);

  assert.equal(reverseResult.changed, true);
  assert.equal(state.parts[0].location, "BOX-Z10");
}

function testUnknownItemDoesNotComplete() {
  const state = createDemoState();
  const session = createScanSession("stocktake");

  applyScanPayload(state, session, WEIGHT_700_QR);
  const result = applyScanPayload(
    state,
    session,
    "v1;type=spool;id=NOPE;brand=Bambu;material=PLA;color=black;tare_g=200;created_on=260613",
  );

  assert.equal(result.changed, false);
  assert.match(result.message, /找不到物品/);
  assert.equal(state.transactions.length, 0);
}
