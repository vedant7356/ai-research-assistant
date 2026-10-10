import { useState, type FormEvent } from 'react'
import { ApiError, askPaper, type Answer } from '../api/client'
import Markdown from './Markdown'

interface Turn {
  question: string
  answer?: Answer
  error?: string
}

export default function AskPanel({ paperId, embedded = false }: { paperId: number; embedded?: boolean }) {
  const [open, setOpen] = useState(embedded)
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState(false)
  const [turns, setTurns] = useState<Turn[]>([])

  async function submit(e: FormEvent) {
    e.preventDefault()
    const q = question.trim()
    if (!q || busy) return
    setBusy(true)
    setQuestion('')
    try {
      const answer = await askPaper(paperId, q)
      setTurns(t => [...t, { question: q, answer }])
    } catch (err) {
      setTurns(t => [...t, { question: q, error: err instanceof ApiError ? err.message : 'Could not get an answer.' }])
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {!embedded && (
        <button className="btn btn-sm" onClick={() => setOpen(o => !o)} aria-expanded={open}>
          {open ? 'Hide Q&A' : 'Ask a question'}
        </button>
      )}
      {open && (
        <div style={embedded
          ? { display: 'flex', flexDirection: 'column', gap: 12 }
          : { flexBasis: '100%', display: 'flex', flexDirection: 'column', gap: 12, padding: 14, marginTop: 4, border: '1px solid var(--border)', borderRadius: 10, background: 'var(--surface)' }}>
          {turns.map((t, i) => (
            <div key={i} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <p style={{ fontWeight: 600, fontSize: 14 }}>{t.question}</p>
              {t.error && <p role="alert" style={{ color: 'var(--error)', fontSize: 14 }}>{t.error}</p>}
              {t.answer && (
                <>
                  <Markdown text={t.answer.answer} />
                  {t.answer.sources.length > 0 && (
                    <details style={{ fontSize: 13, color: 'var(--text-2)' }}>
                      <summary style={{ cursor: 'pointer' }}>Passages used ({t.answer.sources.length})</summary>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, paddingTop: 6 }}>
                        {t.answer.sources.map(s => <p key={s.n}><strong>[{s.n}]</strong> {s.excerpt}</p>)}
                      </div>
                    </details>
                  )}
                  <p style={{ fontSize: 12, color: 'var(--muted)' }}>
                    {t.answer.basis === 'ABSTRACT_ONLY' ? 'Answered from the abstract only.' : 'Answered from the paper text.'} AI-generated: check against the paper.
                  </p>
                </>
              )}
            </div>
          ))}
          {busy && <p style={{ color: 'var(--muted)', fontSize: 14 }} aria-live="polite">Searching the paper and writing an answer…</p>}
          <form onSubmit={e => void submit(e)} style={{ display: 'flex', gap: 8 }}>
            <label className="sr" htmlFor={`ask-${paperId}`}>Question about this paper</label>
            <input id={`ask-${paperId}`} className="input" style={{ flex: 1, minHeight: 38 }} maxLength={1000}
              placeholder="Ask about this paper…" value={question} onChange={e => setQuestion(e.target.value)} />
            <button className="btn btn-primary btn-sm" type="submit" disabled={busy || question.trim() === ''}>Ask</button>
          </form>
        </div>
      )}
    </>
  )
}
