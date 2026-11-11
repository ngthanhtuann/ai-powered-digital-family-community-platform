import { useRoute, type RouteProp } from '@react-navigation/native';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { FlatList, KeyboardAvoidingView, Platform, Text, TextInput, View } from 'react-native';
import {
  EmptyPanel,
  ErrorPanel,
  formatWhen,
  ListLoadingMore,
  MediaGallery,
  nextPage,
} from '../components/community-ui';
import { Button, Card, Loading, Notice } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { communityApi, type Comment, type Media, type ReactionType } from '../services/community';
import { colors, radius, spacing, text } from '../theme';

const REACTIONS: { type: ReactionType; label: string }[] = [
  { type: 'LIKE', label: '👍 Thích' },
  { type: 'LOVE', label: '❤️ Yêu thích' },
  { type: 'HAHA', label: '😄 Haha' },
  { type: 'SAD', label: '😢 Buồn' },
];

export function PostDetailScreen() {
  const { postId } = useRoute<RouteProp<RootStackParamList, 'PostDetail'>>().params;
  const qc = useQueryClient();
  const [content, setContent] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const post = useQuery({ queryKey: ['post', postId], queryFn: () => communityApi.detail(postId) });
  const comments = useInfiniteQuery({
    queryKey: ['comments', postId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => communityApi.comments(postId, pageParam),
    getNextPageParam: nextPage<Comment>,
  });
  const photos = useInfiniteQuery({
    queryKey: ['post-media', postId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => communityApi.media(postId, pageParam),
    getNextPageParam: nextPage<Media>,
  });
  const allComments = useMemo(
    () => comments.data?.pages.flatMap((p) => p.items) ?? [],
    [comments.data],
  );
  const allPhotos = useMemo(() => photos.data?.pages.flatMap((p) => p.items) ?? [], [photos.data]);

  const reaction = useMutation({
    mutationFn: async (type: ReactionType) => {
      if (post.data?.myReaction === type) return communityApi.removeReaction(postId);
      return communityApi.react(postId, type);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['post', postId] });
      qc.invalidateQueries({ queryKey: ['posts'] });
    },
    onError: (error) => setNotice(error.message),
  });
  const comment = useMutation({
    mutationFn: (value: string) => communityApi.comment(postId, value),
    onSuccess: () => {
      setContent('');
      setNotice(null);
      qc.invalidateQueries({ queryKey: ['comments', postId] });
      qc.invalidateQueries({ queryKey: ['post', postId] });
      qc.invalidateQueries({ queryKey: ['posts'] });
    },
    onError: (error) => setNotice(error.message),
  });
  const send = () => {
    const value = content.trim();
    if (!value || value.length > 1000) {
      setNotice('Bình luận phải có 1–1000 ký tự.');
      return;
    }
    setNotice(null);
    comment.mutate(value);
  };

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <FlatList
        data={allComments}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ padding: spacing.page, gap: spacing.gap, flexGrow: 1 }}
        keyboardShouldPersistTaps="handled"
        onRefresh={() => {
          post.refetch();
          comments.refetch();
          photos.refetch();
        }}
        refreshing={comments.isRefetching && !comments.isFetchingNextPage}
        onEndReachedThreshold={0.4}
        onEndReached={() => {
          if (
            comments.hasNextPage &&
            !comments.isFetchingNextPage &&
            !comments.isFetchNextPageError
          )
            comments.fetchNextPage();
        }}
        ListHeaderComponent={
          <View style={{ gap: 16 }}>
            {post.isPending ? (
              <Loading />
            ) : post.isError ? (
              <ErrorPanel error={post.error} retry={() => post.refetch()} />
            ) : (
              <Card>
                <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>
                  {post.data.authorName}
                </Text>
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  {formatWhen(post.data.createdAt)}
                </Text>
                <Text style={[text.body, { color: colors.textPrimary }]}>{post.data.content}</Text>
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  {Object.values(post.data.reactionCounts).reduce((sum, n) => sum + n, 0)} cảm xúc ·{' '}
                  {post.data.commentCount} bình luận
                </Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  {REACTIONS.map(({ type, label }) => (
                    <View key={type} style={{ minWidth: '45%', flexGrow: 1 }}>
                      <Button
                        title={`${post.data.myReaction === type ? '✓ ' : ''}${label}`}
                        variant="secondary"
                        disabled={reaction.isPending}
                        onPress={() => {
                          setNotice(null);
                          reaction.mutate(type);
                        }}
                      />
                    </View>
                  ))}
                </View>
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  Chạm lại cảm xúc đang chọn để bỏ reaction.
                </Text>
              </Card>
            )}
            {photos.isError ? (
              <ErrorPanel error={photos.error} retry={() => photos.refetch()} />
            ) : null}
            {allPhotos.length ? (
              <Card title="Ảnh đính kèm">
                <MediaGallery media={allPhotos} />
                {photos.hasNextPage ? (
                  <Button
                    title="Tải thêm ảnh"
                    variant="secondary"
                    loading={photos.isFetchingNextPage}
                    onPress={() => photos.fetchNextPage()}
                  />
                ) : null}
              </Card>
            ) : null}
            <Card title="Viết bình luận">
              <TextInput
                accessibilityLabel="Nội dung bình luận"
                value={content}
                onChangeText={setContent}
                placeholder="Nhập bình luận..."
                placeholderTextColor={colors.textSecondary}
                multiline
                maxLength={1000}
                style={{
                  minHeight: 90,
                  borderColor: colors.border,
                  borderWidth: 1,
                  borderRadius: radius.control,
                  backgroundColor: colors.surface,
                  padding: 12,
                  textAlignVertical: 'top',
                  color: colors.textPrimary,
                }}
              />
              <Button title="Gửi bình luận" loading={comment.isPending} onPress={send} />
              {notice ? (
                <Notice variant="error" title="Không thực hiện được" message={notice} />
              ) : null}
            </Card>
            <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>Bình luận</Text>
          </View>
        }
        renderItem={({ item }) => (
          <Card>
            <Text style={[text.label, { color: colors.textPrimary }]}>{item.authorName}</Text>
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              {formatWhen(item.createdAt)}
            </Text>
            <Text style={[text.body, { color: colors.textPrimary }]}>{item.content}</Text>
          </Card>
        )}
        ListEmptyComponent={
          comments.isPending ? (
            <Loading />
          ) : comments.isError ? (
            <ErrorPanel error={comments.error} retry={() => comments.refetch()} />
          ) : (
            <EmptyPanel message="Chưa có bình luận." />
          )
        }
        ListFooterComponent={
          comments.isFetchNextPageError ? (
            <ErrorPanel error={comments.error} retry={() => comments.fetchNextPage()} />
          ) : (
            <ListLoadingMore loading={comments.isFetchingNextPage} />
          )
        }
      />
    </KeyboardAvoidingView>
  );
}
