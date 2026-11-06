'use client';

import React, { useState } from 'react';

interface RelationshipManagerProps {
  personId: string;
  familyId: string;
  onSuccess?: () => void;
}

export default function RelationshipManager({ personId, familyId, onSuccess }: RelationshipManagerProps) {
  const [activeTab, setActiveTab] = useState<'parent' | 'marriage'>('parent');
  const [parentId, setParentId] = useState('');
  const [parentRole, setParentRole] = useState<'FATHER' | 'MOTHER'>('FATHER');
  const [kind, setKind] = useState<'BIOLOGICAL' | 'ADOPTED' | 'STEP'>('BIOLOGICAL');
  
  const [spouseId, setSpouseId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleAddParent = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setLoading(true);

    try {
      const res = await fetch(`/api/v1/persons/${personId}/parents`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ parentId, parentRole, kind }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Lỗi khi thêm quan hệ cha/mẹ');
      }

      setSuccessMsg('Thêm quan hệ cha/mẹ thành công!');
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setErrorMsg(err.message || 'Có lỗi xảy ra');
    } finally {
      setLoading(false);
    }
  };

  const handleAddMarriage = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setLoading(true);

    try {
      const res = await fetch(`/api/v1/marriages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ person1Id: personId, person2Id: spouseId, startDate: startDate || null }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || 'Lỗi khi thêm quan hệ hôn nhân');
      }

      setSuccessMsg('Thêm quan hệ hôn nhân thành công!');
      if (onSuccess) onSuccess();
    } catch (err: any) {
      setErrorMsg(err.message || 'Có lỗi xảy ra');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white p-6 rounded-lg shadow-md max-w-lg mx-auto">
      <div className="flex border-b mb-4">
        <button
          className={`py-2 px-4 font-semibold ${activeTab === 'parent' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500'}`}
          onClick={() => setActiveTab('parent')}
        >
          Thêm Cha/Mẹ
        </button>
        <button
          className={`py-2 px-4 font-semibold ${activeTab === 'marriage' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500'}`}
          onClick={() => setActiveTab('marriage')}
        >
          Thêm Hôn Nhân
        </button>
      </div>

      {errorMsg && <div className="mb-4 p-3 bg-red-100 text-red-700 rounded text-sm">{errorMsg}</div>}
      {successMsg && <div className="mb-4 p-3 bg-green-100 text-green-700 rounded text-sm">{successMsg}</div>}

      {activeTab === 'parent' ? (
        <form onSubmit={handleAddParent} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">ID Cha/Mẹ</label>
            <input
              type="text"
              value={parentId}
              onChange={(e) => setParentId(e.target.value)}
              required
              placeholder="Nhập UUID cha/mẹ"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm border p-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Vai trò</label>
            <select
              value={parentRole}
              onChange={(e) => setParentRole(e.target.value as 'FATHER' | 'MOTHER')}
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm border p-2 text-sm"
            >
              <option value="FATHER">Cha (FATHER)</option>
              <option value="MOTHER">Mẹ (MOTHER)</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Loại quan hệ</label>
            <select
              value={kind}
              onChange={(e) => setKind(e.target.value as any)}
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm border p-2 text-sm"
            >
              <option value="BIOLOGICAL">Ruột (BIOLOGICAL)</option>
              <option value="ADOPTED">Nuôi (ADOPTED)</option>
              <option value="STEP">Kế (STEP)</option>
            </select>
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 disabled:opacity-50 text-sm font-medium"
          >
            {loading ? 'Đang xử lý...' : 'Thêm Cha/Mẹ'}
          </button>
        </form>
      ) : (
        <form onSubmit={handleAddMarriage} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">ID Vợ/Chồng</label>
            <input
              type="text"
              value={spouseId}
              onChange={(e) => setSpouseId(e.target.value)}
              required
              placeholder="Nhập UUID vợ/chồng"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm border p-2 text-sm"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Ngày kết hôn (Tùy chọn)</label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm border p-2 text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 disabled:opacity-50 text-sm font-medium"
          >
            {loading ? 'Đang xử lý...' : 'Thêm Hôn Nhân'}
          </button>
        </form>
      )}
    </div>
  );
}
