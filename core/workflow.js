import { findItem, getStockInfo, parsePayload, updateByWeight, updateLocation } from "./inventory.js";

export const SCAN_MODES = {
  lookup: {
    label: "查库存",
    hint: "扫物品码后自动搜索库存。",
  },
  stocktake: {
    label: "盘点称重",
    hint: "扫重量码和物品码，顺序不限，自动写库存。",
  },
  move: {
    label: "绑定库位",
    hint: "扫库位码和物品码，顺序不限，自动更新库位。",
  },
};

export function createScanSession(mode = "lookup") {
  return {
    mode,
    pendingWeight: null,
    pendingItem: null,
    pendingLocation: null,
  };
}

export function setSessionMode(session, mode) {
  if (!SCAN_MODES[mode]) {
    throw new Error(`不支持的扫码模式：${mode}`);
  }
  session.mode = mode;
  return {
    changed: false,
    message: `${SCAN_MODES[mode].label}：${SCAN_MODES[mode].hint}`,
    session,
  };
}

export function resetSession(session) {
  session.pendingWeight = null;
  session.pendingItem = null;
  session.pendingLocation = null;
  return session;
}

export function applyScanPayload(state, session, rawText) {
  const payload = parsePayload(rawText);
  const response = {
    changed: false,
    payload,
    message: "",
    session,
  };

  if (payload.type === "weight") {
    response.message = applyWeight(session, payload);
  } else if (payload.type === "spool" || payload.type === "part") {
    response.message = applyItem(state, session, payload);
  } else if (payload.type === "location") {
    response.message = applyLocation(session, payload);
  } else {
    response.message = `无法识别：${payload.raw || "空内容"}`;
  }

  const completion = tryCompletePendingAction(state, session);
  if (completion) {
    response.changed = true;
    response.message = completion.message;
  }

  return response;
}

function applyWeight(session, payload) {
  const weight = Number(payload.value);
  if (!Number.isFinite(weight) || weight <= 0) {
    return `重量格式错误：${payload.raw}`;
  }

  session.pendingWeight = weight;
  const next = session.mode === "stocktake" ? "继续扫物品码。" : "切到盘点称重后可用于更新库存。";
  return `收到重量：${weight}g。${next}`;
}

function applyItem(state, session, payload) {
  const item = findItem(state, payload.type, payload.value);

  if (!item) {
    return `找不到物品：${payload.raw}`;
  }

  session.pendingItem = { itemType: payload.type, id: payload.value };

  if (session.mode === "lookup") {
    const stock = getStockInfo(payload.type, item);
    return `${item.id} · ${item.name}：${stock.text}，库位 ${item.location || "未填"}。`;
  }

  if (session.mode === "stocktake") {
    const next = session.pendingWeight == null ? "继续扫重量码。" : "正在更新库存。";
    return `收到物品：${payload.raw}。${next}`;
  }

  const next = session.pendingLocation == null ? "继续扫库位码。" : "正在绑定库位。";
  return `收到物品：${payload.raw}。${next}`;
}

function applyLocation(session, payload) {
  session.pendingLocation = payload.value;
  const next = session.mode === "move" ? "继续扫物品码。" : "切到绑定库位后可用于更新库位。";
  return `收到库位：${payload.value}。${next}`;
}

function tryCompletePendingAction(state, session) {
  if (session.mode === "stocktake" && session.pendingWeight != null && session.pendingItem) {
    const result = updateByWeight(
      state,
      session.pendingItem.itemType,
      session.pendingItem.id,
      session.pendingWeight,
    );
    session.pendingWeight = null;
    session.pendingItem = null;
    return { message: `${result.item.name} 已更新：${result.stockText}。` };
  }

  if (session.mode === "move" && session.pendingLocation && session.pendingItem) {
    const item = updateLocation(
      state,
      session.pendingItem.itemType,
      session.pendingItem.id,
      session.pendingLocation,
    );
    session.pendingItem = null;
    session.pendingLocation = null;
    return { message: `${item.id} · ${item.name} 已绑定库位：${item.location}。` };
  }

  return null;
}
