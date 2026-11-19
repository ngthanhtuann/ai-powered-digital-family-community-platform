import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import {
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  EmptyPanel,
  ErrorPanel,
  formatWhen,
  ListLoadingMore,
  MediaGallery,
  nextPage,
  useFamilyBranches,
} from '../components/community-ui';
import { PersonAvatar } from '../components/genealogy-ui';
import { Button, Card, Loading, Notice } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import {
  communityApi,
  scopeLabel,
  type Comment,
  type Media,
  type ReactionType,
} from '../services/community';
import { useSession } from '../session/SessionContext';
import { colors, radius, spacing, text } from '../theme';

const REACTIONS: { type: ReactionType; label: string }[] = [
  { type: 'LIKE', label: '👍 Thích' },
  { type: 'LOVE', label: '❤️ Yêu thích' },
  { type: 'HAHA', label: '😄 Haha' },
  { type: 'SAD', label: '😢 Buồn' },
];

export function PostDetailScreen() {
  const { postId } = useRoute<RouteProp<RootStackParamList, 'PostDetail'>>().params;
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const openPerson = (personId: string) => navigation.push('PersonDetail', { personId });
  const qc = useQueryClient();
  const { session } = useSession();
  const myId = session.status === 'signedIn' ? session.user.id : undefined;
  // Quản trị hệ thống xử lý báo cáo nên không tự báo cáo (như web).
  const canReport = session.status === 'signedIn' && !session.user.roles.includes('SYSTEM_ADMIN');
  const [editingPost, setEditingPost] = useState<string | null>(null);
  const { names: branchNames } = useFamilyBranches(); // nhãn phạm vi bài (SCRUM-101)
  const [content, setContent] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const [replyTo, setReplyTo] = useState<Comment | null>(null);
  const list = useRef<FlatList<Comment>>(null);
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
    mutationFn: (value: string) => communityApi.comment(postId, value, replyTo?.id),
    onSuccess: () => {
      setContent('');
      setReplyTo(null);
      setNotice(null);
      qc.invalidateQueries({ queryKey: ['comments', postId] });
      qc.invalidateQueries({ queryKey: ['post', postId] });
      qc.invalidateQueries({ queryKey: ['posts'] });
    },
    onError: (error) => setNotice(error.message),
  });
  const refreshAll = () => {
    qc.invalidateQueries({ queryKey: ['comments', postId] });
    qc.invalidateQueries({ queryKey: ['post', postId] });
    qc.invalidateQueries({ queryKey: ['posts'] });
  };
  const updatePost = useMutation({
    mutationFn: (value: string) => communityApi.updatePost(post.data!, value),
    onSuccess: () => {
      setEditingPost(null);
      refreshAll();
    },
    onError: (error) => setNotice(error.message),
  });
  const deletePost = useMutation({
    mutationFn: () => communityApi.deletePost(postId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['posts'] });
      navigation.goBack();
    },
    onError: (error) => setNotice(error.message),
  });
  const confirmDeletePost = () =>
    Alert.alert('Xóa bài đăng?', 'Bài và bình luận sẽ bị ẩn.', [
      { text: 'Hủy', style: 'cancel' },
      { text: 'Xóa', style: 'destructive', onPress: () => deletePost.mutate() },
    ]);
  // Trả lời 1 cấp (BR-COM-14): câu trả lời hiện dưới bình luận gốc; bấm "Trả lời" thì cuộn lên ô viết.
  const ids = new Set(allComments.map((c) => c.id));
  const roots = allComments.filter((c) => !c.parentId || !ids.has(c.parentId));
  const repliesOf = new Map<string, Comment[]>();
  for (const c of allComments) {
    if (c.parentId && ids.has(c.parentId))
      repliesOf.set(c.parentId, [c, ...(repliesOf.get(c.parentId) ?? [])]);
  }
  const reply = (target: Comment) => {
    setReplyTo(target);
    list.current?.scrollToOffset({ offset: 0, animated: true });
  };
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
        ref={list}
        data={roots}
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
                <AuthorLink
                  name={post.data.authorName}
                  personId={post.data.authorPersonId}
                  mediaId={post.data.authorAvatarMediaId}
                  onOpen={openPerson}
                  size={44}
                />
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  {formatWhen(post.data.createdAt)} ·{' '}
                  {scopeLabel(post.data.branchId, branchNames, post.data.audience)}
                </Text>
                {editingPost !== null ? (
                  <>
                    <TextInput
                      accessibilityLabel="Sửa nội dung bài"
                      value={editingPost}
                      onChangeText={setEditingPost}
                      multiline
                      maxLength={5000}
                      style={inputStyle}
                    />
                    <Button
                      title="Lưu bài"
                      loading={updatePost.isPending}
                      disabled={!editingPost.trim()}
                      onPress={() => updatePost.mutate(editingPost.trim())}
                    />
                    <Button title="Hủy" variant="secondary" onPress={() => setEditingPost(null)} />
                  </>
                ) : (
                  <Text style={[text.body, { color: colors.textPrimary }]}>
                    {post.data.content}
                  </Text>
                )}
                <ItemActions
                  mine={post.data.createdBy === myId}
                  canDelete={post.data.canDelete}
                  canReport={canReport}
                  onEdit={() => setEditingPost(post.data.content)}
                  onDelete={confirmDeletePost}
                  target={{ type: 'POST', id: postId }}
                />
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
            <Card title={replyTo ? `Trả lời ${replyTo.authorName}` : 'Viết bình luận'}>
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
              <Button
                title={replyTo ? 'Gửi trả lời' : 'Gửi bình luận'}
                loading={comment.isPending}
                onPress={send}
              />
              {replyTo ? (
                <Button title="Hủy trả lời" variant="secondary" onPress={() => setReplyTo(null)} />
              ) : null}
              {notice ? (
                <Notice variant="error" title="Không thực hiện được" message={notice} />
              ) : null}
            </Card>
            <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>Bình luận</Text>
          </View>
        }
        renderItem={({ item }) => (
          <Card>
            <CommentBody
              item={item}
              onReply={reply}
              onOpenPerson={openPerson}
              myId={myId}
              canReport={canReport}
              onChanged={refreshAll}
            />
            {(repliesOf.get(item.id) ?? []).map((r) => (
              <View
                key={r.id}
                style={{
                  marginLeft: 16,
                  paddingLeft: 12,
                  borderLeftWidth: 2,
                  borderLeftColor: colors.border,
                }}
              >
                <CommentBody
                  item={r}
                  onReply={reply}
                  onOpenPerson={openPerson}
                  myId={myId}
                  canReport={canReport}
                  onChanged={refreshAll}
                />
              </View>
            ))}
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

const inputStyle = {
  minHeight: 90,
  borderColor: colors.border,
  borderWidth: 1,
  borderRadius: radius.control,
  backgroundColor: colors.surface,
  padding: 12,
  textAlignVertical: 'top' as const,
  color: colors.textPrimary,
};

/** Sửa, xóa của chủ nội dung; xóa của người quản lý (canDelete); báo cáo vi phạm của người khác (UC-ADM-02). */
function ItemActions({
  mine,
  canDelete,
  canReport,
  onEdit,
  onDelete,
  target,
}: {
  mine: boolean;
  canDelete?: boolean;
  canReport: boolean;
  onEdit: () => void;
  onDelete: () => void;
  target: { type: 'POST' | 'COMMENT'; id: string };
}) {
  const [reason, setReason] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const report = useMutation({
    mutationFn: () => communityApi.report(target.type, target.id, (reason ?? '').trim()),
    onSuccess: () => {
      setReason(null);
      setDone('Đã gửi báo cáo. Quản trị sẽ xem xét.');
    },
    onError: (error) => setDone(error.message),
  });
  const link = (label: string, onPress: () => void, color: string = colors.info) => (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={{ minHeight: 44, justifyContent: 'center' }}
    >
      <Text style={[text.label, { color }]}>{label}</Text>
    </Pressable>
  );
  return (
    <View style={{ gap: 4 }}>
      <View style={{ flexDirection: 'row', gap: 16 }}>
        {mine ? link('Sửa', onEdit) : null}
        {mine || canDelete ? link('Xóa', onDelete, colors.error) : null}
        {!mine && canReport && reason === null ? link('Báo cáo', () => setReason('')) : null}
      </View>
      {reason !== null ? (
        <>
          <TextInput
            accessibilityLabel="Lý do báo cáo"
            placeholder="Lý do báo cáo"
            placeholderTextColor={colors.textSecondary}
            value={reason}
            onChangeText={setReason}
            multiline
            maxLength={255}
            style={inputStyle}
          />
          <Button
            title="Gửi báo cáo"
            loading={report.isPending}
            disabled={!reason.trim()}
            onPress={() => report.mutate()}
          />
          <Button title="Hủy" variant="secondary" onPress={() => setReason(null)} />
        </>
      ) : null}
      {done ? <Text style={[text.caption, { color: colors.textSecondary }]}>{done}</Text> : null}
    </View>
  );
}

/** Ảnh và tên người viết; bấm mở trang cá nhân (SCRUM-130). Chưa gắn hồ sơ gia phả thì chỉ hiện, không bấm được. */
function AuthorLink({
  name,
  personId,
  mediaId,
  onOpen,
  size = 32,
}: {
  name?: string | null;
  personId?: string | null;
  mediaId?: string | null;
  onOpen: (personId: string) => void;
  size?: number;
}) {
  return (
    <Pressable
      accessibilityRole={personId ? 'link' : undefined}
      accessibilityLabel={personId ? `Trang cá nhân ${name ?? ''}` : undefined}
      disabled={!personId}
      onPress={() => personId && onOpen(personId)}
      style={{ flexDirection: 'row', alignItems: 'center', gap: 8, minHeight: 44 }}
    >
      <PersonAvatar name={name} mediaId={mediaId} size={size} />
      <Text
        style={[
          size > 40 ? text.sectionTitle : text.label,
          { color: personId ? colors.info : colors.textPrimary, flexShrink: 1 },
        ]}
      >
        {name}
      </Text>
    </Pressable>
  );
}

function CommentBody({
  item,
  onReply,
  onOpenPerson,
  myId,
  canReport,
  onChanged,
}: {
  item: Comment;
  onReply: (c: Comment) => void;
  onOpenPerson: (personId: string) => void;
  myId?: string;
  canReport: boolean;
  onChanged: () => void;
}) {
  const [editing, setEditing] = useState<string | null>(null);
  const update = useMutation({
    mutationFn: (value: string) => communityApi.updateComment(item.id, value),
    onSuccess: () => {
      setEditing(null);
      onChanged();
    },
  });
  const remove = useMutation({
    mutationFn: () => communityApi.deleteComment(item.id),
    onSuccess: onChanged,
  });
  const confirmDelete = () =>
    Alert.alert('Xóa bình luận?', undefined, [
      { text: 'Hủy', style: 'cancel' },
      { text: 'Xóa', style: 'destructive', onPress: () => remove.mutate() },
    ]);
  const failed = update.error ?? remove.error;
  return (
    <View style={{ gap: 2 }}>
      <AuthorLink
        name={item.authorName}
        personId={item.authorPersonId}
        mediaId={item.authorAvatarMediaId}
        onOpen={onOpenPerson}
      />
      <Text style={[text.caption, { color: colors.textSecondary }]}>
        {formatWhen(item.createdAt)}
      </Text>
      {editing !== null ? (
        <>
          <TextInput
            accessibilityLabel="Sửa bình luận"
            value={editing}
            onChangeText={setEditing}
            multiline
            maxLength={1000}
            style={inputStyle}
          />
          <Button
            title="Lưu"
            loading={update.isPending}
            disabled={!editing.trim()}
            onPress={() => update.mutate(editing.trim())}
          />
          <Button title="Hủy" variant="secondary" onPress={() => setEditing(null)} />
        </>
      ) : (
        <Text style={[text.body, { color: colors.textPrimary }]}>{item.content}</Text>
      )}
      {failed ? (
        <Text style={[text.caption, { color: colors.error }]}>{failed.message}</Text>
      ) : null}
      <ItemActions
        mine={item.createdBy === myId}
        canDelete={item.canDelete}
        canReport={canReport}
        onEdit={() => setEditing(item.content)}
        onDelete={confirmDelete}
        target={{ type: 'COMMENT', id: item.id }}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Trả lời ${item.authorName ?? ''}`}
        onPress={() => onReply(item)}
        style={{ minHeight: 44, justifyContent: 'center' }}
      >
        <Text style={[text.label, { color: colors.info }]}>Trả lời</Text>
      </Pressable>
    </View>
  );
}
