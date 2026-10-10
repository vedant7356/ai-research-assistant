import { useState } from 'react'
import { ApiError, savePaper, type Paper } from '../api/client'

const ABSTRACT_PREVIEW = 320

function authorsShort(authors: string[]): string {
  if (authors.length === 0) return 'Unknown authors'
  if (authors.length <= 3) return authors.join(', ')
  return `${authors.slice(0, 3).join(', ')} +${authors.length - 3}`
}

export default function PaperCard({ paper }: { paper: Paper }) {
  const [expanded, setExpanded] = useState(false)
  const [saved, setSaved] = useState(paper.inLibrary)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const abstract = paper.abstractText
  const canExpand = abstract !== null && abstract.length > ABSTRACT_PREVIEW
  const shown = abstract === null ? null : expanded || !canExpand ? abstract : abstract.slice(0, ABSTRACT_PREVIEW).trimEnd() + '…'

  async function onSave() {
    setSaving(true)
    setSaveError(null)
    try {
      await savePaper(paper)
      setSaved(true)
    } catch (e) {
      setSaveError(e instanceof ApiError ? e.message : 'Could not save this paper.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <article className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: '20px 22px' }}>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {paper.pdfUrl
          ? <span className="badge badge-ok">Full text available</span>
          : <span className="badge badge-warn">Abstract only</span>}
        {saved && <span className="badge badge-ok">In your library</span>}
      </div>
      <h3 className="serif" style={{ fontSize: 21, fontWeight: 600, lineHeight: 1.3 }}>{paper.title}</h3>
      <p style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 1.5 }}>
        {authorsShort(paper.authors)} · {paper.year ?? 'Year unknown'}
      </p>
      {shown !== null
        ? <p style={{ fontSize: 14.5, lineHeight: 1.6, color: 'var(--text-2)' }}>{shown}</p>
        : <p style={{ fontSize: 14.5, color: 'var(--muted)', fontStyle: 'italic' }}>No abstract available for this paper.</p>}
      {canExpand && (
        <div>
          <button className="link-btn" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
            {expanded ? 'Show less' : 'Show full abstract'}
          </button>
        </div>
      )}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, paddingTop: 4 }}>
        {saved
          ? (
            <button className="btn btn-sm btn-saved" disabled>
              <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 5 5 9-10" /></svg>
              Saved
            </button>
          )
          : (
            <button className="btn btn-sm btn-primary" onClick={onSave} disabled={saving}>
              <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z" /></svg>
              {saving ? 'Saving…' : 'Save to Library'}
            </button>
          )}
        {paper.url && (
          <a className="btn btn-sm btn-ghost" href={paper.url} target="_blank" rel="noopener noreferrer">
            Original paper
            <svg className="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" /></svg>
          </a>
        )}
      </div>
      {saveError && <p role="alert" style={{ color: 'var(--error)', fontSize: 13 }}>{saveError}</p>}
    </article>
  )
}
