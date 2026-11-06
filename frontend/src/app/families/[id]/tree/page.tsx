'use client';

import '@xyflow/react/dist/style.css';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { Background, Controls, MiniMap, ReactFlow, useReactFlow, type Edge } from '@xyflow/react';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import PersonNode, {
  PERSON_NODE_HEIGHT, PERSON_NODE_WIDTH, type PersonFlowNode,
} from '@/components/genealogy/PersonNode';
import { PersonPanel } from '@/components/genealogy/PersonPanel';
import { hiddenByCollapse, layoutTree, spouseAnchors } from '@/components/genealogy/tree-layout';
import { genealogyApi, type Relationship, type TreeData, type TreeEdge, type TreeNode } from '@/services/genealogy';

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

/** Các cặp liền nhau trên đường quan hệ, không phân biệt chiều, để tô sáng cạnh. */
function pathPairs(relation: Relationship | null) {
  const ids = relation?.path?.map((step) => step.id ?? '') ?? [];
  const pairs = new Set<string>();
  for (let i = 1; i < ids.length; i++) {
    pairs.add(`${ids[i - 1]}|${ids[i]}`);
    pairs.add(`${ids[i]}|${ids[i - 1]}`);
  }
  return { ids: new Set(ids), pairs };
}

/** Đưa đường quan hệ vừa tra vào giữa khung nhìn (chỉ những người đang hiện trên cây). */
function FitToPath({ ids }: { ids: string }) {
  const { fitView } = useReactFlow();
  useEffect(() => {
    if (ids) void fitView({ nodes: ids.split(',').map((id) => ({ id })), padding: 0.3, duration: 400 });
  }, [ids, fitView]);
  return null;
}

/** UC-GEN-06, 07; SCRUM-52: cây gia phả tương tác, mở nhánh qua GET /families/{id}/tree?rootId=, tô sáng đường quan hệ. */
export default function FamilyTreePage() {
  const { id: familyId } = useParams<{ id: string }>();
  const access = useHeritageAccess();
  const first = useQuery({ queryKey: ['tree', familyId], queryFn: () => genealogyApi.tree(familyId) });
  // Nhánh đã mở giữ theo id gốc, để khi thêm quan hệ (invalidate ['tree']) thì mọi nhánh tải lại.
  const [branchRoots, setBranchRoots] = useState<string[]>([]);
  const branches = useQueries({
    queries: branchRoots.map((rootId) => ({
      queryKey: ['tree', familyId, rootId],
      queryFn: () => genealogyApi.tree(familyId, { rootId }),
    })),
  });
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const [selected, setSelected] = useState<string | null>(null);
  const [relation, setRelation] = useState<Relationship | null>(null);

  // useQueries trả mảng mới mỗi lần render; chỉ gộp lại khi có nhánh tải xong.
  const branchVersion = branches.map((b) => b.dataUpdatedAt).join(',');
  const graph = useMemo(
    () => merge(first.data ? [first.data, ...branches.flatMap((b) => (b.data ? [b.data] : []))] : []),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- branchVersion đại diện cho branches
    [first.data, branchVersion],
  );
  const loadingRoots = new Set(branchRoots.filter((_, i) => branches[i]?.isPending));

  const onToggle = useCallback((id: string) => {
    if (graph.nodes.find((n) => n.id === id)?.hasMoreChildren) {
      setBranchRoots((roots) => (roots.includes(id) ? roots : [...roots, id]));
      return;
    }
    setCollapsed((old) => {
      const next = new Set(old);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }, [graph.nodes]);

  const onSelect = useCallback((id: string) => {
    setSelected(id);
    setRelation(null);
  }, []);

  const loadingKey = [...loadingRoots].join(',');
  const flow = useMemo(() => {
    const hidden = hiddenByCollapse(graph.edges, collapsed);
    const nodes = graph.nodes.filter((n) => !hidden.has(n.id ?? ''));
    const edges = graph.edges.filter((e) => !hidden.has(e.source ?? '') && !hidden.has(e.target ?? ''));
    const anchors = spouseAnchors(nodes, edges, first.data?.rootId);
    const positions = layoutTree(nodes, edges, anchors, PERSON_NODE_WIDTH, PERSON_NODE_HEIGHT);
    const parents = new Set(graph.edges.filter((e) => e.type === 'PARENT_CHILD').map((e) => e.source));
    const loading = new Set(loadingKey.split(','));
    const path = pathPairs(relation);

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
        loading: loading.has(person.id ?? ''),
        selected: selected === person.id,
        onPath: relation ? path.ids.has(person.id ?? '') : undefined,
        onToggle,
        onSelect,
      },
    }));
    const flowEdges: Edge[] = edges.map((e) => {
      const lit = path.pairs.has(`${e.source}|${e.target}`);
      const style = relation ? (lit ? { strokeWidth: 4 } : { opacity: 0.3 }) : {};
      if (e.type !== 'MARRIAGE') {
        return {
          id: e.id ?? '', source: e.source ?? '', target: e.target ?? '', type: 'smoothstep',
          label: KIND_LABEL[e.kind ?? ''], animated: lit, style,
        };
      }
      // Vợ/chồng đứng cạnh nhau: nối ngang từ người bên trái (chủ) sang người bên phải.
      const leftFirst = anchors.get(e.target ?? '') === e.source;
      const [left, right] = leftFirst || !anchors.has(e.source ?? '') ? [e.source, e.target] : [e.target, e.source];
      return {
        id: e.id ?? '', source: left ?? '', target: right ?? '', sourceHandle: 'spouse-out', targetHandle: 'spouse-in',
        style: { strokeDasharray: '6 4', ...style }, animated: lit, label: e.status === 'DIVORCED' ? 'đã ly hôn' : undefined,
      };
    });
    return { nodes: flowNodes, edges: flowEdges };
  }, [graph, collapsed, first.data?.rootId, loadingKey, relation, selected, onToggle, onSelect]);

  const error = (first.error as Error | null)?.message
    ?? (branches.find((b) => b.error)?.error as Error | undefined)?.message;

  return (
    <main className="space-y-4 px-4 py-6 sm:px-6">
      <header>
        <h1 className="text-page-title">Cây gia phả</h1>
        <p className="mt-1 text-body text-text-secondary">
          Kéo để di chuyển, cuộn hoặc dùng nút +/- để phóng to. Bấm tên để xem chi tiết và tra quan hệ, bấm “Mở nhánh” để
          tải thêm đời sau.
        </p>
      </header>
      <AccessState loading={first.isLoading} error={error} />
      {first.isSuccess && flow.nodes.length === 0 && (
        <p className="rounded-card border border-border bg-surface p-4 text-body text-text-secondary">
          Gia đình chưa có thành viên. <Link href="/members" className="text-info underline">Thêm thành viên</Link>
        </p>
      )}
      {flow.nodes.length > 0 && (
        <div className={`grid gap-4 ${selected ? 'lg:grid-cols-[minmax(0,1fr)_320px]' : ''}`}>
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
              <FitToPath ids={relation?.path?.map((step) => step.id).filter((id) => flow.nodes.some((n) => n.id === id)).join(',') ?? ''} />
            </ReactFlow>
          </div>
          {selected && (
            <PersonPanel
              key={selected}
              familyId={familyId}
              personId={selected}
              people={graph.nodes}
              canManage={access.isAdmin}
              onRelation={setRelation}
              onClose={() => { setSelected(null); setRelation(null); }}
            />
          )}
        </div>
      )}
    </main>
  );
}
