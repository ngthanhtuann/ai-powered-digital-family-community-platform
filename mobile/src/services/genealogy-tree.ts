// Biến nodes/edges của GET /families/{id}/tree thành danh sách phân cấp theo đời có mở/thu nhánh (SCRUM-90).
// Mobile chọn dạng danh sách thay cho sơ đồ kéo/zoom vì nhẹ hơn trên màn hình nhỏ (Jira SCRUM-90 bước 2).
import type { TreeData, TreeEdge, TreeNode } from './genealogy';

export interface Graph {
  nodes: Map<string, TreeNode>;
  /** Con theo cha/mẹ (cạnh PARENT_CHILD: source là cha/mẹ). */
  children: Map<string, TreeEdge[]>;
  /** Cha/mẹ theo con. */
  parents: Map<string, TreeEdge[]>;
  /** Hôn nhân theo từng người, hai chiều. */
  spouses: Map<string, { id: string; status: TreeEdge['status'] }[]>;
}

function push<V>(map: Map<string, V[]>, key: string, value: V) {
  const list = map.get(key);
  if (list) list.push(value);
  else map.set(key, [value]);
}

/** Gộp cây đầu và các nhánh tải thêm; bản tải sau ghi đè node trùng (gốc nhánh hết hasMoreChildren), giống web. */
export function mergeGraph(parts: (TreeData | undefined)[]): Graph {
  const nodes = new Map<string, TreeNode>();
  const edges = new Map<string, TreeEdge>();
  for (const part of parts) {
    part?.nodes?.forEach((n) => n.id && nodes.set(n.id, n));
    part?.edges?.forEach((e) => e.id && edges.set(e.id, e));
  }
  const graph: Graph = { nodes, children: new Map(), parents: new Map(), spouses: new Map() };
  for (const e of edges.values()) {
    if (!e.source || !e.target) continue;
    if (e.type === 'PARENT_CHILD') {
      push(graph.children, e.source, e);
      push(graph.parents, e.target, e);
    } else if (e.type === 'MARRIAGE') {
      push(graph.spouses, e.source, { id: e.target, status: e.status });
      push(graph.spouses, e.target, { id: e.source, status: e.status });
    }
  }
  return graph;
}

export interface TreeRow {
  id: string;
  /** 0 là người gốc. */
  level: number;
  /** Có con đã tải hoặc còn nhánh chưa tải. */
  expandable: boolean;
}

function birthOrder(graph: Graph, a: string, b: string): number {
  const ya = graph.nodes.get(a)?.birthYear ?? Number.MAX_SAFE_INTEGER;
  const yb = graph.nodes.get(b)?.birthYear ?? Number.MAX_SAFE_INTEGER;
  return ya - yb;
}

/** Con của một người, xếp theo năm sinh; chỉ lấy con đã có trong cây. */
export function childIds(graph: Graph, id: string): string[] {
  const ids = (graph.children.get(id) ?? [])
    .map((e) => e.target as string)
    .filter((c) => graph.nodes.has(c));
  return [...new Set(ids)].sort((a, b) => birthOrder(graph, a, b));
}

/**
 * Dòng hiển thị: duyệt từ gốc theo cạnh cha-con, chỉ đi xuống người đang mở.
 * Mỗi người chỉ hiện một lần (con có cả cha và mẹ trong cây nằm dưới người gặp trước),
 * nên vợ/chồng từ ngoài họ không thành dòng riêng mà hiện kèm người trong họ.
 */
export function visibleRows(
  graph: Graph,
  rootId: string | null | undefined,
  expanded: Set<string>,
): TreeRow[] {
  const rows: TreeRow[] = [];
  if (!rootId || !graph.nodes.has(rootId)) return rows;
  const seen = new Set<string>();
  const walk = (id: string, level: number) => {
    if (seen.has(id)) return;
    seen.add(id);
    const kids = childIds(graph, id).filter((c) => !seen.has(c));
    const expandable = kids.length > 0 || Boolean(graph.nodes.get(id)?.hasMoreChildren);
    rows.push({ id, level, expandable });
    if (expanded.has(id)) kids.forEach((c) => walk(c, level + 1));
  };
  walk(rootId, 0);
  return rows;
}

const KIND_LABEL = { BIOLOGICAL: '', ADOPTED: ' (nuôi)', STEP: ' (kế)' } as const;
const MARRIAGE_LABEL = { MARRIED: '', DIVORCED: ' (đã ly hôn)', WIDOWED: ' (góa)' } as const;

/** Vợ/chồng kèm trạng thái, dùng cho dòng phụ dưới tên. */
export function spouseLabels(graph: Graph, id: string): { id: string; label: string }[] {
  return (graph.spouses.get(id) ?? [])
    .filter((s) => graph.nodes.has(s.id))
    .map((s) => ({
      id: s.id,
      label: `${graph.nodes.get(s.id)?.fullName ?? ''}${s.status ? MARRIAGE_LABEL[s.status] : ''}`,
    }));
}

/** Quan hệ trực tiếp trong phần cây đã tải: cha, mẹ, vợ/chồng, con. */
export function relativesOf(
  graph: Graph,
  id: string,
): { id: string; name: string; role: string }[] {
  const name = (pid: string) => graph.nodes.get(pid)?.fullName ?? '';
  const parents = (graph.parents.get(id) ?? [])
    .filter((e) => graph.nodes.has(e.source as string))
    .map((e) => {
      const parent = graph.nodes.get(e.source as string);
      const base =
        parent?.gender === 'FEMALE' ? 'Mẹ' : parent?.gender === 'MALE' ? 'Cha' : 'Cha/mẹ';
      return {
        id: e.source as string,
        name: name(e.source as string),
        role: base + (e.kind ? KIND_LABEL[e.kind] : ''),
      };
    });
  const spouses = spouseLabels(graph, id).map((s) => ({
    id: s.id,
    name: s.label,
    role: 'Vợ/chồng',
  }));
  const kids = (graph.children.get(id) ?? [])
    .filter((e) => graph.nodes.has(e.target as string))
    .sort((a, b) => birthOrder(graph, a.target as string, b.target as string))
    .map((e) => ({
      id: e.target as string,
      name: name(e.target as string),
      role: 'Con' + (e.kind ? KIND_LABEL[e.kind] : ''),
    }));
  return [...parents, ...spouses, ...kids];
}
