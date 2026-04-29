import type { CollectionDto, CreateCollectionInput } from "$lib/types";

export type Props = {
  open: boolean;
  onClose: () => void;
};

export const COLOR_OPTIONS = [
  "#6366f1", "#8b5cf6", "#ec4899", "#ef4444",
  "#f97316", "#eab308", "#22c55e", "#14b8a6", "#0ea5e9"
] as const;

export function generateId(): string {
  return Math.random().toString(36).substring(2, 9);
}