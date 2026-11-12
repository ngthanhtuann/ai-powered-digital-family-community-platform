-- Dữ liệu GIẢ cho kiểm thử hiệu năng (SCRUM-59). CHỈ chạy trên DB thử, không chạy trên DB thật.
-- Gia đình và tài khoản tạo trước qua API (prepare-perf.ps1); script này thêm hàng loạt bằng SQL vì nhanh hơn
-- gọi API hàng chục nghìn lần. Không sinh embedding (cần khóa Gemini, xem báo cáo).
-- Biến psql: :fam (family id), :uid (user tạo), :n (số người trong họ, mặc định 5000).
--
-- Cây 4 nhánh: người k (k >= 2) là con của người (k - 2) / 4 + 1, nên đời của k tăng theo log4(k).
-- Ai có con thì có thêm vợ/chồng từ ngoài họ, nên tổng số người khoảng 1,25 x n.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE perf_blood AS
SELECT k,
       gen_random_uuid() AS id,
       CASE WHEN k = 1 THEN NULL ELSE (k - 2) / 4 + 1 END AS parent_k,
       -- log cơ số 4 bằng numeric để không lệch ở lũy thừa đúng (k = 6 phải ra đời 2).
       floor(log(4::numeric, (3 * k - 2)::numeric) + 0.000000001)::int AS level
FROM generate_series(1, :n) AS k;

-- Người có con thì là nam (cha), vợ là nữ; người chưa có con nam nữ xen kẽ.
ALTER TABLE perf_blood ADD COLUMN has_children boolean;
UPDATE perf_blood b SET has_children = (4 * b.k - 2 <= :n);

-- Năm sinh tăng 22 năm mỗi đời từ 1880, cộng lệch theo k; đời 0 đến 2 đã mất.
INSERT INTO person (id, family_id, full_name, gender, birth_date, current_province, is_deceased, death_date, created_by)
SELECT b.id, :'fam',
       'Lê ' || (ARRAY['Văn', 'Thị', 'Minh', 'Gia', 'Đức', 'Thu'])[1 + b.k % 6] || ' Perf ' || b.k,
       CASE WHEN b.has_children OR b.k % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END,
       make_date(1880 + b.level * 22 + b.k % 9, 1 + b.k % 12, 1 + b.k % 28),
       (ARRAY['Hà Nội', 'Nam Định', 'Huế', 'Đà Nẵng', 'TP. Hồ Chí Minh', 'Cần Thơ'])[1 + b.k % 6],
       b.level <= 2,
       CASE WHEN b.level <= 2 THEN make_date(1880 + b.level * 22 + b.k % 9 + 70, 6, 1) END,
       :'uid'
FROM perf_blood b;

CREATE TEMP TABLE perf_spouse AS
SELECT b.k, b.id AS blood_id, gen_random_uuid() AS id, b.level
FROM perf_blood b WHERE b.has_children;

INSERT INTO person (id, family_id, full_name, gender, birth_date, current_province, is_deceased, death_date, created_by)
SELECT s.id, :'fam', 'Nguyễn Thị Dâu ' || s.k, 'FEMALE',
       make_date(1882 + s.level * 22 + s.k % 9, 1 + s.k % 12, 1 + s.k % 28),
       'Hà Nội', s.level <= 2,
       CASE WHEN s.level <= 2 THEN make_date(1882 + s.level * 22 + s.k % 9 + 70, 6, 1) END,
       :'uid'
FROM perf_spouse s;

INSERT INTO marriage (person1_id, person2_id, status, start_date, end_date, created_by)
SELECT s.blood_id, s.id,
       CASE WHEN s.level <= 2 THEN 'WIDOWED' ELSE 'MARRIED' END,
       make_date(1880 + s.level * 22 + s.k % 9 + 20, 3, 1),
       CASE WHEN s.level <= 2 THEN make_date(1880 + s.level * 22 + s.k % 9 + 70, 6, 1) END,
       :'uid'
FROM perf_spouse s;

-- Mỗi người (trừ cụ tổ) có cha là người trong họ và mẹ là vợ của cha.
INSERT INTO parent_child (parent_id, child_id, parent_role, kind, created_by)
SELECT p.id, c.id, 'FATHER', 'BIOLOGICAL', :'uid'
FROM perf_blood c JOIN perf_blood p ON p.k = c.parent_k;
INSERT INTO parent_child (parent_id, child_id, parent_role, kind, created_by)
SELECT s.id, c.id, 'MOTHER', 'BIOLOGICAL', :'uid'
FROM perf_blood c JOIN perf_spouse s ON s.k = c.parent_k;

-- Bảng tin: 2.000 bài, sự kiện: 200, rải đều 2 năm gần đây.
INSERT INTO post (family_id, content, type, created_by, created_at, updated_at)
SELECT :'fam', 'Bài viết giả số ' || i || ' để đo hiệu năng bảng tin.',
       CASE WHEN i % 10 = 0 THEN 'NEWS' ELSE 'POST' END, :'uid',
       now() - (i || ' hours')::interval * 8, now() - (i || ' hours')::interval * 8
FROM generate_series(1, 2000) AS i;
INSERT INTO event (family_id, title, description, start_at, location, created_by)
SELECT :'fam', 'Sự kiện giả ' || i, 'Dữ liệu giả để đo hiệu năng.',
       now() + ((i - 100) || ' days')::interval, 'Nhà thờ họ', :'uid'
FROM generate_series(1, 200) AS i;

COMMIT;
ANALYZE person; ANALYZE parent_child; ANALYZE marriage; ANALYZE post; ANALYZE event;

SELECT 'nguoi' AS loai, count(*) FROM person WHERE family_id = :'fam'
UNION ALL SELECT 'cha-con', count(*) FROM parent_child pc JOIN person c ON c.id = pc.child_id WHERE c.family_id = :'fam'
UNION ALL SELECT 'hon nhan', count(*) FROM marriage m JOIN person a ON a.id = m.person1_id WHERE a.family_id = :'fam'
UNION ALL SELECT 'so doi', max(level) + 1 FROM perf_blood
UNION ALL SELECT 'bai dang', count(*) FROM post WHERE family_id = :'fam';
