import { createDemoState } from "../core/inventory.js";

const STORAGE_KEY = "studio-inventory-v0";

export function loadState() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    const state = createDemoState();
    saveState(state);
    return state;
  }

  try {
    return JSON.parse(raw);
  } catch {
    return resetState();
  }
}

export function saveState(state) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function resetState() {
  const state = createDemoState();
  saveState(state);
  return state;
}
