// Thống kê và báo cáo trên mobile (FR-DSH-01 đến 05), như web /dashboard và /reports. Chỉ trưởng chi, trưởng họ,
// quản trị (BR-DSH-01, 03). Biểu đồ vẽ bằng thanh ngang (không thêm thư viện biểu đồ), luôn kèm số và nhãn chữ.
import { useMutation, useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import { AdminFamilyPicker } from '../components/AdminFamilyPicker';
import { ErrorText, Select, isValidDate, todayIso } from '../components/form-ui';
import { Button, Card, Loading, Notice, Screen, TextField } from '../components/ui';
import { dashboardApi, managedBranches, type DashboardReportType } from '../services/admin';
import { genealogyManageApi } from '../services/genealogy';
import { useFamilyScope } from '../session/FamilyScope';
import { colors, radius, text } from '../theme';

/** Màu theo token (design-system.md mục 1), giống bảng màu biểu đồ của web. */
const BAR_COLORS = [
  colors.primary,
  colors.secondary,
  colors.success,
  colors.info,
  colors.warning,
  colors.textSecondary,
];

function Bars({
  items,
  unit = 'người',
  limit = 8,
}: {
  items: { label: string; value: number }[];
  unit?: string;
  limit?: number;
}) {
  const [all, setAll] = useState(false);
  const shown = all ? items : items.slice(0, limit);
  const max = Math.max(1, ...items.map((i) => i.value));
  if (items.every((i) => i.value === 0)) {
    return <Text style={[text.body, { color: colors.textSecondary }]}>Chưa có dữ liệu.</Text>;
  }
  return (
    <View style={{ gap: 8 }}>
      {shown.map((item, i) => (
        <View
          key={`${item.label}-${i}`}
          accessible
          accessibilityLabel={`${item.label}: ${item.value} ${unit}`}
          style={{ gap: 2 }}
        >
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 8 }}>
            <Text style={[text.caption, { color: colors.textPrimary, flex: 1 }]}>
              {item.label || 'Chưa rõ'}
            </Text>
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              {item.value.toLocaleString('vi-VN')} {unit}
            </Text>
          </View>
          <View
            style={{
              height: 10,
              backgroundColor: colors.surfaceMuted,
              borderRadius: radius.control,
            }}
          >
            <View
              style={{
                height: 10,
                width: `${(item.value / max) * 100}%`,
                backgroundColor: BAR_COLORS[i % BAR_COLORS.length],
                borderRadius: radius.control,
              }}
            />
          </View>
        </View>
      ))}
      {items.length > limit ? (
        <Button
          title={all ? 'Thu gọn' : `Xem thêm (${items.length} mục)`}
          variant="secondary"
          onPress={() => setAll(!all)}
        />
      ) : null}
    </View>
  );
}

function Metric({ title, value }: { title: string; value: number }) {
  return (
    <View
      style={{
        flexBasis: '47%',
        flexGrow: 1,
        backgroundColor: colors.surface,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: radius.card,
        padding: 16,
        gap: 4,
      }}
    >
      <Text style={[text.caption, { color: colors.textSecondary }]}>{title}</Text>
      <Text style={{ fontSize: 28, lineHeight: 36, fontWeight: '700', color: colors.primary }}>
        {value.toLocaleString('vi-VN')}
      </Text>
    </View>
  );
}

/** Phạm vi thống kê/báo cáo: gia đình (quản trị chọn), chi (trưởng chi chỉ thấy chi mình và chi con). */
function useReportScope() {
  const scope = useFamilyScope();
  const familyId = scope.familyId ?? '';
  const isBranchAdmin = scope.isAdmin && !scope.isSystemAdmin;
  const [selectedBranch, setSelectedBranch] = useState('');
  const branches = useQuery({
    queryKey: ['branches', familyId],
    queryFn: () => genealogyManageApi.branches(familyId),
    enabled: scope.isAdmin && Boolean(familyId),
  });
  const visible = useMemo(() => {
    const all = branches.data?.items ?? [];
    return isBranchAdmin && scope.branchId ? managedBranches(all, scope.branchId) : all;
  }, [branches.data, isBranchAdmin, scope.branchId]);
  const branchId =
    isBranchAdmin && scope.branchId ? selectedBranch || scope.branchId : selectedBranch;
  const params = {
    ...(scope.isSystemAdmin ? { familyId } : {}),
    ...(branchId ? { branchId } : {}),
  };
  const branchSelect = (
    <Select
      label="Chi họ"
      value={branchId}
      onChange={setSelectedBranch}
      disabled={branches.isPending || !visible.length}
      options={[
        ...(!isBranchAdmin || !scope.branchId ? [{ value: '', label: 'Toàn gia đình' }] : []),
        ...visible.map((b) => ({ value: b.id ?? '', label: b.name ?? '' })),
      ]}
    />
  );
  return { scope, familyId, branchId, params, branches, branchSelect };
}

export function DashboardScreen() {
  const { scope, familyId, branchId, params, branches, branchSelect } = useReportScope();
  const statistics = useQuery({
    queryKey: ['dashboard-statistics', familyId, branchId],
    queryFn: () => dashboardApi.statistics(params),
    enabled: scope.isAdmin && Boolean(familyId),
  });
  const csv = useMutation({ mutationFn: () => dashboardApi.exportMembersCsv(params) });
  if (!scope.isAdmin) {
    return (
      <Screen>
        <Notice
          variant="error"
          title="PERM_001"
          message="Chỉ quản trị viên gia đình hoặc trưởng chi mới được xem dashboard."
        />
      </Screen>
    );
  }
  const data = statistics.data;
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Theo dõi thành viên, hoạt động cộng đồng và sự kiện trong phạm vi được quản lý.
      </Text>
      <Button
        title="Xuất CSV thành viên"
        loading={csv.isPending}
        disabled={!familyId}
        onPress={() => csv.mutate()}
      />
      {csv.isError ? <ErrorText error={csv.error} /> : null}
      <Card title="Bộ lọc thống kê">
        {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
        {branchSelect}
      </Card>
      <ErrorText error={branches.error ?? statistics.error} />
      {!familyId ? (
        <Notice
          variant="info"
          title="Chưa chọn gia đình"
          message="Chọn gia đình để xem thống kê."
        />
      ) : null}
      {statistics.isPending && familyId ? <Loading label="Đang tải thống kê..." /> : null}
      {data ? (
        <>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}>
            <Metric title="Tổng thành viên" value={data.totalMembers} />
            <Metric title="Nam" value={data.genderStats.MALE} />
            <Metric title="Nữ" value={data.genderStats.FEMALE} />
            <Metric title="Sự kiện sắp tới" value={data.upcomingEvents.length} />
          </View>
          <Card title="Thành viên theo thế hệ">
            <Bars
              items={data.generationStats.map((g) => ({
                label: `Đời ${g.generation}`,
                value: g.count,
              }))}
              limit={20}
            />
          </Card>
          <Card title="Thành viên theo độ tuổi">
            <Bars items={data.ageStats.map((a) => ({ label: a.ageRange, value: a.count }))} />
          </Card>
          <Card title="Cơ cấu giới tính">
            <Bars
              items={[
                { label: 'Nam', value: data.genderStats.MALE },
                { label: 'Nữ', value: data.genderStats.FEMALE },
                { label: 'Khác', value: data.genderStats.OTHER },
              ]}
            />
          </Card>
          <Card title="Nghề nghiệp" description="Không bao gồm hồ sơ của trẻ dưới 16 tuổi.">
            <Bars
              items={data.professionStats.map((p) => ({ label: p.jobTitle, value: p.count }))}
            />
          </Card>
          <Card title="Nơi ở" description="Không bao gồm hồ sơ của trẻ dưới 16 tuổi.">
            <Bars items={data.locationStats.map((l) => ({ label: l.location, value: l.count }))} />
          </Card>
          <Card title="Bài đăng theo tuần" description="8 tuần gần nhất, theo múi giờ Việt Nam.">
            <Bars
              unit="bài"
              items={data.weeklyPosts.map((w) => ({
                label: `Tuần từ ${w.weekStart.slice(0, 10).split('-').reverse().join('/')}`,
                value: w.postCount,
              }))}
            />
          </Card>
          <Card title="Sự kiện sắp tới">
            {data.upcomingEvents.length === 0 ? (
              <Text style={[text.body, { color: colors.textSecondary }]}>
                Chưa có sự kiện sắp tới.
              </Text>
            ) : null}
            {data.upcomingEvents.map((e) => (
              <View
                key={e.id}
                style={{ gap: 2, borderTopWidth: 1, borderColor: colors.border, paddingTop: 8 }}
              >
                <Text style={[text.label, { color: colors.textPrimary }]}>{e.title}</Text>
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  {new Date(e.startAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}
                </Text>
              </View>
            ))}
          </Card>
        </>
      ) : null}
    </Screen>
  );
}

const REPORT_LABEL: Record<DashboardReportType, string> = {
  GENEALOGY: 'Gia phả',
  COMMUNITY: 'Hoạt động cộng đồng',
  EVENTS: 'Sự kiện',
};

const daysAgo = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

/** FR-DSH-05: báo cáo PDF theo gia đình, chi họ và khoảng thời gian (tối đa 366 ngày); mở bằng trình xem PDF của máy. */
export function ReportsScreen() {
  const { scope, familyId, params, branches, branchSelect } = useReportScope();
  const [reportType, setReportType] = useState<DashboardReportType>('GENEALOGY');
  const [from, setFrom] = useState(() => daysAgo(29));
  const [to, setTo] = useState(todayIso);
  const span =
    isValidDate(from) && isValidDate(to)
      ? (new Date(`${to}T00:00:00Z`).getTime() - new Date(`${from}T00:00:00Z`).getTime()) /
        86_400_000
      : -1;
  const invalidRange = span < 0 || span > 365;
  const pdf = useMutation({
    mutationFn: () =>
      dashboardApi.exportPdf({
        ...params,
        reportType,
        from,
        to,
        title: `Báo cáo ${REPORT_LABEL[reportType]}`,
      }),
  });
  if (!scope.isAdmin) {
    return (
      <Screen>
        <Notice
          variant="error"
          title="PERM_001"
          message="Chỉ quản trị viên gia đình hoặc trưởng chi mới được tạo báo cáo."
        />
      </Screen>
    );
  }
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Tạo, xem và tải báo cáo PDF theo gia đình, chi họ và khoảng thời gian.
      </Text>
      <Card title="Phạm vi và bộ lọc báo cáo">
        {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
        {branchSelect}
        <Select
          label="Loại báo cáo"
          value={reportType}
          onChange={setReportType}
          options={(Object.keys(REPORT_LABEL) as DashboardReportType[]).map((t) => ({
            value: t,
            label: REPORT_LABEL[t],
          }))}
        />
        <TextField
          label="Từ ngày (YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={from}
          onChangeText={setFrom}
        />
        <TextField
          label="Đến ngày (YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={to}
          onChangeText={setTo}
        />
        {invalidRange ? (
          <Text accessibilityRole="alert" style={[text.caption, { color: colors.error }]}>
            Vui lòng chọn khoảng ngày hợp lệ, tối đa 366 ngày.
          </Text>
        ) : null}
        <Button
          title="Xem và tải PDF"
          loading={pdf.isPending}
          disabled={!familyId || invalidRange}
          onPress={() => pdf.mutate()}
        />
        {pdf.isError ? <ErrorText error={pdf.error} /> : null}
        <ErrorText error={branches.error} />
      </Card>
    </Screen>
  );
}
