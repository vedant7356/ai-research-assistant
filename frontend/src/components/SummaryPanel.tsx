import { useState } from 'react'
import { ApiError, generateSummary, getSummary, type Summary } from '../api/client'
import Markdown from './Markdown'

const BASIS_NOTE: Record<string, string> = {
  FULL_TEXT: 'Written from the full paper text.',
  FULL_TEXT_TRUNCATED: 'Written from the first part of a long paper (the text was cut to fit the AI model).',
  ABSTRACT_ONLY: 'Written from the abstract only: the full text was not available for this paper.',
}

interface Props {
  paperId: number
  hasSummary: boolean
  onGenerated: () => void
}

export default function SummaryPanel({ paperId, hasSummary, onGenerated }: Props) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [summary, setSummary] = useState<Summary | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function run(refresh: boolean) {
    setBusy(true)
    setError(null)
    try {
      const s = hasSummary && !refresh && summary === null ? await getSummary(paperId) : await generateSummary(paperId, refresh)
      setSummary(s)
      onGenerated()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not create the summary.')
    } finally {
      setBusy(false)
    }
  }

  function toggle() {
    if (open) { setOpen(false); return }
    setOpen(true)
    if (summary === null && !busy) void run(false)
  }

  return (
    <>
      <button className="btn btn-sm" onClick={toggle} aria-expanded={open}>
        {open ? 'Hide summary' : hasSummary ? 'View summary' : 'Summarize'}
      </button>
      {open && (
        <div style={{ flexBasis: '100%', display: 'flex', flexDirection: 'column', gap: 10, padding: 14, marginTop: 4, border: '1px solid var(--border)', borderRadius: 10, background: 'var(--surface)' }} aria-live="polite">
          {busy && <p style={{ color: 'var(--muted)', fontSize: 14 }}>Reading the paper and writing a summary… this can take up to a minute.</p>}
          {error && (
            <div role="alert" style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-start' }}>
              <p style={{ color: 'var(--error)', fontSize: 14 }}>{error}</p>
              <button className="btn btn-sm" onClick={() => void run(true)}>Try again</button>
            </div>
          )}
          {!busy && summary?.summary && (
            <>
              <Markdown text={summary.summary} />
              <p style={{ fontSize: 12, color: 'var(--muted)' }}>
                {BASIS_NOTE[summary.basis ?? ''] ?? ''} AI-generated: check important details against the paper.
              </p>
              <div><button className="btn btn-sm btn-ghost" onClick={() => void run(true)}>Regenerate</button></div>
            </>
          )}
        </div>
      )}
    </>
  )
}
