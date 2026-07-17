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

  // 2. Firestore Timestamp with `.toDate()`
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

  // 3. Object { seconds, nanoseconds } or { _seconds, _nanoseconds }
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

  // 4. Numeric epoch milliseconds or seconds
  if (typeof value === 'number') {
    const ms = value < 50000000000 ? value * 1000 : value;
    const d = new Date(ms);
    if (!isNaN(d.getTime())) {
      return d;
    }
  }

  // 5. ISO date string / Java backend date string
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
    const formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: "Asia/Kolkata",
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
      hour12: true,
    });
    
    const parts = formatter.formatToParts(d);
    const partMap: Record<string, string> = {};
    for (const part of parts) {
      partMap[part.type] = part.value;
    }
    
    const day = partMap.day || '01';
    let month = partMap.month || 'Jan';
    month = month.replace(/\.$/, ''); // Remove any trailing dot from short month abbreviations
    const year = partMap.year || '2026';
    const hour = partMap.hour || '12';
    const minute = partMap.minute || '00';
    let dayPeriod = partMap.dayPeriod || (d.getHours() >= 12 ? 'PM' : 'AM');
    dayPeriod = dayPeriod.toUpperCase().trim();
    
    return `${day} ${month} ${year}, ${hour}:${minute} ${dayPeriod}`;
  } catch (e) {
    return 'Time unavailable';
  }
}
