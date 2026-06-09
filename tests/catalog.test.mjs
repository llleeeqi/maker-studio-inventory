import assert from "node:assert/strict";

import { createDemoState, findItem } from "../core/inventory.js";
import {
  archiveCatalogItem,
  cloneCatalogForm,
  createEmptyCatalogForm,
  formFromItem,
  generateCatalogId,
  saveCatalogItem,
} from "../core/catalog.js";

testCreateSpool();
testUpdatePart();
testDuplicateIdUpdates();
testArchiveAndRestore();
testCloneCatalogForm();
testGenerateCatalogId();
testValidation();

console.log("catalog tests passed");

function testCreateSpool() {
  const state = createDemoState();
  const form = {
    ...createEmptyCatalogForm("spool"),
    id: "pla-red-002",
    name: "PLA+",
    brand: "Bambu",
    material: "PLA",
    color: "红色",
    empty_wt: "180",
    current_wt: "680.5",
    location: "RACK-R01",
    min_alarm: "120",
  };

  const result = saveCatalogItem(state, form);

  assert.equal(result.action, "create");
  assert.equal(result.itemType, "spool");
  assert.equal(result.item.id, "PLA-RED-002");
  assert.equal(result.item.empty_wt, 180);
  assert.equal(state.spools.length, 3);
  assert.equal(state.transactions.at(-1).action, "create");
}

function testUpdatePart() {
  const state = createDemoState();
  const part = findItem(state, "part", "M3-INSERT");
  const form = {
    ...formFromItem("part", part),
    estimated_qty: "900",
    location: "BOX-Z01",
  };

  const result = saveCatalogItem(state, form);

  assert.equal(result.action, "update");
  assert.equal(result.itemType, "part");
  assert.equal(part.estimated_qty, 900);
  assert.equal(part.location, "BOX-Z01");
  assert.equal(state.transactions.at(-1).field, "catalog");
}

function testDuplicateIdUpdates() {
  const state = createDemoState();
  const form = {
    ...createEmptyCatalogForm("spool"),
    id: "PLA-BLK-001",
    name: "PLA Pro",
    empty_wt: "179",
  };

  const result = saveCatalogItem(state, form);

  assert.equal(result.action, "update");
  assert.equal(state.spools.length, 2);
  assert.equal(state.spools[0].name, "PLA Pro");
}

function testArchiveAndRestore() {
  const state = createDemoState();
  const archived = archiveCatalogItem(state, "spool", "PLA-BLK-001", true);

  assert.ok(archived.archived_at);
  assert.equal(state.transactions.at(-1).action, "archive");

  const restored = archiveCatalogItem(state, "spool", "PLA-BLK-001", false);

  assert.equal(restored.archived_at, "");
  assert.equal(state.transactions.at(-1).action, "restore");
}

function testCloneCatalogForm() {
  const state = createDemoState();
  const form = cloneCatalogForm(state, "spool", "PLA-BLK-001");

  assert.equal(form.id, "PLA-BLK-002");
  assert.equal(form.material, "PLA");
  assert.equal(form.color, "黑色");
  assert.equal(form.location, "");
  assert.equal(form.current_wt, "");
}

function testGenerateCatalogId() {
  const state = createDemoState();

  assert.equal(generateCatalogId(state, "spool", { material: "PETG", color: "透明" }), "PETG-CLR-002");
  assert.equal(generateCatalogId(state, "part", { id: "m3-insert", name: "M3 Insert" }), "M3-INSERT-001");
}

function testValidation() {
  const state = createDemoState();
  assert.throws(
    () =>
      saveCatalogItem(state, {
        ...createEmptyCatalogForm("part"),
        id: "m2-screw",
        name: "M2 螺丝",
        unit_wt: "-1",
      }),
    /单件重量/,
  );
}
