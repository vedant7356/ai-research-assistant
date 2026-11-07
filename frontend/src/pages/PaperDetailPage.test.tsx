import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, getPaper: vi.fn() }
})

import { ApiError, getPaper, type LibraryItem } from '../api/client'
import PaperDetailPage from './PaperDetailPage'

function paper(overrides: Partial<LibraryItem> = {}): LibraryItem {
  return {
    id: 5, source: 'SAVED', externalId: 'W5', title: 'Detail paper', authors: ['Ann Lee', 'Bo Chan'], year: 2020,
    abstractText: 'The stored abstract.', url: 'https://example.org/5', pdfUrl: null, openAccess: false,
    originalFilename: null, textStatus: 'ABSTRACT_ONLY', hasSummary: false, createdAt: '2026-03-04T12:00:00Z', ...overrides,
  }
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes><Route path="/library/:id" element={<PaperDetailPage />} /></Routes>
    </MemoryRouter>,
  )
}

const scrollIntoView = vi.fn()

beforeEach(() => {
  vi.mocked(getPaper).mockReset()
  scrollIntoView.mockReset()
  Element.prototype.scrollIntoView = scrollIntoView
})

describe('PaperDetailPage (round 1: page had 0% coverage)', () => {
  it('shows a loading placeholder first', () => {
    vi.mocked(getPaper).mockReturnValue(new Promise(() => {}))
    renderAt('/library/5')
    expect(screen.getByLabelText('Loading paper')).toBeInTheDocument()
  })

  it('loads the paper by the id in the URL and shows its details', async () => {
    vi.mocked(getPaper).mockResolvedValue(paper())
    renderAt('/library/5')
    expect(await screen.findByRole('heading', { name: 'Detail paper', level: 1 })).toBeInTheDocument()
    expect(getPaper).toHaveBeenCalledWith(5)
    expect(screen.getByText('Ann Lee, Bo Chan')).toBeInTheDocument()
    expect(screen.getByText(/^2020 · Added/)).toBeInTheDocument()
    expect(screen.getByText('The stored abstract.')).toBeInTheDocument()
    expect(screen.getByText('Saved from search')).toBeInTheDocument()
    expect(screen.getByText('Abstract only')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '← My Library' })).toHaveAttribute('href', '/library')
  })

  it('shows upload details: file name, full text badge and summary badge', async () => {
    vi.mocked(getPaper).mockResolvedValue(paper({ source: 'UPLOAD', textStatus: 'FULL_TEXT', hasSummary: true, originalFilename: 'my.pdf', url: null }))
    renderAt('/library/5')
    expect(await screen.findByText('Uploaded PDF')).toBeInTheDocument()
    expect(screen.getByText('Full text')).toBeInTheDocument()
    expect(screen.getByText('Summary ready')).toBeInTheDocument()
    expect(screen.getByText(/· my\.pdf/)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Original paper' })).toBeNull()
  })

  it('falls back for unknown authors, unknown year and a missing abstract', async () => {
    vi.mocked(getPaper).mockResolvedValue(paper({ authors: [], year: null, abstractText: null }))
    renderAt('/library/5')
    expect(await screen.findByText('Unknown authors')).toBeInTheDocument()
    expect(screen.getByText(/^Year unknown · Added/)).toBeInTheDocument()
    expect(screen.getByText(/No abstract stored for this paper/)).toBeInTheDocument()
  })

  it('opens the original paper in a new tab without leaking the opener', async () => {
    vi.mocked(getPaper).mockResolvedValue(paper())
    renderAt('/library/5')
    const link = await screen.findByRole('link', { name: 'Original paper' })
    expect(link).toHaveAttribute('href', 'https://example.org/5')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
  })

  it('contains the summary and question sections for the paper', async () => {
    vi.mocked(getPaper).mockResolvedValue(paper())
    renderAt('/library/5')
    expect(await screen.findByRole('heading', { name: 'Summary' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Ask about this paper' })).toBeInTheDocument()
    expect(screen.getByLabelText('Question about this paper')).toBeInTheDocument()
  })

  it.each(['abc', '0', '-3', '1.5'])('rejects the id "%s" without calling the backend', async id => {
    renderAt(`/library/${id}`)
    expect(await screen.findByText('That paper is not in your library.')).toBeInTheDocument()
    expect(getPaper).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull()
  })

  it('shows a not-retryable error without a Retry button', async () => {
    vi.mocked(getPaper).mockRejectedValue(new ApiError('NOT_IN_LIBRARY', 'That paper is not in your library.', false, 404))
    renderAt('/library/99')
    expect(await screen.findByText('This paper couldn’t be opened')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Retry' })).toBeNull()
  })

  it('offers Retry for a retryable error and loads the paper on the second try', async () => {
    vi.mocked(getPaper)
      .mockRejectedValueOnce(new ApiError('INTERNAL', 'Something went wrong on the server.', true, 500))
      .mockResolvedValueOnce(paper())
    renderAt('/library/5')
    await userEvent.click(await screen.findByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('heading', { name: 'Detail paper', level: 1 })).toBeInTheDocument()
    expect(getPaper).toHaveBeenCalledTimes(2)
  })

  it('turns an unexpected failure into a retryable error', async () => {
    vi.mocked(getPaper).mockRejectedValueOnce(new Error('network'))
    renderAt('/library/5')
    expect(await screen.findByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('scrolls to the question box when arriving from "Ask questions" (#ask)', async () => {
    vi.mocked(getPaper).mockResolvedValue(paper())
    renderAt('/library/5#ask')
    await screen.findByRole('heading', { name: 'Detail paper', level: 1 })
    expect(scrollIntoView).toHaveBeenCalledTimes(1)
  })

  it('does not scroll when there is no #ask in the URL', async () => {
    vi.mocked(getPaper).mockResolvedValue(paper())
    renderAt('/library/5')
    await screen.findByRole('heading', { name: 'Detail paper', level: 1 })
    expect(scrollIntoView).not.toHaveBeenCalled()
  })
})
