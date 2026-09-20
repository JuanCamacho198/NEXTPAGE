import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import SettingsDataTab from '$lib/features/settings/components/SettingsDataTab.svelte';

const messages: Record<string, string> = {
  'settings.data.dictionary.title': 'Dictionary',
  'settings.data.dictionary.description': 'Export or import your saved words as JSON or CSV.',
  'settings.data.dictionary.exportJson': 'Export JSON',
  'settings.data.dictionary.exportCsv': 'Export CSV',
  'settings.data.dictionary.import': 'Import',
  'settings.data.coldBackup': 'Cold Backup (Drive)',
  'settings.data.coldBackupDescription': 'Cold backup description',
  'settings.data.coldExport': 'Export to Drive',
  'settings.data.coldImport': 'Import from Drive',
  'settings.data.clearCache': 'Clear cache',
  'settings.data.clearCacheDescription': 'Remove temp files',
  'settings.data.exportLibrary': 'Export library',
  'settings.data.exportLibraryDescription': 'Download books',
  'settings.data.exportLibraryButton': 'Export library',
  'settings.data.exportHighlights': 'Export highlights',
  'settings.data.exportHighlightsDescription': 'Download annotations',
  'settings.data.download': 'Download',
  'settings.data.allBooks': 'All books',
  'settings.data.markdown': 'Markdown',
  'settings.privacy.title': 'Privacy',
  'settings.privacy.description': 'Telemetry',
  'settings.privacy.sendTelemetry': 'Send telemetry',
  'settings.privacy.telemetryOn': 'On',
  'settings.privacy.telemetryOff': 'Off',
};

const t = (key: string, params?: Record<string, string | number>): string => {
  const template = messages[key] ?? key;
  if (!params) return template;
  return Object.entries(params).reduce(
    (acc, [name, value]) => acc.replace(`{{${name}}}`, String(value)),
    template,
  );
};

function baseProps() {
  return {
    t,
    books: [] as { id: string; title: string }[],
    isClearingCache: false,
    cacheCleared: false,
    selectedExportBook: 'all',
    selectedExportFormat: 'json' as 'json' | 'markdown',
    isExportingHighlights: false,
    isExportingDictionary: false,
    isImportingDictionary: false,
    dictionaryExportError: null as string | null,
    dictionaryImportResult: null as string | null,
    dictionaryImportError: null as string | null,
    onClearCache: vi.fn(),
    onExportLibrary: vi.fn(),
    onExportHighlights: vi.fn(),
    onExportDictionary: vi.fn(),
    onImportDictionary: vi.fn(),
    onSelectedExportBookChange: vi.fn(),
    onSelectedExportFormatChange: vi.fn(),
  };
}

function renderTab(overrides: Partial<ReturnType<typeof baseProps>> = {}) {
  const props = { ...baseProps(), ...overrides };
  return { ...render(SettingsDataTab, { props }), props };
}

describe('SettingsDataTab dictionary transfer group', () => {
  it('renders the dictionary group beside the cold-backup pair, not merged with it', () => {
    renderTab();

    const group = screen.getByTestId('dictionary-transfer-actions');
    expect(screen.getByText('Dictionary')).toBeInTheDocument();
    expect(
      screen.getByText('Export or import your saved words as JSON or CSV.'),
    ).toBeInTheDocument();

    expect(screen.getByText('Export JSON')).toBeInTheDocument();
    expect(screen.getByText('Export CSV')).toBeInTheDocument();
    expect(screen.getByText('Import')).toBeInTheDocument();

    // The cold-backup controls stay outside the dictionary group.
    expect(screen.getByText('Export to Drive')).toBeInTheDocument();
    expect(screen.getByText('Import from Drive')).toBeInTheDocument();
    expect(group.contains(screen.getByText('Export to Drive'))).toBe(false);
    expect(group.contains(screen.getByText('Import from Drive'))).toBe(false);
  });

  it('forwards each export format to the same handler', async () => {
    const { props } = renderTab();

    await fireEvent.click(screen.getByText('Export JSON'));
    expect(props.onExportDictionary).toHaveBeenCalledWith('json');

    await fireEvent.click(screen.getByText('Export CSV'));
    expect(props.onExportDictionary).toHaveBeenCalledWith('csv');
  });

  it('forwards the chosen file to the import handler and clears the input', async () => {
    const { props } = renderTab();
    const input = screen.getByTestId('dictionary-import-input') as HTMLInputElement;
    const file = new File(['a,b'], 'words.csv', { type: 'text/csv' });

    await fireEvent.change(input, { target: { files: [file] } });

    expect(props.onImportDictionary).toHaveBeenCalledWith(file);
    expect(input.value).toBe('');
  });

  it('reports export errors, import results and import errors distinctly', () => {
    renderTab({
      dictionaryExportError: 'disk full',
      dictionaryImportResult: 'Imported 3, errors 1',
      dictionaryImportError: 'row 2: bad word',
    });

    expect(screen.getByTestId('dictionary-export-error')).toHaveTextContent('disk full');
    expect(screen.getByTestId('dictionary-import-result')).toHaveTextContent(
      'Imported 3, errors 1',
    );
    expect(screen.getByTestId('dictionary-import-error')).toHaveTextContent('row 2: bad word');
  });

  it('disables the transfer controls while a transfer is in flight', () => {
    renderTab({ isExportingDictionary: true });

    expect(screen.getByText('Export JSON').closest('button')).toBeDisabled();
    expect(screen.getByText('Export CSV').closest('button')).toBeDisabled();
    expect(screen.getByTestId('dictionary-import-input')).toBeDisabled();
  });
});
