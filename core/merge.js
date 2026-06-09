export function mergeStates(localState, remoteState) {
  const result = {
    spools: mergeItems(localState.spools, remoteState.spools),
    parts: mergeItems(localState.parts, remoteState.parts),
    transactions: mergeTransactions(localState.transactions, remoteState.transactions),
  };

  return {
    state: result,
    summary: {
      spools: summarize(localState.spools, remoteState.spools, result.spools),
      parts: summarize(localState.parts, remoteState.parts, result.parts),
      transactions: result.transactions.length,
    },
  };
}

export function previewMergeStates(localState, remoteState) {
  return {
    spools: previewItems(localState.spools, remoteState.spools),
    parts: previewItems(localState.parts, remoteState.parts),
    transactions: previewTransactions(localState.transactions, remoteState.transactions),
  };
}

function mergeItems(localItems, remoteItems) {
  const byId = new Map();
  for (const item of localItems) byId.set(item.id, item);

  for (const remote of remoteItems) {
    const local = byId.get(remote.id);
    if (!local || isRemoteNewer(local, remote)) {
      byId.set(remote.id, remote);
    }
  }

  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
}

function previewItems(localItems, remoteItems) {
  const localById = new Map(localItems.map((item) => [item.id, item]));
  const remoteById = new Map(remoteItems.map((item) => [item.id, item]));
  const addedFromRemote = [];
  const remoteOverrides = [];
  const localKeeps = [];
  const localOnly = [];

  for (const remote of remoteItems) {
    const local = localById.get(remote.id);
    if (!local) {
      addedFromRemote.push(remote.id);
    } else if (isRemoteNewer(local, remote)) {
      remoteOverrides.push(remote.id);
    } else {
      localKeeps.push(remote.id);
    }
  }

  for (const local of localItems) {
    if (!remoteById.has(local.id)) localOnly.push(local.id);
  }

  return {
    addedFromRemote: addedFromRemote.sort(),
    remoteOverrides: remoteOverrides.sort(),
    localKeeps: localKeeps.sort(),
    localOnly: localOnly.sort(),
  };
}

function previewTransactions(localTransactions, remoteTransactions) {
  const localKeys = new Set(localTransactions.map(transactionKey));
  const incoming = remoteTransactions.filter((transaction) => !localKeys.has(transactionKey(transaction))).length;
  return {
    local: localTransactions.length,
    remote: remoteTransactions.length,
    incoming,
    merged: mergeTransactions(localTransactions, remoteTransactions).length,
  };
}

function mergeTransactions(localTransactions, remoteTransactions) {
  const byKey = new Map();
  for (const transaction of [...localTransactions, ...remoteTransactions]) {
    byKey.set(transactionKey(transaction), transaction);
  }

  return [...byKey.values()]
    .sort((a, b) => dateValue(a.created_at) - dateValue(b.created_at))
    .map((transaction, index) => ({ ...transaction, id: index + 1 }));
}

function summarize(localItems, remoteItems, mergedItems) {
  return {
    local: localItems.length,
    remote: remoteItems.length,
    merged: mergedItems.length,
  };
}

function isRemoteNewer(local, remote) {
  return dateValue(remote.updated_at) > dateValue(local.updated_at);
}

function dateValue(value) {
  const time = Date.parse(value);
  return Number.isFinite(time) ? time : 0;
}

function transactionKey(transaction) {
  return [
    transaction.item_type,
    transaction.item_id,
    transaction.action,
    transaction.field,
    transaction.created_at,
    JSON.stringify(transaction.before_val),
    JSON.stringify(transaction.after_val),
  ].join("|");
}
