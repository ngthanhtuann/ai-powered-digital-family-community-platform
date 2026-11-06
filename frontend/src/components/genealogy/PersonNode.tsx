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
  /** Đang tô sáng đường quan hệ: true nếu người này nằm trên đường, false nếu bị làm mờ, undefined nếu không tô. */
  onPath?: boolean;
  onToggle: (id: string) => void;
  onSelect: (id: string) => void;
};
export type PersonFlowNode = Node<PersonNodeData, 'person'>;

const GENDER: Record<string, { label: string; border: string }> = {
  MALE: { label: 'Nam', border: 'border-accent-green' },
  FEMALE: { label: 'Nữ', border: 'border-accent-pink' },
};

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
  const { person, hasLoadedChildren, collapsed, loading, selected, onPath, onToggle, onSelect } = data;
  const gender = GENDER[person.gender ?? ''] ?? { label: 'Khác', border: 'border-border-strong' };
  const canOpen = person.hasMoreChildren || (hasLoadedChildren && collapsed);
  const toggleLabel = loading ? 'Đang tải...' : canOpen ? 'Mở nhánh' : 'Thu nhánh';
  const name = person.fullName ?? '';
  const ring = selected || onPath ? 'ring-4 ring-secondary' : '';

  return (
    <div
      style={{ width: PERSON_NODE_WIDTH, height: PERSON_NODE_HEIGHT }}
      className={`flex flex-col items-center justify-center gap-1 rounded-card border-2 px-2 text-center shadow-card ${gender.border} ${ring} ${
        person.isDeceased ? 'bg-surface-muted' : 'bg-surface'
      } ${onPath === false ? 'opacity-40' : ''}`}
    >
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
        {gender.label}
        {person.generation ? ` · Đời ${person.generation}` : ''}
        {person.birthYear ? ` · ${person.birthYear}` : ''}
        {person.isDeceased ? ' · Đã mất' : ''}
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
