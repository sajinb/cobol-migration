import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react'
import { streamLogs, getLogs } from '../services/api'

const STEPS = ['mapa', 'ingest', 'analyse', 'migrate', 'validate']

const STEP_DESC = {
  mapa:     'Run the MAPA static analyser to parse COBOL source files and produce the structural CSV',
  ingest:   'Import MAPA CSV output into Neo4j — creates Program, Paragraph, DataItem and Copybook nodes',
  analyse:  'Traverse the graph to classify paragraph complexity, detect PERFORM chains and shared data flow',
  migrate:  'Convert each paragraph to a Java Spring Boot service method using graph context and LLM',
  validate: 'Check generated Java code compiles, all PERFORM calls are resolved and data items are mapped',
}

const LEVEL_CLASS = {
  ERROR:   'text-red-400',
  WARNING: 'text-yellow-400',
  INFO:    'text-slate-300',
  DEBUG:   'text-slate-500',
}

function StepBar({ activeStep, status }) {
  const done  = status === 'completed'
  const failed = status === 'failed'
  const idx   = STEPS.indexOf(activeStep)

  return (
    <div className="flex items-center gap-1 flex-wrap mb-4">
      {STEPS.map((step, i) => {
        const isActive   = step === activeStep && !done && !failed
        const isDone     = done || i < idx || (failed && i < idx)
        const isFailed   = failed && step === activeStep
        return (
          <div key={step} className="flex items-center gap-1">
            <div className="relative group">
              <span
                className={`text-xs font-semibold px-3 py-1 rounded-full uppercase tracking-wide transition-all cursor-default
                  ${isFailed  ? 'bg-red-500/20 text-red-400 border border-red-500/40' :
                    isDone    ? 'bg-green-500/20 text-green-400 border border-green-500/40' :
                    isActive  ? 'bg-blue-500/20 text-blue-400 border border-blue-500/40 animate-pulse' :
                                'bg-slate-800 text-slate-500 border border-slate-700'}`}
              >
                {step}
              </span>
              <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-56 px-3 py-2
                              bg-slate-800 border border-slate-600 rounded-lg text-slate-300 text-xs
                              leading-snug shadow-xl pointer-events-none
                              opacity-0 group-hover:opacity-100 transition-opacity duration-150 z-10">
                {STEP_DESC[step]}
                <div className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-slate-600" />
              </div>
            </div>
            {i < STEPS.length - 1 && <span className="text-slate-700">→</span>}
          </div>
        )
      })}
    </div>
  )
}

export default function MigrationProgress({ projectId, runId, initialStatus, onComplete }) {
  const [logs, setLogs]         = useState([])
  const [status, setStatus]     = useState(initialStatus || 'running')
  const [activeStep, setActive] = useState('mapa')
  const logBoxRef               = useRef()

  // If already completed/failed, load logs from API
  useEffect(() => {
    if (initialStatus && initialStatus !== 'running' && initialStatus !== 'pending') {
      getLogs(projectId, runId).then(setLogs)
      setStatus(initialStatus)
    }
  }, [runId])

  // Stream logs for active runs
  useEffect(() => {
    if (status !== 'running' && status !== 'pending') return
    const close = streamLogs(
      projectId,
      runId,
      (log) => {
        setLogs(prev => [...prev, log])
        if (log.step && STEPS.includes(log.step)) setActive(log.step)
      },
      (result) => { setStatus(result.status); onComplete?.() },
      () => {},
    )
    return close
  }, [runId, status])

  // Auto-scroll the log box to bottom (not the whole page)
  useEffect(() => {
    const el = logBoxRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [logs])

  const running = status === 'running' || status === 'pending'

  return (
    <div className="space-y-3">
      {/* Status header */}
      <div className="flex items-center gap-3">
        {running    && <Loader2 className="animate-spin text-blue-400" size={18} />}
        {status === 'completed' && <CheckCircle2 className="text-green-400" size={18} />}
        {status === 'failed'    && <XCircle      className="text-red-400"   size={18} />}
        <span className="font-semibold capitalize">{status}</span>
        <span className="text-slate-500 text-xs font-mono ml-auto">{logs.length} lines</span>
      </div>

      {/* Step progress */}
      <StepBar activeStep={activeStep} status={status} />

      {/* Terminal log */}
      <div ref={logBoxRef} className="bg-slate-950 border border-slate-800 rounded-xl p-4 h-96 overflow-y-auto font-mono text-xs leading-relaxed">
        {logs.length === 0 && running && (
          <span className="text-slate-600">Waiting for output…</span>
        )}
        {logs.map((log) => (
          <div key={log.id} className={`${LEVEL_CLASS[log.level] || LEVEL_CLASS.INFO}`}>
            <span className="text-slate-600 mr-2 select-none">
              {new Date(log.timestamp).toLocaleTimeString()}
            </span>
            <span className={`mr-2 select-none ${
              log.level === 'ERROR'   ? 'text-red-500' :
              log.level === 'WARNING' ? 'text-yellow-500' : 'text-slate-600'
            }`}>
              [{log.step || log.level}]
            </span>
            {log.message}
          </div>
        ))}
      </div>
    </div>
  )
}
