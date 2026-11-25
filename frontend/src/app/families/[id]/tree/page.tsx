'use client';

import '@xyflow/react/dist/style.css';
import Link from 'next/link';
import { useParams, useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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

/** Mở cây thì đưa ô của mình (hoặc người mở từ trang cá nhân) vào giữa, một lần, khi ô đó đã hiện (SCRUM-120). */
function FocusOnce({ id }: { id?: string }) {
  const { fitView } = useReactFlow();
  const done = useRef(false);
  useEffect(() => {
    if (!id || done.current) return;
    done.current = true;
    void fitView({ nodes: [{ id }], maxZoom: 1, duration: 400 });
  }, [id, fitView]);
  return null;
}

/** SCRUM-143: đưa người vừa tìm vào giữa; n đổi mỗi lần chọn để tìm lại cùng người vẫn chạy. */
function FocusOn({ target }: { target: { id: string; n: number } | null }) {
  const { fitView } = useReactFlow();
  useEffect(() => {
    if (target) void fitView({ nodes: [{ id: target.id }], maxZoom: 1, duration: 400 });
  }, [target, fitView]);
  return null;
}

/** SCRUM-143: tìm người theo tên trong dòng họ (GET /families/{id}/persons?q=), chọn thì cây tự tìm tới. */
function TreeSearch({ familyId, onPick }: { familyId: string; onPick: (id: string) => void }) {
  const [q, setQ] = useState('');
  const term = q.trim();
  const found = useQuery({
    queryKey: ['tree-search', familyId, term],
    queryFn: () => genealogyApi.persons(familyId, { q: term, page: 0, size: 8, sort: 'fullName,asc' }),
    enabled: term.length >= 2,
  });
  return (
    <div className="relative max-w-md">
      <label className="grid gap-1 text-label">Tìm người trên cây
        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Gõ ít nhất 2 chữ của họ tên"
          className="h-[42px] w-full rounded-control border border-border-strong bg-surface px-3 text-body text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info" />
      </label>
      {term.length >= 2 && (
        <ul aria-label="Kết quả tìm" className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-card border border-border bg-surface shadow-overlay">
          {found.isPending && <li className="p-3 text-body text-text-secondary">Đang tìm...</li>}
          {found.isError && <li role="alert" className="p-3 text-body text-error">Không tìm được, vui lòng thử lại.</li>}
          {found.data?.items.length === 0 && <li className="p-3 text-body text-text-secondary">Không thấy ai có tên như vậy.</li>}
          {found.data?.items.map((p) => (
            <li key={p.id}>
              <button type="button" className="flex min-h-11 w-full items-center px-3 text-left text-body hover:bg-surface-muted"
                onClick={() => { onPick(p.id ?? ''); setQ(''); }}>
                {p.fullName}{p.birthYear ? ` (${p.birthYear})` : ''}{p.isDeceased ? ' · đã mất' : ''}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
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
  // Mở từ người tổ, sẵn 10 đời để thấy cả dòng họ (góp ý 16/11/2026).
  const first = useQuery({ queryKey: ['tree', familyId], queryFn: () => genealogyApi.tree(familyId, { depth: 10 }) });
  // Hồ sơ của chính mình để tô nổi trên cây; admin hoặc tài khoản chưa gắn hồ sơ thì 404, bỏ qua.
  const myPersonId = useQuery({ queryKey: ['person', 'me'], queryFn: genealogyApi.myPerson, retry: false }).data?.id;
  // Nhánh đã mở giữ theo id gốc, để khi thêm quan hệ (invalidate ['tree']) thì mọi nhánh tải lại.
  const [branchRoots, setBranchRoots] = useState<string[]>([]);
  const branches = useQueries({
    queries: branchRoots.map((rootId) => ({
      queryKey: ['tree', familyId, rootId],
      queryFn: () => genealogyApi.tree(familyId, { rootId }),
    })),
  });
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  // Từ trang cá nhân "Xem trên gia phả" (?focus=): mở sẵn panel của người đó.
  const focusParam = useSearchParams().get('focus');
  const [selected, setSelected] = useState<string | null>(focusParam);
  const [relation, setRelation] = useState<Relationship | null>(null);
  // SCRUM-56: tra quan hệ với người chưa hiện trên cây thì tải thêm cây từ tổ tiên chung, đủ sâu tới cả hai người.
  const [focusRoots, setFocusRoots] = useState<{ rootId: string; depth: number }[]>([]);
  const focus = useQueries({
    queries: focusRoots.map(({ rootId, depth }) => ({
      queryKey: ['tree', familyId, rootId, depth],
      queryFn: () => genealogyApi.tree(familyId, { rootId, depth }),
    })),
  });

  // useQueries trả mảng mới mỗi lần render; chỉ gộp lại khi có nhánh tải xong.
  const branchVersion = [...branches, ...focus].map((b) => b.dataUpdatedAt).join(',');
  const graph = useMemo(
    () => merge(first.data
      ? [first.data, ...[...branches, ...focus].flatMap((b) => (b.data ? [b.data] : []))] : []),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- branchVersion đại diện cho branches và focus
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

  const onRelation = useCallback((found: Relationship | null) => {
    setRelation(found);
    const shown = new Set(graph.nodes.map((n) => n.id));
    const rootId = found?.commonAncestorId;
    if (!rootId || found.path?.every((step) => shown.has(step.id))) return;
    const depth = Math.min(10, Math.max(found.stepsFromA ?? 0, found.stepsFromB ?? 0) + 1);
    setFocusRoots((roots) => (roots.some((r) => r.rootId === rootId && r.depth >= depth) ? roots : [...roots, { rootId, depth }]));
  }, [graph.nodes]);

  // Mở từ trang Tra cứu xưng hô (?a=&b=): tra cặp đó một lần khi cây đầu đã tải, rồi tô sáng như tra trong panel.
  const search = useSearchParams();
  const linkA = search.get('a');
  const linkB = search.get('b');
  const linked = useQuery({
    queryKey: ['relationship', linkA, linkB],
    queryFn: () => genealogyApi.relationship(linkA ?? '', linkB ?? ''),
    enabled: Boolean(linkA && linkB && first.data),
  });
  const linkApplied = useRef(false);
  useEffect(() => {
    if (!linked.data || linkApplied.current) return;
    linkApplied.current = true;
    onRelation(linked.data);
  }, [linked.data, onRelation]);

  const onSelect = useCallback((id: string) => {
    setSelected(id);
    setRelation(null);
  }, []);

  // SCRUM-143: chọn người từ ô tìm kiếm; chưa hiện trên cây thì tải cây từ tổ tiên chung với người tổ, đủ sâu tới người đó.
  const [searchFocus, setSearchFocus] = useState<{ id: string; n: number } | null>(null);
  const [pendingFocus, setPendingFocus] = useState<string | null>(null);
  const onSearchPick = useCallback(async (id: string) => {
    setSelected(id);
    setRelation(null);
    setCollapsed(new Set());
    if (graph.nodes.some((n) => n.id === id)) {
      setSearchFocus({ id, n: Date.now() });
      return;
    }
    setPendingFocus(id);
    const rootId = first.data?.rootId;
    const found = rootId ? await genealogyApi.relationship(rootId, id).catch(() => null) : null;
    const ancestor = found?.commonAncestorId ?? id;
    const depth = Math.min(10, (found?.stepsFromB ?? 0) + 1);
    setFocusRoots((roots) => (roots.some((r) => r.rootId === ancestor && r.depth >= depth) ? roots : [...roots, { rootId: ancestor, depth }]));
  }, [graph.nodes, first.data?.rootId]);
  useEffect(() => {
    if (pendingFocus && graph.nodes.some((n) => n.id === pendingFocus)) {
      setSearchFocus({ id: pendingFocus, n: Date.now() });
      setPendingFocus(null);
    }
  }, [pendingFocus, graph.nodes]);

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
        isMe: myPersonId === person.id,
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
  }, [graph, collapsed, first.data?.rootId, loadingKey, relation, selected, myPersonId, onToggle, onSelect]);

  const error = (first.error as Error | null)?.message
    ?? ([...branches, ...focus].find((b) => b.error)?.error as Error | undefined)?.message;

  return (
    <main className="space-y-4 px-4 py-6 md:px-6">
      <header>
        <h1 className="text-page-title">Cây gia phả</h1>
        <p className="mt-1 text-body text-text-secondary">
          Kéo để di chuyển, cuộn hoặc dùng nút +/- để phóng to. Bấm tên để xem chi tiết và tra quan hệ, bấm “Mở nhánh” để
          tải thêm đời sau.
        </p>
        {/* SCRUM-136: chú giải màu và huy hiệu trên cây */}
        <ul aria-label="Chú giải" className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-2 text-caption text-text-secondary">
          <li className="flex items-center gap-1.5"><span aria-hidden="true" className="h-4 w-6 rounded-sm border-4 border-tree-male" />Nam</li>
          <li className="flex items-center gap-1.5"><span aria-hidden="true" className="h-4 w-6 rounded-sm border-4 border-tree-female" />Nữ</li>
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="h-4 w-6 rounded-sm border-4 border-dashed border-border-strong bg-surface-muted" />Đã mất (viền nét đứt)
          </li>
          <li className="flex items-center gap-1.5">
            <span className="rounded-full bg-border-strong px-2 font-semibold text-surface">Đã ly hôn</span>khung mờ
          </li>
          <li><span className="rounded-full bg-secondary px-2 font-semibold text-primary">Trưởng họ</span></li>
          <li><span className="rounded-full bg-primary px-2 font-semibold text-cream-on-dark">Trưởng chi</span></li>
          <li className="flex items-center gap-1.5"><span className="rounded-full bg-primary px-2 font-semibold text-cream-on-dark">Bạn</span>hồ sơ của bạn</li>
        </ul>
      </header>
      <AccessState loading={first.isLoading} error={error} />
      {first.isSuccess && <TreeSearch familyId={familyId} onPick={(id) => void onSearchPick(id)} />}
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
              // Không thu nhỏ quá 0,4 khi mở: cây lớn trên điện thoại vẫn đọc được tên, kéo để xem phần còn lại.
              fitViewOptions={{ minZoom: 0.4 }}
              minZoom={0.1}
              maxZoom={1.5}
              proOptions={{ hideAttribution: true }}
            >
              <Controls showInteractive={false} className="[&_button]:!h-11 [&_button]:!w-11" />
              <MiniMap pannable zoomable ariaLabel="Bản đồ thu nhỏ của cây"
                nodeClassName={(n) => {
                  const g = (n.data as PersonFlowNode['data'] | undefined)?.person.gender;
                  return g === 'MALE' ? 'fill-tree-male' : g === 'FEMALE' ? 'fill-tree-female' : 'fill-border-strong';
                }} />
              <Background gap={16} />
              <FocusOnce id={[focusParam, myPersonId].find((id) => id && flow.nodes.some((n) => n.id === id)) ?? undefined} />
              <FocusOn target={searchFocus} />
              <FitToPath ids={relation?.path?.map((step) => step.id).filter((id) => flow.nodes.some((n) => n.id === id)).join(',') ?? ''} />
            </ReactFlow>
          </div>
          {selected && (
            <PersonPanel
              key={selected}
              familyId={familyId}
              personId={selected}
              people={graph.nodes}
              edges={graph.edges}
              myPersonId={myPersonId}
              canManage={access.isAdmin}
              onRelation={onRelation}
              onClose={() => { setSelected(null); setRelation(null); }}
            />
          )}
        </div>
      )}
    </main>
  );
}
