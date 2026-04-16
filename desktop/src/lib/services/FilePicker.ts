import { open } from "@tauri-apps/plugin-dialog";

export type FilePickerResult = {
  path: string;
  name: string;
};

const filters = [
  {
    name: "Books",
    extensions: ["pdf", "epub"],
  },
];

export async function pickFile(): Promise<FilePickerResult | null> {
  const selected = await open({
    multiple: false,
    filters,
    title: "Select a book",
  });

  if (!selected) {
    return null;
  }

  const filePath = selected as string;
  const parts = filePath.split(/[\\/]/);
  const name = parts[parts.length - 1];

  return {
    path: filePath,
    name,
  };
}