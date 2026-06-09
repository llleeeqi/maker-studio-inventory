export function createDemoState() {
  const timestamp = now();
  return {
    spools: [
      {
        id: "PLA-BLK-001",
        name: "PLA+",
        brand: "Bambu",
        material: "PLA",
        color: "黑色",
        empty_wt: 178,
        current_wt: 712.4,
        location: "RACK-A01",
        min_alarm: 100,
        archived_at: "",
        created_at: timestamp,
        updated_at: timestamp,
      },
      {
        id: "PETG-CLR-001",
        name: "PETG",
        brand: "eSUN",
        material: "PETG",
        color: "透明",
        empty_wt: 185,
        current_wt: 244,
        location: "RACK-A02",
        min_alarm: 120,
        archived_at: "",
        created_at: timestamp,
        updated_at: timestamp,
      },
    ],
    parts: [
      {
        id: "M3-INSERT",
        name: "M3 热熔螺母",
        unit_wt: 0.27,
        container_wt: 42,
        estimated_qty: 800,
        location: "BOX-B03",
        min_alarm: 100,
        archived_at: "",
        created_at: timestamp,
        updated_at: timestamp,
      },
    ],
    transactions: [],
  };
}

export function parsePayload(text) {
  const payload = String(text || "").trim();
  const index = payload.indexOf(":");
  if (index < 1) {
    return { type: "unknown", value: payload, raw: payload };
  }

  const type = payload.slice(0, index).trim().toLowerCase();
  const value = payload.slice(index + 1).trim();
  return { type, value, raw: payload };
}

export function findItem(state, type, id) {
  if (type === "spool") {
    return state.spools.find((item) => item.id === id) || null;
  }
  if (type === "part") {
    return state.parts.find((item) => item.id === id) || null;
  }
  return null;
}

export function updateByWeight(state, itemType, itemId, grossWeight, source = "scan") {
  const item = findItem(state, itemType, itemId);
  if (!item) {
    throw new Error(`找不到物品：${itemType}:${itemId}`);
  }

  if (itemType === "spool") {
    const before = item.current_wt ?? null;
    item.current_wt = round(grossWeight, 1);
    item.updated_at = now();
    addTransaction(state, itemType, itemId, "update", "current_wt", before, item.current_wt, source);
    return {
      item,
      field: "current_wt",
      stockText: `${round(item.current_wt - item.empty_wt, 1)}g 可用`,
    };
  }

  if (itemType === "part") {
    const before = item.estimated_qty ?? null;
    const qty = Math.max(0, Math.floor((grossWeight - item.container_wt) / item.unit_wt));
    item.estimated_qty = qty;
    item.updated_at = now();
    addTransaction(state, itemType, itemId, "update", "estimated_qty", before, qty, source);
    return {
      item,
      field: "estimated_qty",
      stockText: `${qty} 件`,
    };
  }

  throw new Error(`不支持的物品类型：${itemType}`);
}

export function updateLocation(state, itemType, itemId, location, source = "scan") {
  const item = findItem(state, itemType, itemId);
  if (!item) {
    throw new Error(`找不到物品：${itemType}:${itemId}`);
  }

  const before = item.location || null;
  item.location = location;
  item.updated_at = now();
  addTransaction(state, itemType, itemId, "update", "location", before, location, source);
  return item;
}

export function getStockInfo(itemType, item) {
  if (itemType === "spool") {
    const usable = item.current_wt == null ? null : round(item.current_wt - item.empty_wt, 1);
    return {
      text: usable == null ? "未称重" : `${usable}g`,
      low: usable != null && usable < item.min_alarm,
    };
  }

  return {
    text: item.estimated_qty == null ? "未估算" : `${item.estimated_qty} 件`,
    low: item.estimated_qty != null && item.estimated_qty < item.min_alarm,
  };
}

export function listItems(state) {
  return [
    ...state.spools.map((item) => ({ itemType: "spool", item })),
    ...state.parts.map((item) => ({ itemType: "part", item })),
  ];
}

function addTransaction(state, itemType, itemId, action, field, beforeVal, afterVal, source) {
  state.transactions.push({
    id: state.transactions.length + 1,
    item_type: itemType,
    item_id: itemId,
    action,
    field,
    before_val: beforeVal,
    after_val: afterVal,
    source,
    created_at: now(),
  });
}

function now() {
  return new Date().toISOString();
}

function round(value, digits) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}
