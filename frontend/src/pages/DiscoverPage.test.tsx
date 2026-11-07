import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, searchPapers: vi.fn() }
})

import { ApiError, searchPapers, type Paper, type SearchResponse } from '../api/client'
import DiscoverPage from './DiscoverPage'

function makePaper(n: number): Paper {
  return {
    externalId: `W${n}`, title: `Paper number ${n}`, authors: ['Ann Lee'], year: 2020, abstractText: `Abstract ${n}`,
    url: `https://example.org/${n}`, pdfUrl: null, openAccess: false, inLibrary: false,
  }
}

function response(items: Paper[], total = items.length): SearchResponse {
  return { query: 'q', total, page: 1, limit: 10, items }
}

function renderPage(url = '/discover') {
  return render(<MemoryRouter initialEntries={[url]}><DiscoverPage /></MemoryRouter>)
}

beforeEach(() => { vi.mocked(searchPapers).mockReset() })

describe('DiscoverPage', () => {
  it('starts with example topics and does not search', () => {
    renderPage()
    expect(screen.getByText('Start with a topic, a paper title, or an author.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'federated learning' })).toBeInTheDocument()
    expect(searchPapers).not.toHaveBeenCalled()
  })

  it('searches when the form is submitted and lists the results', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([makePaper(1), makePaper(2)], 2))
    renderPage()
    await userEvent.type(screen.getByRole('textbox', { name: 'Search research papers' }), 'federated learning{enter}')
    expect(await screen.findByText('Paper number 1')).toBeInTheDocument()
    expect(screen.getByText('Paper number 2')).toBeInTheDocument()
    expect(screen.getByText(/Showing 2 of 2 results for/)).toBeInTheDocument()
    expect(searchPapers).toHaveBeenCalledWith(
      { q: 'federated learning', yearFrom: undefined, sort: 'relevance', openAccessOnly: false, page: 1, limit: 10 },
      expect.any(AbortSignal),
    )
  })

  it('rejects an empty search without calling the backend', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter a topic, title, author or keyword to search.')
    expect(searchPapers).not.toHaveBeenCalled()
  })

  it('treats a whitespace-only query as empty', async () => {
    renderPage()
    await userEvent.type(screen.getByRole('textbox', { name: 'Search research papers' }), '   {enter}')
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(searchPapers).not.toHaveBeenCalled()
  })

  it('reads query and filters from the url', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([makePaper(1)]))
    renderPage('/discover?q=rag&year=2015&sort=newest&open=1')
    await screen.findByText('Paper number 1')
    expect(searchPapers).toHaveBeenCalledWith(
      { q: 'rag', yearFrom: 2015, sort: 'newest', openAccessOnly: true, page: 1, limit: 10 },
      expect.any(AbortSignal),
    )
    expect(screen.getByRole('textbox', { name: 'Search research papers' })).toHaveValue('rag')
  })

  it('searches again when a filter changes', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([makePaper(1)]))
    renderPage('/discover?q=rag')
    await screen.findByText('Paper number 1')
    await userEvent.selectOptions(screen.getByLabelText('Published'), '2020')
    await waitFor(() => expect(searchPapers).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'rag', yearFrom: 2020 }), expect.any(AbortSignal)))
    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'newest')
    await waitFor(() => expect(searchPapers).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: 'newest', yearFrom: 2020 }), expect.any(AbortSignal)))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Open access only' }))
    await waitFor(() => expect(searchPapers).toHaveBeenLastCalledWith(
      expect.objectContaining({ openAccessOnly: true }), expect.any(AbortSignal)))
  })

  it('shows a loading message while the search runs', async () => {
    vi.mocked(searchPapers).mockReturnValue(new Promise(() => {}))
    renderPage('/discover?q=slow')
    expect(await screen.findByText(/Searching for/)).toBeInTheDocument()
    expect(screen.getByLabelText('Loading results')).toHaveAttribute('aria-busy', 'true')
  })

  it('shows the error with a retry button for retryable failures', async () => {
    vi.mocked(searchPapers)
      .mockRejectedValueOnce(new ApiError('RATE_LIMITED', 'The paper search limit was reached.', true, 429))
      .mockResolvedValueOnce(response([makePaper(1)]))
    renderPage('/discover?q=rag')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The paper search limit was reached.')
    expect(alert).toHaveTextContent('Your query is kept.')
    await userEvent.click(screen.getByRole('button', { name: 'Retry search' }))
    expect(await screen.findByText('Paper number 1')).toBeInTheDocument()
    expect(searchPapers).toHaveBeenCalledTimes(2)
  })

  it('offers no retry for failures that retrying cannot fix', async () => {
    vi.mocked(searchPapers).mockRejectedValue(new ApiError('SEARCH_AUTH_FAILED', 'Check OPENALEX_API_KEY.', false, 502))
    renderPage('/discover?q=rag')
    expect(await screen.findByRole('alert')).toHaveTextContent('Check OPENALEX_API_KEY.')
    expect(screen.queryByRole('button', { name: 'Retry search' })).toBeNull()
  })

  it('explains when nothing was found and lets the user reset active filters', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([], 0))
    renderPage('/discover?q=zzzz&year=2020')
    expect(await screen.findByText(/No papers found for/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Reset filters' }))
    await waitFor(() => expect(searchPapers).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'zzzz', yearFrom: undefined }), expect.any(AbortSignal)))
  })

  it('does not offer to reset filters when none are active', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([], 0))
    renderPage('/discover?q=zzzz')
    await screen.findByText(/No papers found for/)
    expect(screen.queryByRole('button', { name: 'Reset filters' })).toBeNull()
  })

  it('loads more results and appends them until everything is shown', async () => {
    vi.mocked(searchPapers)
      .mockResolvedValueOnce(response([makePaper(1)], 3))
      .mockResolvedValueOnce(response([makePaper(2), makePaper(3)], 3))
    renderPage('/discover?q=rag')
    await screen.findByText('Paper number 1')
    await userEvent.click(screen.getByRole('button', { name: 'Load more results' }))
    expect(await screen.findByText('Paper number 3')).toBeInTheDocument()
    expect(screen.getByText('Paper number 1')).toBeInTheDocument()
    expect(searchPapers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
    expect(screen.queryByRole('button', { name: 'Load more results' })).toBeNull()
  })

  it('keeps the existing results when loading more fails', async () => {
    vi.mocked(searchPapers)
      .mockResolvedValueOnce(response([makePaper(1)], 5))
      .mockRejectedValueOnce(new ApiError('SEARCH_UPSTREAM_FAILED', 'Try again shortly.', true, 502))
    renderPage('/discover?q=rag')
    await screen.findByText('Paper number 1')
    await userEvent.click(screen.getByRole('button', { name: 'Load more results' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Try again shortly.')
    expect(screen.getByText('Paper number 1')).toBeInTheDocument()
  })

  it('searches for an example topic when its chip is clicked', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([makePaper(1)]))
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'transformer attention' }))
    await screen.findByText('Paper number 1')
    expect(searchPapers).toHaveBeenCalledWith(expect.objectContaining({ q: 'transformer attention' }), expect.any(AbortSignal))
  })

  it('clears the search box and returns to the start screen', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([makePaper(1)]))
    renderPage('/discover?q=rag')
    await screen.findByText('Paper number 1')
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }))
    expect(screen.getByRole('textbox', { name: 'Search research papers' })).toHaveValue('')
    expect(await screen.findByText('Start with a topic, a paper title, or an author.')).toBeInTheDocument()
  })

  it('formats large result counts with separators', async () => {
    vi.mocked(searchPapers).mockResolvedValue(response([makePaper(1)], 1113722))
    renderPage('/discover?q=mobile+security')
    const status = await screen.findByText(/Showing 1 of/)
    expect(within(status.parentElement as HTMLElement).getByText(/1,113,722/)).toBeInTheDocument()
  })
})
