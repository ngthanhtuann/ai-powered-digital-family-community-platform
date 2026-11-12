// Kiểm thử hồi quy qua REST API (SCRUM-65): luồng xuyên module và các nhóm bắt buộc của test-plan.md mục 3.
// Dữ liệu GIẢ, tự tạo tài khoản và gia đình mới mỗi lần chạy (email ngẫu nhiên), nên chạy lại được.
// Cần Node 20+ (fetch, FormData có sẵn). Không cài thêm gói.
//   node docker/test/regression-api.mjs --api http://localhost:8080/api/v1 [--admin-email a@b --admin-password x]
// Thiếu tài khoản SYSTEM_ADMIN thì bỏ qua các ca cần quản trị và ghi "Bỏ qua". Thoát mã 1 nếu có ca không đạt.
import { writeFileSync } from 'node:fs';

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, v, i, all) =>
  (v.startsWith('--') ? [...acc, [v.slice(2), all[i + 1]]] : acc), []));
const API = args.api ?? 'http://localhost:8080/api/v1';
const PASSWORD = 'Hoiquy@2026'; // chỉ cho tài khoản giả do script tạo
const RUN = Date.now().toString(36);
const results = [];
const errors = []; // mọi phản hồi lỗi để kiểm tra chung định dạng (nhóm API)

function record(id, group, name, pass, actual) {
  results.push({ id, group, name, status: pass === null ? 'Bỏ qua' : pass ? 'Đạt' : 'Không đạt', actual });
}

async function call(method, path, { token, body, form } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  let payload;
  if (form) payload = form;
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
  const res = await fetch(API + path, { method, headers, body: payload });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 200) }; }
  if (res.status >= 400) errors.push({ path, status: res.status, json });
  return { status: res.status, json, code: json?.error?.code, data: json?.data };
}

const expect = (id, group, name, res, status, code) =>
  record(id, group, name, res.status === status && (code === undefined || res.code === code),
    `${res.status}${res.code ? ' ' + res.code : ''}`);

async function register(tag, dob = '1990-01-01') {
  const email = `hoiquy.${tag}.${RUN}@demo.familyconnect.vn`;
  const res = await call('POST', '/users', { body: { email, password: PASSWORD, fullName: `Hồi quy ${tag}`, dateOfBirth: dob } });
  return { email, id: res.data?.id, status: res.status };
}
async function login(email) {
  return call('POST', '/auth/login', { body: { email, password: PASSWORD } });
}

const png = (size) => { const b = new Uint8Array(size); b.set([0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10]); return b; };
const fileForm = (name, bytes) => { const f = new FormData(); f.append('file', new Blob([bytes]), name); return f; };

async function main() {
  // ---- AUTH: đăng ký, đăng nhập, token
  const headA = await register('truonghoA', '1970-05-01');
  record('AUTH-01', 'AUTH', 'Đăng ký tài khoản', headA.status === 201, String(headA.status));
  const loginA = await login(headA.email);
  expect('AUTH-02', 'AUTH', 'Đăng nhập đúng nhận token', loginA, 200);
  const tA = loginA.data?.accessToken;
  expect('AUTH-03', 'AUTH', 'Sai mật khẩu: 401 AUTH_001',
    await call('POST', '/auth/login', { body: { email: headA.email, password: 'SaiMatKhau1' } }), 401, 'AUTH_001');
  expect('AUTH-04', 'AUTH', 'Không token: 401 AUTH_002', await call('GET', '/users/me'), 401, 'AUTH_002');
  expect('AUTH-05', 'AUTH', 'Token giả: 401 AUTH_002', await call('GET', '/users/me', { token: 'abc.def.ghi' }), 401, 'AUTH_002');
  const refreshed = await call('POST', '/auth/refresh', { body: { refreshToken: loginA.data?.refreshToken } });
  record('AUTH-06', 'AUTH', 'Làm mới token bằng refresh token', refreshed.status === 200 && !!refreshed.data?.accessToken,
    String(refreshed.status));

  // ---- Luồng xuyên module (Jira SCRUM-65 bước 3): đăng ký → xin vào gia đình → thêm thành viên → đăng bài → sự kiện → chatbot
  const famA = await call('POST', '/families', { token: tA, body: { name: `Họ Hồi Quy A ${RUN}`, description: 'Dữ liệu giả' } });
  expect('FLOW-01', 'FLOW', 'Tạo gia đình A', famA, 201);
  const fA = famA.data?.id;
  const branch = await call('POST', `/families/${fA}/branches`, { token: tA, body: { name: 'Chi 1' } });
  expect('FLOW-02', 'FLOW', 'Tạo chi', branch, 201);

  const member = await register('thanhvien', '1995-03-03');
  let tM = (await login(member.email)).data?.accessToken;
  expect('PERM-01', 'PERM', 'Chưa xác minh xem gia đình: 403 PERM_002', await call('GET', `/families/${fA}`, { token: tM }), 403, 'PERM_002');
  const join = await call('POST', '/join-requests', { token: tM, body: { code: fA, message: 'Xin vào họ' } });
  record('FLOW-03', 'FLOW', 'Xin vào gia đình: 201 PENDING', join.status === 201 && join.data?.status === 'PENDING',
    `${join.status} ${join.data?.status}`);
  const personM = await call('POST', `/families/${fA}/persons`, {
    token: tA, body: { fullName: 'Hồi quy thanhvien', gender: 'MALE', birthDate: '1995-03-03', branchId: branch.data?.id } });
  expect('FLOW-04', 'FLOW', 'Trưởng họ thêm thành viên vào cây', personM, 201);
  const approve = await call('PATCH', `/join-requests/${join.data?.id}`, {
    token: tA, body: { status: 'APPROVED', personId: personM.data?.id } });
  record('FLOW-05', 'FLOW', 'Duyệt và gắn hồ sơ', approve.status === 200 && approve.data?.status === 'APPROVED',
    `${approve.status} ${approve.data?.status ?? approve.code ?? ''}`);
  tM = (await login(member.email)).data?.accessToken;
  expect('FLOW-06', 'FLOW', 'Thành viên đã duyệt xem gia đình', await call('GET', `/families/${fA}`, { token: tM }), 200);

  const child = await call('POST', `/families/${fA}/persons`, {
    token: tA, body: { fullName: 'Hồi quy Bé', gender: 'FEMALE', birthDate: '2018-06-01', currentAddress: '12 Đường Giả', branchId: branch.data?.id } });
  const deceased = await call('POST', `/families/${fA}/persons`, {
    token: tA, body: { fullName: 'Hồi quy Cụ', gender: 'MALE', isDeceased: true, birthYear: 1920, deathDate: '1990-01-01' } });
  record('FLOW-07', 'FLOW', 'Thêm trẻ em và người đã mất', child.status === 201 && deceased.status === 201,
    `${child.status}/${deceased.status}`);
  const link = await call('POST', `/persons/${child.data?.id}/parents`, {
    token: tA, body: { parentId: personM.data?.id, parentRole: 'FATHER' } });
  expect('FLOW-08', 'FLOW', 'Thêm quan hệ cha-con', link, 201);
  const rel = await call('GET', `/relationships?personA=${child.data?.id}&personB=${personM.data?.id}`, { token: tM });
  record('FLOW-09', 'FLOW', 'Tra quan hệ: con của', rel.status === 200 && rel.data?.supported === true,
    `${rel.status} ${rel.data?.relation ?? ''}`);
  const tree = await call('GET', `/families/${fA}/tree?rootId=${personM.data?.id}`, { token: tM });
  record('FLOW-10', 'FLOW', 'Xem cây gia phả', tree.status === 200 && tree.data?.nodes?.length >= 2,
    `${tree.status} ${tree.data?.nodes?.length ?? 0} node`);

  const post = await call('POST', '/posts', { token: tM, body: { content: 'Bài hồi quy', type: 'POST', branchId: null } });
  expect('FLOW-11', 'FLOW', 'Thành viên đăng bài', post, 201);
  const photo = await call('POST', `/posts/${post.data?.id}/media`, { token: tM, form: fileForm('anh.png', png(2048)) });
  expect('FILE-01', 'FILE', 'Ảnh png hợp lệ: 201', photo, 201);
  expect('FILE-02', 'FILE', 'Đổi đuôi .jpg nhưng không phải ảnh: 415 FILE_002',
    await call('POST', `/posts/${post.data?.id}/media`, { token: tM, form: fileForm('gia.jpg', new TextEncoder().encode('khong phai anh')) }),
    415, 'FILE_002');
  expect('FILE-03', 'FILE', 'Ảnh > 5 MB: 413 FILE_001',
    await call('POST', `/posts/${post.data?.id}/media`, { token: tM, form: fileForm('lon.png', png(5 * 1024 * 1024 + 1)) }),
    413, 'FILE_001');
  const comment = await call('POST', `/posts/${post.data?.id}/comments`, { token: tA, body: { content: 'Bình luận hồi quy' } });
  expect('COM-01', 'FLOW', 'Bình luận bài đăng (FR-COM-02)', comment, 201);
  const comments = await call('GET', `/posts/${post.data?.id}/comments?page=0&size=20`, { token: tM });
  record('COM-02', 'FLOW', 'Danh sách bình luận có bình luận vừa thêm', comments.status === 200
    && (comments.json?.data ?? []).some((c) => c.id === comment.data?.id), `${comments.status} ${comments.json?.meta?.total ?? ''}`);
  const reaction = await call('POST', `/posts/${post.data?.id}/reactions`, { token: tA, body: { type: 'LOVE' } });
  record('COM-03', 'FLOW', 'Thả reaction (FR-COM-03)', reaction.status === 200 || reaction.status === 201,
    `${reaction.status}${reaction.code ? ' ' + reaction.code : ''}`);
  const feed = await call('GET', '/posts?page=0&size=20', { token: tM });
  record('FLOW-12', 'FLOW', 'Bảng tin có bài vừa đăng', feed.status === 200 && feed.json?.data?.some((p) => p.id === post.data?.id),
    String(feed.status));

  const event = await call('POST', '/events', { token: tA, body: {
    title: 'Sự kiện hồi quy', startAt: '2026-12-31T08:00:00+07:00', location: 'Nhà thờ họ', branchId: null } });
  expect('FLOW-13', 'FLOW', 'Trưởng họ tạo sự kiện', event, 201);
  expect('FLOW-14', 'FLOW', 'Thành viên RSVP', await call('PUT', `/events/${event.data?.id}/rsvp`, { token: tM, body: { rsvpStatus: 'GOING' } }), 200);
  expect('EVT-01', 'FILE', 'Tải ảnh vào thư viện sự kiện (FR-EVT-04)',
    await call('POST', `/events/${event.data?.id}/media`, { token: tA, form: fileForm('su-kien.png', png(4096)) }), 201);

  const chat = await call('POST', '/ai/chat', { token: tM, body: { question: 'Ai là con của Hồi quy thanhvien?' } });
  record('AI-01', 'AI', 'Chatbot: 200 khi có khóa LLM, 503 AI_001 khi chưa có', chat.status === 200 || (chat.status === 503 && chat.code === 'AI_001'),
    `${chat.status}${chat.code ? ' ' + chat.code : ''}`);
  const stranger = await register('nguoila');
  const tS = (await login(stranger.email)).data?.accessToken;
  expect('AI-02', 'AI', 'Chưa xác minh hỏi AI: 403 PERM_002', await call('POST', '/ai/chat', { token: tS, body: { question: 'Xin chào' } }), 403, 'PERM_002');
  expect('AUTH-07', 'AUTH', 'Tài khoản chưa xác minh vẫn xem được hồ sơ mình', await call('GET', '/users/me', { token: tS }), 200);

  // ---- API: quy ước phản hồi
  const list = await call('GET', `/families/${fA}/persons?page=0&size=20`, { token: tM });
  record('API-01', 'API', 'Danh sách có meta page, size, total', list.status === 200 && list.json?.meta?.page === 0
    && list.json?.meta?.size === 20 && typeof list.json?.meta?.total === 'number', JSON.stringify(list.json?.meta));
  expect('API-02', 'API', 'size=101: 400 VALID_001', await call('GET', `/families/${fA}/persons?page=0&size=101`, { token: tM }), 400, 'VALID_001');
  // Xóa một bài riêng: bài có ảnh còn dùng cho ca IDOR-04 bên dưới.
  const temp = await call('POST', '/posts', { token: tM, body: { content: 'Bài sẽ xóa', type: 'POST', branchId: null } });
  const del = await call('DELETE', `/posts/${temp.data?.id}`, { token: tM });
  record('API-03', 'API', 'DELETE: 200, data null', del.status === 200 && del.json?.success === true && del.json?.data === null, String(del.status));

  // ---- PRIV: MEMBER xem trẻ em, người đã mất
  const childView = await call('GET', `/persons/${child.data?.id}`, { token: tM });
  record('PRIV-01', 'PRIV', 'MEMBER xem trẻ < 16: không có ngày sinh đầy đủ, địa chỉ',
    childView.status === 200 && !childView.data?.birthDate && !childView.data?.currentAddress && childView.data?.birthYear === 2018,
    `${childView.status} birthDate=${childView.data?.birthDate ?? null} address=${childView.data?.currentAddress ?? null}`);
  const deadView = await call('GET', `/persons/${deceased.data?.id}`, { token: tM });
  record('PRIV-02', 'PRIV', 'Người đã mất: không có trường nguyên nhân tử vong',
    deadView.status === 200 && !Object.keys(deadView.data ?? {}).some((k) => /cause/i.test(k)), Object.keys(deadView.data ?? {}).join(','));
  const me = await call('GET', `/persons/${personM.data?.id}`, { token: tM });
  record('PRIV-03', 'PRIV', 'Hồ sơ người không trả email, số điện thoại, userId',
    me.status === 200 && !['email', 'phone', 'userId'].some((k) => k in (me.data ?? {})), Object.keys(me.data ?? {}).join(','));

  // ---- PERM, IDOR, INT: gia đình B gọi dữ liệu gia đình A
  const headB = await register('truonghoB', '1972-01-01');
  const tB = (await login(headB.email)).data?.accessToken;
  const famB = await call('POST', '/families', { token: tB, body: { name: `Họ Hồi Quy B ${RUN}` } });
  record('IDOR-00', 'IDOR', 'Tạo gia đình B', famB.status === 201, String(famB.status));
  expect('IDOR-01', 'IDOR', 'B xem gia đình A: 403 PERM_001', await call('GET', `/families/${fA}`, { token: tB }), 403, 'PERM_001');
  expect('IDOR-02', 'IDOR', 'B xem người của A: 403 PERM_001', await call('GET', `/persons/${child.data?.id}`, { token: tB }), 403, 'PERM_001');
  expect('IDOR-03', 'IDOR', 'B xem cây của A: 403 PERM_001', await call('GET', `/families/${fA}/tree`, { token: tB }), 403, 'PERM_001');
  expect('IDOR-04', 'IDOR', 'B tải ảnh của A: 403 PERM_001', await call('GET', `/media/${photo.data?.id}/content`, { token: tB }), 403, 'PERM_001');
  expect('IDOR-05', 'IDOR', 'B tra quan hệ người của A: 403 PERM_001',
    await call('GET', `/relationships?personA=${child.data?.id}&personB=${personM.data?.id}`, { token: tB }), 403, 'PERM_001');
  const listB = await call('GET', '/posts?page=0&size=100', { token: tB });
  record('IDOR-06', 'IDOR', 'Bảng tin của B không có bài của A', listB.status === 200 && !(listB.json?.data ?? []).some((p) => p.familyId === fA),
    String(listB.status));
  const crossLink = await call('POST', `/persons/${child.data?.id}/parents`, { token: tB, body: { parentId: personM.data?.id, parentRole: 'MOTHER' } });
  record('INT-01', 'INT', 'B gắn quan hệ cho người của A: bị từ chối', crossLink.status === 403 || crossLink.status === 404,
    `${crossLink.status} ${crossLink.code ?? ''}`);
  expect('INT-02', 'INT', 'ID không tồn tại: 404 NOT_FOUND_001', await call('GET', `/persons/${crypto.randomUUID()}`, { token: tM }), 404, 'NOT_FOUND_001');
  expect('PERM-02', 'PERM', 'MEMBER thêm thành viên: 403 PERM_001', await call('POST', `/families/${fA}/persons`, {
    token: tM, body: { fullName: 'Không được', gender: 'MALE', birthDate: '2000-01-01' } }), 403, 'PERM_001');
  expect('PERM-03', 'PERM', 'MEMBER gọi API quản trị: 403 PERM_001', await call('GET', '/admin/users', { token: tM }), 403, 'PERM_001');

  // ---- Cần SYSTEM_ADMIN
  if (args['admin-email']) {
    const tAdmin = (await call('POST', '/auth/login', { body: { email: args['admin-email'], password: args['admin-password'] } })).data?.accessToken;
    const lock = await call('PATCH', `/admin/users/${member.id}`, { token: tAdmin, body: { status: 'LOCKED', lockReason: 'Kiểm thử hồi quy' } });
    expect('ADM-01', 'AUTH', 'Quản trị khóa tài khoản', lock, 200);
    expect('AUTH-08', 'AUTH', 'Tài khoản bị khóa đăng nhập: 403 AUTH_003', await login(member.email), 403, 'AUTH_003');
    expect('ADM-02', 'AUTH', 'Mở khóa', await call('PATCH', `/admin/users/${member.id}`, { token: tAdmin, body: { status: 'ACTIVE' } }), 200);
    const audit = await call('GET', '/admin/audit-logs?action=JOIN_APPROVED&page=0&size=5', { token: tAdmin });
    record('NFR-11', 'AUDIT', 'Nhật ký có JOIN_APPROVED', audit.status === 200 && audit.json?.meta?.total >= 1, `${audit.status} total=${audit.json?.meta?.total}`);
  } else {
    for (const id of ['ADM-01', 'AUTH-08', 'ADM-02', 'NFR-11']) record(id, 'AUTH', 'Cần --admin-email', null, '-');
  }

  // ---- API: mọi phản hồi lỗi đúng định dạng {success:false, error:{code,message,details[]}}
  const bad = errors.filter((e) => !(e.json?.success === false && e.json?.error?.code && typeof e.json?.error?.message === 'string'
    && Array.isArray(e.json?.error?.details)));
  record('API-04', 'API', `Mọi phản hồi lỗi đúng định dạng (${errors.length} lỗi)`, bad.length === 0,
    bad.length ? bad.map((e) => `${e.status} ${e.path}`).join('; ') : 'ok');
}

main().catch((e) => record('RUN', 'RUN', 'Kịch bản bị dừng do lỗi', false, String(e))).finally(() => {
  const width = Math.max(...results.map((r) => r.name.length));
  for (const r of results) console.log(`${r.status.padEnd(10)} ${r.id.padEnd(8)} ${r.name.padEnd(width)}  ${r.actual}`);
  const failed = results.filter((r) => r.status === 'Không đạt').length;
  const skipped = results.filter((r) => r.status === 'Bỏ qua').length;
  console.log(`\nTổng ${results.length} ca: ${results.length - failed - skipped} đạt, ${failed} không đạt, ${skipped} bỏ qua.`);
  if (args.json) writeFileSync(args.json, JSON.stringify(results, null, 2));
  process.exitCode = failed ? 1 : 0;
});
