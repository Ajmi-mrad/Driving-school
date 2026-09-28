import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AlertTriangle,
  CheckCircle2,
  ChevronRight,
  Eye,
  Lightbulb,
  ListChecks,
  Loader2,
  Search,
  ShieldCheck,
  Square,
  XCircle,
} from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Textarea } from '@/components/ui/textarea'
import { opsApi, type OpsEvent, type OpsReport } from '@/core/api'
import { cn } from '@/lib/utils'

interface ToolStep {
  id: string
  tool: string
  input: string
  output?: string
  ok?: boolean
}

interface Turn {
  key: number
  question: string
  steps: ToolStep[]
  warnings: string[]
  report?: OpsReport
  error?: string
  running: boolean
}

const CONFIDENCE_TONE = { low: 'neutral', medium: 'warning', high: 'danger' } as const

/** Apply one streamed event to a turn (pure: returns the updated turn). */
function applyEvent(turn: Turn, event: OpsEvent): Turn {
  switch (event.type) {
    case 'tool_call':
      return { ...turn, steps: [...turn.steps, { id: event.id, tool: event.tool, input: event.input }] }
    case 'tool_result':
      return {
        ...turn,
        steps: turn.steps.map((s) => (s.id === event.id ? { ...s, output: event.output, ok: event.ok } : s)),
      }
    case 'warning':
      return { ...turn, warnings: [...turn.warnings, event.message] }
    case 'final':
      return { ...turn, report: event.report }
    case 'error':
      return { ...turn, error: event.message }
  }
}

export function OpsPage() {
  const { t } = useTranslation()
  const [question, setQuestion] = useState('')
  const [turns, setTurns] = useState<Turn[]>([])
  const abortRef = useRef<AbortController | null>(null)
  const running = turns.some((turn) => turn.running)

  // Leaving the page cancels the investigation (the backend stops on disconnect).
  useEffect(() => () => abortRef.current?.abort(), [])

  const ask = async (text: string) => {
    const q = text.trim()
    if (!q || running) return
    const key = Date.now()
    const update = (fn: (turn: Turn) => Turn) =>
      setTurns((all) => all.map((turn) => (turn.key === key ? fn(turn) : turn)))

    setQuestion('')
    setTurns((all) => [{ key, question: q, steps: [], warnings: [], running: true }, ...all])
    const controller = new AbortController()
    abortRef.current = controller
    try {
      await opsApi.ask(q, (event) => update((turn) => applyEvent(turn, event)), controller.signal)
    } catch (e) {
      update((turn) => ({ ...turn, error: (e as Error).message }))
    } finally {
      update((turn) => ({ ...turn, running: false }))
    }
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    void ask(question)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void ask(question)
    }
  }

  const examples = [t('ops.example1'), t('ops.example2'), t('ops.example3')]

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('ops.title')}
        description={t('ops.subtitle')}
        actions={
          <StatusBadge tone="success">
            <ShieldCheck className="size-3.5" />
            {t('ops.readOnly')}
          </StatusBadge>
        }
      />

      <Card>
        <CardContent className="space-y-3">
          <form onSubmit={onSubmit} className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <Textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder={t('ops.placeholder')}
              maxLength={1000}
              rows={2}
              className="min-h-0 resize-none"
              aria-label={t('ops.placeholder')}
            />
            {running ? (
              <Button type="button" variant="outline" onClick={() => abortRef.current?.abort()}>
                <Square />
                {t('ops.stop')}
              </Button>
            ) : (
              <Button type="submit" disabled={!question.trim()}>
                <Search />
                {t('ops.ask')}
              </Button>
            )}
          </form>
          <div className="flex flex-wrap gap-2">
            {examples.map((example) => (
              <Button
                key={example}
                type="button"
                variant="secondary"
                size="sm"
                disabled={running}
                onClick={() => void ask(example)}
              >
                {example}
              </Button>
            ))}
          </div>
        </CardContent>
      </Card>

      {turns.map((turn) => (
        <TurnCard key={turn.key} turn={turn} />
      ))}
    </div>
  )
}

function TurnCard({ turn }: { turn: Turn }) {
  const { t } = useTranslation()
  const anchor = (id: string) => `ops-${turn.key}-${id}`

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{turn.question}</CardTitle>
        {turn.running && (
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            {t('ops.running')} <span className="text-xs">{t('ops.rateLimitHint')}</span>
          </p>
        )}
      </CardHeader>
      <CardContent className="grid gap-6 lg:grid-cols-5">
        {/* Timeline: every tool call the agent made, with its evidence id. */}
        <section className="space-y-2 lg:col-span-2">
          <h3 className="text-sm font-medium text-muted-foreground">{t('ops.timeline')}</h3>
          {turn.warnings.map((warning) => (
            <p key={warning} className="flex items-center gap-2 text-sm text-warning">
              <AlertTriangle className="size-4 shrink-0" />
              {warning}
            </p>
          ))}
          {turn.steps.length === 0 && !turn.running && (
            <p className="text-sm text-muted-foreground">{t('ops.noToolCalls')}</p>
          )}
          <ol className="space-y-2">
            {turn.steps.map((step) => (
              <li key={step.id} id={anchor(step.id)} className="scroll-mt-20 rounded-md border p-2 text-sm target:ring-2 target:ring-primary">
                <Collapsible>
                  <CollapsibleTrigger className="group flex w-full items-center gap-2 text-start">
                    {step.ok === undefined ? (
                      <Loader2 className="size-4 shrink-0 animate-spin text-muted-foreground" />
                    ) : step.ok ? (
                      <CheckCircle2 className="size-4 shrink-0 text-success" />
                    ) : (
                      <XCircle className="size-4 shrink-0 text-destructive" />
                    )}
                    <span className="font-mono text-xs text-muted-foreground">{step.id}</span>
                    <span className="font-medium">{step.tool}</span>
                    <ChevronRight className="ms-auto size-4 shrink-0 transition-transform group-data-[state=open]:rotate-90" />
                  </CollapsibleTrigger>
                  <p className="mt-1 truncate font-mono text-xs text-muted-foreground" title={step.input}>
                    {step.input}
                  </p>
                  <CollapsibleContent>
                    <p className="mt-2 text-xs font-medium">{t('ops.output')}</p>
                    <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap break-all rounded bg-muted p-2 text-xs">
                      {step.output ?? '…'}
                    </pre>
                  </CollapsibleContent>
                </Collapsible>
              </li>
            ))}
          </ol>
        </section>

        {/* Report: facts / hypotheses / actions are kept visually distinct on purpose. */}
        <section className="space-y-4 lg:col-span-3">
          {turn.error && (
            <p className="flex items-start gap-2 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
              <XCircle className="mt-0.5 size-4 shrink-0" />
              {t('ops.failed', { message: turn.error })}
            </p>
          )}
          {turn.report && (
            <>
              {turn.report.summary && (
                <div>
                  <h3 className="text-sm font-medium text-muted-foreground">{t('ops.summary')}</h3>
                  <p className="mt-1 text-sm">{turn.report.summary}</p>
                </div>
              )}

              <ReportSection
                icon={<Eye className="size-4" />}
                title={t('ops.facts')}
                hint={t('ops.factsHint')}
                tone="border-success/40 bg-success/5"
                empty={turn.report.facts.length === 0}
              >
                {turn.report.facts.map((fact) => (
                  <li key={fact.statement}>
                    {fact.statement}{' '}
                    {fact.evidence.map((id) => (
                      <a
                        key={id}
                        href={`#${anchor(id)}`}
                        className="ms-1 rounded bg-success/15 px-1.5 py-0.5 font-mono text-xs text-success hover:underline"
                      >
                        {id}
                      </a>
                    ))}
                  </li>
                ))}
              </ReportSection>

              <ReportSection
                icon={<Lightbulb className="size-4" />}
                title={t('ops.hypotheses')}
                hint={t('ops.hypothesesHint')}
                tone="border-warning/40 bg-warning/5"
                empty={turn.report.hypotheses.length === 0}
              >
                {turn.report.hypotheses.map((hypothesis) => {
                  const level = (['low', 'medium', 'high'] as const).find((c) => c === hypothesis.confidence) ?? 'low'
                  return (
                    <li key={hypothesis.statement}>
                      {hypothesis.statement}{' '}
                      <StatusBadge tone={CONFIDENCE_TONE[level]} className="ms-1 align-middle">
                        {t(`ops.confidence.${level}`)}
                      </StatusBadge>
                    </li>
                  )
                })}
              </ReportSection>

              <ReportSection
                icon={<ListChecks className="size-4" />}
                title={t('ops.actions')}
                hint={t('ops.actionsHint')}
                tone="border-info/40 bg-info/5"
                empty={turn.report.actions.length === 0}
                ordered
              >
                {turn.report.actions.map((action) => (
                  <li key={action}>{action}</li>
                ))}
              </ReportSection>
            </>
          )}
        </section>
      </CardContent>
    </Card>
  )
}

function ReportSection({
  icon,
  title,
  hint,
  tone,
  empty,
  ordered = false,
  children,
}: {
  icon: React.ReactNode
  title: string
  hint: string
  tone: string
  empty: boolean
  ordered?: boolean
  children: React.ReactNode
}) {
  const { t } = useTranslation()
  const List = ordered ? 'ol' : 'ul'
  return (
    <div className={cn('rounded-lg border p-3', tone)}>
      <h3 className="flex items-center gap-2 text-sm font-semibold">
        {icon}
        {title}
      </h3>
      <p className="text-xs text-muted-foreground">{hint}</p>
      {empty ? (
        <p className="mt-2 text-sm text-muted-foreground">{t('ops.none')}</p>
      ) : (
        <List className={cn('mt-2 space-y-1.5 ps-5 text-sm', ordered ? 'list-decimal' : 'list-disc')}>
          {children}
        </List>
      )}
    </div>
  )
}
