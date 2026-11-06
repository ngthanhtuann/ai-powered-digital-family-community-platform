'use client';

import React, { use, useEffect, useState } from 'react';
import ReactFlow, {
  MiniMap,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
  Node,
  Edge,
} from 'reactflow';
import dagre from 'dagre';
import 'reactflow/dist/style.css';
import PersonNode from '@/components/genealogy/PersonNode';

const nodeTypes = { personNode: PersonNode };

const getLayoutedElements = (nodes: Node[], edges: Edge[]) => {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));
  dagreGraph.setGraph({ rankdir: 'TB', nodesep: 70, ranksep: 100 });

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: 160, height: 100 });
  });

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  dagre.layout(dagreGraph);

  nodes.forEach((node) => {
    const nodeWithPosition = dagreGraph.node(node.id);
    node.position = {
      x: nodeWithPosition.x - 80,
      y: nodeWithPosition.y - 50,
    };
  });

  return { nodes, edges };
};

interface PageParams {
  id: string;
}

export default function FamilyTreePage({ params }: { params: Promise<PageParams> }) {
  // Giải mã Promise params theo đúng đặc tả kỹ thuật của Next.js 15
  const resolvedParams = use(params);
  const familyId = resolvedParams.id;

  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchTreeData = async () => {
      setLoading(true);
      try {
        const mockNodes: Node[] = [];
        const mockEdges: Edge[] = [];

        // Tạo vòng lặp sinh dữ liệu 100 người mượt mà để test hiệu năng đồ thị
        for (let i = 1; i <= 100; i++) {
          mockNodes.push({
            id: `${i}`,
            type: 'personNode',
            data: {
              id: `${i}`,
              name: `Thành viên thứ ${i}`,
              generation: Math.floor(i / 20) + 1,
              isDeceased: i % 7 === 0,
              hasChildren: i <= 40,
              onToggleBranch: (id: string) => alert(`Lazy load nhánh con của node: ${id}`),
              onNodeClick: (id: string) => alert(`Phối hợp TV1: Mở panel chi tiết cho node: ${id}`),
            },
            position: { x: 0, y: 0 },
          });

          if (i > 1 && i <= 40) {
            mockEdges.push({
              id: `e-${Math.floor(i / 2)}-${i}`,
              source: `${Math.floor(i / 2)}`,
              target: `${i}`,
              type: i % 3 === 0 ? 'smoothstep' : 'straight',
              style: { strokeWidth: 2, stroke: '#94a3b8' },
            });
          }
        }

        const layouted = getLayoutedElements(mockNodes, mockEdges);
        setNodes(layouted.nodes);
        setEdges(layouted.edges);
      } catch (error) {
        console.error('Lỗi nạp cây gia phả:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchTreeData();
  }, [familyId, setNodes, setEdges]);

  if (loading) {
    return <div className="p-8 text-center text-gray-500 font-semibold">Dựng cấu trúc đồ thị cây mượt với 100+ người...</div>;
  }

  return (
    <div className="w-full h-screen bg-gray-50 flex flex-col">
      <div className="p-4 bg-white border-b shadow-sm flex justify-between items-center">
        <h1 className="text-lg font-bold text-gray-800">Cây Gia Phả Tương Tác - FamilyConnect</h1>
        <div className="text-xs text-blue-600 bg-blue-50 px-3 py-1 rounded-full font-medium">Layout: Dagre Vertical</div>
      </div>
      
      <div className="flex-1 w-full h-full">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          nodeTypes={nodeTypes}
          fitView
          maxZoom={1.5}
          minZoom={0.2}
        >
          <Controls />
          <MiniMap nodeStrokeWidth={3} zoomable pannable />
          <Background color="#cbd5e1" gap={16} size={1} />
        </ReactFlow>
      </div>
    </div>
  );
}

