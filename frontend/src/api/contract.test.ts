import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, getPaper } from './client'

// Shared with the backend test ErrorContractTest, which checks the backend really sends exactly these bodies.
const samples = JSON.parse(readFileSync(resolve(process.cwd(), '..', 'contract', 'error-envelope.json'), 'utf8')) as
  Record<string, { status: number; body: { error: { code: string; message: string; retryable: boolean } } }>

afterEach(() => vi.unstubAllGlobals())

describe('error contract with the backend (round 2)', () => {
  it('has the five documented samples', () => {
    expect(Object.keys(samples).sort()).toEqual(['fileTooLarge', 'llmDown', 'missingTitle', 'notInLibrary', 'unexpected'])
  })

  it.each(Object.entries(samples))('turns the backend "%s" response into the matching ApiError', async (_name, sample) => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(sample.body), {
      status: sample.status, headers: { 'Content-Type': 'application/json' },
    })))
    const err = await getPaper(1).catch(e => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({
      code: sample.body.error.code,
      message: sample.body.error.message,
      retryable: sample.body.error.retryable,
      status: sample.status,
    })
  })
})
