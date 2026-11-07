import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import Markdown from './Markdown'

describe('Markdown', () => {
  it('renders headings of any level as headings', () => {
    render(<Markdown text={'## Overview\n### Method'} />)
    expect(screen.getByRole('heading', { name: 'Overview' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Method' })).toBeInTheDocument()
  })

  it('groups consecutive bullets into one list', () => {
    render(<Markdown text={'- one\n- two\n* three\n• four'} />)
    expect(screen.getAllByRole('list')).toHaveLength(1)
    expect(screen.getAllByRole('listitem')).toHaveLength(4)
  })

  it('starts a new list after a paragraph', () => {
    render(<Markdown text={'- a\nplain text\n- b'} />)
    expect(screen.getAllByRole('list')).toHaveLength(2)
    expect(screen.getByText('plain text')).toBeInTheDocument()
  })

  it('renders bold and italic text', () => {
    const { container } = render(<Markdown text={'This is **bold** and *italic* text'} />)
    expect(container.querySelector('strong')?.textContent).toBe('bold')
    expect(container.querySelector('em')?.textContent).toBe('italic')
  })

  it('shows raw HTML as plain text instead of inserting it', () => {
    const { container } = render(<Markdown text={'<script>alert(1)</script> and <b>x</b>'} />)
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument()
  })

  it('ignores blank lines and renders nothing for empty text', () => {
    const { container } = render(<Markdown text={''} />)
    expect(container.querySelectorAll('p, h4, ul')).toHaveLength(0)
  })

  it('does not treat a line starting with bold text as a bullet', () => {
    render(<Markdown text={'**Note:** this is a paragraph'} />)
    expect(screen.queryByRole('list')).toBeNull()
  })
})
