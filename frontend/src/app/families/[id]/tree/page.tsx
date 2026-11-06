'use client';

import '@xyflow/react/dist/style.css';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Background, Controls, MiniMap, ReactFlow, type Edge } from '@xyflow/react';
import { AccessState } from '@/components/heritage/HeritageAccess';
import PersonNode, {
  PERSON_NODE_HEIGHT, PERSON_NODE_WIDTH, type PersonFlowNode,
} from '@/components/genealogy/PersonNode';
import { hiddenByCollapse, layoutTree, spouseAnchors } from '@/components/genealogy/tree-layout';
import { genealogyApi, type TreeData, type TreeEdge, type TreeNode } from '@/services/genealogy';

const nodeTypes = { person: PersonNode };
const KIND_LABEL: Record<string, string> = { ADOPTED: 'nuôi', STEP: 'kế' };

/** Gộp cây đầu và các nhánh tải thêm; bản tải sau ghi đè (node gốc nhánh hết hasMoreChildren). */
function merge(parts: TreeData[]) {
  const nodes = new Map<string, TreeNode>();
  const edges = new Map<string, TreeEdge>();
  for (const part of parts) {
    part.nodes?.forEach((n) => nodes.set(n.id ?? '', n));
    part.edges?.forEach((e) => edges.set(e.id ?? '', e));
  }
  return { nodes: [...nodes.values()], edges: [...edges.values()] };
}

/** UC-GEN-06, SCRUM-52: cây gia phả tương tác, mở nhánh tải thêm qua GET /families/{id}/tree?rootId=. */
export default function FamilyTreePage() {
  const { id: familyId } = useParams<{ id: string }>();
  const first = useQuery({ queryKey: ['tree', familyId], queryFn: () => genealogyApi.tree(familyId) });
  const [branches, setBranches] = useState<TreeData[]>([]);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const loadBranch = useMutation({
    mutationFn: (rootId: string) => genealogyApi.tree(familyId, { rootId }),
    onSuccess: (data) => setBranches((all) => [...all, data]),
  });

  const graph = useMemo(() => merge(first.data ? [first.data, ...branches] : []), [first.data, branches]);

  const onToggle = useCallback((id: string) => {
    if (graph.nodes.find((n) => n.id === id)?.hasMoreChildren) {
      loadBranch.mutate(id);
      return;
    }
    setCollapsed((old) => {
      const next = new Set(old);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }, [graph.nodes, loadBranch]);

  const flow = useMemo(() => {
    const hidden = hiddenByCollapse(graph.edges, collapsed);
    const nodes = graph.nodes.filter((n) => !hidden.has(n.id ?? ''));
    const edges = graph.edges.filter((e) => !hidden.has(e.source ?? '') && !hidden.has(e.target ?? ''));
    const anchors = spouseAnchors(nodes, edges, first.data?.rootId);
    const positions = layoutTree(nodes, edges, anchors, PERSON_NODE_WIDTH, PERSON_NODE_HEIGHT);
    const parents = new Set(graph.edges.filter((e) => e.type === 'PARENT_CHILD').map((e) => e.source));
    const pending = loadBranch.isPending ? loadBranch.variables : undefined;

    const flowNodes: PersonFlowNode[] = nodes.map((person) => ({
      id: person.id ?? '',
      type: 'person',
      width: PERSON_NODE_WIDTH, // kích thước cố định để MiniMap vẽ được node mà không cần đo
      height: PERSON_NODE_HEIGHT,
      position: positions.get(person.id ?? '') ?? { x: 0, y: 0 },
      data: {
        person,
        hasLoadedChildren: parents.has(person.id),
        collapsed: collapsed.has(person.id ?? ''),
        loading: pending === person.id,
        onToggle,
      },
    }));
    const flowEdges: Edge[] = edges.map((e) => {
      if (e.type !== 'MARRIAGE') {
        return { id: e.id ?? '', source: e.source ?? '', target: e.target ?? '', type: 'smoothstep', label: KIND_LABEL[e.kind ?? ''] };
      }
      // Vợ/chồng đứng cạnh nhau: nối ngang từ người bên trái (chủ) sang người bên phải.
      const leftFirst = anchors.get(e.target ?? '') === e.source;
      const [left, right] = leftFirst || !anchors.has(e.source ?? '') ? [e.source, e.target] : [e.target, e.source];
      return {
        id: e.id ?? '', source: left ?? '', target: right ?? '', sourceHandle: 'spouse-out', targetHandle: 'spouse-in',
        style: { strokeDasharray: '6 4' }, label: e.status === 'DIVORCED' ? 'đã ly hôn' : undefined,
      };
    });
    return { nodes: flowNodes, edges: flowEdges };
  }, [graph, collapsed, first.data?.rootId, loadBranch.isPending, loadBranch.variables, onToggle]);

  const error = (first.error as Error | null)?.message ?? (loadBranch.error as Error | null)?.message;

  return (
    <main className="space-y-4 px-4 py-6 sm:px-6">
      <header>
        <h1 className="text-page-title">Cây gia phả</h1>
        <p className="mt-1 text-body text-text-secondary">
          Kéo để di chuyển, cuộn hoặc dùng nút +/- để phóng to. Bấm tên để xem hồ sơ, bấm “Mở nhánh” để tải thêm đời sau.
        </p>
      </header>
      <AccessState loading={first.isLoading} error={error} />
      {first.isSuccess && flow.nodes.length === 0 && (
        <p className="rounded-card border border-border bg-surface p-4 text-body text-text-secondary">
          Gia đình chưa có thành viên. <Link href="/members" className="text-info underline">Thêm thành viên</Link>
        </p>
      )}
      {flow.nodes.length > 0 && (
        <div className="h-[70vh] min-h-[480px] overflow-hidden rounded-card border border-border bg-surface">
          <ReactFlow
            nodes={flow.nodes}
            edges={flow.edges}
            nodeTypes={nodeTypes}
            nodesDraggable={false}
            nodesConnectable={false}
            fitView
            minZoom={0.1}
            maxZoom={1.5}
            proOptions={{ hideAttribution: true }}
          >
            <Controls showInteractive={false} />
            <MiniMap pannable zoomable nodeClassName="fill-border-strong" ariaLabel="Bản đồ thu nhỏ của cây" />
            <Background gap={16} />
          </ReactFlow>
        </div>
      )}
    </main>
  );
}
