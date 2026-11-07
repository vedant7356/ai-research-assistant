import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, listLibrary: vi.fn() }
})

import { ApiError, listLibrary, type LibraryItem } from '../api/client'
import HomePage from './HomePage'

function item(id: number, overrides: Partial<LibraryItem> = {}): LibraryItem {
  return {
    id, source: 'SAVED', externalId: `W${id}`, title: `Home paper ${id}`, authors: ['Ann Lee'], year: 2021,
    abstractText: 'An abstract.', url: null, pdfUrl: null, openAccess: false, originalFilename: null,
    textStatus: 'ABSTRACT_ONLY', hasSummary: false, createdAt: '2026-03-04T12:00:00Z', ...overrides,
  }
}

function Where() {
  const l = useLocation()
  return <p data-testid="where">{l.pathname + l.search}</p>
}

function renderHome() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => { vi.mocked(listLibrary).mockReset() })

describe('HomePage (round 1: page had 0% coverage)', () => {
  it('shows a checking message while the library loads', () => {
    vi.mocked(listLibrary).mockReturnValue(new Promise(() => {}))
    renderHome()
    expect(screen.getByText('Checking your library…')).toBeInTheDocument()
  })

  it('shows the empty state and the three onboarding steps for an empty library', async () => {
    vi.mocked(listLibrary).mockResolvedValue([])
    renderHome()
    expect(await screen.findByText('Your library is empty.')).toBeInTheDocument()
    expect(screen.getByText(/Nothing here yet/)).toBeInTheDocument()
    expect(screen.getByText('Or upload a PDF you already have.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View library' })).toBeNull()
  })

  it('shows only the three most recent papers but counts them all', async () => {
    vi.mocked(listLibrary).mockResolvedValue([1, 2, 3, 4, 5].map(i => item(i)))
    renderHome()
    expect(await screen.findByText('5 papers saved or uploaded.')).toBeInTheDocument()
    expect(screen.getByText('Home paper 1')).toBeInTheDocument()
    expect(screen.getByText('Home paper 3')).toBeInTheDocument()
    expect(screen.queryByText('Home paper 4')).toBeNull()
    expect(screen.getByRole('link', { name: 'View library' })).toHaveAttribute('href', '/library')
  })

  it('uses the singular for exactly one paper', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1)])
    renderHome()
    expect(await screen.findByText('1 paper saved or uploaded.')).toBeInTheDocument()
  })

  it('links each recent paper to its page and to its question box', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(7)])
    renderHome()
    await screen.findByText('Home paper 7')
    expect(screen.getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/library/7')
    expect(screen.getByRole('link', { name: 'Ask questions' })).toHaveAttribute('href', '/library/7#ask')
  })

  it('abbreviates authors and falls back for unknown authors, year and abstract', async () => {
    vi.mocked(listLibrary).mockResolvedValue([
      item(1, { authors: ['A One', 'B Two', 'C Three', 'D Four'] }),
      item(2, { authors: [], year: null, abstractText: null }),
    ])
    renderHome()
    expect(await screen.findByText(/A One, B Two \+2/)).toBeInTheDocument()
    expect(screen.getByText(/Unknown authors · Year unknown/)).toBeInTheDocument()
    expect(screen.getByText('No abstract stored for this paper.')).toBeInTheDocument()
  })

  it('labels uploads and papers with a summary', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1, { source: 'UPLOAD', hasSummary: true })])
    renderHome()
    expect(await screen.findByText('Uploaded PDF')).toBeInTheDocument()
    expect(screen.getByText('Summary ready')).toBeInTheDocument()
  })

  it('shows a retryable error and loads again when Retry is pressed', async () => {
    vi.mocked(listLibrary)
      .mockRejectedValueOnce(new ApiError('INTERNAL', 'boom', true, 500))
      .mockResolvedValueOnce([item(1)])
    renderHome()
    expect(await screen.findByText(/couldn’t load your library/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Home paper 1')).toBeInTheDocument()
    expect(listLibrary).toHaveBeenCalledTimes(2)
  })

  it('treats a non-API failure as an error too', async () => {
    vi.mocked(listLibrary).mockRejectedValueOnce(new Error('network'))
    renderHome()
    expect(await screen.findByText(/couldn’t load your library/)).toBeInTheDocument()
  })

  it('refuses an empty or whitespace-only search and does not navigate', async () => {
    vi.mocked(listLibrary).mockResolvedValue([])
    renderHome()
    await screen.findByText('Your library is empty.')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a topic, title, author or keyword to search.')
    await userEvent.type(screen.getByLabelText('Search research papers'), '   ')
    expect(screen.queryByText('Enter a topic, title, author or keyword to search.')).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.queryByTestId('where')).toBeNull()
  })

  it('goes to Discover with the trimmed, URL-encoded query', async () => {
    vi.mocked(listLibrary).mockResolvedValue([])
    renderHome()
    await screen.findByText('Your library is empty.')
    await userEvent.type(screen.getByLabelText('Search research papers'), '  rag & llms  ')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByTestId('where')).toHaveTextContent('/discover?q=rag%20%26%20llms')
  })
})
