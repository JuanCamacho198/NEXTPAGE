export type Props = {
  bookId: string;
  isFavorite: boolean;
  readLabel: string;
  editLabel: string;
  removeLabel: string;
  favoriteAddLabel: string;
  favoriteRemoveLabel: string;
  triggerLabel: string;
  onRead?: () => void;
  onEdit: () => void;
  onRemove: () => void;
  onToggleFavorite: () => void;
};

export function getMenuId(bookId: string): string {
  return `shelf-actions-menu-${bookId}`;
}