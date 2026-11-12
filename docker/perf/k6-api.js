// Kiểm thử hiệu năng các API chính (SCRUM-59, NFR-12). Chạy bằng Docker, không cần cài k6:
//   docker run --rm -i --network <mạng compose> -e BASE=http://backend:8080/api/v1 -e VUS=10 \
//     -v "<thư mục ghi kết quả>:/out" grafana/k6:0.54.0 run - < docker/perf/k6-api.js
// Mỗi API là một scenario chạy nối tiếp để số liệu không lẫn nhau. Dữ liệu từ prepare-perf.ps1.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8080/api/v1';
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '30s';
const EMAIL = __ENV.EMAIL || 'perf@demo.familyconnect.vn';
const PASSWORD = __ENV.PASSWORD || 'Demo@2026';
const STEP = Number(DURATION.replace('s', '')) + 5; // nghỉ 5 giây giữa các scenario

const ENDPOINTS = ['tree_depth3', 'tree_depth10', 'relationships', 'feed', 'ai_search'];
const scenarios = {};
ENDPOINTS.forEach((name, i) => {
  scenarios[name] = {
    executor: 'constant-vus',
    vus: name === 'ai_search' ? Math.min(VUS, 5) : VUS, // AI phụ thuộc LLM, đo nhẹ
    duration: DURATION,
    startTime: `${i * STEP}s`,
    exec: name,
    tags: { endpoint: name },
  };
});

export const options = {
  scenarios,
  thresholds: {
    // NFR-12: cây < 1 giây với 5.000 người, tìm kiếm ngữ nghĩa < 2 giây. Ngưỡng khác chỉ để có số liệu riêng.
    'http_req_duration{endpoint:tree_depth3}': ['p(95)<1000'],
    'http_req_duration{endpoint:tree_depth10}': ['p(95)<1000'],
    'http_req_duration{endpoint:relationships}': ['p(95)<1000'],
    'http_req_duration{endpoint:feed}': ['p(95)<1000'],
    'http_req_duration{endpoint:ai_search}': ['p(95)<2000'],
    'http_req_failed{endpoint:tree_depth3}': ['rate<0.01'],
    'http_req_failed{endpoint:tree_depth10}': ['rate<0.01'],
    'http_req_failed{endpoint:relationships}': ['rate<0.01'],
    'http_req_failed{endpoint:feed}': ['rate<0.01'],
    'http_req_failed{endpoint:ai_search}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'max'],
};

export function setup() {
  const login = http.post(`${BASE}/auth/login`, JSON.stringify({ email: EMAIL, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  });
  const token = login.json('data.accessToken');
  if (!token) throw new Error(`Đăng nhập thất bại: ${login.status} ${login.body}`);
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const familyId = http.get(`${BASE}/users/me/membership`, auth).json('data.familyId');
  const ids = [];
  for (let page = 0; page < 10; page++) {
    const res = http.get(`${BASE}/families/${familyId}/persons?page=${page}&size=100`, auth);
    (res.json('data') || []).forEach((p) => ids.push(p.id));
  }
  if (ids.length < 100) throw new Error(`Chỉ thấy ${ids.length} người, chưa chạy prepare-perf.ps1?`);
  return { token, familyId, ids };
}

const pick = (ids) => ids[Math.floor(Math.random() * ids.length)];
const params = (data, name) => ({
  headers: { Authorization: `Bearer ${data.token}` },
  // name gom các URL có id ngẫu nhiên vào một chuỗi metric, tránh k6 cảnh báo quá nhiều chuỗi.
  tags: { endpoint: name, name },
  timeout: '60s',
});
const ok = (res) => check(res, { 'HTTP 200': (r) => r.status === 200 });

export function tree_depth3(data) {
  ok(http.get(`${BASE}/families/${data.familyId}/tree`, params(data, 'tree_depth3')));
}

export function tree_depth10(data) {
  ok(http.get(`${BASE}/families/${data.familyId}/tree?depth=10`, params(data, 'tree_depth10')));
}

export function relationships(data) {
  const url = `${BASE}/relationships?personA=${pick(data.ids)}&personB=${pick(data.ids)}`;
  ok(http.get(url, params(data, 'relationships')));
}

export function feed(data) {
  const page = Math.floor(Math.random() * 5);
  ok(http.get(`${BASE}/posts?page=${page}&size=20&sort=createdAt,desc`, params(data, 'feed')));
}

export function ai_search(data) {
  const q = encodeURIComponent(['ông nội', 'giỗ tổ', 'kỹ sư Hà Nội', 'quỹ khuyến học'][Math.floor(Math.random() * 4)]);
  ok(http.get(`${BASE}/ai/search?q=${q}&page=0&size=10`, params(data, 'ai_search')));
}

export function handleSummary(data) {
  const out = __ENV.OUT || `/out/k6-summary-${VUS}vus.json`;
  return { [out]: JSON.stringify(data, null, 2), stdout: '' };
}
