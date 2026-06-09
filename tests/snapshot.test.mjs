import assert from "node:assert/strict";

import { createDemoState } from "../core/inventory.js";
import { exportSnapshot, importSnapshotText, makeSnapshotFilename } from "../core/snapshot.js";

testRoundTripSnapshot();
testLegacySnapshotImport();
testInvalidJson();
testInvalidShape();
testFilename();

console.log("snapshot tests passed");

function testRoundTripSnapshot() {
  const state = createDemoState();
  const imported = importSnapshotText(exportSnapshot(state));

  assert.equal(imported.spools.length, state.spools.length);
  assert.equal(imported.parts.length, state.parts.length);
  assert.equal(imported.transactions.length, state.transactions.length);
  assert.equal(imported.spools[0].id, "PLA-BLK-001");
}

function testLegacySnapshotImport() {
  const state = createDemoState();
  const imported = importSnapshotText(JSON.stringify(state));

  assert.equal(imported.parts[0].id, "M3-INSERT");
}

function testInvalidJson() {
  assert.throws(() => importSnapshotText("{bad"), /有效 JSON/);
}

function testInvalidShape() {
  assert.throws(() => importSnapshotText(JSON.stringify({ spools: [] })), /格式不匹配/);
}

function testFilename() {
  assert.equal(makeSnapshotFilename(new Date("2026-06-09T00:00:00.000Z")), "studio-inventory-2026-06-09.json");
}
