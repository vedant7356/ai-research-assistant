import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, savePaper: vi.fn() }
})

import { ApiError, savePaper, type Paper } from '../api/client'
import PaperCard from './PaperCard'

function makePaper(overrides: Partial<Paper> = {}): Paper {
  return {
    externalId: 'W1', title: 'Mobile Security Survey', authors: ['Ann Lee', 'Bo Chan'], year: 2021,
    abstractText: 'A short abstract.', url: 'https://example.org/paper', pdfUrl: null, openAccess: false, inLibrary: false,
    ...overrides,
  }
}

beforeEach(() => { vi.mocked(savePaper).mockReset() })

describe('PaperCard', () => {
  it('shows title, authors, year and abstract', () => {
    render(<PaperCard paper={makePaper()} />)
    expect(screen.getByRole('heading', { name: 'Mobile Security Survey' })).toBeInTheDocument()
    expect(screen.getByText(/Ann Lee, Bo Chan/)).toBeInTheDocument()
    expect(screen.getByText(/2021/)).toBeInTheDocument()
    expect(screen.getByText('A short abstract.')).toBeInTheDocument()
  })

  it('abbreviates long author lists and handles missing authors and year', () => {
    const { rerender } = render(<PaperCard paper={makePaper({ authors: ['A One', 'B Two', 'C Three', 'D Four', 'E Five'] })} />)
    expect(screen.getByText(/A One, B Two, C Three \+2/)).toBeInTheDocument()
    rerender(<PaperCard paper={makePaper({ authors: [], year: null })} />)
    expect(screen.getByText(/Unknown authors · Year unknown/)).toBeInTheDocument()
  })

  it('truncates a long abstract and expands it on request', async () => {
    const long = 'word '.repeat(100).trim()
    render(<PaperCard paper={makePaper({ abstractText: long })} />)
    expect(screen.queryByText(long)).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: 'Show full abstract' }))
    expect(screen.getByText(long)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Show less' }))
    expect(screen.queryByText(long)).toBeNull()
  })

  it('offers no expand button for a short abstract', () => {
    render(<PaperCard paper={makePaper()} />)
    expect(screen.queryByRole('button', { name: /full abstract/i })).toBeNull()
  })

  it('says so when there is no abstract', () => {
    render(<PaperCard paper={makePaper({ abstractText: null })} />)
    expect(screen.getByText('No abstract available for this paper.')).toBeInTheDocument()
  })

  it('labels papers with and without an open-access PDF', () => {
    const { rerender } = render(<PaperCard paper={makePaper({ pdfUrl: 'https://example.org/p.pdf' })} />)
    expect(screen.getByText('Full text available')).toBeInTheDocument()
    rerender(<PaperCard paper={makePaper({ pdfUrl: null })} />)
    expect(screen.getByText('Abstract only')).toBeInTheDocument()
  })

  it('shows an already saved paper as saved and disables the button', () => {
    render(<PaperCard paper={makePaper({ inLibrary: true })} />)
    expect(screen.getByText('In your library')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /saved/i })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /save to library/i })).toBeNull()
  })

  it('saves the paper and then shows it as saved', async () => {
    vi.mocked(savePaper).mockResolvedValue({} as never)
    const paper = makePaper()
    render(<PaperCard paper={paper} />)
    await userEvent.click(screen.getByRole('button', { name: /save to library/i }))
    expect(savePaper).toHaveBeenCalledWith(paper)
    expect(await screen.findByRole('button', { name: /saved/i })).toBeDisabled()
    expect(screen.getByText('In your library')).toBeInTheDocument()
  })

  it('shows the server message when saving fails and lets the user try again', async () => {
    vi.mocked(savePaper).mockRejectedValue(new ApiError('INTERNAL', 'Something went wrong on the server.', true, 500))
    render(<PaperCard paper={makePaper()} />)
    await userEvent.click(screen.getByRole('button', { name: /save to library/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on the server.')
    expect(screen.getByRole('button', { name: /save to library/i })).toBeEnabled()
  })

  it('shows a generic message for unexpected save errors', async () => {
    vi.mocked(savePaper).mockRejectedValue(new Error('boom'))
    render(<PaperCard paper={makePaper()} />)
    await userEvent.click(screen.getByRole('button', { name: /save to library/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not save this paper.')
  })

  it('disables the button while saving', async () => {
    let finish: (v: never) => void = () => {}
    vi.mocked(savePaper).mockReturnValue(new Promise(res => { finish = res as (v: never) => void }))
    render(<PaperCard paper={makePaper()} />)
    await userEvent.click(screen.getByRole('button', { name: /save to library/i }))
    expect(screen.getByRole('button', { name: /saving/i })).toBeDisabled()
    finish({} as never)
    await waitFor(() => expect(screen.getByRole('button', { name: /saved/i })).toBeInTheDocument())
  })

  it('links to the original paper in a new tab, and omits the link when there is no url', () => {
    const { rerender } = render(<PaperCard paper={makePaper()} />)
    const link = screen.getByRole('link', { name: /original paper/i })
    expect(link).toHaveAttribute('href', 'https://example.org/paper')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
    rerender(<PaperCard paper={makePaper({ url: null })} />)
    expect(screen.queryByRole('link', { name: /original paper/i })).toBeNull()
  })
})
