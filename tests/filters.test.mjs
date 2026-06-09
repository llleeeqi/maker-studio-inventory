import assert from "node:assert/strict";

import { createDemoState, listItems } from "../core/inventory.js";
import { countLowStock, filterInventory } from "../core/filters.js";

testQuerySearch();
testTypeFilter();
testLowStockOnly();
testCombinedFilters();
testArchiveStatusFilter();

console.log("filters tests passed");

function testQuerySearch() {
  const entries = listItems(createDemoState());
  const result = filterInventory(entries, { query: "petg" });

  assert.equal(result.length, 1);
  assert.equal(result[0].item.id, "PETG-CLR-001");
}

function testTypeFilter() {
  const entries = listItems(createDemoState());
  const result = filterInventory(entries, { itemType: "part" });

  assert.equal(result.length, 1);
  assert.equal(result[0].itemType, "part");
}

function testLowStockOnly() {
  const state = createDemoState();
  state.spools[0].current_wt = 220;
  state.spools[1].current_wt = 400;
  const entries = listItems(state);
  const result = filterInventory(entries, { lowOnly: true });

  assert.equal(countLowStock(entries), 1);
  assert.equal(result.length, 1);
  assert.equal(result[0].item.id, "PLA-BLK-001");
}

function testCombinedFilters() {
  const state = createDemoState();
  state.spools[0].current_wt = 220;
  state.spools[1].current_wt = 400;
  const entries = listItems(state);
  const result = filterInventory(entries, {
    query: "PLA",
    itemType: "spool",
    lowOnly: true,
  });

  assert.equal(result.length, 1);
  assert.equal(result[0].item.id, "PLA-BLK-001");
}

function testArchiveStatusFilter() {
  const state = createDemoState();
  state.spools[0].archived_at = "2026-06-09T00:00:00.000Z";
  const entries = listItems(state);

  assert.equal(filterInventory(entries).length, 2);
  assert.equal(filterInventory(entries, { archiveStatus: "archived" }).length, 1);
  assert.equal(filterInventory(entries, { archiveStatus: "all" }).length, 3);
}
