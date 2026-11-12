// Chọn một thành viên trong gia đình (GET /families/{id}/persons?q=), dùng cho Cây gia phả và Tra cứu xưng hô (SCRUM-90).
// Là màn của stack (không dùng Modal) để thanh trạng thái Android do app quản lý; chọn xong trả kết quả bằng popTo.
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { EmptyPanel, ErrorPanel } from '../components/community-ui';
import { PersonAvatar } from '../components/genealogy-ui';
import { Loading, TextField } from '../components/ui';
import type { PersonPick, RootStackParamList } from '../navigation/types';
import { genealogyApi, lifeSpan, type Person } from '../services/genealogy';
import { useSession } from '../session/SessionContext';
import { colors, spacing, text } from '../theme';

/** Mốc thời gian tạo ngoài component (quy tắc thuần của React 19) để màn nhận biết mỗi lần chọn. */
function toPick(person: Person): PersonPick {
  return { id: person.id ?? '', name: person.fullName ?? '', at: Date.now() };
}

export function PersonPickerScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'PersonPicker'>) {
  const { session } = useSession();
  const familyId = session.status === 'signedIn' ? session.membership.familyId : null;
  const [input, setInput] = useState('');
  const [q, setQ] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setQ(input), 300);
    return () => clearTimeout(timer);
  }, [input]);
  const persons = useQuery({
    queryKey: ['persons', familyId, q],
    queryFn: () => genealogyApi.persons(familyId as string, q),
    enabled: Boolean(familyId),
  });
  const items = persons.data?.items ?? [];

  const pick = (person: Person) => {
    const picked = toPick(person);
    if (route.params.target === 'treeRoot') {
      navigation.popTo('App', { screen: 'FamilyTree', params: { root: picked } });
    } else {
      navigation.popTo('App', {
        screen: 'Relationship',
        params: route.params.target === 'relationA' ? { a: picked } : { b: picked },
      });
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <FlatList
        data={items}
        keyExtractor={(p) => p.id ?? ''}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ padding: spacing.page, gap: 0 }}
        ListHeaderComponent={
          <View style={{ paddingBottom: 12 }}>
            <TextField
              label="Tìm theo họ tên"
              value={input}
              onChangeText={setInput}
              // API tìm theo họ tên có dấu (PersonRepository, SCRUM-102); gõ không dấu sẽ không ra.
              placeholder="Gõ có dấu, ví dụ: Nguyễn Văn"
              autoCorrect={false}
            />
          </View>
        }
        ListEmptyComponent={
          persons.isLoading ? (
            <Loading />
          ) : persons.isError ? (
            <ErrorPanel error={persons.error} retry={() => persons.refetch()} />
          ) : (
            <EmptyPanel message="Không tìm thấy thành viên phù hợp." />
          )
        }
        ListFooterComponent={
          persons.data && persons.data.meta.total > items.length ? (
            <Text style={[text.caption, { color: colors.textSecondary, padding: 8 }]}>
              Hiện {items.length}/{persons.data.meta.total} người. Gõ thêm tên để thu hẹp.
            </Text>
          ) : undefined
        }
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            onPress={() => pick(item)}
            style={({ pressed }) => ({
              minHeight: 56,
              flexDirection: 'row',
              alignItems: 'center',
              gap: 12,
              paddingVertical: 8,
              borderBottomWidth: 1,
              borderColor: colors.border,
              backgroundColor: pressed ? colors.surfaceMuted : 'transparent',
            })}
          >
            <PersonAvatar name={item.fullName} gender={item.gender} mediaId={item.avatarMediaId} />
            <View style={{ flex: 1 }}>
              <Text style={[text.label, { color: colors.textPrimary }]}>{item.fullName}</Text>
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {lifeSpan(item.birthYear, item.isDeceased, item.deathDate) || ' '}
              </Text>
            </View>
          </Pressable>
        )}
      />
    </View>
  );
}
