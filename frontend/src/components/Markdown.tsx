import type { ReactNode } from 'react'

/** Very small Markdown renderer for LLM output: "## heading", "- bullet", "**bold**". No raw HTML is ever inserted. */
function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*|\*[^*\s][^*]*\*)/g).map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) return <strong key={i}>{part.slice(2, -2)}</strong>
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) return <em key={i}>{part.slice(1, -1)}</em>
    return part
  })
}

export default function Markdown({ text }: { text: string }) {
  const blocks: ReactNode[] = []
  let bullets: string[] = []
  const flush = () => {
    if (bullets.length === 0) return
    const items = bullets
    blocks.push(<ul key={`u${blocks.length}`} style={{ margin: 0, paddingLeft: 20, display: 'flex', flexDirection: 'column', gap: 4 }}>
      {items.map((b, i) => <li key={i}>{inline(b)}</li>)}
    </ul>)
    bullets = []
  }
  for (const raw of text.split('\n')) {
    const line = raw.trim()
    if (line === '') { flush(); continue }
    const heading = /^#{1,4}\s+(.*)$/.exec(line)
    const bullet = /^[-*•]\s+(.*)$/.exec(line)
    if (heading) {
      flush()
      blocks.push(<h4 key={`h${blocks.length}`} style={{ fontSize: 14, fontWeight: 600, marginTop: 6 }}>{heading[1]}</h4>)
    } else if (bullet) {
      bullets.push(bullet[1])
    } else {
      flush()
      blocks.push(<p key={`p${blocks.length}`} style={{ margin: 0 }}>{inline(line)}</p>)
    }
  }
  flush()
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 14, lineHeight: 1.55, color: 'var(--text-2)' }}>{blocks}</div>
}
