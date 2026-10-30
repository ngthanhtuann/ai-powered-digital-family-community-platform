#!/usr/bin/env node
// Đánh giá tìm kiếm ngữ nghĩa (SCRUM-49), điền bảng ở docs/decisions/semantic-search-evaluation.md.
//
// Cách chạy (backend đang chạy và đã cấu hình GEMINI_API_KEY trong .env của bạn, ví dụ `docker compose up`):
//   node scripts/eval-semantic-search.mjs                       # mặc định http://localhost:8080
//   node scripts/eval-semantic-search.mjs --base-url http://localhost:8080
//
// Script tự tạo tài khoản, gia đình và toàn bộ dữ liệu GIẢ (gói miễn phí Gemini dùng nội dung gửi đi để cải thiện
// sản phẩm, nên không dùng dữ liệu thật). Script không đọc .env và không cần khóa Gemini; khóa chỉ nằm ở backend.
// Cần Node 18 trở lên (dùng fetch, FormData có sẵn).

const argIndex = process.argv.indexOf('--base-url');
const BASE = (argIndex > 0 ? process.argv[argIndex + 1] : process.env.FC_BASE_URL ?? 'http://localhost:8080')
  .replace(/\/$/, '') + '/api/v1';
const PASSWORD = 'Matkhau123';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function call(method, path, { token, body, form } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  let payload;
  if (form) payload = form;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const response = await fetch(BASE + path, { method, headers, body: payload });
  const text = await response.text();
  const json = text ? JSON.parse(text) : null;
  return { status: response.status, json };
}

async function must(method, path, options, expected = [200, 201]) {
  const result = await call(method, path, options);
  if (!expected.includes(result.status)) {
    throw new Error(`${method} ${path} trả ${result.status}: ${JSON.stringify(result.json?.error ?? result.json)}`);
  }
  return result.json?.data;
}

// Dữ liệu giả, khớp cột "Kết quả kỳ vọng" của bảng 15 câu.
const PEOPLE = [
  { fullName: 'Trần Minh An', gender: 'MALE', birthDate: '1985-03-02', currentProvince: 'Hà Nội',
    currentAddress: '25 Lý Thường Kiệt', job: 'Bác sĩ' },
  { fullName: 'Lê Thu Hà', gender: 'FEMALE', birthDate: '1990-07-12', currentProvince: 'Đà Nẵng', job: 'Giáo viên' },
  { fullName: 'Nguyễn Hoàng Nam', gender: 'MALE', birthDate: '1980-01-20', currentProvince: 'Huế', job: 'Kỹ sư xây dựng' },
  { fullName: 'Trần Thị Mai', gender: 'FEMALE', birthYear: 1940, isDeceased: true, currentProvince: 'Nam Định' },
];
const POSTS = [
  'Ngày giỗ tổ dòng họ năm nay tổ chức tại nhà thờ họ vào mùng 10 tháng 3 âm lịch, mời cả gia đình về dự.',
  'Họp mặt đầu xuân: gia đình tổ chức họp mặt dịp Tết Nguyên đán, chúc thọ ông bà và lì xì các cháu.',
  'Chuyến thiện nguyện tháng 7: cả họ quyên góp sách vở và gạo cho học sinh vùng cao.',
  'Lễ hội mùa xuân của làng: các chi họ cùng tham gia tổ chức rước kiệu và thi nấu bánh chưng.',
];
const QUERIES = [
  ['người làm bác sĩ ở Hà Nội', 'Trần Minh An'],
  ['tìm người là bác sĩ tại thủ đô', 'Trần Minh An'],
  ['ai làm nghề y ở Hà Nội', 'Trần Minh An'],
  ['thành viên nào đang sống tại Đà Nẵng', 'Lê Thu Hà'],
  ['người sinh năm 1980', 'Nguyễn Hoàng Nam'],
  ['bài viết về ngày giỗ tổ', 'giỗ tổ'],
  ['thông báo họp mặt gia đình dịp Tết', 'Họp mặt đầu xuân'],
  ['ảnh và câu chuyện về nhà thờ họ', 'Trùng tu nhà thờ họ'],
  ['tài liệu gia phả bản PDF', 'Gia phả chi trưởng'],
  ['ai là con của Nguyễn Hoàng Nam', 'Nguyễn Hoàng Nam'],
  ['tìm thông tin về bà Trần Thị Mai', 'Trần Thị Mai'],
  ['người tham gia tổ chức lễ hội mùa xuân', 'Lễ hội mùa xuân'],
  ['hoạt động thiện nguyện của gia đình', 'thiện nguyện'],
  ['địa chỉ nhà riêng của thành viên', { noText: 'Lý Thường Kiệt' }],
  ['người nổi tiếng không tồn tại trong gia đình', { empty: true }],
];

function judge(expected, results) {
  const top = results.slice(0, 3);
  if (expected.empty) return results.length === 0;
  if (expected.noText) return !results.some((r) => `${r.title} ${r.snippet}`.includes(expected.noText));
  return top.some((r) => `${r.title} ${r.snippet}`.toLowerCase().includes(expected.toLowerCase()));
}

async function main() {
  const email = `eval.${Date.now()}@example.com`;
  await must('POST', '/users', { body: { email, password: PASSWORD, fullName: 'Người đánh giá', dateOfBirth: '1990-01-01' } });
  const token = (await must('POST', '/auth/login', { body: { email, password: PASSWORD } })).accessToken;
  const familyId = (await must('POST', '/families', { token, body: { name: 'FC-DEMO-A (dữ liệu giả)' } })).id;
  console.error(`Đã tạo gia đình giả ${familyId}, đang nạp dữ liệu...`);

  for (const { job, ...person } of PEOPLE) {
    const created = await must('POST', `/families/${familyId}/persons`, { token, body: person });
    if (job) await must('PUT', `/directory/members/${created.id}/profession-profile`, { token, body: { jobTitle: job } });
  }
  for (const content of POSTS) await must('POST', '/posts', { token, body: { content, type: 'POST' } });
  await must('POST', '/heritage/stories', { token, body: { title: 'Trùng tu nhà thờ họ',
    content: 'Năm 2015 con cháu góp công góp của trùng tu nhà thờ họ, giữ lại bức hoành phi cổ.' } });
  const form = new FormData();
  form.set('file', new Blob([Buffer.from('%PDF-1.4\n% du lieu gia\n')], { type: 'application/pdf' }), 'gia-pha.pdf');
  form.set('ownerType', 'HERITAGE');
  form.set('ownerId', familyId);
  const media = await must('POST', '/media', { token, form });
  await must('POST', '/heritage/documents', { token, body: { title: 'Gia phả chi trưởng', category: 'gia-pha',
    description: 'Bản scan gia phả chi trưởng, ghi các đời từ cụ tổ.', mediaId: media.id } });
  await sleep(2000); // chỉ mục được ghi sau khi mỗi giao dịch commit

  const rows = [];
  let correct = 0;
  for (const [i, [query, expected]] of QUERIES.entries()) {
    const result = await call('GET', `/ai/search?q=${encodeURIComponent(query)}&size=10`, { token });
    if (result.status !== 200) {
      const code = result.json?.error?.code;
      if (code === 'AI_001') throw new Error('Backend trả AI_001: chưa cấu hình GEMINI_API_KEY hoặc Gemini lỗi.');
      throw new Error(`Câu ${i + 1} trả ${result.status} ${code ?? ''}`);
    }
    const results = result.json.data.results;
    const ok = judge(expected, results);
    if (ok) correct += 1;
    const actual = results.length === 0 ? 'Rỗng' : results.slice(0, 3)
      .map((r) => `${r.sourceType}: ${r.title} (${r.score.toFixed(3)})`).join('<br>');
    const want = typeof expected === 'string' ? expected : expected.empty ? 'Rỗng' : `Không chứa "${expected.noText}"`;
    rows.push(`| ${i + 1} | ${query} | ${want} | ${actual} | ${ok ? 'Đúng' : 'Sai'} |`);
    await sleep(700); // tránh chạm giới hạn số lời gọi mỗi phút của Gemini
  }

  console.log(`Ngày đo: ${new Date().toISOString().slice(0, 10)}. Backend: ${BASE}. Gia đình giả: ${familyId}.`);
  console.log('');
  console.log('| # | Câu truy vấn | Kết quả kỳ vọng | Thực tế (top 3: loại, tiêu đề, điểm) | Đúng? |');
  console.log('|---:|---|---|---|---|');
  rows.forEach((row) => console.log(row));
  console.log('');
  console.log(`Độ chính xác: ${correct}/${QUERIES.length}.`);
}

main().catch((error) => {
  console.error(`Lỗi: ${error.message}`);
  process.exit(1);
});
