import { getStockInfo } from "./inventory.js";

export function filterInventory(entries, options = {}) {
  const query = normalize(options.query);
  const itemType = options.itemType || "all";
  const lowOnly = Boolean(options.lowOnly);
  const archiveStatus = options.archiveStatus || "active";

  return entries.filter((entry) => {
    if (!matchesArchiveStatus(entry.item, archiveStatus)) return false;
    if (itemType !== "all" && entry.itemType !== itemType) return false;
    if (lowOnly && !getStockInfo(entry.itemType, entry.item).low) return false;
    if (!query) return true;
    return makeSearchText(entry).includes(query);
  });
}

export function countLowStock(entries) {
  return entries.filter((entry) => !entry.item.archived_at && getStockInfo(entry.itemType, entry.item).low).length;
}

function makeSearchText(entry) {
  const item = entry.item;
  return [
    entry.itemType,
    item.id,
    item.name,
    item.brand,
    item.material,
    item.color,
    item.location,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
}

function matchesArchiveStatus(item, archiveStatus) {
  const archived = Boolean(item.archived_at);
  if (archiveStatus === "all") return true;
  if (archiveStatus === "archived") return archived;
  return !archived;
}

function normalize(value) {
  return String(value ?? "").trim().toLowerCase();
}
