import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('./api/client')>()
  return { ...actual, fetchHealth: vi.fn(), listLibrary: vi.fn() }
})

import { fetchHealth, listLibrary } from './api/client'
import App from './App'

beforeEach(() => {
  vi.mocked(fetchHealth).mockReset().mockResolvedValue({ status: 'UP', database: 'UP', time: 'now' })
  vi.mocked(listLibrary).mockReset().mockResolvedValue([])
})

describe('App routing (round 1: file had 0% coverage)', () => {
  it('shows the home page at /', async () => {
    window.history.pushState({}, '', '/')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Your research workspace' })).toBeInTheDocument()
  })

  it('shows the upload page at /upload', async () => {
    window.history.pushState({}, '', '/upload')
    render(<App />)
    expect(await screen.findByText('Drop a PDF here, or click to choose one')).toBeInTheDocument()
  })

  it('sends an unknown address back to the home page', async () => {
    window.history.pushState({}, '', '/no/such/page')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Your research workspace' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
  })

  it('shows the navigation on every page', async () => {
    window.history.pushState({}, '', '/library')
    render(<App />)
    expect(await screen.findByRole('link', { name: 'Discover Papers' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'My Library' })).toBeInTheDocument()
  })
})
