import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, uploadPdf: vi.fn(), updatePaper: vi.fn() }
})

import { ApiError, updatePaper, uploadPdf, type LibraryItem, type UploadResult } from '../api/client'
import UploadPage from './UploadPage'

function item(overrides: Partial<LibraryItem> = {}): LibraryItem {
  return {
    id: 11, source: 'UPLOAD', externalId: null, title: 'Extracted Title', authors: ['Ann Lee', 'Bo Chan'], year: 2022,
    abstractText: 'Extracted abstract text.', url: null, pdfUrl: null, openAccess: false, originalFilename: 'paper.pdf',
    textStatus: 'FULL_TEXT', hasSummary: false, createdAt: '2026-01-01T00:00:00Z', ...overrides,
  }
}

function result(overrides: Partial<UploadResult> = {}): UploadResult {
  return { item: item(), duplicate: false, missingFields: [], pageCount: 3, ...overrides }
}

function pdfFile(name = 'paper.pdf') {
  return new File(['%PDF-1.4 content'], name, { type: 'application/pdf' })
}

function renderPage() {
  return render(<MemoryRouter><UploadPage /></MemoryRouter>)
}

function chooseFile(file: File) {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement
  fireEvent.change(input, { target: { files: [file] } })
}

beforeEach(() => {
  vi.mocked(uploadPdf).mockReset()
  vi.mocked(updatePaper).mockReset()
})

describe('UploadPage', () => {
  it('shows the drop zone first', () => {
    renderPage()
    expect(screen.getByText('Drop a PDF here, or click to choose one')).toBeInTheDocument()
  })

  it('rejects files that are not PDFs without calling the backend', () => {
    renderPage()
    chooseFile(new File(['hello'], 'notes.txt', { type: 'text/plain' }))
    expect(screen.getByRole('alert')).toHaveTextContent('That file is not a PDF. Choose a .pdf file.')
    expect(uploadPdf).not.toHaveBeenCalled()
  })

  it('rejects empty files', () => {
    renderPage()
    chooseFile(new File([], 'empty.pdf', { type: 'application/pdf' }))
    expect(screen.getByRole('alert')).toHaveTextContent('That file is empty.')
    expect(uploadPdf).not.toHaveBeenCalled()
  })

  it('rejects files over 25 MB', () => {
    renderPage()
    const big = pdfFile('big.pdf')
    Object.defineProperty(big, 'size', { value: 26 * 1024 * 1024 })
    chooseFile(big)
    expect(screen.getByRole('alert')).toHaveTextContent('larger than 25 MB')
    expect(uploadPdf).not.toHaveBeenCalled()
  })

  it('accepts a PDF whose type is missing but whose name ends in .pdf', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    renderPage()
    chooseFile(new File(['%PDF-1.4'], 'PAPER.PDF', { type: '' }))
    expect(await screen.findByDisplayValue('Extracted Title')).toBeInTheDocument()
  })

  it('shows the extracted details in an editable form after a successful upload', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    renderPage()
    chooseFile(pdfFile())
    expect(await screen.findByDisplayValue('Extracted Title')).toBeInTheDocument()
    expect(screen.getByLabelText(/Authors/)).toHaveValue('Ann Lee\nBo Chan')
    expect(screen.getByLabelText('Year')).toHaveValue('2022')
    expect(screen.getByLabelText('Abstract')).toHaveValue('Extracted abstract text.')
    expect(screen.getByText(/Added to your library · 3 pages/)).toBeInTheDocument()
    expect(screen.getByText(/may be wrong/)).toBeInTheDocument()
  })

  it('uses the singular for a one page PDF', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result({ pageCount: 1 }))
    renderPage()
    chooseFile(pdfFile())
    expect(await screen.findByText(/Added to your library · 1 page$/)).toBeInTheDocument()
  })

  it('warns about every field the extractor could not find', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result({
      item: item({ title: 'file name', authors: [], year: null, abstractText: null }),
      missingFields: ['title', 'authors', 'year', 'abstract'],
    }))
    renderPage()
    chooseFile(pdfFile())
    expect(await screen.findByText(/No title found/)).toBeInTheDocument()
    expect(screen.getByText(/No authors found/)).toBeInTheDocument()
    expect(screen.getByText(/No year found/)).toBeInTheDocument()
    expect(screen.getByText(/No abstract found/)).toBeInTheDocument()
    expect(screen.getByLabelText(/^Year/)).toHaveValue('')
  })

  it('tells the user when the exact PDF was uploaded before', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result({ duplicate: true }))
    renderPage()
    chooseFile(pdfFile())
    expect(await screen.findByText('This exact PDF is already in your library')).toBeInTheDocument()
  })

  it('saves corrections with trimmed values and clean author lines', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    vi.mocked(updatePaper).mockResolvedValue(item())
    renderPage()
    chooseFile(pdfFile())
    const title = await screen.findByLabelText('Title')
    await userEvent.clear(title)
    await userEvent.type(title, '  Fixed Title  ')
    const authors = screen.getByLabelText(/Authors/)
    await userEvent.clear(authors)
    await userEvent.type(authors, ' Real Author {enter}{enter}  Second Author ')
    await userEvent.clear(screen.getByLabelText('Abstract'))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(updatePaper).toHaveBeenCalledWith(11, {
      title: 'Fixed Title', authors: ['Real Author', 'Second Author'], year: 2022, abstractText: null,
    })
    expect(await screen.findByRole('status')).toHaveTextContent('Changes saved to your library.')
  })

  it('validates the year before saving', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    renderPage()
    chooseFile(pdfFile())
    const year = await screen.findByLabelText('Year')
    for (const bad of ['abc', '1850', '2101', '20.5']) {
      await userEvent.clear(year)
      await userEvent.type(year, bad)
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      expect(await screen.findByRole('alert')).toHaveTextContent('Enter a year between 1900 and 2100, or leave it empty.')
    }
    expect(updatePaper).not.toHaveBeenCalled()
  })

  it('allows saving with an empty year', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    vi.mocked(updatePaper).mockResolvedValue(item())
    renderPage()
    chooseFile(pdfFile())
    await userEvent.clear(await screen.findByLabelText('Year'))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(updatePaper).toHaveBeenCalledWith(11, expect.objectContaining({ year: null }))
  })

  it('does not save an empty title', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    renderPage()
    chooseFile(pdfFile())
    await userEvent.clear(await screen.findByLabelText('Title'))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The title cannot be empty.')
    expect(updatePaper).not.toHaveBeenCalled()
  })

  it('shows the server message when saving corrections fails', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    vi.mocked(updatePaper).mockRejectedValue(new ApiError('VALIDATION_FAILED', 'The year must be between 1900 and 2100.', false, 400))
    renderPage()
    chooseFile(pdfFile())
    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The year must be between 1900 and 2100.')
  })

  it('shows the upload error with a retry for retryable failures', async () => {
    vi.mocked(uploadPdf)
      .mockRejectedValueOnce(new ApiError('UPLOAD_FAILED', 'The file could not be read. Try uploading it again.', true, 400))
      .mockResolvedValueOnce(result())
    renderPage()
    chooseFile(pdfFile())
    expect(await screen.findByText('This PDF couldn’t be added')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByDisplayValue('Extracted Title')).toBeInTheDocument()
    expect(uploadPdf).toHaveBeenCalledTimes(2)
  })

  it('shows scanned-PDF errors without a retry button', async () => {
    vi.mocked(uploadPdf).mockRejectedValue(new ApiError('EMPTY_PDF', 'No readable text was found in this PDF.', false, 422))
    renderPage()
    chooseFile(pdfFile('scan.pdf'))
    expect(await screen.findByText('No readable text was found in this PDF.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('can start over after a successful upload', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    renderPage()
    chooseFile(pdfFile())
    await userEvent.click(await screen.findByRole('button', { name: 'Upload another PDF' }))
    expect(screen.getByText('Drop a PDF here, or click to choose one')).toBeInTheDocument()
  })

  it('links to the library after an upload', async () => {
    vi.mocked(uploadPdf).mockResolvedValue(result())
    renderPage()
    chooseFile(pdfFile())
    expect(await screen.findByRole('link', { name: 'Open My Library' })).toHaveAttribute('href', '/library')
  })
})
