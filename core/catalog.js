import { findItem } from "./inventory.js";

const COLOR_CODES = {
  黑: "BLK",
  黑色: "BLK",
  白: "WHT",
  白色: "WHT",
  透明: "CLR",
  红: "RED",
  红色: "RED",
  蓝: "BLU",
  蓝色: "BLU",
  绿: "GRN",
  绿色: "GRN",
  黄: "YLW",
  黄色: "YLW",
  灰: "GRY",
  灰色: "GRY",
  银: "SLV",
  银色: "SLV",
};

export function createEmptyCatalogForm(itemType = "spool") {
  return itemType === "part"
    ? {
        itemType: "part",
        id: "",
        name: "",
        unit_wt: "",
        container_wt: "0",
        estimated_qty: "",
        location: "",
        min_alarm: "10",
        archived_at: "",
      }
    : {
        itemType: "spool",
        id: "",
        name: "",
        brand: "",
        material: "",
        color: "",
        empty_wt: "",
        current_wt: "",
        location: "",
        min_alarm: "100",
        archived_at: "",
      };
}

export function formFromItem(itemType, item) {
  return {
    itemType,
    ...Object.fromEntries(
      Object.entries(item).map(([key, value]) => [key, value == null ? "" : String(value)]),
    ),
  };
}

export function saveCatalogItem(state, form) {
  if (form.itemType === "spool") {
    return saveSpool(state, form);
  }
  if (form.itemType === "part") {
    return savePart(state, form);
  }
  throw new Error(`不支持的物品类型：${form.itemType}`);
}

export function archiveCatalogItem(state, itemType, id, archived = true) {
  const item = findItem(state, itemType, id);
  if (!item) {
    throw new Error(`找不到物品：${itemType}:${id}`);
  }

  const before = item.archived_at || "";
  const timestamp = new Date().toISOString();
  item.archived_at = archived ? timestamp : "";
  item.updated_at = timestamp;
  addCatalogTransaction(
    state,
    itemType,
    id,
    archived ? "archive" : "restore",
    "archived_at",
    before,
    item.archived_at,
  );
  return item;
}

export function cloneCatalogForm(state, itemType, id) {
  const item = findItem(state, itemType, id);
  if (!item) {
    throw new Error(`找不到物品：${itemType}:${id}`);
  }

  const form = formFromItem(itemType, item);
  form.id = generateCatalogId(state, itemType, form);
  form.location = "";
  form.archived_at = "";
  if (itemType === "spool") {
    form.current_wt = "";
  } else {
    form.estimated_qty = "";
  }
  return form;
}

export function generateCatalogId(state, itemType, values = {}) {
  const prefix = itemType === "spool" ? makeSpoolPrefix(values) : makePartPrefix(values);
  const ids = new Set((itemType === "spool" ? state.spools : state.parts).map((item) => item.id));

  for (let number = 1; number < 10000; number += 1) {
    const id = `${prefix}-${String(number).padStart(3, "0")}`;
    if (!ids.has(id)) return id;
  }

  throw new Error(`无法为 ${prefix} 生成可用 ID。`);
}

function saveSpool(state, form) {
  const id = cleanId(form.id);
  const name = cleanText(form.name);
  if (!id) throw new Error("耗材卷 ID 不能为空。");
  if (!name) throw new Error("耗材名称不能为空。");

  const item = {
    id,
    name,
    brand: cleanText(form.brand),
    material: cleanText(form.material),
    color: cleanText(form.color),
    empty_wt: numberRequired(form.empty_wt, "空卷重量"),
    current_wt: numberOptional(form.current_wt),
    location: cleanText(form.location),
    min_alarm: numberOptional(form.min_alarm, 100),
    archived_at: cleanText(form.archived_at),
  };

  return upsertItem(state, "spool", item);
}

function savePart(state, form) {
  const id = cleanId(form.id);
  const name = cleanText(form.name);
  if (!id) throw new Error("零件 ID 不能为空。");
  if (!name) throw new Error("零件名称不能为空。");

  const item = {
    id,
    name,
    unit_wt: numberRequired(form.unit_wt, "单件重量"),
    container_wt: numberOptional(form.container_wt, 0),
    estimated_qty: integerOptional(form.estimated_qty),
    location: cleanText(form.location),
    min_alarm: integerOptional(form.min_alarm, 10),
    archived_at: cleanText(form.archived_at),
  };

  return upsertItem(state, "part", item);
}

function upsertItem(state, itemType, values) {
  const existing = findItem(state, itemType, values.id);
  const timestamp = new Date().toISOString();
  const collection = itemType === "spool" ? state.spools : state.parts;

  if (existing) {
    const before = JSON.stringify(existing);
    Object.assign(existing, values, { updated_at: timestamp });
    addCatalogTransaction(state, itemType, values.id, "update", "catalog", before, JSON.stringify(existing));
    return { action: "update", itemType, item: existing };
  }

  const item = {
    ...values,
    created_at: timestamp,
    updated_at: timestamp,
  };
  collection.push(item);
  addCatalogTransaction(state, itemType, values.id, "create", "catalog", null, JSON.stringify(item));
  return { action: "create", itemType, item };
}

function addCatalogTransaction(state, itemType, itemId, action, field, beforeVal, afterVal) {
  state.transactions.push({
    id: state.transactions.length + 1,
    item_type: itemType,
    item_id: itemId,
    action,
    field,
    before_val: beforeVal,
    after_val: afterVal,
    source: "manual",
    created_at: new Date().toISOString(),
  });
}

function cleanId(value) {
  return cleanText(value).toUpperCase();
}

function cleanText(value) {
  return String(value ?? "").trim();
}

function numberRequired(value, label) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`${label}必须是非负数字。`);
  }
  return number;
}

function numberOptional(value, fallback = null) {
  const text = cleanText(value);
  if (!text) return fallback;
  const number = Number(text);
  if (!Number.isFinite(number) || number < 0) {
    throw new Error("数字字段必须是非负数字。");
  }
  return number;
}

function integerOptional(value, fallback = null) {
  const number = numberOptional(value, fallback);
  if (number == null) return null;
  if (!Number.isInteger(number)) {
    throw new Error("数量字段必须是整数。");
  }
  return number;
}

function makeSpoolPrefix(values) {
  const material = slugPart(values.material || values.name || "SPOOL");
  const color = colorCode(values.color) || "MIX";
  return `${material}-${color}`;
}

function makePartPrefix(values) {
  return slugPart(values.name || values.id || "PART");
}

function colorCode(value) {
  const text = cleanText(value);
  if (!text) return "";
  return COLOR_CODES[text] || COLOR_CODES[text.replace(/色$/, "")] || slugPart(text);
}

function slugPart(value) {
  const text = cleanText(value)
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return text || "ITEM";
}
