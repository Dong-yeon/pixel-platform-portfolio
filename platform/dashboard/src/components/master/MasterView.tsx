import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api'
import type { BomNode, BomRevision, Part, PartType, VehicleModel } from '../../types'

const PART_TYPE_LABEL: Record<PartType, string> = {
  PRODUCT: '완제품',
  SEMI: '반제품',
  MATERIAL: '자재',
}

/**
 * 생산 기준정보 — 차종 → 품번 → BOM.
 *
 * <p>"이 공장이 무엇을 만드는가"를 보여준다. 지금까지 화면에는 작업지시 번호만 떴다.
 *
 * <p><b>개정 버튼은 rev를 보내지 않는다.</b> 화면이 보던 rev에 +1 하면 최신이 아닌 rev를
 * 띄워 둔 채 누를 때 이미 있는 rev와 부딪혀 중복이 적재된다(실 운영 MES 사고).
 * 대상은 서버가 DB의 MAX+1로 정한다.
 */
export function MasterView({ role }: { role: string }) {
  const [models, setModels] = useState<VehicleModel[]>([])
  const [modelCode, setModelCode] = useState<string>('')
  const [parts, setParts] = useState<Part[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [tree, setTree] = useState<BomNode | null>(null)
  const [revisions, setRevisions] = useState<BomRevision[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    api.factory.vehicleModels().then(setModels).catch(() => {})
  }, [])

  useEffect(() => {
    api.factory.parts(modelCode || undefined).then(setParts).catch(() => {})
  }, [modelCode])

  const loadBom = useCallback((partCode: string) => {
    api.factory.bomTree(partCode).then(setTree).catch(() => setTree(null))
    api.factory.bomRevisions(partCode).then(setRevisions).catch(() => setRevisions([]))
  }, [])

  function selectPart(partCode: string) {
    setSelected(partCode)
    setNotice(null)
    setError(null)
    loadBom(partCode)
  }

  async function revise() {
    if (!selected) return
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const result = await api.factory.reviseBom(selected)
      setNotice(`${result.partCode} rev ${result.revNo} 로 개정했습니다.`)
      loadBom(selected)
    } catch (err) {
      setError(err instanceof Error ? err.message : '개정에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid">
      <section className="card">
        <div className="module-head">
          <h2>품번</h2>
          <label className="umap-view-pick">
            차종
            <select value={modelCode} onChange={(e) => setModelCode(e.target.value)}>
              <option value="">전체 (공용 부품 포함)</option>
              {models.map((m) => (
                <option key={m.modelCode} value={m.modelCode}>
                  {m.name} ({m.modelCode})
                </option>
              ))}
            </select>
          </label>
        </div>
        {parts.length === 0 ? (
          <p className="muted small">품번이 없습니다.</p>
        ) : (
          <div className="wo-list">
            {parts.map((part) => (
              <div
                key={part.partCode}
                className={`wo-row part-row ${selected === part.partCode ? 'selected' : ''}`}
                onClick={() => selectPart(part.partCode)}
                role="button"
                tabIndex={0}
              >
                <div className="wo-top">
                  <span className="mono">{part.partCode}</span>
                  <span className={`badge part-${part.partType}`}>{PART_TYPE_LABEL[part.partType]}</span>
                </div>
                <div className="muted small">
                  {part.name} · {part.unit}
                  {part.modelCode && ` · ${part.modelCode}`}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="card">
        <div className="module-head">
          <h2>BOM {selected && <span className="mono small">{selected}</span>}</h2>
          {selected && role === 'ADMIN' && (
            <button className="mini" disabled={busy} onClick={revise}>
              {busy ? '개정 중…' : 'BOM 개정'}
            </button>
          )}
        </div>
        {error && <div className="error">{error}</div>}
        {notice && <div className="small" style={{ color: '#1a7f4b' }}>{notice}</div>}

        {!selected && <p className="muted small">왼쪽에서 품번을 고르면 구성이 보입니다.</p>}
        {selected && tree && (
          <>
            <div className="bom-tree">
              <BomRow node={tree} />
            </div>
            {revisions.length > 0 && (
              <div className="bom-revs muted small">
                개정 이력:{' '}
                {revisions.map((r) => (
                  <span key={r.revNo} className={r.latest ? 'rev latest' : 'rev'}>
                    rev{r.revNo}({r.lineCount})
                  </span>
                ))}
              </div>
            )}
          </>
        )}
        {selected && tree && tree.children.length === 0 && (
          <p className="muted small">구성이 없는 품번입니다(자재 또는 미등록).</p>
        )}
      </section>
    </div>
  )
}

/** 중첩 그대로 그린다 — 서버가 트리를 조립해 주므로 화면이 부모를 추측할 일이 없다. */
function BomRow({ node }: { node: BomNode }) {
  return (
    <div className="bom-node" style={{ marginLeft: node.level * 16 }}>
      <div className="bom-line">
        <span className="mono">{node.partCode}</span>
        <span className="muted"> {node.name}</span>
        {node.qtyPer && <span className="bom-qty">× {Number(node.qtyPer)}</span>}
        <span className={`badge part-${node.partType}`}>{PART_TYPE_LABEL[node.partType]}</span>
      </div>
      {node.children.map((child) => (
        <BomRow key={`${child.partCode}-${child.level}`} node={child} />
      ))}
    </div>
  )
}
