import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import AskPanel from '../components/AskPanel'
import SummaryPanel from '../components/SummaryPanel'
import { ApiError, listLibrary, removeFromLibrary, type LibraryItem } from '../api/client'

type Source = 'ALL' | 'SAVED' | 'UPLOAD'
type Sort = 'recent' | 'title' | 'year'

function authorsShort(authors: string[]): string {
  if (authors.length === 0) return 'Unknown authors'
  if (authors.length <= 3) return authors.join(', ')
  return `${authors.slice(0, 3).join(', ')} +${authors.length - 3}`
}

export default function LibraryPage() {
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [source, setSource] = useState<Source>('ALL')
  const [sort, setSort] = useState<Sort>('recent')
  const [items, setItems] = useState<LibraryItem[] | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [tick, setTick] = useState(0)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [removeError, setRemoveError] = useState<string | null>(null)

  // Wait briefly after typing before asking the server.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q), 250)
    return () => clearTimeout(t)
  }, [q])

  useEffect(() => {
    const ctrl = new AbortController()
    listLibrary({ q: debouncedQ, source, sort }, ctrl.signal)
      .then(r => { setItems(r); setError(null) })
      .catch((e: unknown) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setError(e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Something went wrong.', true, 0))
      })
    return () => ctrl.abort()
  }, [debouncedQ, source, sort, tick])

  async function onRemove(id: number) {
    setRemoveError(null)
    try {
      await removeFromLibrary(id)
      setConfirmId(null)
      setTick(t => t + 1)
    } catch (e) {
      setRemoveError(e instanceof ApiError ? e.message : 'Could not remove this paper.')
    }
  }

  const filtering = debouncedQ.trim() !== '' || source !== 'ALL'

  return (
    <div className="stack">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1 className="serif h1" style={{ fontSize: 36, fontWeight: 500 }}>My Library</h1>
        <p style={{ color: 'var(--muted)' }}>Papers you saved are stored in the database, so they are here after a refresh or a restart.</p>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '12px 18px', padding: '12px 14px', border: '1px solid var(--border)', borderRadius: 10, background: 'var(--surface)', fontSize: 14 }}>
        <label className="sr" htmlFor="lib-q">Search your library</label>
        <input id="lib-q" className="input" style={{ flex: 1, minWidth: 200, minHeight: 38 }} placeholder="Filter by title or author…" value={q} onChange={e => setQ(e.target.value)} />
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-2)' }}>Source
          <select className="input" value={source} onChange={e => setSource(e.target.value as Source)}>
            <option value="ALL">All</option>
            <option value="SAVED">Saved from search</option>
            <option value="UPLOAD">Uploaded PDFs</option>
          </select>
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-2)' }}>Sort
          <select className="input" value={sort} onChange={e => setSort(e.target.value as Sort)}>
            <option value="recent">Recently added</option>
            <option value="title">Title</option>
            <option value="year">Year (newest)</option>
          </select>
        </label>
      </div>

      {error && (
        <div className="card" role="alert" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 12, padding: 26, borderColor: '#F3C7C2' }}>
          <h2 style={{ color: 'var(--error)', fontSize: 16, fontWeight: 600 }}>Your library couldn’t be loaded</h2>
          <p style={{ color: 'var(--text-2)' }}>{error.message}</p>
          <button className="btn" onClick={() => { setError(null); setTick(t => t + 1) }}>Retry</button>
        </div>
      )}

      {!error && items === null && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }} aria-busy="true" aria-label="Loading library">
          {[0, 1, 2].map(i => (
            <div key={i} className="card" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div className="skel" style={{ height: 20, width: '68%' }} />
              <div className="skel" style={{ height: 12, width: '40%' }} />
            </div>
          ))}
        </div>
      )}

      {!error && items !== null && items.length === 0 && (
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 26, alignItems: 'flex-start' }}>
          <h2 style={{ fontSize: 16, fontWeight: 600 }}>{filtering ? 'No papers match your filters' : 'Your library is waiting for its first paper.'}</h2>
          <p style={{ color: 'var(--muted)', lineHeight: 1.55 }}>
            {filtering ? 'Try a different search or clear the filters.' : 'Find a paper on the Discover page and press “Save to Library”.'}
          </p>
          {filtering
            ? <button className="btn btn-sm" onClick={() => { setQ(''); setSource('ALL') }}>Clear filters</button>
            : <Link className="btn btn-primary" to="/discover">Discover Papers</Link>}
        </div>
      )}

      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p style={{ color: 'var(--muted)', fontSize: 14 }} aria-live="polite">{items.length} {items.length === 1 ? 'paper' : 'papers'}</p>
          {items.map(p => (
            <article key={p.id} className="card" style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '18px 22px' }}>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <span className="badge badge-neutral">{p.source === 'UPLOAD' ? 'Uploaded PDF' : 'Saved from search'}</span>
                {p.textStatus === 'FULL_TEXT'
                  ? <span className="badge badge-ok">Full text</span>
                  : <span className="badge badge-warn">Abstract only</span>}
                {p.hasSummary && <span className="badge badge-ok">Summary ready</span>}
              </div>
              <h3 className="serif" style={{ fontSize: 20, fontWeight: 600, lineHeight: 1.3 }}><Link to={`/library/${p.id}`} style={{ color: 'inherit', textDecoration: 'none' }}>{p.title}</Link></h3>
              <p style={{ fontSize: 13, color: 'var(--muted)' }}>
                {authorsShort(p.authors)} · {p.year ?? 'Year unknown'} · Added {new Date(p.createdAt).toLocaleDateString()}{p.originalFilename ? ` · ${p.originalFilename}` : ''}
              </p>
              <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8, paddingTop: 4 }}>
                <Link className="btn btn-sm btn-ghost" to={`/library/${p.id}`}>Details</Link>
                {p.url && (
                  <a className="btn btn-sm btn-ghost" href={p.url} target="_blank" rel="noopener noreferrer">Original paper</a>
                )}
                <SummaryPanel paperId={p.id} hasSummary={p.hasSummary} onGenerated={() => setTick(t => t + 1)} />
                <AskPanel paperId={p.id} />
                {confirmId === p.id
                  ? (
                    <>
                      <span style={{ fontSize: 13, color: 'var(--text-2)' }}>Remove from library?</span>
                      <button className="btn btn-sm" onClick={() => setConfirmId(null)} autoFocus>Cancel</button>
                      <button className="btn btn-sm btn-danger" onClick={() => onRemove(p.id)}>Remove</button>
                    </>
                  )
                  : <button className="btn btn-sm btn-ghost" onClick={() => { setRemoveError(null); setConfirmId(p.id) }}>Remove</button>}
              </div>
              {confirmId === p.id && removeError && <p role="alert" style={{ color: 'var(--error)', fontSize: 13 }}>{removeError}</p>}
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
