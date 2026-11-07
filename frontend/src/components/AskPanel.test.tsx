import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, askPaper: vi.fn() }
})

import { ApiError, askPaper, type Answer } from '../api/client'
import AskPanel from './AskPanel'

const ANSWER: Answer = {
  answer: 'Tokens are stored in plain text [1].', basis: 'FULL_TEXT',
  sources: [{ n: 1, excerpt: 'Phones store tokens in plain text files.' }],
}

beforeEach(() => { vi.mocked(askPaper).mockReset() })

async function ask(question: string) {
  await userEvent.type(screen.getByLabelText('Question about this paper'), question)
  await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
}

describe('AskPanel', () => {
  it('is collapsed until the toggle is pressed', async () => {
    render(<AskPanel paperId={5} />)
    expect(screen.queryByLabelText('Question about this paper')).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: 'Ask a question' }))
    expect(screen.getByLabelText('Question about this paper')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Hide Q&A' }))
    expect(screen.queryByLabelText('Question about this paper')).toBeNull()
  })

  it('is always open and has no toggle when embedded', () => {
    render(<AskPanel paperId={5} embedded />)
    expect(screen.getByLabelText('Question about this paper')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /ask a question|hide q&a/i })).toBeNull()
  })

  it('sends the trimmed question and shows the answer, passages and basis', async () => {
    vi.mocked(askPaper).mockResolvedValue(ANSWER)
    render(<AskPanel paperId={5} embedded />)
    await ask('  Where are tokens stored?  ')
    expect(askPaper).toHaveBeenCalledWith(5, 'Where are tokens stored?')
    expect(await screen.findByText('Tokens are stored in plain text [1].')).toBeInTheDocument()
    expect(screen.getByText('Where are tokens stored?')).toBeInTheDocument()
    expect(screen.getByText('Passages used (1)')).toBeInTheDocument()
    expect(screen.getByText(/Phones store tokens in plain text files\./)).toBeInTheDocument()
    expect(screen.getByText(/Answered from the paper text\./)).toBeInTheDocument()
    expect(screen.getByLabelText('Question about this paper')).toHaveValue('')
  })

  it('says when the answer came from the abstract only', async () => {
    vi.mocked(askPaper).mockResolvedValue({ answer: 'From the abstract.', basis: 'ABSTRACT_ONLY', sources: [] })
    render(<AskPanel paperId={5} embedded />)
    await ask('Anything?')
    expect(await screen.findByText(/Answered from the abstract only\./)).toBeInTheDocument()
    expect(screen.queryByText(/Passages used/)).toBeNull()
  })

  it('shows the not-in-paper answer like any other answer', async () => {
    vi.mocked(askPaper).mockResolvedValue({ answer: 'The paper text I have does not say.', basis: 'FULL_TEXT', sources: [] })
    render(<AskPanel paperId={5} embedded />)
    await ask('What was the accuracy on ImageNet?')
    expect(await screen.findByText('The paper text I have does not say.')).toBeInTheDocument()
  })

  it('keeps earlier question and answer pairs', async () => {
    vi.mocked(askPaper).mockResolvedValueOnce({ answer: 'First answer.', basis: 'FULL_TEXT', sources: [] })
      .mockResolvedValueOnce({ answer: 'Second answer.', basis: 'FULL_TEXT', sources: [] })
    render(<AskPanel paperId={5} embedded />)
    await ask('First question?')
    await screen.findByText('First answer.')
    await ask('Second question?')
    expect(await screen.findByText('Second answer.')).toBeInTheDocument()
    expect(screen.getByText('First answer.')).toBeInTheDocument()
  })

  it('shows the server error message under the question', async () => {
    vi.mocked(askPaper).mockRejectedValue(new ApiError('NO_CONTENT', 'This paper has no text or abstract to answer from.', false, 422))
    render(<AskPanel paperId={5} embedded />)
    await ask('Hello there?')
    expect(await screen.findByRole('alert')).toHaveTextContent('This paper has no text or abstract to answer from.')
  })

  it('shows a generic message for unexpected errors', async () => {
    vi.mocked(askPaper).mockRejectedValue(new Error('boom'))
    render(<AskPanel paperId={5} embedded />)
    await ask('Hello there?')
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not get an answer.')
  })

  it('does not allow empty or whitespace-only questions', async () => {
    render(<AskPanel paperId={5} embedded />)
    const button = screen.getByRole('button', { name: 'Ask' })
    expect(button).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Question about this paper'), '   ')
    expect(button).toBeDisabled()
    expect(askPaper).not.toHaveBeenCalled()
  })

  it('shows a progress message and disables asking while waiting', async () => {
    let finish: (a: Answer) => void = () => {}
    vi.mocked(askPaper).mockReturnValue(new Promise(res => { finish = res }))
    render(<AskPanel paperId={5} embedded />)
    await ask('Slow question?')
    expect(screen.getByText(/Searching the paper and writing an answer/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ask' })).toBeDisabled()
    finish(ANSWER)
    expect(await screen.findByText('Tokens are stored in plain text [1].')).toBeInTheDocument()
  })

  it('limits the question box to 1000 characters', () => {
    render(<AskPanel paperId={5} embedded />)
    expect(screen.getByLabelText('Question about this paper')).toHaveAttribute('maxlength', '1000')
  })
})
