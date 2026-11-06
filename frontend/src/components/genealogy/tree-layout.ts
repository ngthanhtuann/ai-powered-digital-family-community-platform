import dagre from 'dagre';
import type { TreeEdge, TreeNode } from '@/services/genealogy';

export type XY = { x: number; y: number };
const GAP = 24;

/**
 * Vợ/chồng không có cha mẹ trên cây (người kết hôn vào dòng họ) được đặt cạnh người kia, cùng hàng.
 * Trả về map khách -> chủ; người còn lại tự có vị trí theo cha mẹ.
 */
export function spouseAnchors(nodes: TreeNode[], edges: TreeEdge[], rootId?: string): Map<string, string> {
  const ids = new Set(nodes.map((n) => n.id));
  const hasParent = new Set(
    edges.filter((e) => e.type === 'PARENT_CHILD' && ids.has(e.source)).map((e) => e.target),
  );
  const anchors = new Map<string, string>();
  const hosts = new Set<string>();
  for (const e of edges) {
    const s = e.source ?? '';
    const t = e.target ?? '';
    if (e.type !== 'MARRIAGE' || !ids.has(s) || !ids.has(t)) continue;
    let host: string;
    if (!hasParent.has(t) && hasParent.has(s)) host = s;
    else if (!hasParent.has(s) && hasParent.has(t)) host = t;
    else if (!hasParent.has(s) && !hasParent.has(t)) host = s === rootId ? s : t === rootId ? t : s < t ? s : t;
    else continue; // cả hai đều có cha mẹ trên cây: mỗi người đứng ở nhánh của mình
    const guest = host === s ? t : s;
    if (anchors.has(guest) || hosts.has(guest) || anchors.has(host)) continue;
    anchors.set(guest, host);
    hosts.add(host);
  }
  return anchors;
}

/** Bố cục dagre từ trên xuống (thế hệ trên ở trên); trả về góc trên trái của từng node. */
export function layoutTree(
  nodes: TreeNode[], edges: TreeEdge[], anchors: Map<string, string>, width: number, height: number,
): Map<string, XY> {
  const guests = new Map<string, string[]>();
  anchors.forEach((host, guest) => guests.set(host, [...(guests.get(host) ?? []), guest]));

  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: 'TB', nodesep: 48, ranksep: 80 });
  g.setDefaultEdgeLabel(() => ({}));
  for (const n of nodes) {
    const id = n.id ?? '';
    if (anchors.has(id)) continue;
    const extra = guests.get(id)?.length ?? 0;
    g.setNode(id, { width: width + extra * (width + GAP), height });
  }
  for (const e of edges) {
    if (e.type !== 'PARENT_CHILD') continue;
    const source = anchors.get(e.source ?? '') ?? e.source ?? '';
    const target = e.target ?? '';
    if (source !== target && g.hasNode(source) && g.hasNode(target)) g.setEdge(source, target);
  }
  dagre.layout(g);

  const positions = new Map<string, XY>();
  for (const id of g.nodes()) {
    const box = g.node(id);
    const left = box.x - box.width / 2;
    const top = box.y - height / 2;
    positions.set(id, { x: left, y: top });
    (guests.get(id) ?? []).forEach((guest, i) => positions.set(guest, { x: left + (i + 1) * (width + GAP), y: top }));
  }
  return positions;
}

/** Người bị ẩn khi thu các nhánh trong collapsed: hậu duệ, và vợ/chồng không có cha mẹ của người bị ẩn. */
export function hiddenByCollapse(edges: TreeEdge[], collapsed: Set<string>): Set<string> {
  const children = new Map<string, string[]>();
  const hasParent = new Set<string>();
  for (const e of edges) {
    if (e.type !== 'PARENT_CHILD') continue;
    children.set(e.source ?? '', [...(children.get(e.source ?? '') ?? []), e.target ?? '']);
    hasParent.add(e.target ?? '');
  }
  const hidden = new Set<string>();
  const stack = [...collapsed].flatMap((id) => children.get(id) ?? []);
  while (stack.length > 0) {
    const id = stack.pop() as string;
    if (hidden.has(id)) continue;
    hidden.add(id);
    stack.push(...(children.get(id) ?? []));
  }
  for (const e of edges) {
    if (e.type !== 'MARRIAGE') continue;
    const [s, t] = [e.source ?? '', e.target ?? ''];
    if (hidden.has(s) && !hasParent.has(t)) hidden.add(t);
    if (hidden.has(t) && !hasParent.has(s)) hidden.add(s);
  }
  return hidden;
}
