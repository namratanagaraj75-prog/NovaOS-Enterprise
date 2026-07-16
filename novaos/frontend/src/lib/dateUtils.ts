export interface FirestoreTimestamp {
  seconds: number;
  nanoseconds: number;
  _seconds?: number;
  _nanoseconds?: number;
  toDate?: () => Date;
}

export type TimestampInput = Date | string | number | FirestoreTimestamp | null | undefined;

export function normalizeDate(value: any): Date | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  // 1. JavaScript Date
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }

  // 5. Firestore Timestamp with `.toDate()`
  if (value && typeof value.toDate === 'function') {
    try {
      const d = value.toDate();
      if (d instanceof Date && !isNaN(d.getTime())) {
        return d;
      }
    } catch (e) {
      // ignore
    }
  }

  // 6 & 7. Object { seconds, nanoseconds } or { _seconds, _nanoseconds }
  if (value && typeof value === 'object') {
    const s = value.seconds !== undefined ? value.seconds : value._seconds;
    const ns = value.nanoseconds !== undefined ? value.nanoseconds : value._nanoseconds;
    if (typeof s === 'number') {
      const ms = s * 1000 + Math.floor((ns || 0) / 1000000);
      const d = new Date(ms);
      if (!isNaN(d.getTime())) {
        return d;
      }
    }
  }

  // 3 & 4. Numeric epoch milliseconds or seconds
  if (typeof value === 'number') {
    // If it's a small number, e.g. < 50000000000, it's likely epoch seconds (e.g. 1721124954), otherwise milliseconds
    const ms = value < 50000000000 ? value * 1000 : value;
    const d = new Date(ms);
    if (!isNaN(d.getTime())) {
      return d;
    }
  }

  // 2. ISO date string / Java backend date string
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    // Check if it's a numeric string first (like epoch milliseconds/seconds)
    if (/^\d+$/.test(trimmed)) {
      const num = Number(trimmed);
      const ms = num < 50000000000 ? num * 1000 : num;
      const d = new Date(ms);
      if (!isNaN(d.getTime())) {
        return d;
      }
    }

    const d = new Date(trimmed);
    if (!isNaN(d.getTime())) {
      return d;
    }
  }

  return null;
}

export function formatNormalizedDate(value: any): string {
  const d = normalizeDate(value);
  if (!d) {
    return 'Time unavailable';
  }

  try {
    const day = d.getDate();
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const month = months[d.getMonth()];
    const year = d.getFullYear();
    let hours = d.getHours();
    const minutes = d.getMinutes().toString().padStart(2, '0');
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    hours = hours ? hours : 12; // the hour '0' should be '12'
    return `${day} ${month} ${year}, ${hours}:${minutes} ${ampm}`;
  } catch (e) {
    return 'Time unavailable';
  }
}
