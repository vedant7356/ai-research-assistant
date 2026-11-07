import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, getSummary: vi.fn(), generateSummary: vi.fn() }
})

import { ApiError, generateSummary, getSummary, type Summary } from '../api/client'
import SummaryPanel from './SummaryPanel'

const SUMMARY: Summary = { paperId: 1, summary: '## Overview\nA short summary.', basis: 'FULL_TEXT', cached: false }

beforeEach(() => {
  vi.mocked(getSummary).mockReset()
  vi.mocked(generateSummary).mockReset()
})

describe('SummaryPanel (inline in the library list)', () => {
  it('generates a summary when Summarize is pressed and says what it was written from', async () => {
    vi.mocked(generateSummary).mockResolvedValue(SUMMARY)
    const onGenerated = vi.fn()
    render(<SummaryPanel paperId={1} hasSummary={false} onGenerated={onGenerated} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(generateSummary).toHaveBeenCalledWith(1, false)
    expect(await screen.findByText('A short summary.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    expect(screen.getByText(/Written from the full paper text\./)).toBeInTheDocument()
    expect(onGenerated).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Hide summary' })).toBeInTheDocument()
  })

  it('labels abstract-only and truncated summaries differently', async () => {
    vi.mocked(generateSummary).mockResolvedValueOnce({ ...SUMMARY, basis: 'ABSTRACT_ONLY' })
    const { unmount } = render(<SummaryPanel paperId={1} hasSummary={false} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(await screen.findByText(/Written from the abstract only/)).toBeInTheDocument()
    unmount()

    vi.mocked(generateSummary).mockResolvedValueOnce({ ...SUMMARY, basis: 'FULL_TEXT_TRUNCATED' })
    render(<SummaryPanel paperId={2} hasSummary={false} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(await screen.findByText(/first part of a long paper/)).toBeInTheDocument()
  })

  it('loads an existing summary instead of generating a new one', async () => {
    vi.mocked(getSummary).mockResolvedValue({ ...SUMMARY, cached: true })
    render(<SummaryPanel paperId={1} hasSummary />)
    await userEvent.click(screen.getByRole('button', { name: 'View summary' }))
    expect(await screen.findByText('A short summary.')).toBeInTheDocument()
    expect(getSummary).toHaveBeenCalledWith(1)
    expect(generateSummary).not.toHaveBeenCalled()
  })

  it('hides and re-shows the summary without asking the server again', async () => {
    vi.mocked(generateSummary).mockResolvedValue(SUMMARY)
    render(<SummaryPanel paperId={1} hasSummary={false} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    await screen.findByText('A short summary.')
    await userEvent.click(screen.getByRole('button', { name: 'Hide summary' }))
    expect(screen.queryByText('A short summary.')).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(screen.getByText('A short summary.')).toBeInTheDocument()
    expect(generateSummary).toHaveBeenCalledTimes(1)
  })

  it('shows a progress message while the model works', async () => {
    let finish: (s: Summary) => void = () => {}
    vi.mocked(generateSummary).mockReturnValue(new Promise(res => { finish = res }))
    render(<SummaryPanel paperId={1} hasSummary={false} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(screen.getByText(/Reading the paper and writing a summary/)).toBeInTheDocument()
    finish(SUMMARY)
    expect(await screen.findByText('A short summary.')).toBeInTheDocument()
    expect(screen.queryByText(/Reading the paper/)).toBeNull()
  })

  it('shows the error and retries with refresh when Try again is pressed', async () => {
    vi.mocked(generateSummary)
      .mockRejectedValueOnce(new ApiError('RATE_LIMITED', 'The AI service is busy.', true, 429))
      .mockResolvedValueOnce(SUMMARY)
    render(<SummaryPanel paperId={1} hasSummary={false} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The AI service is busy.')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('A short summary.')).toBeInTheDocument()
    expect(generateSummary).toHaveBeenLastCalledWith(1, true)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('uses a generic message for unexpected errors', async () => {
    vi.mocked(generateSummary).mockRejectedValue(new Error('boom'))
    render(<SummaryPanel paperId={1} hasSummary={false} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not create the summary.')
  })

  it('regenerates on request', async () => {
    vi.mocked(generateSummary).mockResolvedValue(SUMMARY)
    render(<SummaryPanel paperId={1} hasSummary={false} />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    await screen.findByText('A short summary.')
    await userEvent.click(screen.getByRole('button', { name: 'Regenerate' }))
    await waitFor(() => expect(generateSummary).toHaveBeenLastCalledWith(1, true))
  })
})

describe('SummaryPanel (embedded on the detail page)', () => {
  it('shows an existing summary straight away', async () => {
    vi.mocked(getSummary).mockResolvedValue({ ...SUMMARY, cached: true })
    render(<SummaryPanel paperId={1} hasSummary embedded />)
    expect(await screen.findByText('A short summary.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /summarize|view summary|hide summary/i })).toBeNull()
  })

  it('never generates a summary without a click', async () => {
    render(<SummaryPanel paperId={1} hasSummary={false} embedded />)
    expect(screen.getByText(/No summary yet/)).toBeInTheDocument()
    expect(generateSummary).not.toHaveBeenCalled()
    expect(getSummary).not.toHaveBeenCalled()
    vi.mocked(generateSummary).mockResolvedValue(SUMMARY)
    await userEvent.click(screen.getByRole('button', { name: 'Generate summary' }))
    expect(await screen.findByText('A short summary.')).toBeInTheDocument()
  })
})
