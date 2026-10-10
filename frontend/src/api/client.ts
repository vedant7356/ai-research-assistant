// The only place that calls fetch. Pages and components import from here.

export interface HealthResponse {
  status: string
  database: string
  time: string
}

export interface Paper {
  externalId: string | null
  title: string
  authors: string[]
  year: number | null
  abstractText: string | null
  url: string | null
  pdfUrl: string | null
  openAccess: boolean
  inLibrary: boolean
}

export interface LibraryItem {
  id: number
  source: 'SAVED' | 'UPLOAD'
  externalId: string | null
  title: string
  authors: string[]
  year: number | null
  abstractText: string | null
  url: string | null
  pdfUrl: string | null
  openAccess: boolean
  originalFilename: string | null
  textStatus: 'FULL_TEXT' | 'ABSTRACT_ONLY'
  hasSummary: boolean
  createdAt: string
}

export interface SearchResponse {
  query: string
  total: number
  page: number
  limit: number
  items: Paper[]
}

export interface SearchParams {
  q: string
  yearFrom?: number
  sort: 'relevance' | 'newest'
  openAccessOnly: boolean
  page: number
  limit: number
}

/** Error from the backend's { error: { code, message, retryable } } envelope (or a network failure). */
export class ApiError extends Error {
  code: string
  retryable: boolean
  status: number

  constructor(code: string, message: string, retryable: boolean, status: number) {
    super(message)
    this.code = code
    this.retryable = retryable
    this.status = status
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, init)
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError('NETWORK', "Can't reach the server. Check that the backend is running.", true, 0)
  }
  if (!res.ok) {
    let envelope: { error?: { code?: string; message?: string; retryable?: boolean } } | null = null
    try {
      envelope = await res.json()
    } catch {
      // not JSON (for example the dev proxy answering 502 while the backend is down)
    }
    const err = envelope?.error
    throw new ApiError(
      err?.code ?? `HTTP_${res.status}`,
      err?.message ?? (res.status >= 500 ? "The server isn't responding." : `Request failed (${res.status}).`),
      err?.retryable ?? res.status >= 500,
      res.status,
    )
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export function fetchHealth(): Promise<HealthResponse> {
  return request<HealthResponse>('/api/health')
}

export function searchPapers(p: SearchParams, signal?: AbortSignal): Promise<SearchResponse> {
  const qs = new URLSearchParams({
    q: p.q,
    sort: p.sort,
    openAccessOnly: String(p.openAccessOnly),
    page: String(p.page),
    limit: String(p.limit),
  })
  if (p.yearFrom !== undefined) qs.set('yearFrom', String(p.yearFrom))
  return request<SearchResponse>(`/api/papers/search?${qs.toString()}`, { signal })
}

/** Save a search result. The backend is idempotent: saving twice returns the existing item. */
export async function savePaper(paper: Paper): Promise<LibraryItem> {
  return request<LibraryItem>('/api/library', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    // Send only what the backend stores (not search-only fields such as inLibrary).
    body: JSON.stringify({
      externalId: paper.externalId,
      title: paper.title,
      authors: paper.authors,
      year: paper.year,
      abstractText: paper.abstractText,
      url: paper.url,
      pdfUrl: paper.pdfUrl,
      openAccess: paper.openAccess,
    }),
  })
}

export interface LibraryQuery {
  q: string
  source: 'ALL' | 'SAVED' | 'UPLOAD'
  sort: 'recent' | 'title' | 'year'
}

export async function listLibrary(p: LibraryQuery, signal?: AbortSignal): Promise<LibraryItem[]> {
  const qs = new URLSearchParams({ q: p.q, source: p.source, sort: p.sort })
  const r = await request<{ items: LibraryItem[] }>(`/api/library?${qs.toString()}`, { signal })
  return r.items
}

export function removeFromLibrary(id: number): Promise<void> {
  return request<void>(`/api/library/${id}`, { method: 'DELETE' })
}

export interface UploadResult {
  item: LibraryItem
  duplicate: boolean
  missingFields: string[]
  pageCount: number
}

/** Upload a PDF; the backend extracts text and metadata and stores it in the library. */
export function uploadPdf(file: File): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  return request<UploadResult>('/api/uploads', { method: 'POST', body: form })
}

export interface PaperEdits {
  title: string
  authors: string[]
  year: number | null
  abstractText: string | null
}

export function updatePaper(id: number, edits: PaperEdits): Promise<LibraryItem> {
  return request<LibraryItem>(`/api/library/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(edits),
  })
}

export interface Summary {
  paperId: number
  summary: string | null
  /** FULL_TEXT, FULL_TEXT_TRUNCATED or ABSTRACT_ONLY: what the summary was written from. */
  basis: string | null
  cached: boolean
}

export function getSummary(id: number): Promise<Summary> {
  return request<Summary>(`/api/library/${id}/summary`)
}

export function generateSummary(id: number, refresh = false): Promise<Summary> {
  return request<Summary>(`/api/library/${id}/summary?refresh=${refresh}`, { method: 'POST' })
}

export interface Answer {
  answer: string
  basis: 'FULL_TEXT' | 'ABSTRACT_ONLY'
  sources: { n: number; excerpt: string }[]
}

export function askPaper(id: number, question: string): Promise<Answer> {
  return request<Answer>(`/api/library/${id}/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  })
}
