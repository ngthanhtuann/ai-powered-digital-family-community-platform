import { memo, useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { genealogyApi, type TreeNode } from '@/services/genealogy';

export const PERSON_NODE_WIDTH = 176;
export const PERSON_NODE_HEIGHT = 128;

export type PersonNodeData = {
  person: TreeNode;
  /** Có con đã tải trên cây: hiện nút thu/mở. */
  hasLoadedChildren: boolean;
  collapsed: boolean;
  loading: boolean;
  selected: boolean;
  /** Hồ sơ gắn với tài khoản đang xem: nền khác màu và nhãn "Bạn" để biết vị trí của mình (SCRUM-120). */
  isMe?: boolean;
  /** Đang tô sáng đường quan hệ: true nếu người này nằm trên đường, false nếu bị làm mờ, undefined nếu không tô. */
  onPath?: boolean;
  onToggle: (id: string) => void;
  onSelect: (id: string) => void;
};
export type PersonFlowNode = Node<PersonNodeData, 'person'>;

const GENDER: Record<string, { label: string; border: string; text: string }> = {
  MALE: { label: 'Nam', border: 'border-tree-male', text: 'text-tree-male' },
  FEMALE: { label: 'Nữ', border: 'border-tree-female', text: 'text-tree-female' },
};

/** SCRUM-136: huy hiệu trưởng họ/chi, tương phản theo design-system.md mục 1.3. */
const LEADER: Record<string, { label: string; className: string }> = {
  FAMILY_HEAD: { label: 'Trưởng họ', className: 'bg-secondary text-primary' },
  BRANCH_HEAD: { label: 'Trưởng chi', className: 'bg-primary text-cream-on-dark' },
};

/** Năm sinh – năm mất; còn sống thì chỉ năm sinh. */
export function lifeSpan(person: TreeNode): string {
  if (!person.isDeceased) return person.birthYear ? String(person.birthYear) : '';
  return `${person.birthYear ?? '?'} – ${person.deathYear ?? '?'}`;
}

/** Ảnh đại diện tải qua API có token; chưa có ảnh hoặc lỗi thì hiện chữ cái đầu. */
export function Avatar({ mediaId, name, size = 'h-10 w-10' }: { mediaId?: string | null; name: string; size?: string }) {
  const blob = useQuery({
    queryKey: ['media', mediaId],
    queryFn: () => genealogyApi.mediaContent(mediaId!),
    enabled: Boolean(mediaId),
    staleTime: Infinity,
  });
  const [url, setUrl] = useState<string>();
  useEffect(() => {
    if (!blob.data) return undefined;
    const next = URL.createObjectURL(blob.data);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [blob.data]);
  if (url) {
    // eslint-disable-next-line @next/next/no-img-element -- blob URL cục bộ, next/image không tối ưu được
    return <img src={url} alt="" className={`${size} rounded-full object-cover`} />;
  }
  return (
    <span aria-hidden className={`${size} flex shrink-0 items-center justify-center rounded-full bg-primary text-label text-cream-on-dark`}>
      {name.charAt(0)}
    </span>
  );
}

/** Node cây gia phả (SCRUM-52): màu viền theo giới tính luôn kèm nhãn chữ (design-system.md mục 1.2). */
function PersonNode({ data }: NodeProps<PersonFlowNode>) {
  const { person, hasLoadedChildren, collapsed, loading, selected, isMe, onPath, onToggle, onSelect } = data;
  const gender = GENDER[person.gender ?? ''] ?? { label: 'Khác', border: 'border-border-strong', text: 'text-text-secondary' };
  const leader = person.leaderRole ? LEADER[person.leaderRole] : undefined;
  const years = lifeSpan(person);
  const canOpen = person.hasMoreChildren || (hasLoadedChildren && collapsed);
  const toggleLabel = loading ? 'Đang tải...' : canOpen ? 'Mở nhánh' : 'Thu nhánh';
  const name = person.fullName ?? '';
  const ring = selected || onPath ? 'ring-4 ring-secondary' : '';

  return (
    <div
      style={{ width: PERSON_NODE_WIDTH, height: PERSON_NODE_HEIGHT }}
      className={`relative flex flex-col items-center justify-center gap-1 rounded-card border-4 px-2 text-center shadow-card ${gender.border} ${ring} ${
        person.isDeceased ? 'border-dashed' : ''
      } ${isMe ? 'bg-info-soft shadow-overlay' : person.isDeceased ? 'bg-surface-muted' : 'bg-surface'} ${onPath === false ? 'opacity-40' : ''}`}
    >
      {person.isDeceased && (
        <span className="absolute -top-3 left-2 rounded-full bg-text-secondary px-2 text-caption font-semibold text-surface">
          Đã mất
        </span>
      )}
      {leader && (
        <span className={`absolute -top-3 right-2 rounded-full px-2 text-caption font-semibold ${leader.className}`}>
          {leader.label}
        </span>
      )}
      {isMe && (
        <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-primary px-2 text-caption font-semibold text-cream-on-dark">
          Bạn
        </span>
      )}
      <Handle type="target" position={Position.Top} isConnectable={false} />
      <button
        type="button"
        onClick={() => onSelect(person.id ?? '')}
        aria-pressed={selected}
        className="nodrag flex max-w-full items-center gap-2 rounded-control text-left hover:bg-surface-muted"
      >
        <Avatar mediaId={person.avatarMediaId} name={name} />
        <span className="max-w-[112px] truncate text-label text-text-primary">{name}</span>
      </button>
      <p className="text-caption text-text-secondary">
        <span className={`font-semibold ${gender.text}`}>{gender.label}</span>
        {person.generation ? ` · Đời ${person.generation}` : ''}
        {years ? ` · ${years}` : ''}
      </p>
      {(person.hasMoreChildren || hasLoadedChildren) && (
        <button
          type="button"
          disabled={loading}
          aria-expanded={!canOpen}
          onClick={() => onToggle(person.id ?? '')}
          className="nodrag min-h-8 rounded-control border border-border-strong px-2 text-caption text-text-primary hover:bg-surface-muted disabled:opacity-60"
        >
          {toggleLabel}
        </button>
      )}
      <Handle type="source" position={Position.Bottom} isConnectable={false} />
      {/* Cạnh hôn nhân nối ngang giữa hai vợ chồng đứng cạnh nhau */}
      <Handle id="spouse-out" type="source" position={Position.Right} isConnectable={false} className="opacity-0" />
      <Handle id="spouse-in" type="target" position={Position.Left} isConnectable={false} className="opacity-0" />
    </div>
  );
}

export default memo(PersonNode);
