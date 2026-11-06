import Link from 'next/link';
import { memo } from 'react';
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import type { TreeNode } from '@/services/genealogy';

export const PERSON_NODE_WIDTH = 176;
export const PERSON_NODE_HEIGHT = 128;

export type PersonNodeData = {
  person: TreeNode;
  /** Có con đã tải trên cây: hiện nút thu/mở. */
  hasLoadedChildren: boolean;
  collapsed: boolean;
  loading: boolean;
  onToggle: (id: string) => void;
};
export type PersonFlowNode = Node<PersonNodeData, 'person'>;

const GENDER: Record<string, { label: string; border: string }> = {
  MALE: { label: 'Nam', border: 'border-accent-green' },
  FEMALE: { label: 'Nữ', border: 'border-accent-pink' },
};

/** Node cây gia phả (SCRUM-52): màu viền theo giới tính luôn kèm nhãn chữ (design-system.md mục 1.2). */
function PersonNode({ data }: NodeProps<PersonFlowNode>) {
  const { person, hasLoadedChildren, collapsed, loading, onToggle } = data;
  const gender = GENDER[person.gender ?? ''] ?? { label: 'Khác', border: 'border-border-strong' };
  const canOpen = person.hasMoreChildren || (hasLoadedChildren && collapsed);
  const toggleLabel = loading ? 'Đang tải...' : canOpen ? 'Mở nhánh' : 'Thu nhánh';
  const name = person.fullName ?? '';

  return (
    <div
      style={{ width: PERSON_NODE_WIDTH, height: PERSON_NODE_HEIGHT }}
      className={`flex flex-col items-center justify-center gap-1 rounded-card border-2 px-2 text-center shadow-card ${gender.border} ${
        person.isDeceased ? 'bg-surface-muted' : 'bg-surface'
      }`}
    >
      <Handle type="target" position={Position.Top} isConnectable={false} />
      <div className="flex items-center gap-2">
        <span aria-hidden className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-label text-cream-on-dark">
          {name.charAt(0)}
        </span>
        <Link href={`/persons/${person.id}`} className="nodrag max-w-[120px] truncate text-label text-text-primary underline-offset-2 hover:underline">
          {name}
        </Link>
      </div>
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
