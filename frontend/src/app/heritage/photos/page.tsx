'use client';

import { useEffect, useMemo, useState } from 'react';
import Image from 'next/image';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { HeritageHeader, HeritagePage } from '@/components/heritage/HeritageHeader';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { ApiError } from '@/lib/api';
import { heritageApi, type HeritagePhoto } from '@/services/heritage';

function ProtectedPhoto({ photo, className }: { photo: HeritagePhoto; className: string }) {
  const [src, setSrc] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    let objectUrl = '';
    setSrc('');
    setError('');

    heritageApi
      .file(photo.contentUrl)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : 'Không tải được ảnh.');
      });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photo.contentUrl]);

  if (error)
    return (
      <span
        role="img"
        aria-label="Không tải được ảnh"
        className={`${className} flex items-center justify-center bg-surface-muted p-3 text-center text-caption text-error`}
      >
        {error}
      </span>
    );
  if (!src)
    return (
      <span aria-label="Đang tải ảnh" className={`${className} animate-pulse bg-surface-muted`} />
    );
  return (
    <Image
      src={src}
      alt={photo.caption || 'Ảnh di sản gia đình'}
      width={1600}
      height={1200}
      unoptimized
      className={className}
    />
  );
}

export default function HeritagePhotosPage() {
  const access = useHeritageAccess();
  const queryClient = useQueryClient();
  const [adminFamily, setAdminFamily] = useState('');
  const [targetMediaId, setTargetMediaId] = useState('');
  const [caption, setCaption] = useState('');
  const [uploadError, setUploadError] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [slideshow, setSlideshow] = useState(false);
  const familyId = access.isSystemAdmin ? adminFamily.trim() : (access.familyId ?? '');
  const enabled = access.isReady && Boolean(familyId);

  useEffect(() => {
    setTargetMediaId(new URLSearchParams(window.location.search).get('mediaId') ?? '');
  }, []);

  const photos = useInfiniteQuery({
    queryKey: ['heritage-photos', familyId, targetMediaId],
    queryFn: ({ pageParam }) =>
      heritageApi.photos({
        familyId: access.isSystemAdmin ? familyId : undefined,
        mediaId: targetMediaId || undefined,
        page: pageParam,
        size: targetMediaId ? 1 : 24,
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => {
      const loaded = allPages.reduce((count, page) => count + page.items.length, 0);
      return loaded < lastPage.meta.total ? lastPage.meta.page + 1 : undefined;
    },
    enabled,
  });
  const items = useMemo(
    () => photos.data?.pages.flatMap((page) => page.items) ?? [],
    [photos.data],
  );
  useEffect(() => {
    if (targetMediaId && items.some((photo) => photo.mediaId === targetMediaId)) {
      setSelected(targetMediaId);
    }
  }, [items, targetMediaId]);
  const selectedIndex = items.findIndex((photo) => photo.mediaId === selected);
  const activePhoto = selectedIndex >= 0 ? items[selectedIndex] : null;

  const upload = useMutation({
    mutationFn: ({ file, photoCaption }: { file: File; photoCaption: string }) =>
      heritageApi.upload(file, familyId, photoCaption),
    onSuccess: async () => {
      setCaption('');
      setUploadError('');
      await queryClient.invalidateQueries({ queryKey: ['heritage-photos', familyId] });
    },
    onError: (reason: unknown) =>
      setUploadError(
        reason instanceof ApiError
          ? `${reason.code}: ${reason.message}`
          : reason instanceof Error
            ? reason.message
            : 'Không thể tải ảnh lên.',
      ),
  });

  useEffect(() => {
    if (!activePhoto) {
      setSlideshow(false);
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSelected(null);
      if (event.key === 'ArrowRight')
        setSelected(items[(selectedIndex + 1) % items.length]?.mediaId ?? null);
      if (event.key === 'ArrowLeft')
        setSelected(items[(selectedIndex - 1 + items.length) % items.length]?.mediaId ?? null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activePhoto, items, selectedIndex]);

  useEffect(() => {
    if (!slideshow || items.length < 2) return;
    const timer = window.setInterval(() => {
      setSelected((current) => {
        const index = items.findIndex((photo) => photo.mediaId === current);
        return items[(index + 1) % items.length]?.mediaId ?? null;
      });
    }, 3500);
    return () => window.clearInterval(timer);
  }, [slideshow, items]);

  const accessError =
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message;
  const dataError = (photos.error as ApiError | undefined)?.message;

  return (
    <HeritagePage>
      <HeritageHeader />
      <div className="mt-6">
        <AccessState
          loading={access.membership.isLoading || access.profile.isLoading}
          error={accessError}
        />
      </div>
      {access.isSystemAdmin && access.isReady && (
        <div className="mt-5 max-w-xl">
          <TextField
            label="Family ID (SYSTEM_ADMIN)"
            value={adminFamily}
            onChange={(event) => setAdminFamily(event.target.value)}
            placeholder="UUID gia đình"
          />
        </div>
      )}
      {enabled && (
        <section className="mt-8 rounded-card border border-border bg-surface p-5 shadow-card">
          <h2 className="text-section-title">Thêm ảnh vào thư viện</h2>
          <p className="mt-1 text-caption text-text-secondary">JPG, PNG hoặc WebP, tối đa 5 MB.</p>
          <form
            className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end"
            onSubmit={(event) => {
              event.preventDefault();
              const form = event.currentTarget;
              const input = form.elements.namedItem('photo') as HTMLInputElement | null;
              const file = input?.files?.[0];
              setUploadError('');
              if (!file) {
                setUploadError('Vui lòng chọn ảnh cần tải lên.');
                return;
              }
              if (
                !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
                file.size > 5 * 1024 * 1024
              ) {
                setUploadError('Ảnh phải có định dạng JPG, PNG hoặc WebP và không vượt quá 5 MB.');
                return;
              }
              upload.mutate(
                { file, photoCaption: caption },
                {
                  onSuccess: () => {
                    form.reset();
                  },
                },
              );
            }}
          >
            <label className="flex flex-1 flex-col gap-1.5 text-label">
              Chọn ảnh
              <input
                name="photo"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body"
              />
            </label>
            <div className="flex-1">
              <TextField
                label="Chú thích"
                value={caption}
                onChange={(event) => setCaption(event.target.value)}
                maxLength={255}
                placeholder="Mô tả ảnh để dễ tìm kiếm"
              />
            </div>
            <Button className="sm:w-auto" type="submit" disabled={upload.isPending}>
              {upload.isPending ? 'Đang tải...' : 'Tải ảnh lên'}
            </Button>
          </form>
          {uploadError && (
            <p role="alert" className="mt-3 text-body text-error">
              {uploadError}
            </p>
          )}
        </section>
      )}

      <section className="mt-8">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-section-title">Thư viện ảnh</h2>
            <p className="mt-1 text-caption text-text-secondary">
              Ảnh gia đình được lưu riêng; ảnh đã gắn với tài liệu sẽ hiển thị trong kho lưu trữ.
            </p>
          </div>
          {items.length > 0 && (
            <span className="text-caption text-text-secondary">
              {photos.data?.pages[0]?.meta.total ?? items.length} ảnh
            </span>
          )}
        </div>
        {dataError && (
          <p role="alert" className="mt-4 rounded-control bg-error-soft p-3 text-body text-error">
            {dataError}
          </p>
        )}
        {!enabled && access.isReady && (
          <p className="mt-4 text-body text-text-secondary">
            {access.isSystemAdmin
              ? 'Nhập Family ID để xem thư viện.'
              : 'Tài khoản chưa được liên kết với gia đình.'}
          </p>
        )}
        {enabled && photos.isLoading && (
          <p className="mt-4 text-body text-text-secondary">Đang tải ảnh...</p>
        )}
        {enabled && !photos.isLoading && items.length === 0 && !dataError && (
          <p className="mt-4 rounded-card border border-dashed border-border-strong p-5 text-body text-text-secondary">
            Chưa có ảnh trong thư viện.
          </p>
        )}
        <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((photo) => (
            <li key={photo.mediaId}>
              <button
                type="button"
                className="group w-full overflow-hidden rounded-card border border-border bg-surface text-left shadow-card hover:border-secondary"
                onClick={() => setSelected(photo.mediaId)}
              >
                <ProtectedPhoto photo={photo} className="aspect-square w-full object-cover" />
                <span className="block min-h-12 p-3 text-caption text-text-secondary">
                  {photo.caption || new Date(photo.createdAt).toLocaleDateString('vi-VN')}
                </span>
              </button>
            </li>
          ))}
        </ul>
        {photos.hasNextPage && (
          <div className="mt-5 text-center">
            <Button
              className="sm:w-auto"
              disabled={photos.isFetchingNextPage}
              onClick={() => photos.fetchNextPage()}
            >
              {photos.isFetchingNextPage ? 'Đang tải...' : 'Tải thêm ảnh'}
            </Button>
          </div>
        )}
      </section>

      {activePhoto && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Xem ảnh"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 p-4"
          onClick={() => setSelected(null)}
        >
          <div
            className="flex max-h-full w-full max-w-6xl flex-col items-center gap-3"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex w-full justify-end gap-2">
              {items.length > 1 && !targetMediaId && (
                <button
                  type="button"
                  className="rounded-control border border-white/40 px-3 py-2 text-label text-white"
                  onClick={() => setSlideshow((value) => !value)}
                >
                  {slideshow ? 'Tạm dừng' : 'Trình chiếu'}
                </button>
              )}
              <button
                type="button"
                aria-label="Đóng"
                className="rounded-control border border-white/40 px-3 py-2 text-label text-white"
                onClick={() => setSelected(null)}
              >
                Đóng
              </button>
            </div>
            <ProtectedPhoto
              photo={activePhoto}
              className="max-h-[75vh] max-w-full object-contain"
            />
            <div className="flex w-full items-center justify-between gap-3 text-white">
              {items.length > 1 && !targetMediaId && (
                <button
                  type="button"
                  className="rounded-control border border-white/40 px-3 py-2 text-label"
                  onClick={() =>
                    setSelected(
                      items[(selectedIndex - 1 + items.length) % items.length]?.mediaId ?? null,
                    )
                  }
                >
                  Ảnh trước
                </button>
              )}
              <p className="text-center text-caption">
                {activePhoto.caption || new Date(activePhoto.createdAt).toLocaleDateString('vi-VN')}{' '}
                · {selectedIndex + 1}/{items.length}
              </p>
              {items.length > 1 && !targetMediaId && (
                <button
                  type="button"
                  className="rounded-control border border-white/40 px-3 py-2 text-label"
                  onClick={() =>
                    setSelected(items[(selectedIndex + 1) % items.length]?.mediaId ?? null)
                  }
                >
                  Ảnh sau
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </HeritagePage>
  );
}
