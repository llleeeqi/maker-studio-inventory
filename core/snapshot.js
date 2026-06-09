const SNAPSHOT_SCHEMA = "studio-inventory-snapshot";
const SNAPSHOT_VERSION = 1;

export function exportSnapshot(state) {
  return JSON.stringify(
    {
      schema: SNAPSHOT_SCHEMA,
      version: SNAPSHOT_VERSION,
      exported_at: new Date().toISOString(),
      state: normalizeState(state),
    },
    null,
    2,
  );
}

export function importSnapshotText(text) {
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    throw new Error("快照不是有效 JSON。");
  }

  if (data.schema === SNAPSHOT_SCHEMA && data.version === SNAPSHOT_VERSION && data.state) {
    return normalizeState(data.state);
  }

  // Backward compatibility for early exports that placed arrays at the top level.
  if (Array.isArray(data.spools) && Array.isArray(data.parts) && Array.isArray(data.transactions)) {
    return normalizeState(data);
  }

  throw new Error("快照格式不匹配。");
}

export function makeSnapshotFilename(date = new Date()) {
  return `studio-inventory-${date.toISOString().slice(0, 10)}.json`;
}

function normalizeState(state) {
  const spools = ensureArray(state.spools, "spools").map(normalizeSpool);
  const parts = ensureArray(state.parts, "parts").map(normalizePart);
  const transactions = ensureArray(state.transactions, "transactions").map(normalizeTransaction);
  return { spools, parts, transactions };
}

function normalizeSpool(item) {
  const id = stringRequired(item.id, "耗材卷 ID");
  return {
    id,
    name: stringRequired(item.name, `${id} 名称`),
    brand: stringOptional(item.brand),
    material: stringOptional(item.material),
    color: stringOptional(item.color),
    empty_wt: numberRequired(item.empty_wt, `${id} 空卷重量`),
    current_wt: numberOptional(item.current_wt),
    location: stringOptional(item.location),
    min_alarm: numberOptional(item.min_alarm, 100),
    archived_at: stringOptional(item.archived_at),
    created_at: dateOptional(item.created_at),
    updated_at: dateOptional(item.updated_at),
  };
}

function normalizePart(item) {
  const id = stringRequired(item.id, "零件 ID");
  return {
    id,
    name: stringRequired(item.name, `${id} 名称`),
    unit_wt: numberRequired(item.unit_wt, `${id} 单件重量`),
    container_wt: numberOptional(item.container_wt, 0),
    estimated_qty: integerOptional(item.estimated_qty),
    location: stringOptional(item.location),
    min_alarm: integerOptional(item.min_alarm, 10),
    archived_at: stringOptional(item.archived_at),
    created_at: dateOptional(item.created_at),
    updated_at: dateOptional(item.updated_at),
  };
}

function normalizeTransaction(item) {
  return {
    id: integerOptional(item.id, 0),
    item_type: stringRequired(item.item_type, "流水物品类型"),
    item_id: stringRequired(item.item_id, "流水物品 ID"),
    action: stringRequired(item.action, "流水操作"),
    field: stringRequired(item.field, "流水字段"),
    before_val: item.before_val ?? null,
    after_val: item.after_val ?? null,
    source: stringOptional(item.source, "unknown"),
    created_at: dateOptional(item.created_at),
  };
}

function ensureArray(value, label) {
  if (!Array.isArray(value)) {
    throw new Error(`${label} 必须是数组。`);
  }
  return value;
}

function stringRequired(value, label) {
  const text = String(value ?? "").trim();
  if (!text) {
    throw new Error(`${label} 不能为空。`);
  }
  return text;
}

function stringOptional(value, fallback = "") {
  const text = String(value ?? "").trim();
  return text || fallback;
}

function numberRequired(value, label) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`${label} 必须是非负数字。`);
  }
  return number;
}

function numberOptional(value, fallback = null) {
  if (value == null || value === "") return fallback;
  const number = Number(value);
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

function dateOptional(value) {
  const text = String(value ?? "").trim();
  return text || new Date().toISOString();
}
