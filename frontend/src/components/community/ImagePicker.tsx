'use client';

import { useEffect, useState } from 'react';

type Props = {
  files: File[];
  onChange: (files: File[]) => void;
  disabled?: boolean;
  progress?: number;
};

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/** Chọn nhiều ảnh, preview trên máy; backend vẫn kiểm tra magic bytes và giới hạn. */
export function ImagePicker({ files, onChange, disabled = false, progress }: Props) {
  const [previews, setPreviews] = useState<{ url: string; name: string }[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    const next = files.map((file) => ({ url: URL.createObjectURL(file), name: file.name }));
    setPreviews(next);
    return () => next.forEach(({ url }) => URL.revokeObjectURL(url));
  }, [files]);

  const choose = (chosen: FileList | null) => {
    if (!chosen) return;
    const list = Array.from(chosen);
    if (list.some((file) => file.size > MAX_IMAGE_BYTES)) {
      setError('Mỗi ảnh tối đa 5 MB.');
      return;
    }
    if (list.some((file) => !['image/jpeg', 'image/png', 'image/webp'].includes(file.type))) {
      setError('Chỉ chọn ảnh JPG, PNG hoặc WEBP.');
      return;
    }
    setError('');
    onChange(list);
  };

  return (
    <section aria-label="Ảnh đính kèm" className="space-y-2">
      <label htmlFor="post-images" className="block text-sm font-medium">Ảnh đính kèm</label>
      <input id="post-images" type="file" accept="image/jpeg,image/png,image/webp" multiple
        disabled={disabled} className="block w-full text-sm" onChange={(event) => {
          choose(event.target.files);
          event.target.value = '';
        }} />
      {error && <p role="alert" className="text-error">{error}</p>}
      {previews.length > 0 && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {previews.map((preview, index) => (
            <li key={index} className="space-y-1">
              {/* URL Blob chỉ dùng để preview, không phải tệp công khai trên server. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={preview.url} alt={preview.name} className="h-28 w-full rounded-lg object-cover" />
              <p className="truncate text-xs text-text-secondary">{preview.name}</p>
              {!disabled && <button type="button" className="text-sm underline"
                onClick={() => onChange(files.filter((_, i) => i !== index))}>Bỏ ảnh</button>}
            </li>
          ))}
        </ul>
      )}
      {progress !== undefined && (
        <div role="status" aria-live="polite">
          <p className="text-xs">Đang tải ảnh: {progress}%</p>
          <progress className="w-full" value={progress} max={100} />
        </div>
      )}
    </section>
  );
}
