import assert from "node:assert/strict";

import { createDemoState, getStockInfo } from "../core/inventory.js";
import { applyScanPayload, createScanSession, setSessionMode } from "../core/workflow.js";

testLookup();
testWeightAutoSwitchesToStocktake();
testStocktakeAnyOrder();
testMoveAnyOrder();
testUnknownItemDoesNotComplete();

console.log("workflow tests passed");

function testLookup() {
  const state = createDemoState();
  const session = createScanSession("lookup");
  const result = applyScanPayload(state, session, "spool:PLA-BLK-001");

  assert.equal(result.changed, false);
  assert.match(result.message, /PLA-BLK-001/);
  assert.match(result.message, /RACK-A01/);
  assert.equal(state.transactions.length, 0);
}

function testWeightAutoSwitchesToStocktake() {
  const state = createDemoState();
  const session = createScanSession("lookup");
  const weightResult = applyScanPayload(state, session, "weight:700");

  assert.equal(weightResult.changed, false);
  assert.equal(session.mode, "stocktake");
  assert.match(weightResult.message, /已切到盘点称重/);

  const itemResult = applyScanPayload(state, session, "spool:PLA-BLK-001");

  assert.equal(itemResult.changed, true);
  assert.equal(getStockInfo("spool", state.spools[0]).text, "522g");
}

function testStocktakeAnyOrder() {
  const state = createDemoState();
  const session = createScanSession();
  setSessionMode(session, "stocktake");

  applyScanPayload(state, session, "weight:700");
  const result = applyScanPayload(state, session, "spool:PLA-BLK-001");

  assert.equal(result.changed, true);
  assert.equal(getStockInfo("spool", state.spools[0]).text, "522g");
  assert.equal(state.transactions.length, 1);

  const reverse = createScanSession("stocktake");
  applyScanPayload(state, reverse, "part:M3-INSERT");
  const partResult = applyScanPayload(state, reverse, "weight:69");

  assert.equal(partResult.changed, true);
  assert.equal(state.parts[0].estimated_qty, 100);
  assert.equal(state.transactions.length, 2);
}

function testMoveAnyOrder() {
  const state = createDemoState();
  const session = createScanSession("move");

  applyScanPayload(state, session, "location:RACK-Z09");
  const result = applyScanPayload(state, session, "spool:PLA-BLK-001");

  assert.equal(result.changed, true);
  assert.equal(state.spools[0].location, "RACK-Z09");

  const reverse = createScanSession("move");
  applyScanPayload(state, reverse, "part:M3-INSERT");
  const reverseResult = applyScanPayload(state, reverse, "location:BOX-Z10");

  assert.equal(reverseResult.changed, true);
  assert.equal(state.parts[0].location, "BOX-Z10");
}

function testUnknownItemDoesNotComplete() {
  const state = createDemoState();
  const session = createScanSession("stocktake");

  applyScanPayload(state, session, "weight:700");
  const result = applyScanPayload(state, session, "spool:NOPE");

  assert.equal(result.changed, false);
  assert.match(result.message, /找不到物品/);
  assert.equal(state.transactions.length, 0);
}
