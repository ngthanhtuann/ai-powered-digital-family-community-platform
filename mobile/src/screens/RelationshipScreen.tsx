// UC-GEN-07, 08 trên mobile (SCRUM-90): chọn hai người, gọi GET /relationships; giải thích bằng AI (UC-AI-04) khi cần.
import type { DrawerScreenProps } from '@react-navigation/drawer';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Share, Text, View } from 'react-native';
import { PersonPicker, PersonSelect } from '../components/genealogy-ui';
import { Button, Card, Notice, Screen } from '../components/ui';
import type { AppDrawerParamList } from '../navigation/types';
import { genealogyApi } from '../services/genealogy';
import { useSession } from '../session/SessionContext';
import { colors, radius, text } from '../theme';

type Picked = { id: string; name: string } | null;
type Slot = 'A' | 'B';

/** Bảng tra nhanh trên Figma "UC-GEN-07 Tra cứu cách xưng hô / 360": kiến thức chung, không phải dữ liệu gia đình. */
const QUICK_TABLE: [string, string, string][] = [
  ['Con → Cha', 'Bố / Ba / Cha', 'Con'],
  ['Cháu → Ông bà', 'Ông / Bà', 'Cháu'],
  ['Em → Anh/Chị', 'Anh / Chị', 'Em'],
];

export function RelationshipScreen({
  route,
}: DrawerScreenProps<AppDrawerParamList, 'Relationship'>) {
  const { session } = useSession();
  const familyId = session.status === 'signedIn' ? session.membership.familyId : null;
  const familyName = session.status === 'signedIn' ? session.membership.familyName : null;
  const [a, setA] = useState<Picked>(null);
  const [b, setB] = useState<Picked>(null);
  const [picking, setPicking] = useState<Slot | null>(null);

  // Mở từ thẻ chi tiết trên cây: điền sẵn người thứ nhất (chỉnh state khi tham số đổi, không dùng effect).
  const presetId = route.params?.personAId;
  const [appliedPreset, setAppliedPreset] = useState<string | undefined>();
  if (presetId !== appliedPreset) {
    setAppliedPreset(presetId);
    if (presetId) {
      setA({ id: presetId, name: route.params?.personAName ?? '' });
      setB(null);
    }
  }

  const ready = Boolean(a && b && a.id !== b.id);
  const relation = useQuery({
    queryKey: ['relationship', a?.id, b?.id],
    queryFn: () => genealogyApi.relationship(a!.id, b!.id),
    enabled: ready,
  });
  const explain = useMutation({ mutationFn: () => genealogyApi.explain(a!.id, b!.id) });
  const { reset: resetExplain } = explain;
  useEffect(() => resetExplain(), [a?.id, b?.id, resetExplain]);

  const reset = () => {
    setA(null);
    setB(null);
  };
  const share = () => {
    if (relation.data?.description) void Share.share({ message: relation.data.description });
  };

  if (!familyId) {
    return (
      <Screen>
        <Notice
          variant="info"
          title="Chưa thuộc gia đình"
          message="Tham gia một gia đình để tra cứu xưng hô."
        />
      </Screen>
    );
  }

  return (
    <Screen>
      <View style={{ gap: 2 }}>
        <Text style={[text.pageTitle, { color: colors.textPrimary }]}>Tra cứu cách xưng hô</Text>
        {familyName ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>{familyName}</Text>
        ) : null}
      </View>

      <Card>
        <PersonSelect label="Người thứ nhất" value={a?.name} onPress={() => setPicking('A')} />
        <PersonSelect label="Người thứ hai" value={b?.name} onPress={() => setPicking('B')} />
        {a && b && a.id === b.id ? (
          <Notice variant="warning" title="Trùng người" message="Hãy chọn hai người khác nhau." />
        ) : null}

        {!ready ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>
            Chọn hai thành viên để xem người thứ nhất là gì của người thứ hai.
          </Text>
        ) : relation.isLoading ? (
          <ActivityIndicator color={colors.primary} />
        ) : relation.isError ? (
          <View style={{ gap: 8 }}>
            <Notice
              variant="error"
              title="Không tra cứu được"
              message={
                relation.error instanceof Error ? relation.error.message : 'Vui lòng thử lại.'
              }
            />
            <Button title="Thử lại" variant="secondary" onPress={() => relation.refetch()} />
          </View>
        ) : relation.data ? (
          <View
            style={{
              gap: 12,
              padding: 14,
              borderWidth: 1,
              borderColor: colors.border,
              borderRadius: radius.card,
              backgroundColor: colors.surfaceMuted,
            }}
          >
            <View
              style={{ borderLeftWidth: 3, borderColor: colors.secondary, paddingLeft: 8, gap: 4 }}
            >
              {relation.data.supported && relation.data.relation ? (
                <Text style={[text.sectionTitle, { color: colors.error }]}>
                  {relation.data.relation}
                </Text>
              ) : null}
              <Text style={[text.body, { color: colors.textPrimary }]}>
                {relation.data.description}
              </Text>
            </View>
            {relation.data.path && relation.data.path.length > 1 ? (
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                Đường quan hệ: {relation.data.path.map((step) => step.fullName).join(' → ')}
              </Text>
            ) : null}
            {!relation.data.supported ? (
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                Hệ thống chỉ gọi tên quan hệ đến đời chắt và họ gần; họ xa hơn chưa hỗ trợ.
              </Text>
            ) : null}

            {explain.data ? (
              <Notice
                variant="info"
                title="Giải thích của trợ lý AI"
                message={explain.data.explanation}
              />
            ) : explain.isError ? (
              <Notice
                variant="error"
                title="AI chưa giải thích được"
                message={
                  explain.error instanceof Error ? explain.error.message : 'Vui lòng thử lại sau.'
                }
              />
            ) : null}
            {relation.data.supported && !explain.data ? (
              <Button
                title="Giải thích bằng AI"
                variant="secondary"
                loading={explain.isPending}
                onPress={() => explain.mutate()}
              />
            ) : null}
          </View>
        ) : null}

        <Button title="Tra cứu khác" onPress={reset} disabled={!a && !b} />
        {relation.data ? <Button title="Chia sẻ" variant="secondary" onPress={share} /> : null}
      </Card>

      <Card title="Bảng cách xưng hô nhanh">
        <View style={{ borderWidth: 1, borderColor: colors.border, borderRadius: radius.control }}>
          {[['Quan hệ', 'Gọi là', 'Xưng là'] as [string, string, string], ...QUICK_TABLE].map(
            (row, i) => (
              <View
                key={row[0]}
                style={{
                  flexDirection: 'row',
                  borderTopWidth: i ? 1 : 0,
                  borderColor: colors.border,
                  backgroundColor: i ? colors.surface : colors.surfaceMuted,
                }}
              >
                {row.map((cell, j) => (
                  <Text
                    key={j}
                    style={[
                      i ? text.body : text.label,
                      {
                        flex: j === 2 ? 0.7 : 1,
                        padding: 8,
                        color: i && j ? colors.error : colors.textPrimary,
                      },
                    ]}
                  >
                    {cell}
                  </Text>
                ))}
              </View>
            ),
          )}
        </View>
      </Card>

      <PersonPicker
        visible={picking !== null}
        familyId={familyId}
        title={picking === 'B' ? 'Chọn người thứ hai' : 'Chọn người thứ nhất'}
        onClose={() => setPicking(null)}
        onPick={(person) => {
          const picked = { id: person.id ?? '', name: person.fullName ?? '' };
          if (picking === 'B') setB(picked);
          else setA(picked);
          setPicking(null);
        }}
      />
    </Screen>
  );
}
