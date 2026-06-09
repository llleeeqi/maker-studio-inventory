export function createScannerPort(onPayload, options = {}) {
  return {
    push(scanResult) {
      try {
        const payload = normalizeScanResult(scanResult);
        onPayload(payload);
        return payload;
      } catch (error) {
        if (options.onError) {
          options.onError(error);
          return null;
        }
        throw error;
      }
    },
  };
}

export function normalizeScanResult(scanResult) {
  if (typeof scanResult === "string") {
    return cleanPayload(scanResult);
  }

  if (scanResult && typeof scanResult === "object") {
    for (const key of ["payload", "text", "rawValue", "raw", "value", "data"]) {
      if (typeof scanResult[key] === "string") {
        return cleanPayload(scanResult[key]);
      }
    }
  }

  throw new Error("扫码结果必须是字符串，或包含 payload/text/rawValue/raw/value/data 字段。");
}

export function registerScannerBridge(targetWindow, scannerPort) {
  targetWindow.StudioInventoryScanner = {
    push: scannerPort.push,
  };

  // Backward-compatible alias for earlier notes and quick manual tests.
  targetWindow.StudioInventory = {
    handleScanPayload: scannerPort.push,
  };
}

function cleanPayload(value) {
  const payload = value.trim();
  if (!payload) {
    throw new Error("扫码结果不能为空。");
  }
  return payload;
}
