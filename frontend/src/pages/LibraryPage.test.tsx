import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, listLibrary: vi.fn(), removeFromLibrary: vi.fn() }
})

import { ApiError, listLibrary, removeFromLibrary, type LibraryItem } from '../api/client'
import LibraryPage from './LibraryPage'

function item(id: number, overrides: Partial<LibraryItem> = {}): LibraryItem {
  return {
    id, source: 'SAVED', externalId: `W${id}`, title: `Library paper ${id}`, authors: ['Ann Lee'], year: 2021,
    abstractText: 'An abstract.', url: `https://example.org/${id}`, pdfUrl: null, openAccess: false, originalFilename: null,
    textStatus: 'ABSTRACT_ONLY', hasSummary: false, createdAt: '2026-03-04T12:00:00Z', ...overrides,
  }
}

function renderPage() {
  return render(<MemoryRouter><LibraryPage /></MemoryRouter>)
}

beforeEach(() => {
  vi.mocked(listLibrary).mockReset()
  vi.mocked(removeFromLibrary).mockReset()
})

describe('LibraryPage', () => {
  it('loads and lists the saved papers', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1), item(2)])
    renderPage()
    expect(await screen.findByText('Library paper 1')).toBeInTheDocument()
    expect(screen.getByText('Library paper 2')).toBeInTheDocument()
    expect(screen.getByText('2 papers')).toBeInTheDocument()
    expect(listLibrary).toHaveBeenCalledWith({ q: '', source: 'ALL', sort: 'recent' }, expect.any(AbortSignal))
  })

  it('uses the singular for one paper', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1)])
    renderPage()
    expect(await screen.findByText('1 paper')).toBeInTheDocument()
  })

  it('shows source, text status and summary badges and the file name of uploads', async () => {
    vi.mocked(listLibrary).mockResolvedValue([
      item(1, { source: 'UPLOAD', textStatus: 'FULL_TEXT', hasSummary: true, originalFilename: 'my-paper.pdf', url: null }),
      item(2),
    ])
    renderPage()
    await screen.findByText('Library paper 1')
    // "Saved from search" is also an option in the Source filter, so look inside each paper card.
    const [uploadCard, savedCard] = screen.getAllByRole('article')
    expect(within(uploadCard).getByText('Uploaded PDF')).toBeInTheDocument()
    expect(within(uploadCard).getByText('Full text')).toBeInTheDocument()
    expect(within(uploadCard).getByText('Summary ready')).toBeInTheDocument()
    expect(within(uploadCard).getByText(/my-paper\.pdf/)).toBeInTheDocument()
    expect(within(savedCard).getByText('Saved from search')).toBeInTheDocument()
    expect(within(savedCard).getByText('Abstract only')).toBeInTheDocument()
    expect(within(savedCard).queryByText('Summary ready')).toBeNull()
  })

  it('abbreviates many authors and shows unknown authors and years', async () => {
    vi.mocked(listLibrary).mockResolvedValue([
      item(1, { authors: ['A One', 'B Two', 'C Three', 'D Four'] }),
      item(2, { authors: [], year: null }),
    ])
    renderPage()
    expect(await screen.findByText(/A One, B Two, C Three \+1/)).toBeInTheDocument()
    expect(screen.getByText(/Unknown authors · Year unknown/)).toBeInTheDocument()
  })

  it('links titles and Details to the paper page', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(7)])
    renderPage()
    const title = await screen.findByRole('link', { name: 'Library paper 7' })
    expect(title).toHaveAttribute('href', '/library/7')
    expect(screen.getByRole('link', { name: 'Details' })).toHaveAttribute('href', '/library/7')
  })

  it('invites the user to discover papers when the library is empty', async () => {
    vi.mocked(listLibrary).mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('Your library is waiting for its first paper.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Discover Papers' })).toHaveAttribute('href', '/discover')
  })

  it('shows a loading placeholder first', () => {
    vi.mocked(listLibrary).mockReturnValue(new Promise(() => {}))
    renderPage()
    expect(screen.getByLabelText('Loading library')).toHaveAttribute('aria-busy', 'true')
  })

  it('shows an error with a working retry button', async () => {
    vi.mocked(listLibrary)
      .mockRejectedValueOnce(new ApiError('NETWORK', "Can't reach the server.", true, 0))
      .mockResolvedValueOnce([item(1)])
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent("Can't reach the server.")
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Library paper 1')).toBeInTheDocument()
  })

  it('filters by text after a short pause in typing', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1)])
    renderPage()
    await screen.findByText('Library paper 1')
    await userEvent.type(screen.getByLabelText('Search your library'), 'hinton')
    await waitFor(() => expect(listLibrary).toHaveBeenLastCalledWith(
      { q: 'hinton', source: 'ALL', sort: 'recent' }, expect.any(AbortSignal)))
    // typing six letters must not send six requests
    const queries = vi.mocked(listLibrary).mock.calls.map(c => c[0].q)
    expect(queries.filter(q => q !== '' && q !== 'hinton')).toHaveLength(0)
  })

  it('reloads when the source or sort changes', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1)])
    renderPage()
    await screen.findByText('Library paper 1')
    await userEvent.selectOptions(screen.getByLabelText('Source'), 'UPLOAD')
    await waitFor(() => expect(listLibrary).toHaveBeenLastCalledWith(
      { q: '', source: 'UPLOAD', sort: 'recent' }, expect.any(AbortSignal)))
    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'year')
    await waitFor(() => expect(listLibrary).toHaveBeenLastCalledWith(
      { q: '', source: 'UPLOAD', sort: 'year' }, expect.any(AbortSignal)))
  })

  it('says when filters match nothing and clears them', async () => {
    vi.mocked(listLibrary).mockResolvedValueOnce([item(1)]).mockResolvedValue([])
    renderPage()
    await screen.findByText('Library paper 1')
    await userEvent.selectOptions(screen.getByLabelText('Source'), 'UPLOAD')
    expect(await screen.findByText('No papers match your filters')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(listLibrary).toHaveBeenLastCalledWith(
      { q: '', source: 'ALL', sort: 'recent' }, expect.any(AbortSignal)))
  })

  it('asks for confirmation before removing and can be cancelled', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1)])
    renderPage()
    await screen.findByText('Library paper 1')
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect(screen.getByText('Remove from library?')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByText('Remove from library?')).toBeNull()
    expect(removeFromLibrary).not.toHaveBeenCalled()
  })

  it('removes a confirmed paper and refreshes the list', async () => {
    vi.mocked(listLibrary).mockResolvedValueOnce([item(1), item(2)]).mockResolvedValueOnce([item(2)])
    vi.mocked(removeFromLibrary).mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('Library paper 1')
    const card = screen.getByText('Library paper 1').closest('article') as HTMLElement
    await userEvent.click(within(card).getByRole('button', { name: 'Remove' }))
    await userEvent.click(within(card).getByRole('button', { name: 'Remove' }))
    expect(removeFromLibrary).toHaveBeenCalledWith(1)
    await waitFor(() => expect(screen.queryByText('Library paper 1')).toBeNull())
    expect(screen.getByText('Library paper 2')).toBeInTheDocument()
  })

  it('keeps the paper and shows the error when removing fails', async () => {
    vi.mocked(listLibrary).mockResolvedValue([item(1)])
    vi.mocked(removeFromLibrary).mockRejectedValue(new ApiError('NOT_IN_LIBRARY', 'That paper is not in your library.', false, 404))
    renderPage()
    await screen.findByText('Library paper 1')
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('That paper is not in your library.')
    expect(screen.getByText('Library paper 1')).toBeInTheDocument()
  })
})
