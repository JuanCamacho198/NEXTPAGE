/**
 * Unit tests for GDriveProvider — Task 2.1
 * Tests NEW behavior: token from GoogleOAuthService, optional `name` on upload.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock SupabaseAuthService BEFORE importing GDriveProvider
vi.mock('$lib/shared/services/SupabaseAuthService', () => ({
  getDriveToken: vi.fn(),
}));

import { GDriveProvider } from '$lib/shared/services/storage/GDriveProvider';
import { getDriveToken } from '$lib/shared/services/SupabaseAuthService';

function mockDriveApiResponses(responses: Array<{ ok: boolean; json: () => unknown }>) {
  let callIndex = 0;
  vi.mocked(globalThis.fetch).mockImplementation(async () => {
    const resp = responses[callIndex++] ?? responses[responses.length - 1];
    return {
      ok: resp.ok,
      status: resp.ok ? 200 : 401,
      statusText: resp.ok ? 'OK' : 'Unauthorized',
      json: resp.json,
      arrayBuffer: async () => new ArrayBuffer(0),
      text: async () => 'error',
    } as Response;
  });
}

function mockAuth(token: string) {
  vi.mocked(getDriveToken).mockResolvedValue(token);
}

function mockAuthError(message: string) {
  vi.mocked(getDriveToken).mockRejectedValue(new Error(message));
}

describe('GDriveProvider — token source swap', () => {
  let provider: GDriveProvider;

  beforeEach(() => {
    vi.clearAllMocks();
    globalThis.fetch = vi.fn();
    provider = new GDriveProvider();
  });

  it('calls getDriveToken for authentication', async () => {
    mockAuth('ya29.test-token');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-1', name: 'Books' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [] }) },
    ]);

    await provider.list('');

    expect(getDriveToken).toHaveBeenCalled();
  });

  it('throws when getDriveToken rejects (not authenticated)', async () => {
    mockAuthError('No Google Drive token available. Please sign in with Google again.');

    await expect(provider.list('')).rejects.toThrow(
      'No Google Drive token available. Please sign in with Google again.',
    );
    expect(getDriveToken).toHaveBeenCalled();
  });

  it('upload with custom name uses it in Drive API metadata', async () => {
    mockAuth('token-123');
    // Mock folder found, then upload
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-abc' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-abc' }] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-xyz' }) },
    ]);

    const data = new Uint8Array([1, 2, 3]);
    const result = await provider.upload('book-001', data, 'book-001_state.json');

    expect(result).toBe('file-xyz');
    // Root and Books folder lookups precede the upload.
    const uploadCall = vi.mocked(globalThis.fetch).mock.calls[2];
    expect(uploadCall[0]).toContain('upload/drive/v3/files');
    // Verify the FormData contains the custom name
    const formData = uploadCall[1]?.body as FormData;
    const metadataBlob = formData?.get('metadata') as Blob;
    const metadataText = await metadataBlob?.text();
    const metadata = JSON.parse(metadataText);
    expect(metadata.name).toBe('book-001_state.json');
    expect(metadata.parents).toEqual(['folder-abc']);
  });

  it('upload without name defaults to id as filename', async () => {
    mockAuth('token-456');
    mockDriveApiResponses([
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'root-def' }] }) },
      { ok: true, json: () => Promise.resolve({ files: [{ id: 'folder-def' }] }) },
      { ok: true, json: () => Promise.resolve({ id: 'file-ghi' }) },
    ]);

    const data = new Uint8Array([4, 5, 6]);
    const result = await provider.upload('epub-001', data);

    expect(result).toBe('file-ghi');
    // Verify metadata name defaults to id
    const uploadCall = vi.mocked(globalThis.fetch).mock.calls[2];
    const formData = uploadCall[1]?.body as FormData;
    const metadataBlob = formData?.get('metadata') as Blob;
    const metadataText = await metadataBlob?.text();
    const metadata = JSON.parse(metadataText);
    expect(metadata.name).toBe('epub-001');
  });
});
