import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, listLibrary, type LibraryItem } from '../api/client'

function authorsShort(authors: string[]): string {
  if (authors.length === 0) return 'Unknown authors'
  if (authors.length <= 2) return authors.join(', ')
  return `${authors.slice(0, 2).join(', ')} +${authors.length - 2}`
}

export default function HomePage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [invalid, setInvalid] = useState(false)
  const [items, setItems] = useState<LibraryItem[] | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    const ctrl = new AbortController()
    listLibrary({ q: '', source: 'ALL', sort: 'recent' }, ctrl.signal)
      .then(r => { setItems(r); setError(null) })
      .catch((e: unknown) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setError(e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Something went wrong.', true, 0))
      })
    return () => ctrl.abort()
  }, [tick])

  function onSearch(e: FormEvent) {
    e.preventDefault()
    const q = query.trim()
    if (!q) { setInvalid(true); return }
    navigate(`/discover?q=${encodeURIComponent(q)}`)
  }

  const recent = items?.slice(0, 3) ?? []
  const count = items?.length ?? 0
  const countLabel = items === null
    ? 'Checking your library…'
    : count === 0 ? 'Your library is empty.' : `${count} ${count === 1 ? 'paper' : 'papers'} saved or uploaded.`

  const actionStyle = { flexDirection: 'column', alignItems: 'flex-start', gap: 8, padding: 18, minHeight: 108, whiteSpace: 'normal', textAlign: 'left', borderRadius: 12 } as const
  const actionTitle = { display: 'flex', alignItems: 'center', gap: 10, fontSize: 15, fontWeight: 600, color: 'var(--primary)' } as const
  const actionText = { fontWeight: 400, color: 'var(--muted)', lineHeight: 1.45 } as const

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <h1 className="serif h1" style={{ fontSize: 42, fontWeight: 500, lineHeight: 1.1 }}>Your research workspace</h1>
        <p style={{ color: 'var(--muted)', fontSize: 16 }}>Discover papers, organize your reading, and understand research faster.</p>
      </div>

      <form onSubmit={onSearch} role="search" className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: 20, boxShadow: '0 8px 24px rgba(27,31,70,.06)' }}>
        <label htmlFor="home-q" style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-2)' }}>Search research papers</label>
        <div className="stack-sm" style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <div style={{ position: 'relative', flex: 1 }}>
            <svg className="ic" viewBox="0 0 24 24" aria-hidden="true" style={{ position: 'absolute', left: 16, top: 15, color: '#6B7183' }}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
            <input id="home-q" className="input" type="search" value={query}
              onChange={e => { setQuery(e.target.value); setInvalid(false) }}
              placeholder="Search papers, topics, authors…"
              style={{ minHeight: 52, paddingLeft: 44, fontSize: 16 }} />
          </div>
          <button className="btn btn-primary" type="submit" style={{ minHeight: 52, padding: '0 22px', fontSize: 15 }}>Search</button>
        </div>
        {invalid && <p role="alert" style={{ color: 'var(--error)', fontSize: 13 }}>Enter a topic, title, author or keyword to search.</p>}
      </form>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(240px, 100%), 1fr))', gap: 14 }}>
        <Link to="/discover" className="card btn" style={actionStyle}>
          <span style={actionTitle}><svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>Discover Papers</span>
          <span style={actionText}>Search published research by topic, title or author.</span>
        </Link>
        <Link to="/upload" className="card btn" style={actionStyle}>
          <span style={actionTitle}><svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 15V4m0 0-4 4m4-4 4 4" /><path d="M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4" /></svg>Upload a PDF</span>
          <span style={actionText}>Add a paper you already have and work with its full text.</span>
        </Link>
        <Link to="/library" className="card btn" style={actionStyle}>
          <span style={actionTitle}><svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z" /></svg>Open My Library</span>
          <span style={actionText}>{countLabel}</span>
        </Link>
      </div>

      <section style={{ display: 'flex', flexDirection: 'column', gap: 14 }} aria-labelledby="recent-h">
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12 }}>
          <h2 id="recent-h" style={{ fontSize: 17, fontWeight: 600 }}>Recent research</h2>
          {recent.length > 0 && <Link to="/library" style={{ fontSize: 13, fontWeight: 500 }}>View library</Link>}
        </div>

        {error && (
          <div className="card" role="alert" style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '16px 18px', borderColor: '#F3C7C2', background: '#FEF5F4' }}>
            <span style={{ flex: 1, color: '#7A1A12' }}>We couldn’t load your library. Your saved papers are safe on the server.</span>
            <button className="btn btn-sm" onClick={() => { setError(null); setTick(t => t + 1) }}>Retry</button>
          </div>
        )}

        {!error && items === null && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(300px, 100%), 1fr))', gap: 14 }} aria-busy="true">
            {[0, 1, 2].map(i => (
              <div key={i} className="card" style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div className="skel" style={{ height: 18, width: '80%' }} />
                <div className="skel" style={{ height: 12, width: '50%' }} />
                <div className="skel" style={{ height: 12 }} />
              </div>
            ))}
          </div>
        )}

        {!error && items !== null && recent.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(300px, 100%), 1fr))', gap: 14 }}>
            {recent.map(p => (
              <article key={p.id} className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: 18 }}>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  <span className={`badge ${p.source === 'UPLOAD' ? 'badge-violet' : 'badge-neutral'}`}>{p.source === 'UPLOAD' ? 'Uploaded PDF' : 'Saved from search'}</span>
                  {p.hasSummary && <span className="badge badge-violet">Summary ready</span>}
                </div>
                <h3 className="serif" style={{ fontSize: 19, fontWeight: 600, lineHeight: 1.3 }}>{p.title}</h3>
                <p style={{ fontSize: 13, color: 'var(--muted)' }}>{authorsShort(p.authors)} · {p.year ?? 'Year unknown'}</p>
                <p style={{ fontSize: 14, lineHeight: 1.55, color: 'var(--text-2)', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                  {p.abstractText ?? 'No abstract stored for this paper.'}
                </p>
                <div style={{ display: 'flex', gap: 8, marginTop: 'auto', paddingTop: 4 }}>
                  <Link className="btn btn-sm" to={`/library/${p.id}`}>Open</Link>
                  <Link className="btn btn-sm btn-ghost" to={`/library/${p.id}#ask`}>Ask questions</Link>
                </div>
              </article>
            ))}
          </div>
        )}

        {!error && items !== null && recent.length === 0 && (
          <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 18, padding: 28 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <h3 className="serif" style={{ fontSize: 22, fontWeight: 500 }}>Nothing here yet: start with one paper.</h3>
              <p style={{ color: 'var(--muted)' }}>Papers you save or upload appear here so you can pick up where you left off.</p>
            </div>
            <ol style={{ margin: 0, padding: 0, listStyle: 'none', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(220px, 100%), 1fr))', gap: 12 }}>
              {['Search for a paper by topic, title or author.', 'Or upload a PDF you already have.', 'Save it to your library, then summarize it or ask questions.'].map((t, i) => (
                <li key={t} style={{ display: 'flex', gap: 12, padding: 14, borderRadius: 10, background: 'var(--bg)' }}>
                  <span style={{ color: 'var(--accent)', fontWeight: 500 }}>{`0${i + 1}`}</span>
                  <span style={{ lineHeight: 1.45 }}>{t}</span>
                </li>
              ))}
            </ol>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <Link className="btn btn-primary" to="/discover">Discover Papers</Link>
              <Link className="btn" to="/upload">Upload PDF</Link>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
