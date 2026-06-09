import assert from "node:assert/strict";

import { createDemoState } from "../core/inventory.js";
import { mergeStates } from "../core/merge.js";

testRemoteNewerWins();
testLocalNewerStays();
testNewRemoteItemAdded();
testTransactionsDeduped();

console.log("merge tests passed");

function testRemoteNewerWins() {
  const local = createDemoState();
  const remote = createDemoState();
  local.spools[0].name = "Local PLA";
  local.spools[0].updated_at = "2026-01-01T00:00:00.000Z";
  remote.spools[0].name = "Remote PLA";
  remote.spools[0].updated_at = "2026-01-02T00:00:00.000Z";

  const result = mergeStates(local, remote);

  assert.equal(result.state.spools.find((item) => item.id === "PLA-BLK-001").name, "Remote PLA");
}

function testLocalNewerStays() {
  const local = createDemoState();
  const remote = createDemoState();
  local.parts[0].location = "LOCAL-BOX";
  local.parts[0].updated_at = "2026-01-03T00:00:00.000Z";
  remote.parts[0].location = "REMOTE-BOX";
  remote.parts[0].updated_at = "2026-01-02T00:00:00.000Z";

  const result = mergeStates(local, remote);

  assert.equal(result.state.parts.find((item) => item.id === "M3-INSERT").location, "LOCAL-BOX");
}

function testNewRemoteItemAdded() {
  const local = createDemoState();
  const remote = createDemoState();
  remote.parts.push({
    ...remote.parts[0],
    id: "M2-SCREW",
    name: "M2 螺丝",
    updated_at: "2026-01-02T00:00:00.000Z",
  });

  const result = mergeStates(local, remote);

  assert.equal(result.state.parts.some((item) => item.id === "M2-SCREW"), true);
  assert.equal(result.summary.parts.merged, 2);
}

function testTransactionsDeduped() {
  const local = createDemoState();
  const remote = createDemoState();
  const transaction = {
    id: 1,
    item_type: "spool",
    item_id: "PLA-BLK-001",
    action: "update",
    field: "current_wt",
    before_val: 700,
    after_val: 650,
    source: "scan",
    created_at: "2026-01-01T12:00:00.000Z",
  };
  local.transactions.push(transaction);
  remote.transactions.push({ ...transaction, id: 99 });

  const result = mergeStates(local, remote);

  assert.equal(result.state.transactions.length, 1);
  assert.equal(result.state.transactions[0].id, 1);
}
