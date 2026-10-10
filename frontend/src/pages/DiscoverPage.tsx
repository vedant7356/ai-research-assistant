import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { ApiError, searchPapers, type Paper } from '../api/client'
import PaperCard from '../components/PaperCard'

const PAGE_SIZE = 10
const EXAMPLES = ['transformer attention', 'federated learning', 'retrieval augmented generation', 'on-device machine learning']

interface ResultState {
  status: 'idle' | 'loading' | 'error' | 'done'
  items: Paper[]
  total: number
  page: number
  error: ApiError | null
  loadingMore: boolean
  loadMoreError: string | null
}

const INITIAL: ResultState = { status: 'idle', items: [], total: 0, page: 1, error: null, loadingMore: false, loadMoreError: null }

export default function DiscoverPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const year = params.get('year') ?? 'any'
  const sort = params.get('sort') === 'newest' ? 'newest' : 'relevance'
  const openOnly = params.get('open') === '1'
  const filtersActive = year !== 'any' || sort !== 'relevance' || openOnly

  const [input, setInput] = useState(q)
  const [invalid, setInvalid] = useState(false)
  const [state, setState] = useState<ResultState>(INITIAL)
  const [retryTick, setRetryTick] = useState(0)

  const yearFrom = year === 'any' ? undefined : Number(year)

  // Keep the text box in sync when the URL changes (example chips, back button).
  useEffect(() => { setInput(q) }, [q])

  // Fetch page 1 whenever the query or a filter changes (or Retry is pressed).
  useEffect(() => {
    if (!q.trim()) { setState(INITIAL); return }
    const ctrl = new AbortController()
    setState({ ...INITIAL, status: 'loading' })
    searchPapers({ q, yearFrom, sort, openAccessOnly: openOnly, page: 1, limit: PAGE_SIZE }, ctrl.signal)
      .then(r => setState({ ...INITIAL, status: 'done', items: r.items, total: r.total }))
      .catch((e: unknown) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setState({ ...INITIAL, status: 'error', error: e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Something went wrong.', true, 0) })
      })
    return () => ctrl.abort()
  }, [q, yearFrom, sort, openOnly, retryTick])

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, v)
    }
    setParams(next)
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmed = input.trim()
    if (!trimmed) { setInvalid(true); return }
    setInvalid(false)
    if (trimmed === q) setRetryTick(t => t + 1)
    else update({ q: trimmed })
  }

  async function loadMore() {
    setState(s => ({ ...s, loadingMore: true, loadMoreError: null }))
    try {
      const r = await searchPapers({ q, yearFrom, sort, openAccessOnly: openOnly, page: state.page + 1, limit: PAGE_SIZE })
      setState(s => ({ ...s, items: [...s.items, ...r.items], page: s.page + 1, loadingMore: false }))
    } catch (e) {
      setState(s => ({ ...s, loadingMore: false, loadMoreError: e instanceof ApiError ? e.message : 'Could not load more results.' }))
    }
  }

  return (
    <div className="stack">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1 className="serif h1" style={{ fontSize: 36, fontWeight: 500 }}>Discover papers</h1>
        <p style={{ color: 'var(--muted)' }}>Search published research. Results come from OpenAlex through the ResearchMate backend.</p>
      </div>

      <form onSubmit={onSubmit} role="search" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <label htmlFor="disc-q" className="sr">Search research papers</label>
        <div className="stack-sm" style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <div style={{ position: 'relative', flex: 1 }}>
            <svg className="ic" viewBox="0 0 24 24" aria-hidden="true" style={{ position: 'absolute', left: 16, top: 15, color: '#6B7183' }}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
            <input id="disc-q" className="input" type="text" value={input} onChange={e => setInput(e.target.value)}
              placeholder="Search papers, topics, authors…"
              style={{ minHeight: 50, paddingLeft: 44, paddingRight: 44, fontSize: 16 }} />
            {input && (
              <button type="button" className="btn btn-ghost btn-icon" aria-label="Clear search" onClick={() => { setInput(''); update({ q: null }) }}
                style={{ position: 'absolute', right: 7, top: 7 }}>
                <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
              </button>
            )}
          </div>
          <button className="btn btn-primary" type="submit" style={{ minHeight: 50, padding: '0 22px' }}>Search</button>
        </div>
        {invalid && <p role="alert" style={{ color: 'var(--error)', fontSize: 13 }}>Enter a topic, title, author or keyword to search.</p>}
      </form>

      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '12px 18px', padding: '12px 14px', border: '1px solid var(--border)', borderRadius: 10, background: 'var(--surface)', fontSize: 14 }}>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-2)' }}>Published
          <select className="input" value={year} onChange={e => update({ year: e.target.value === 'any' ? null : e.target.value })}>
            <option value="any">Any year</option>
            <option value="2020">2020 or later</option>
            <option value="2015">2015 or later</option>
            <option value="2010">2010 or later</option>
          </select>
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-2)' }}>Sort
          <select className="input" value={sort} onChange={e => update({ sort: e.target.value === 'newest' ? 'newest' : null })}>
            <option value="relevance">Relevance</option>
            <option value="newest">Newest first</option>
          </select>
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-2)', minHeight: 38, cursor: 'pointer' }}>
          <input type="checkbox" checked={openOnly} onChange={e => update({ open: e.target.checked ? '1' : null })} style={{ width: 18, height: 18, accentColor: 'var(--primary)' }} />
          Open access only
        </label>
      </div>

      {state.status === 'idle' && (
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 14, padding: 26 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600 }}>Start with a topic, a paper title, or an author.</h2>
          <p style={{ color: 'var(--muted)', lineHeight: 1.55 }}>Short, specific queries work best. Try one of these:</p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {EXAMPLES.map(ex => <button key={ex} className="chip" onClick={() => update({ q: ex })}>{ex}</button>)}
          </div>
        </div>
      )}

      {state.status === 'loading' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }} aria-busy="true" aria-label="Loading results">
          <p style={{ color: 'var(--muted)', fontSize: 14 }}>Searching for “{q}”…</p>
          {[0, 1, 2].map(i => (
            <div key={i} className="card" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div className="skel" style={{ height: 20, width: '72%' }} />
              <div className="skel" style={{ height: 12, width: '44%' }} />
              <div className="skel" style={{ height: 12 }} />
              <div className="skel" style={{ height: 12, width: '88%' }} />
            </div>
          ))}
        </div>
      )}

      {state.status === 'error' && state.error && (
        <div className="card" role="alert" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 12, padding: 26, borderColor: '#F3C7C2' }}>
          <h2 style={{ display: 'flex', alignItems: 'center', gap: 10, color: 'var(--error)', fontSize: 16, fontWeight: 600 }}>
            <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v6M12 16.5v.5" /></svg>
            Search is unavailable right now
          </h2>
          <p style={{ color: 'var(--text-2)', lineHeight: 1.55 }}>{state.error.message} Your query is kept.</p>
          {state.error.retryable && <button className="btn" onClick={() => setRetryTick(t => t + 1)}>Retry search</button>}
        </div>
      )}

      {state.status === 'done' && state.items.length === 0 && (
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: 26 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600 }}>No papers found for “{q}”</h2>
          <p style={{ color: 'var(--muted)', lineHeight: 1.55 }}>Try fewer or broader keywords, check the spelling of author names, or loosen the filters above.</p>
          {filtersActive && <div><button className="btn btn-sm" onClick={() => update({ year: null, sort: null, open: null })}>Reset filters</button></div>}
        </div>
      )}

      {state.status === 'done' && state.items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p style={{ color: 'var(--muted)', fontSize: 14 }} aria-live="polite">
            Showing {state.items.length} of {state.total.toLocaleString()} results for “{q}”
          </p>
          {state.items.map(p => <PaperCard key={p.externalId ?? p.title} paper={p} />)}
          {state.loadMoreError && <p role="alert" style={{ color: 'var(--error)', fontSize: 13 }}>{state.loadMoreError}</p>}
          {state.items.length < state.total && (
            <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 6 }}>
              <button className="btn" onClick={loadMore} disabled={state.loadingMore}>{state.loadingMore ? 'Loading…' : 'Load more results'}</button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
