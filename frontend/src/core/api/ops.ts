import { requestBlob, streamEvents } from './client'

/** A fact the AI observed, backed by the ids of the tool calls that show it (e.g. "t2"). */
export interface OpsFact {
  statement: string
  evidence: string[]
}

export interface OpsHypothesis {
  statement: string
  confidence: 'low' | 'medium' | 'high' | string
}

/** Final report of an investigation (facts are verified server-side against real tool calls). */
export interface OpsReport {
  summary: string
  facts: OpsFact[]
  hypotheses: OpsHypothesis[]
  actions: string[]
}

export type OpsEvent =
  | { type: 'tool_call'; id: string; tool: string; input: string }
  | { type: 'tool_result'; id: string; ok: boolean; output: string }
  | { type: 'warning'; message: string }
  | { type: 'final'; report: OpsReport }
  | { type: 'error'; message: string }

/**
 * AI Ops assistant (owner-only, read-only). `ask` streams the investigation:
 * tool calls and their results as they happen, then the final report.
 */
export const opsApi = {
  async ask(question: string, onEvent: (event: OpsEvent) => void, signal?: AbortSignal): Promise<void> {
    let finished = false
    await streamEvents(
      '/ai/ops/ask',
      { question },
      (name, data) => {
        if (name === 'final' || name === 'error') finished = true
        if (name === 'final') onEvent({ type: 'final', report: data as OpsReport })
        else onEvent({ type: name, ...(data as object) } as OpsEvent)
      },
      signal,
    )
    // The server closed the stream without a report or an error (e.g. its 5-minute timeout).
    if (!finished && !signal?.aborted) {
      throw new Error('The investigation ended without a result (server timeout?)')
    }
  },

  /** Text to speech (Murf Falcon, via ai-service): returns an MP3. `language` = UI language code. */
  speak(text: string, language: string): Promise<Blob> {
    return requestBlob('/ai/ops/speak', { text, language })
  },
}
