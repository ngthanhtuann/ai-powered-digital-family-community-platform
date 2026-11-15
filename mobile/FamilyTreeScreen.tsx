import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  Pressable,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { colors, text, spacing } from '../theme';

interface FamilyNode {
  id: string;
  fullName: string;
  generation: number;
  birthYear?: number;
  deathYear?: number;
  profession?: string;
  spouse?: string;
  roleInFamily: string; // Ví dụ: Trưởng tộc, Thành viên,...
}

// Mock service dữ liệu cây gia phả phân theo thế hệ
const fetchFamilyTreeData = async (): Promise<FamilyNode[]> => {
  return [
    // Thế hệ 1
    { id: '1', fullName: 'Nguyễn Văn Tổ', generation: 1, birthYear: 1920, deathYear: 1995, profession: 'Cụ tổ', roleInFamily: 'Tổ tiên' },
    { id: '2', fullName: 'Lê Thị Mẫu', generation: 1, birthYear: 1925, deathYear: 2005, spouse: 'Nguyễn Văn Tổ', roleInFamily: 'Tổ mẫu' },
    
    // Thế hệ 2
    { id: '3', fullName: 'Nguyễn Văn Cha', generation: 2, birthYear: 1950, profession: 'Giáo viên về hưu', roleInFamily: 'Trưởng nam' },
    { id: '4', fullName: 'Trần Thị Mẹ', generation: 2, birthYear: 1955, spouse: 'Nguyễn Văn Cha', roleInFamily: 'Con dâu' },
    
    // Thế hệ 3
    { id: '5', fullName: 'Nguyễn Văn A', generation: 3, birthYear: 1995, profession: 'Kỹ sư phần mềm', roleInFamily: 'Cháu đích tôn' },
    { id: '6', fullName: 'Nguyễn Thị B', generation: 3, birthYear: 1998, profession: 'Bác sĩ', roleInFamily: 'Cháu gái' },
  ];
};

export function FamilyTreeScreen() {
  const [selectedGeneration, setSelectedGeneration] = useState<number | null>(null);

  const { data: nodes, isLoading, error } = useQuery({
    queryKey: ['familyTree'],
    queryFn: fetchFamilyTreeData,
  });

  // Nhóm các thành viên theo thế hệ
  const generations = nodes ? Array.from(new Set(nodes.map(n => n.generation))).sort() : [];

  const filteredNodes = selectedGeneration 
    ? nodes?.filter(n => n.generation === selectedGeneration) 
    : nodes;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      <View style={styles.headerBanner}>
        <Text style={styles.bannerTitle}>🌳 Sơ đồ Cây Gia Phả</Text>
        <Text style={styles.bannerSubtitle}>Quản lý và kết nối các thế hệ dòng họ</Text>
      </View>

      {/* Bộ lọc thế hệ nhanh */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filterRow}>
        <Pressable
          style={[styles.filterChip, selectedGeneration === null && styles.filterChipActive]}
          onPress={() => setSelectedGeneration(null)}
        >
          <Text style={[styles.filterText, selectedGeneration === null && styles.filterTextActive]}>Tất cả đời</Text>
        </Pressable>
        {generations.map((gen) => (
          <Pressable
            key={gen}
            style={[styles.filterChip, selectedGeneration === gen && styles.filterChipActive]}
            onPress={() => setSelectedGeneration(gen)}
          >
            <Text style={[styles.filterText, selectedGeneration === gen && styles.filterTextActive]}>
              Thế hệ thứ {gen}
            </Text>
          </Pressable>
        ))}
      </ScrollView>

      {/* Nội dung danh sách cây gia phả */}
      {isLoading ? (
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      ) : error ? (
        <View style={styles.centerContainer}>
          <Text style={{ color: colors.error }}>Không thể tải sơ đồ gia phả.</Text>
        </View>
      ) : (
        <View style={styles.treeContainer}>
          {generations
            .filter(gen => selectedGeneration === null || selectedGeneration === gen)
            .map((gen) => {
              const genMembers = filteredNodes?.filter(n => n.generation === gen) || [];
              if (genMembers.length === 0) return null;

              return (
                <View key={gen} style={styles.generationGroup}>
                  <View style={styles.generationHeaderBadge}>
                    <Text style={styles.generationHeaderText}>Thế hệ thứ {gen}</Text>
                  </View>

                  <View style={styles.nodesList}>
                    {genMembers.map((member) => (
                      <Pressable
                        key={member.id}
                        style={styles.nodeCard}
                        onPress={() => {
                          Alert.alert(
                            member.fullName,
                            `Vai trò: ${member.roleInFamily}\nNăm sinh: ${member.birthYear || 'N/A'} ${member.deathYear ? `- Mất: ${member.deathYear}` : ''}\nNghề nghiệp: ${member.profession || 'Chưa cập nhật'}`
                          );
                        }}
                      >
                        <View style={styles.nodeAvatar}>
                          <Text style={styles.nodeAvatarText}>{member.fullName.charAt(0)}</Text>
                        </View>
                        <View style={styles.nodeInfo}>
                          <View style={styles.rowBetween}>
                            <Text style={styles.nodeName}>{member.fullName}</Text>
                            <Text style={styles.nodeRoleTag}>{member.roleInFamily}</Text>
                          </View>
                          <Text style={styles.nodeDetails}>
                            📅 {member.birthYear}{member.deathYear ? ` - ${member.deathYear}` : ''} 
                            {member.profession ? ` | 💼 ${member.profession}` : ''}
                          </Text>
                          {member.spouse && (
                            <Text style={styles.nodeSpouse}>❤️ Vợ/Chồng: {member.spouse}</Text>
                          )}
                        </View>
                      </Pressable>
                    ))}
                  </View>
                </View>
              );
            })}
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  contentContainer: { paddingBottom: 30 },
  headerBanner: {
    padding: spacing.page,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border || '#eee',
  },
  bannerTitle: { fontSize: 20, fontWeight: 'bold', color: colors.textPrimary },
  bannerSubtitle: { fontSize: 13, color: colors.textSecondary, marginTop: 2 },
  filterRow: {
    paddingHorizontal: spacing.page,
    paddingVertical: 10,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.border || '#eee',
    maxHeight: 55,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border || '#ccc',
    marginRight: 8,
    height: 34,
    justifyContent: 'center',
  },
  filterChipActive: {
    backgroundColor: colors.secondary,
    borderColor: colors.secondary,
  },
  filterText: { fontSize: 13, color: colors.textSecondary },
  filterTextActive: { color: colors.textPrimary, fontWeight: '600' },
  treeContainer: { padding: spacing.page, gap: 16 },
  generationGroup: { gap: 8 },
  generationHeaderBadge: {
    backgroundColor: colors.primary,
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 6,
    alignSelf: 'flex-start',
    marginBottom: 4,
  },
  generationHeaderText: { color: colors.creamOnDark, fontWeight: 'bold', fontSize: 12 },
  nodesList: { gap: 10, paddingLeft: 8, borderLeftWidth: 2, borderLeftColor: colors.primary + '40' },
  nodeCard: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: 12,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border || '#eee',
    shadowColor: '#000',
    shadowOpacity: 0.03,
    shadowRadius: 3,
    elevation: 2,
  },
  nodeAvatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.secondary,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  nodeAvatarText: { color: colors.textPrimary, fontSize: 18, fontWeight: 'bold' },
  nodeInfo: { flex: 1, gap: 3 },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  nodeName: { fontSize: 15, fontWeight: 'bold', color: colors.textPrimary },
  nodeRoleTag: { fontSize: 10, fontWeight: '600', color: colors.primary, backgroundColor: colors.primary + '15', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  nodeDetails: { fontSize: 12, color: colors.textSecondary },
  nodeSpouse: { fontSize: 11, color: colors.textSecondary, fontStyle: 'italic' },
  centerContainer: { padding: 40, justifyContent: 'center', alignItems: 'center' },
});
