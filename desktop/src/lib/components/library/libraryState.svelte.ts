import type { LibraryBookDto, CollectionDto } from "$lib/types";
import type { MessageKey } from "$lib/i18n";

export const LIBRARY_VIEW_MODE = {
  LIST: "list",
  GRID: "grid",
} as const;

export type LibraryViewMode = (typeof LIBRARY_VIEW_MODE)[keyof typeof LIBRARY_VIEW_MODE];

export type Props = {
  books: LibraryBookDto[];
  collections?: CollectionDto[];
  selectedBookId?: string | null;
  selectedCollectionId?: string | null;
  isLoading?: boolean;
  disabledReason?: string | null;
  viewMode?: LibraryViewMode;
  onSelect?: (book: LibraryBookDto) => void;
  onOpen?: (book: LibraryBookDto) => void;
  onHide?: (book: LibraryBookDto) => void;
  onEdit?: (book: LibraryBookDto) => void;
  onToggleView?: (mode: LibraryViewMode) => void;
  onCollectionSelect?: (collectionId: string | null) => void;
  onManageCollections?: () => void;
  onImportFolder?: () => void;
  isImportingFolder?: boolean;
  t: (key: MessageKey, params?: Record<string, string | number>) => string;
};

export function formatUpdatedAt(iso: string, t: (key: MessageKey, params?: Record<string, string | number>) => string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return t("settings.unknownBook");
  }
  return parsed.toLocaleDateString();
}

export function formatProgress(progress: number): string {
  return `${Math.round(progress)}%`;
}