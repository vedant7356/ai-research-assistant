import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/client', async importOriginal => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, fetchHealth: vi.fn() }
})

import { fetchHealth } from '../api/client'
import Layout from './Layout'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<p>Home content</p>} />
          <Route path="/discover" element={<p>Discover content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => vi.mocked(fetchHealth).mockReset())

describe('Layout', () => {
  it('shows the navigation links and the routed page', () => {
    vi.mocked(fetchHealth).mockResolvedValue({ status: 'UP', database: 'UP', time: 'now' })
    renderAt('/discover')
    for (const name of ['Discover Papers', 'My Library', 'Upload Papers']) {
      expect(screen.getByRole('link', { name })).toBeInTheDocument()
    }
    expect(screen.getAllByRole('link', { name: 'Home' }).length).toBeGreaterThan(0)
    expect(screen.getByText('Discover content')).toBeInTheDocument()
  })

  it('marks the current page as active', () => {
    vi.mocked(fetchHealth).mockResolvedValue({ status: 'UP', database: 'UP', time: 'now' })
    renderAt('/discover')
    expect(screen.getByRole('link', { name: 'Discover Papers' })).toHaveClass('active')
    expect(screen.getByRole('link', { name: 'My Library' })).not.toHaveClass('active')
  })

  it('shows backend and database as up when the health check succeeds', async () => {
    vi.mocked(fetchHealth).mockResolvedValue({ status: 'UP', database: 'UP', time: 'now' })
    renderAt('/')
    expect(await screen.findByText(/Backend up/)).toBeInTheDocument()
    expect(screen.getByText(/Database up/)).toBeInTheDocument()
  })

  it('shows checking while the health request is pending', () => {
    vi.mocked(fetchHealth).mockReturnValue(new Promise(() => {}))
    renderAt('/')
    expect(screen.getByText(/Backend checking/)).toBeInTheDocument()
    expect(screen.getByText(/Database checking/)).toBeInTheDocument()
  })

  it('shows the database as down when only the database fails', async () => {
    vi.mocked(fetchHealth).mockResolvedValue({ status: 'UP', database: 'DOWN', time: 'now' })
    renderAt('/')
    expect(await screen.findByText(/Database down/)).toBeInTheDocument()
    expect(screen.getByText(/Backend up/)).toBeInTheDocument()
  })

  it('shows the backend as unreachable when the health request fails', async () => {
    vi.mocked(fetchHealth).mockRejectedValue(new Error('network'))
    renderAt('/')
    expect(await screen.findByText(/Backend unreachable/)).toBeInTheDocument()
    expect(screen.getByText(/Database down/)).toBeInTheDocument()
  })

  it('uses the wide layout only on the home page', () => {
    vi.mocked(fetchHealth).mockResolvedValue({ status: 'UP', database: 'UP', time: 'now' })
    const { container, unmount } = renderAt('/')
    expect(container.querySelector('.content-wide')).not.toBeNull()
    unmount()
    const second = renderAt('/discover')
    expect(second.container.querySelector('.content-wide')).toBeNull()
  })
})
