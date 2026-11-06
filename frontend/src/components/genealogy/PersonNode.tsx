import React, { memo } from 'react';
import { Handle, Position } from 'reactflow';

export interface PersonNodeData {
  id: string;
  name: string;
  birthDate?: string;
  birthYear?: number;
  isDeceased: boolean;
  avatarMediaId?: string;
  generation: number;
  hasChildren?: boolean;
  onToggleBranch?: (id: string) => void;
  onNodeClick?: (id: string) => void;
}

const PersonNode = ({ data }: { data: PersonNodeData }) => {
  const displayYear = data.birthYear 
    ? data.birthYear 
    : data.birthDate 
      ? new Date(data.birthDate).getFullYear() 
      : '???';

  return (
    <div 
      className={`p-3 rounded-lg border-2 bg-white shadow-md text-center min-w-[150px] transition-all hover:scale-105 cursor-pointer ${
        data.isDeceased ? 'border-gray-400 bg-gray-50 text-gray-500' : 'border-blue-500 text-gray-800'
      }`}
      onClick={() => data.onNodeClick?.(data.id)}
    >
      {/* Handle kết nối đường nối React Flow */}
      <Handle type="target" position={Position.Top} className="w-2 h-2 bg-gray-400" />
      
      <div className="flex flex-col items-center gap-1">
        {/* Ảnh đại diện mặc định hoặc từ Media ID */}
        <div className={`w-12 h-12 rounded-full flex items-center justify-center text-white font-bold text-sm ${
          data.isDeceased ? 'bg-gray-400' : 'bg-blue-500'
        }`}>
          {data.name.charAt(0)}
        </div>
        
        <div className="font-semibold text-sm truncate max-w-[140px]">{data.name}</div>
        <div className="text-xs text-gray-500">
          th. hệ {data.generation} ({displayYear}{data.isDeceased ? ' - †' : ''})
        </div>

        {/* Nút mở rộng/thu nhánh đệ quy phục vụ lazy-load */}
        {data.hasChildren && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              data.onToggleBranch?.(data.id);
            }}
            className="mt-1 px-2 py-0.5 text-[10px] bg-gray-100 hover:bg-gray-200 border rounded transition-colors text-gray-600"
          >
            ± Thu/Mở nhánh
          </button>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="w-2 h-2 bg-gray-400" />
    </div>
  );
};

export default memo(PersonNode);

