import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError, getPaper, type LibraryItem } from '../api/client'
import AskPanel from '../components/AskPanel'
import SummaryPanel from '../components/SummaryPanel'

export default function PaperDetailPage() {
  const { id } = useParams()
  const paperId = Number(id)
  const [paper, setPaper] = useState<LibraryItem | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [tick, setTick] = useState(0)
  const reload = useCallback(() => setTick(t => t + 1), [])

  useEffect(() => {
    if (!Number.isInteger(paperId) || paperId <= 0) {
      setError(new ApiError('NOT_IN_LIBRARY', 'That paper is not in your library.', false, 404))
      return
    }
    const ctrl = new AbortController()
    getPaper(paperId)
      .then(p => { if (!ctrl.signal.aborted) { setPaper(p); setError(null) } })
      .catch((e: unknown) => {
        if (ctrl.signal.aborted) return
        setError(e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Something went wrong.', true, 0))
      })
    return () => ctrl.abort()
  }, [paperId, tick])

  const back = <Link to="/library" style={{ fontSize: 14, fontWeight: 500 }}>← My Library</Link>

  if (error) {
    return (
      <div className="stack">
        {back}
        <div className="card" role="alert" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 12, padding: 26, borderColor: '#F3C7C2' }}>
          <h1 style={{ color: 'var(--error)', fontSize: 16, fontWeight: 600 }}>This paper couldn’t be opened</h1>
          <p style={{ color: 'var(--text-2)' }}>{error.message}</p>
          {error.retryable && <button className="btn" onClick={reload}>Retry</button>}
        </div>
      </div>
    )
  }

  if (!paper) {
    return (
      <div className="stack" aria-busy="true" aria-label="Loading paper">
        {back}
        <div className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="skel" style={{ height: 26, width: '70%' }} />
          <div className="skel" style={{ height: 12, width: '40%' }} />
          <div className="skel" style={{ height: 90, width: '100%' }} />
        </div>
      </div>
    )
  }

  return (
    <div className="stack">
      {back}
      <section className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '22px 24px' }}>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <span className="badge badge-neutral">{paper.source === 'UPLOAD' ? 'Uploaded PDF' : 'Saved from search'}</span>
          {paper.textStatus === 'FULL_TEXT'
            ? <span className="badge badge-ok">Full text</span>
            : <span className="badge badge-warn">Abstract only</span>}
          {paper.hasSummary && <span className="badge badge-ok">Summary ready</span>}
        </div>
        <h1 className="serif" style={{ fontSize: 28, fontWeight: 600, lineHeight: 1.25 }}>{paper.title}</h1>
        <p style={{ fontSize: 14, color: 'var(--text-2)', lineHeight: 1.5 }}>
          {paper.authors.length > 0 ? paper.authors.join(', ') : 'Unknown authors'}
        </p>
        <p style={{ fontSize: 13, color: 'var(--muted)' }}>
          {paper.year ?? 'Year unknown'} · Added {new Date(paper.createdAt).toLocaleDateString()}{paper.originalFilename ? ` · ${paper.originalFilename}` : ''}
        </p>
        <h2 style={{ fontSize: 14, fontWeight: 600, marginTop: 6 }}>Abstract</h2>
        {paper.abstractText
          ? <p style={{ fontSize: 14.5, lineHeight: 1.65, color: 'var(--text-2)' }}>{paper.abstractText}</p>
          : <p style={{ fontSize: 14.5, color: 'var(--muted)', fontStyle: 'italic' }}>No abstract stored for this paper. You can add one by re-uploading or editing it on the Upload page.</p>}
        {paper.url && (
          <div><a className="btn btn-sm" href={paper.url} target="_blank" rel="noopener noreferrer">Original paper</a></div>
        )}
      </section>

      <section className="card" style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: '20px 24px' }}>
        <h2 style={{ fontSize: 17, fontWeight: 600 }}>Summary</h2>
        <SummaryPanel embedded paperId={paper.id} hasSummary={paper.hasSummary} onGenerated={reload} />
      </section>

      <section className="card" style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: '20px 24px' }}>
        <h2 style={{ fontSize: 17, fontWeight: 600 }}>Ask about this paper</h2>
        <AskPanel embedded paperId={paper.id} />
      </section>
    </div>
  )
}
