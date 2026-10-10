import { useState, type DragEvent, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, updatePaper, uploadPdf, type UploadResult } from '../api/client'

const MAX_UPLOAD_MB = 25 // keep in sync with spring.servlet.multipart.max-file-size

function validate(file: File): string | null {
  const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) return 'That file is not a PDF. Choose a .pdf file.'
  if (file.size === 0) return 'That file is empty.'
  if (file.size > MAX_UPLOAD_MB * 1024 * 1024) return `That file is larger than ${MAX_UPLOAD_MB} MB.`
  return null
}

export default function UploadPage() {
  const [phase, setPhase] = useState<'idle' | 'uploading' | 'done' | 'error'>('idle')
  const [over, setOver] = useState(false)
  const [fileError, setFileError] = useState<string | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [lastFile, setLastFile] = useState<File | null>(null)
  const [result, setResult] = useState<UploadResult | null>(null)

  // Review/edit form state
  const [title, setTitle] = useState('')
  const [authors, setAuthors] = useState('')
  const [year, setYear] = useState('')
  const [abstractText, setAbstractText] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveMsg, setSaveMsg] = useState<{ ok: boolean; text: string } | null>(null)

  async function handleFile(file: File | undefined) {
    if (!file) return
    const problem = validate(file)
    setFileError(problem)
    if (problem) return
    setLastFile(file)
    setPhase('uploading')
    setError(null)
    setSaveMsg(null)
    try {
      const r = await uploadPdf(file)
      setResult(r)
      setTitle(r.item.title)
      setAuthors(r.item.authors.join('\n'))
      setYear(r.item.year === null ? '' : String(r.item.year))
      setAbstractText(r.item.abstractText ?? '')
      setPhase('done')
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Something went wrong.', true, 0))
      setPhase('error')
    }
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setOver(false)
    void handleFile(e.dataTransfer.files[0])
  }

  async function onSaveEdits(e: FormEvent) {
    e.preventDefault()
    if (!result) return
    const yearNum = year.trim() === '' ? null : Number(year)
    if (yearNum !== null && (!Number.isInteger(yearNum) || yearNum < 1900 || yearNum > 2100)) {
      setSaveMsg({ ok: false, text: 'Enter a year between 1900 and 2100, or leave it empty.' })
      return
    }
    if (!title.trim()) { setSaveMsg({ ok: false, text: 'The title cannot be empty.' }); return }
    setSaving(true)
    setSaveMsg(null)
    try {
      await updatePaper(result.item.id, {
        title: title.trim(),
        authors: authors.split('\n').map(a => a.trim()).filter(Boolean),
        year: yearNum,
        abstractText: abstractText.trim() === '' ? null : abstractText.trim(),
      })
      setSaveMsg({ ok: true, text: 'Changes saved to your library.' })
    } catch (err) {
      setSaveMsg({ ok: false, text: err instanceof ApiError ? err.message : 'Could not save your changes.' })
    } finally {
      setSaving(false)
    }
  }

  function reset() {
    setPhase('idle'); setResult(null); setError(null); setFileError(null); setSaveMsg(null)
  }

  const missing = new Set(result?.missingFields ?? [])

  return (
    <div className="stack">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h1 className="serif h1" style={{ fontSize: 36, fontWeight: 500 }}>Upload papers</h1>
        <p style={{ color: 'var(--muted)' }}>Upload a research paper as a PDF. The text and details are extracted on the server and added to your library.</p>
      </div>

      {(phase === 'idle' || phase === 'error') && (
        <label className={`drop${over ? ' is-over' : ''}`}
          onDragOver={e => { e.preventDefault(); setOver(true) }} onDragLeave={() => setOver(false)} onDrop={onDrop}>
          <svg className="ic" viewBox="0 0 24 24" aria-hidden="true" style={{ width: 32, height: 32, color: 'var(--accent)' }}><path d="M12 16V4m0 0-4 4m4-4 4 4M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" /></svg>
          <span style={{ fontWeight: 600 }}>Drop a PDF here, or click to choose one</span>
          <span style={{ color: 'var(--muted)', fontSize: 13 }}>PDF only, up to {MAX_UPLOAD_MB} MB. Scanned images without text can’t be read.</span>
          <input className="sr" type="file" accept="application/pdf,.pdf" onChange={e => { void handleFile(e.target.files?.[0]); e.target.value = '' }} />
        </label>
      )}
      {fileError && <p role="alert" style={{ color: 'var(--error)', fontSize: 14 }}>{fileError}</p>}

      {phase === 'uploading' && (
        <div className="card" aria-busy="true" style={{ padding: 26, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h2 style={{ fontSize: 16, fontWeight: 600 }}>Reading {lastFile?.name}…</h2>
          <p style={{ color: 'var(--muted)' }}>Uploading and extracting text. Long papers can take a few seconds.</p>
          <div className="skel" style={{ height: 8 }} />
        </div>
      )}

      {phase === 'error' && error && (
        <div className="card" role="alert" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 12, padding: 26, borderColor: '#F3C7C2' }}>
          <h2 style={{ color: 'var(--error)', fontSize: 16, fontWeight: 600 }}>This PDF couldn’t be added</h2>
          <p style={{ color: 'var(--text-2)', lineHeight: 1.55 }}>{error.message}</p>
          {error.retryable && lastFile && <button className="btn" onClick={() => void handleFile(lastFile)}>Try again</button>}
        </div>
      )}

      {phase === 'done' && result && (
        <form className="card" onSubmit={onSaveEdits} style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 26 }}>
          {result.duplicate
            ? <p className="badge badge-warn" style={{ alignSelf: 'flex-start' }}>This exact PDF is already in your library</p>
            : <p className="badge badge-ok" style={{ alignSelf: 'flex-start' }}>Added to your library · {result.pageCount} {result.pageCount === 1 ? 'page' : 'pages'}</p>}
          <p style={{ color: 'var(--muted)', fontSize: 14, lineHeight: 1.55 }}>
            Details are guessed from the PDF and may be wrong. Check them and correct anything that isn’t right.
          </p>

          <label className="field">Title
            <input className="input" value={title} onChange={e => setTitle(e.target.value)} />
            {missing.has('title') && <span className="hint-warn">No title found; the file name was used. Please check.</span>}
          </label>
          <label className="field">Authors (one per line)
            <textarea className="input" rows={3} value={authors} onChange={e => setAuthors(e.target.value)} />
            {missing.has('authors') && <span className="hint-warn">No authors found. Please add them.</span>}
          </label>
          <label className="field">Year
            <input className="input" style={{ maxWidth: 140 }} inputMode="numeric" value={year} onChange={e => setYear(e.target.value)} />
            {missing.has('year') && <span className="hint-warn">No year found. Please add it.</span>}
          </label>
          <label className="field">Abstract
            <textarea className="input" rows={7} value={abstractText} onChange={e => setAbstractText(e.target.value)} />
            {missing.has('abstract') && <span className="hint-warn">No abstract found. Please paste it if you have it.</span>}
          </label>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
            <button className="btn btn-primary" type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button>
            <Link className="btn" to="/library">Open My Library</Link>
            <button className="btn btn-ghost" type="button" onClick={reset}>Upload another PDF</button>
          </div>
          {saveMsg && <p role={saveMsg.ok ? 'status' : 'alert'} style={{ fontSize: 14, color: saveMsg.ok ? 'var(--success)' : 'var(--error)' }}>{saveMsg.text}</p>}
        </form>
      )}
    </div>
  )
}
