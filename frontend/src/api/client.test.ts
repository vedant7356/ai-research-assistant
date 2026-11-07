import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError, askPaper, fetchHealth, generateSummary, getSummary, listLibrary, removeFromLibrary,
  savePaper, searchPapers, updatePaper, uploadPdf, type Paper,
} from './client'

function mockFetch(impl: (url: string, init?: RequestInit) => Promise<Response>) {
  const f = vi.fn(impl)
  vi.stubGlobal('fetch', f)
  return f
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const paper: Paper = {
  externalId: 'W1', title: 'T', authors: ['A'], year: 2020, abstractText: 'abs', url: 'https://x', pdfUrl: null,
  openAccess: true, inLibrary: true,
}

afterEach(() => vi.unstubAllGlobals())

describe('request handling', () => {
  it('returns the parsed JSON body on success', async () => {
    mockFetch(async () => json({ status: 'UP', database: 'UP', time: 'now' }))
    await expect(fetchHealth()).resolves.toEqual({ status: 'UP', database: 'UP', time: 'now' })
  })

  it('turns the backend error envelope into an ApiError', async () => {
    mockFetch(async () => json({ error: { code: 'RATE_LIMITED', message: 'Slow down.', retryable: true } }, 429))
    const err = await fetchHealth().catch(e => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ code: 'RATE_LIMITED', message: 'Slow down.', retryable: true, status: 429 })
  })

  it('handles a non-JSON 5xx (dev proxy while the backend is down) as retryable', async () => {
    mockFetch(async () => new Response('Bad gateway', { status: 502 }))
    const err = await fetchHealth().catch(e => e)
    expect(err).toMatchObject({ code: 'HTTP_502', message: "The server isn't responding.", retryable: true, status: 502 })
  })

  it('handles a non-JSON 4xx as not retryable', async () => {
    mockFetch(async () => new Response('nope', { status: 404 }))
    const err = await fetchHealth().catch(e => e)
    expect(err).toMatchObject({ code: 'HTTP_404', message: 'Request failed (404).', retryable: false })
  })

  it('reports a network failure as a retryable NETWORK error', async () => {
    mockFetch(async () => { throw new TypeError('Failed to fetch') })
    const err = await fetchHealth().catch(e => e)
    expect(err).toMatchObject({ code: 'NETWORK', retryable: true, status: 0 })
    expect(err.message).toContain('backend is running')
  })

  it('lets an AbortError through unchanged so callers can ignore it', async () => {
    mockFetch(async () => { throw new DOMException('aborted', 'AbortError') })
    await expect(fetchHealth()).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('returns undefined for 204 responses', async () => {
    mockFetch(async () => new Response(null, { status: 204 }))
    await expect(removeFromLibrary(3)).resolves.toBeUndefined()
  })
})

describe('endpoints', () => {
  it('searchPapers sends every parameter and omits yearFrom when not set', async () => {
    const f = mockFetch(async () => json({ query: 'rag', total: 0, page: 1, limit: 10, items: [] }))
    await searchPapers({ q: 'rag & co', sort: 'relevance', openAccessOnly: false, page: 1, limit: 10 })
    const url = new URL(String(f.mock.calls[0][0]), 'http://localhost')
    expect(url.pathname).toBe('/api/papers/search')
    expect(url.searchParams.get('q')).toBe('rag & co')
    expect(url.searchParams.get('sort')).toBe('relevance')
    expect(url.searchParams.get('openAccessOnly')).toBe('false')
    expect(url.searchParams.get('page')).toBe('1')
    expect(url.searchParams.get('limit')).toBe('10')
    expect(url.searchParams.has('yearFrom')).toBe(false)
  })

  it('searchPapers includes yearFrom and passes the abort signal', async () => {
    const f = mockFetch(async () => json({ query: 'x', total: 0, page: 1, limit: 10, items: [] }))
    const ctrl = new AbortController()
    await searchPapers({ q: 'x', yearFrom: 2015, sort: 'newest', openAccessOnly: true, page: 2, limit: 5 }, ctrl.signal)
    const url = new URL(String(f.mock.calls[0][0]), 'http://localhost')
    expect(url.searchParams.get('yearFrom')).toBe('2015')
    expect(url.searchParams.get('openAccessOnly')).toBe('true')
    expect(f.mock.calls[0][1]?.signal).toBe(ctrl.signal)
  })

  it('savePaper posts only the fields the backend stores', async () => {
    const f = mockFetch(async () => json({ id: 1 }))
    await savePaper(paper)
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/library')
    expect(init?.method).toBe('POST')
    const body = JSON.parse(String(init?.body))
    expect(body).toEqual({
      externalId: 'W1', title: 'T', authors: ['A'], year: 2020, abstractText: 'abs', url: 'https://x', pdfUrl: null, openAccess: true,
    })
    expect(body).not.toHaveProperty('inLibrary')
  })

  it('listLibrary unwraps the items and encodes the query', async () => {
    const f = mockFetch(async () => json({ items: [{ id: 1 }] }))
    const items = await listLibrary({ q: 'a b&c', source: 'SAVED', sort: 'title' })
    expect(items).toEqual([{ id: 1 }])
    const url = new URL(String(f.mock.calls[0][0]), 'http://localhost')
    expect(url.searchParams.get('q')).toBe('a b&c')
    expect(url.searchParams.get('source')).toBe('SAVED')
    expect(url.searchParams.get('sort')).toBe('title')
  })

  it('removeFromLibrary sends DELETE to the paper url', async () => {
    const f = mockFetch(async () => new Response(null, { status: 204 }))
    await removeFromLibrary(7)
    expect(f.mock.calls[0][0]).toBe('/api/library/7')
    expect(f.mock.calls[0][1]?.method).toBe('DELETE')
  })

  it('uploadPdf sends the file as multipart form data', async () => {
    const f = mockFetch(async () => json({ item: { id: 1 }, duplicate: false, missingFields: [], pageCount: 1 }))
    const file = new File(['%PDF-1.4'], 'paper.pdf', { type: 'application/pdf' })
    await uploadPdf(file)
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/uploads')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBeInstanceOf(FormData)
    expect((init?.body as FormData).get('file')).toBeInstanceOf(File)
  })

  it('updatePaper sends a PATCH with the edits as JSON', async () => {
    const f = mockFetch(async () => json({ id: 4 }))
    await updatePaper(4, { title: 'New', authors: ['A'], year: null, abstractText: null })
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/library/4')
    expect(init?.method).toBe('PATCH')
    expect(JSON.parse(String(init?.body))).toEqual({ title: 'New', authors: ['A'], year: null, abstractText: null })
  })

  it('getSummary and generateSummary use the right urls', async () => {
    const f = mockFetch(async () => json({ paperId: 1, summary: null, basis: null, cached: false }))
    await getSummary(1)
    await generateSummary(1)
    await generateSummary(1, true)
    expect(f.mock.calls[0][0]).toBe('/api/library/1/summary')
    expect(f.mock.calls[1][0]).toBe('/api/library/1/summary?refresh=false')
    expect(f.mock.calls[1][1]?.method).toBe('POST')
    expect(f.mock.calls[2][0]).toBe('/api/library/1/summary?refresh=true')
  })

  it('askPaper posts the question as JSON', async () => {
    const f = mockFetch(async () => json({ answer: 'a', basis: 'FULL_TEXT', sources: [] }))
    await askPaper(9, 'Why?')
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/api/library/9/ask')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ question: 'Why?' })
  })
})
