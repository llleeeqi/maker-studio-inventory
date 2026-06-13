const VERSION_TABLE = [
  { version: 1, size: 21, dataCodewords: 19, eccCodewords: 7, alignment: [], blocks: [19] },
  { version: 2, size: 25, dataCodewords: 34, eccCodewords: 10, alignment: [6, 18], blocks: [34] },
  { version: 3, size: 29, dataCodewords: 55, eccCodewords: 15, alignment: [6, 22], blocks: [55] },
  { version: 4, size: 33, dataCodewords: 80, eccCodewords: 20, alignment: [6, 26], blocks: [80] },
  { version: 5, size: 37, dataCodewords: 108, eccCodewords: 26, alignment: [6, 30], blocks: [108] },
  { version: 6, size: 41, dataCodewords: 136, eccCodewords: 18, alignment: [6, 34], blocks: [68, 68] },
  { version: 7, size: 45, dataCodewords: 156, eccCodewords: 20, alignment: [6, 22, 38], blocks: [78, 78] },
  { version: 8, size: 49, dataCodewords: 194, eccCodewords: 24, alignment: [6, 24, 42], blocks: [97, 97] },
  { version: 9, size: 53, dataCodewords: 232, eccCodewords: 30, alignment: [6, 26, 46], blocks: [116, 116] },
  { version: 10, size: 57, dataCodewords: 274, eccCodewords: 18, alignment: [6, 28, 50], blocks: [68, 68, 69, 69] },
];

const MAX_SUPPORTED_BYTES = maxSupportedBytes();

export function renderQrSvg(text, scale = 8, border = 4) {
  const qr = makeQr(text);
  const pixels = qr.size + border * 2;
  const parts = [];

  for (let y = 0; y < qr.size; y += 1) {
    let start = -1;
    for (let x = 0; x <= qr.size; x += 1) {
      const dark = x < qr.size && qr.modules[y][x];
      if (dark && start < 0) start = x;
      if ((!dark || x === qr.size) && start >= 0) {
        parts.push(`<rect x="${start + border}" y="${y + border}" width="${x - start}" height="1"/>`);
        start = -1;
      }
    }
  }

  return `<svg viewBox="0 0 ${pixels} ${pixels}" width="${pixels * scale}" height="${pixels * scale}" role="img" aria-label="QR ${escapeAttr(text)}" xmlns="http://www.w3.org/2000/svg"><rect width="100%" height="100%" fill="#fff"/><g fill="#111">${parts.join("")}</g></svg>`;
}

export function getQrInfo(text) {
  const bytes = new TextEncoder().encode(text);
  const spec = selectSpec(bytes.length);
  return {
    bytes: bytes.length,
    version: spec?.version ?? null,
    size: spec?.size ?? null,
    maxBytes: spec ? getMaxBytes(spec) : MAX_SUPPORTED_BYTES,
    maxSupportedBytes: MAX_SUPPORTED_BYTES,
    fits: Boolean(spec),
  };
}

function makeQr(text) {
  const bytes = new TextEncoder().encode(text);
  const spec = selectSpec(bytes.length);
  if (!spec) {
    throw new Error(`二维码内容过长，当前检测版最多支持 ${MAX_SUPPORTED_BYTES} 字节。`);
  }

  const data = encodeData(bytes, spec);
  const codewords = interleaveCodewords(data, spec);
  const qr = createMatrix(spec);
  drawCodewords(qr, codewords);
  applyMask(qr, 0);
  drawFormatBits(qr, 0);
  drawVersionBits(qr);
  return qr;
}

function selectSpec(byteLength) {
  return VERSION_TABLE.find((item) => byteLength <= getMaxBytes(item));
}

function maxSupportedBytes() {
  return getMaxBytes(VERSION_TABLE[VERSION_TABLE.length - 1]);
}

function getMaxBytes(spec) {
  const countBits = spec.version < 10 ? 8 : 16;
  return Math.floor((spec.dataCodewords * 8 - 4 - countBits) / 8);
}

function encodeData(bytes, spec) {
  const bits = [];
  appendBits(bits, 0b0100, 4);
  appendBits(bits, bytes.length, spec.version < 10 ? 8 : 16);
  for (const byte of bytes) appendBits(bits, byte, 8);

  const capacityBits = spec.dataCodewords * 8;
  appendBits(bits, 0, Math.min(4, capacityBits - bits.length));
  while (bits.length % 8 !== 0) bits.push(0);

  const data = [];
  for (let i = 0; i < bits.length; i += 8) {
    data.push(bitsToByte(bits.slice(i, i + 8)));
  }

  for (let pad = 0xec; data.length < spec.dataCodewords; pad = pad === 0xec ? 0x11 : 0xec) {
    data.push(pad);
  }
  return data;
}

function interleaveCodewords(data, spec) {
  const blocks = [];
  let offset = 0;

  for (const size of spec.blocks) {
    const blockData = data.slice(offset, offset + size);
    blocks.push({
      data: blockData,
      ecc: reedSolomonRemainder(blockData, spec.eccCodewords),
    });
    offset += size;
  }

  const codewords = [];
  const maxDataSize = Math.max(...blocks.map((block) => block.data.length));
  for (let index = 0; index < maxDataSize; index += 1) {
    for (const block of blocks) {
      if (index < block.data.length) codewords.push(block.data[index]);
    }
  }

  for (let index = 0; index < spec.eccCodewords; index += 1) {
    for (const block of blocks) {
      codewords.push(block.ecc[index]);
    }
  }

  return codewords;
}

function createMatrix(spec) {
  const modules = Array.from({ length: spec.size }, () => Array(spec.size).fill(false));
  const reserved = Array.from({ length: spec.size }, () => Array(spec.size).fill(false));
  const qr = { ...spec, modules, reserved };

  drawFinder(qr, 0, 0);
  drawFinder(qr, spec.size - 7, 0);
  drawFinder(qr, 0, spec.size - 7);
  drawTiming(qr);
  drawAlignment(qr);
  reserveFormat(qr);
  reserveVersion(qr);
  setFunction(qr, 8, spec.size - 8, true);
  return qr;
}

function drawFinder(qr, left, top) {
  for (let y = -1; y <= 7; y += 1) {
    for (let x = -1; x <= 7; x += 1) {
      const xx = left + x;
      const yy = top + y;
      if (!inBounds(qr, xx, yy)) continue;
      const dark =
        x >= 0 &&
        x <= 6 &&
        y >= 0 &&
        y <= 6 &&
        (x === 0 || x === 6 || y === 0 || y === 6 || (x >= 2 && x <= 4 && y >= 2 && y <= 4));
      setFunction(qr, xx, yy, dark);
    }
  }
}

function drawTiming(qr) {
  for (let i = 8; i < qr.size - 8; i += 1) {
    const dark = i % 2 === 0;
    setFunction(qr, i, 6, dark);
    setFunction(qr, 6, i, dark);
  }
}

function drawAlignment(qr) {
  for (const cy of qr.alignment) {
    for (const cx of qr.alignment) {
      if (isFinderOverlap(qr, cx, cy)) continue;
      for (let y = -2; y <= 2; y += 1) {
        for (let x = -2; x <= 2; x += 1) {
          const dark = Math.max(Math.abs(x), Math.abs(y)) !== 1;
          setFunction(qr, cx + x, cy + y, dark);
        }
      }
    }
  }
}

function isFinderOverlap(qr, cx, cy) {
  const nearLeft = cx <= 8;
  const nearRight = cx >= qr.size - 9;
  const nearTop = cy <= 8;
  const nearBottom = cy >= qr.size - 9;
  return (nearLeft && nearTop) || (nearRight && nearTop) || (nearLeft && nearBottom);
}

function reserveFormat(qr) {
  for (let i = 0; i < 15; i += 1) {
    const a = i < 6 ? i : i < 8 ? i + 1 : qr.size - 15 + i;
    const b = i < 8 ? qr.size - 1 - i : i < 9 ? 15 - i : 14 - i;
    reserve(qr, 8, a);
    reserve(qr, b, 8);
  }
}

function reserveVersion(qr) {
  if (qr.version < 7) return;

  for (let i = 0; i < 18; i += 1) {
    reserve(qr, qr.size - 11 + (i % 3), Math.floor(i / 3));
    reserve(qr, Math.floor(i / 3), qr.size - 11 + (i % 3));
  }
}

function drawCodewords(qr, codewords) {
  const bits = [];
  for (const codeword of codewords) appendBits(bits, codeword, 8);

  let bitIndex = 0;
  let direction = -1;
  let x = qr.size - 1;

  while (x > 0) {
    if (x === 6) x -= 1;
    for (let row = 0; row < qr.size; row += 1) {
      const y = direction === -1 ? qr.size - 1 - row : row;
      for (let dx = 0; dx < 2; dx += 1) {
        const xx = x - dx;
        if (qr.reserved[y][xx]) continue;
        qr.modules[y][xx] = bitIndex < bits.length ? bits[bitIndex] === 1 : false;
        bitIndex += 1;
      }
    }
    direction *= -1;
    x -= 2;
  }
}

function applyMask(qr, mask) {
  for (let y = 0; y < qr.size; y += 1) {
    for (let x = 0; x < qr.size; x += 1) {
      if (qr.reserved[y][x]) continue;
      if (maskCondition(mask, x, y)) qr.modules[y][x] = !qr.modules[y][x];
    }
  }
}

function drawFormatBits(qr, mask) {
  const eclLow = 1;
  const data = (eclLow << 3) | mask;
  let rem = data;
  for (let i = 0; i < 10; i += 1) {
    rem = (rem << 1) ^ (((rem >> 9) & 1) * 0x537);
  }
  const bits = ((data << 10) | rem) ^ 0x5412;

  for (let i = 0; i < 15; i += 1) {
    const bit = ((bits >> i) & 1) === 1;
    const a = i < 6 ? i : i < 8 ? i + 1 : qr.size - 15 + i;
    const b = i < 8 ? qr.size - 1 - i : i < 9 ? 15 - i : 14 - i;
    setFunction(qr, 8, a, bit);
    setFunction(qr, b, 8, bit);
  }
  setFunction(qr, 8, qr.size - 8, true);
}

function drawVersionBits(qr) {
  if (qr.version < 7) return;

  let rem = qr.version;
  for (let i = 0; i < 12; i += 1) {
    rem = (rem << 1) ^ (((rem >> 11) & 1) * 0x1f25);
  }
  const bits = (qr.version << 12) | rem;

  for (let i = 0; i < 18; i += 1) {
    const bit = ((bits >> i) & 1) === 1;
    setFunction(qr, qr.size - 11 + (i % 3), Math.floor(i / 3), bit);
    setFunction(qr, Math.floor(i / 3), qr.size - 11 + (i % 3), bit);
  }
}

function reedSolomonRemainder(data, degree) {
  const divisor = reedSolomonDivisor(degree);
  const result = Array(degree).fill(0);

  for (const byte of data) {
    const factor = byte ^ result.shift();
    result.push(0);
    for (let i = 0; i < degree; i += 1) {
      result[i] ^= gfMultiply(divisor[i], factor);
    }
  }
  return result;
}

function reedSolomonDivisor(degree) {
  let result = [1];
  for (let i = 0; i < degree; i += 1) {
    const next = Array(result.length + 1).fill(0);
    for (let j = 0; j < result.length; j += 1) {
      next[j] ^= gfMultiply(result[j], 1);
      next[j + 1] ^= gfMultiply(result[j], gfPow(i));
    }
    result = next;
  }
  return result.slice(1);
}

function gfPow(power) {
  let value = 1;
  for (let i = 0; i < power; i += 1) value = gfMultiply(value, 2);
  return value;
}

function gfMultiply(x, y) {
  let result = 0;
  for (let i = 7; i >= 0; i -= 1) {
    result = (result << 1) ^ (((result >>> 7) & 1) * 0x11d);
    result ^= (((y >>> i) & 1) * x);
  }
  return result & 0xff;
}

function appendBits(bits, value, length) {
  for (let i = length - 1; i >= 0; i -= 1) bits.push((value >> i) & 1);
}

function bitsToByte(bits) {
  return bits.reduce((value, bit) => (value << 1) | bit, 0);
}

function setFunction(qr, x, y, dark) {
  if (!inBounds(qr, x, y)) return;
  qr.modules[y][x] = dark;
  qr.reserved[y][x] = true;
}

function reserve(qr, x, y) {
  if (inBounds(qr, x, y)) qr.reserved[y][x] = true;
}

function inBounds(qr, x, y) {
  return x >= 0 && y >= 0 && x < qr.size && y < qr.size;
}

function maskCondition(mask, x, y) {
  if (mask === 0) return (x + y) % 2 === 0;
  return false;
}

function escapeAttr(value) {
  return String(value).replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;");
}
