import { open } from "@tauri-apps/plugin-dialog";

export type FilePickerResult = {
  path: string;
  name: string;
};

export type FolderPickerResult = {
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

export async function pickFolder(title = "Select a folder"): Promise<FolderPickerResult | null> {
  const selected = await open({
    directory: true,
    multiple: false,
    title,
  });

  if (!selected) {
    return null;
  }

  const folderPath = selected as string;
  const parts = folderPath.split(/[\\/]/).filter((part) => part.length > 0);
  const name = parts.length > 0 ? parts[parts.length - 1] : folderPath;

  return {
    path: folderPath,
    name,
  };
}
