// Giờ của dòng họ, giống backend (EventAccess, DashboardService) và câu trả lời AI: luôn giờ Việt Nam,
// không theo múi giờ của máy người xem. Việt Nam cố định UTC+7, không đổi giờ mùa hè.
export const FAMILY_TIME_ZONE = 'Asia/Ho_Chi_Minh';
const FAMILY_UTC_OFFSET = '+07:00';

/** Ngày giờ (vd. "08:00:00 20/12/2026"); thiếu giá trị thì trả `empty`. */
export function formatDateTime(value?: string | null, empty = '') {
  return value ? new Date(value).toLocaleString('vi-VN', { timeZone: FAMILY_TIME_ZONE }) : empty;
}

/** Chỉ ngày (vd. "20/12/2026"); thiếu giá trị thì trả `empty`. */
export function formatDate(value?: string | null, empty = '') {
  return value ? new Date(value).toLocaleDateString('vi-VN', { timeZone: FAMILY_TIME_ZONE }) : empty;
}

const INPUT_PARTS = new Intl.DateTimeFormat('en-CA', {
  timeZone: FAMILY_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** ISO từ API → giá trị ô `datetime-local` ("yyyy-MM-ddTHH:mm") theo giờ dòng họ. */
export function toFamilyInput(value: string) {
  const part = Object.fromEntries(INPUT_PARTS.formatToParts(new Date(value)).map((p) => [p.type, p.value]));
  return `${part.year}-${part.month}-${part.day}T${part.hour}:${part.minute}`;
}

/** Giá trị ô `datetime-local` (hiểu là giờ dòng họ) → ISO UTC gửi API. */
export function fromFamilyInput(value: string) {
  return value ? new Date(`${value}:00${FAMILY_UTC_OFFSET}`).toISOString() : undefined;
}
