type CsvValue = string | number | boolean | null | undefined;

export interface CsvColumn<T> {
  header: string;
  value: (row: T) => CsvValue;
}

function escapeCsv(value: CsvValue) {
  const text = value == null ? '' : String(value);
  if (!/[",\r\n]/.test(text)) {
    return text;
  }
  return `"${text.replace(/"/g, '""')}"`;
}

export function downloadCsv<T>(filename: string, rows: T[], columns: CsvColumn<T>[]) {
  const header = columns.map((column) => escapeCsv(column.header)).join(',');
  const body = rows.map((row) => columns.map((column) => escapeCsv(column.value(row))).join(','));
  const blob = new Blob([[header, ...body].join('\r\n')], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function confirmDelete(label: string) {
  return window.confirm(`Delete ${label}?\n\nThis cannot be undone.`);
}
