import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import NaverMap from './NaverMap'
import {
  deleteBuildingById, getMapData, publishMap, type MapData, type MapEdge, type MapNode,
} from './api/adminMapApi'

type Mode = 'SELECT' | 'ADD_BUILDING' | 'ADD_NODE' | 'ADD_EDGE' | 'DELETE'

export default function AdminMapEditor() {
  const [data, setData] = useState<MapData>({ buildings: [], nodes: [], edges: [] })
  const [mode, setMode] = useState<Mode>('SELECT')
  const [selected, setSelected] = useState<number[]>([])
  const selectedRef = useRef<number[]>([])
  const [selectedBuildingId, setSelectedBuildingId] = useState<number>()
  const [selectedEdgeId, setSelectedEdgeId] = useState<number>()
  const [deletedBuildingIds, setDeletedBuildingIds] = useState<number[]>([])
  const [deletedNodeIds, setDeletedNodeIds] = useState<number[]>([])
  const [deletedEdgeIds, setDeletedEdgeIds] = useState<number[]>([])
  const [edgeStartNodeId, setEdgeStartNodeId] = useState('')
  const [edgeEndNodeId, setEdgeEndNodeId] = useState('')
  const [message, setMessage] = useState('지도의 노드를 선택하거나 새로 추가하세요.')
  const [saving, setSaving] = useState(false)
  useEffect(() => { selectedRef.current = selected }, [selected])
  const modeRef = useRef<Mode>(mode)
  const dataRef = useRef<MapData>(data)
  useEffect(() => { modeRef.current = mode }, [mode])
  useEffect(() => { dataRef.current = data }, [data])

  useEffect(() => { getMapData().then(setData).catch(() => setMessage('서버에서 지도 데이터를 불러오지 못했습니다.')) }, [])
  const visibleNodes = useMemo(() => data.nodes.filter(node => !node.virtualNode), [data.nodes])
  const visibleNodeIds = useMemo(() => new Set(visibleNodes.map(node => node.id)), [visibleNodes])
  const visibleEdges = useMemo(() => data.edges.filter(edge =>
    visibleNodeIds.has(edge.startNodeId) && visibleNodeIds.has(edge.endNodeId)), [data.edges, visibleNodeIds])
  const mapPlaces = useMemo(() => visibleNodes.map(node => ({
    id: node.id, name: node.name, detail: node.floor ? `${node.floor}층` : '외부',
    latitude: node.latitude, longitude: node.longitude,
  })), [visibleNodes])
  const buildingPlaces = useMemo(() => data.buildings.map(building => ({
    id: building.id, name: building.name, detail: building.detail,
    latitude: building.latitude, longitude: building.longitude,
  })), [data.buildings])
  const fallback = mapPlaces[0] ?? { id: 0, name: '숙명여대', detail: '', latitude: 37.54525, longitude: 126.96455 }

  const addNode = useCallback((latitude: number, longitude: number) => {
    if (modeRef.current === 'ADD_BUILDING') {
      const id = -Date.now()
      setData(value => ({ ...value, buildings: [...value.buildings, {
        id, name: '새 건물', detail: '건물 설명', latitude, longitude,
        floorCount: 1, basementFloorCount: 0,
      }] }))
      setSelectedBuildingId(id)
      setSelectedEdgeId(undefined)
      setSelected([])
      setMessage('새 건물이 추가되었습니다. 오른쪽에서 이름과 설명을 수정하세요.')
      return
    }
    if (modeRef.current !== 'ADD_NODE') return
    const id = -Date.now()
    const node: MapNode = { id, name: '새 노드', latitude, longitude, floor: null, nodeType: 'OUTDOOR',
      buildingId: null, indoorX: null, indoorY: null, virtualNode: false }
    setData(value => ({ ...value, nodes: [...value.nodes, node] }))
    setSelectedBuildingId(undefined)
    setSelectedEdgeId(undefined)
    setSelected([id]); setMessage('새 노드가 추가되었습니다. 오른쪽에서 속성을 수정하세요.')
  }, [])

  const createEdge = useCallback((startNodeId: number, endNodeId: number) => {
    if (startNodeId === endNodeId) {
      setMessage('같은 노드는 서로 연결할 수 없습니다.')
      return false
    }
    const currentData = dataRef.current
    const a = currentData.nodes.find(node => node.id === startNodeId)
    const b = currentData.nodes.find(node => node.id === endNodeId)
    if (!a || !b) {
      setMessage('입력한 ID에 해당하는 노드를 찾을 수 없습니다.')
      return false
    }
    const alreadyExists = currentData.edges.some(edge =>
      (edge.startNodeId === startNodeId && edge.endNodeId === endNodeId)
      || (edge.startNodeId === endNodeId && edge.endNodeId === startNodeId))
    if (alreadyExists) {
      selectedRef.current = []
      setSelected([])
      setMessage('두 노드 사이에 이미 Edge가 있습니다.')
      return false
    }
    const distance = haversine(a.latitude, a.longitude, b.latitude, b.longitude)
    const sameBuilding = a.buildingId != null && a.buildingId === b.buildingId
    const edge: MapEdge = {
      id: -Date.now(), startNodeId: a.id, endNodeId: b.id,
      pathType: sameBuilding ? 'CORRIDOR' : 'OUTDOOR',
      distanceMeters: Math.round(distance), durationSeconds: Math.round(distance / 1.3),
      indoor: sameBuilding, rainExposure: sameBuilding ? 0 : 1,
      stairCount: 0, wheelchairAccessible: true, bidirectional: true,
      connectionFloors: [],
    }
    setData(current => ({ ...current, edges: [...current.edges, edge] }))
    selectedRef.current = []
    setSelected([])
    setSelectedEdgeId(edge.id)
    setMessage(`${a.name} ↔ ${b.name} Edge를 만들었습니다.${sameBuilding ? ' 같은 건물이므로 실내로 설정했습니다.' : ''}`)
    return true
  }, [])

  const selectNode = useCallback((id: number) => {
    setSelectedBuildingId(undefined)
    setSelectedEdgeId(undefined)
    if (modeRef.current === 'DELETE') {
      const connectedEdgeIds = dataRef.current.edges
        .filter(edge => edge.startNodeId === id || edge.endNodeId === id)
        .map(edge => edge.id)
      setData(value => ({ ...value, nodes: value.nodes.filter(node => node.id !== id),
        edges: value.edges.filter(edge => edge.startNodeId !== id && edge.endNodeId !== id) }))
      if (id > 0) setDeletedNodeIds(value => [...value, id])
      setDeletedEdgeIds(value => [
        ...value,
        ...connectedEdgeIds.filter(edgeId => edgeId > 0 && !value.includes(edgeId)),
      ])
      setSelected([]); return
    }
    if (modeRef.current === 'ADD_EDGE') {
      const currentSelection = selectedRef.current
      if (currentSelection.length !== 1) {
        selectedRef.current = [id]
        setSelected([id])
        setMessage('연결할 두 번째 노드를 선택하세요.')
        return
      }
      const startNodeId = currentSelection[0]
      createEdge(startNodeId, id)
      return
    }
    setSelected([id])
  }, [createEdge])
  const selectBuilding = useCallback((id: number) => {
    if (modeRef.current === 'ADD_EDGE') {
      setMessage('Edge를 만들 때는 원형 Node 마커를 선택하세요.')
      return
    }
    setSelectedBuildingId(id)
    setSelectedEdgeId(undefined)
    setSelected([])
  }, [])
  const moveNode = useCallback((id: number, latitude: number, longitude: number) => {
    setData(current => {
      const nodes = current.nodes.map(node =>
        node.id === id ? { ...node, latitude, longitude } : node)
      const nodeById = new Map(nodes.map(node => [node.id, node]))
      const edges = current.edges.map(edge => {
        if (edge.startNodeId !== id && edge.endNodeId !== id) return edge
        const startNode = nodeById.get(edge.startNodeId)
        const endNode = nodeById.get(edge.endNodeId)
        if (!startNode || !endNode) return edge
        const distanceMeters = Math.round(haversine(
          startNode.latitude, startNode.longitude,
          endNode.latitude, endNode.longitude,
        ))
        return {
          ...edge,
          distanceMeters,
          durationSeconds: Math.round(distanceMeters / 1.3),
        }
      })
      return { ...current, nodes, edges }
    })
    setSelectedBuildingId(undefined)
    setSelectedEdgeId(undefined)
    setSelected([id])
    setMessage(`Node ${id}의 위치와 연결된 Edge의 거리·예상 시간을 다시 계산했습니다.`)
  }, [])

  const selectedNode = data.nodes.find(node => node.id === selected[0])
  const selectedBuilding = data.buildings.find(building => building.id === selectedBuildingId)
  const selectedEdge = data.edges.find(edge => edge.id === selectedEdgeId)
  const selectedEdgeStart = selectedEdge
    ? data.nodes.find(node => node.id === selectedEdge.startNodeId) : undefined
  const selectedEdgeEnd = selectedEdge
    ? data.nodes.find(node => node.id === selectedEdge.endNodeId) : undefined
  const isConnectorEdge = selectedEdgeStart?.nodeType === 'CONNECTOR'
    && selectedEdgeEnd?.nodeType === 'CONNECTOR'
  const connectionFloorOptions = (() => {
    if (!isConnectorEdge) return []
    const startBuilding = data.buildings.find(building => building.id === selectedEdgeStart?.buildingId)
    const endBuilding = data.buildings.find(building => building.id === selectedEdgeEnd?.buildingId)
    if (!startBuilding || !endBuilding) return []
    const basementCount = Math.min(
      startBuilding.basementFloorCount ?? 0, endBuilding.basementFloorCount ?? 0)
    const floorCount = Math.min(startBuilding.floorCount, endBuilding.floorCount)
    return [
      ...Array.from({ length: basementCount }, (_, index) => -basementCount + index),
      ...Array.from({ length: floorCount }, (_, index) => index + 1),
    ]
  })()
  const updateNode = (patch: Partial<MapNode>) => {
    if (!selectedNode) return
    setData(value => ({ ...value, nodes: value.nodes.map(node => node.id === selectedNode.id ? { ...node, ...patch } : node) }))
  }
  const updateBuilding = (patch: Partial<MapData['buildings'][number]>) => {
    if (!selectedBuilding) return
    setData(value => ({ ...value, buildings: value.buildings.map(building =>
      building.id === selectedBuilding.id ? { ...building, ...patch } : building) }))
  }
  const updateEdge = (patch: Partial<MapEdge>) => {
    if (!selectedEdge) return
    setData(value => ({ ...value, edges: value.edges.map(edge =>
      edge.id === selectedEdge.id ? { ...edge, ...patch } : edge) }))
  }
  const save = async () => {
    if (saving) return
    setSaving(true)
    setMessage('Node·Edge를 PostgreSQL에 저장하는 중입니다…')
    try {
      await publishMap({
        buildings: data.buildings, nodes: data.nodes, edges: data.edges,
        deletedBuildingIds, deletedNodeIds, deletedEdgeIds,
      })
      const fresh = await getMapData()
      setData(fresh)
      setDeletedNodeIds([])
      setDeletedEdgeIds([])
      setDeletedBuildingIds([])
      setSelected([])
      setSelectedEdgeId(undefined)
      selectedRef.current = []
      setMessage('저장 완료: DB 저장과 경로 그래프 재적용이 완료되었습니다.')
      localStorage.setItem('noongill-map-published-at', String(Date.now()))
    } catch {
      setMessage('저장 실패: Spring Boot가 8080 포트에서 실행 중인지 확인해 주세요.')
    } finally {
      setSaving(false)
    }
  }
  const deleteBuilding = async (id: number, name: string) => {
    if (!window.confirm(`'${name}' 건물을 삭제할까요?\n연결된 노드는 유지되며 건물 연결만 해제됩니다.`)) return
    if (id > 0) {
      try {
        await deleteBuildingById(id)
      } catch {
        setMessage('건물 삭제에 실패했습니다. Spring Boot 실행 상태를 확인해 주세요.')
        return
      }
    }
    setData(value => ({
      ...value,
      buildings: value.buildings.filter(building => building.id !== id),
      nodes: value.nodes.map(node => node.buildingId === id ? { ...node, buildingId: null } : node),
    }))
    if (selectedBuildingId === id) setSelectedBuildingId(undefined)
    localStorage.setItem('noongill-map-published-at', String(Date.now()))
    setMessage(`${name} 건물을 DB에서 삭제했습니다.`)
  }

  return <div className="admin-page">
    <header className="admin-header"><div><b>눈길 지도 편집기</b><span>Node · Edge 관리자</span></div>
      <a href="/">사용자 지도 보기</a></header>
    <div className="admin-toolbar">
      {(['SELECT','ADD_BUILDING','ADD_NODE','ADD_EDGE','DELETE'] as Mode[]).map(value =>
        <button className={mode === value ? 'active' : ''} onClick={() => {
          modeRef.current = value
          selectedRef.current = []
          setMode(value)
          setSelected([])
          setSelectedEdgeId(undefined)
        }} key={value}>{value}</button>)}
      <button className="publish-button" disabled={saving} onClick={() => void save()}>
        {saving ? '저장 중…' : '저장하고 게시'}
      </button>
    </div>
    <main className="admin-workspace">
      <section className="admin-map"><NaverMap places={mapPlaces} start={fallback} end={fallback}
        buildingPlaces={buildingPlaces} selectedBuildingId={selectedBuildingId}
        graphNodes={visibleNodes} graphEdges={visibleEdges}
        compactMarkers
        onMapClick={addNode} onPlaceClick={selectNode} onPlaceMove={moveNode}
        onBuildingClick={selectBuilding} /></section>
      <aside className="admin-panel">
        <p className="admin-message">{message}</p>
        {mode === 'ADD_EDGE' && <div className="manual-edge-form">
          <h3>Node ID로 Edge 추가</h3>
          <div>
            <label>시작 Node ID<input type="number" value={edgeStartNodeId}
              onChange={e => setEdgeStartNodeId(e.target.value)} placeholder="예: 22"/></label>
            <label>도착 Node ID<input type="number" value={edgeEndNodeId}
              onChange={e => setEdgeEndNodeId(e.target.value)} placeholder="예: 56"/></label>
          </div>
          <button onClick={() => {
            if (!edgeStartNodeId || !edgeEndNodeId) {
              setMessage('시작 노드 ID와 도착 노드 ID를 모두 입력하세요.')
              return
            }
            if (createEdge(Number(edgeStartNodeId), Number(edgeEndNodeId))) {
              setEdgeStartNodeId('')
              setEdgeEndNodeId('')
            }
          }}>Edge 추가</button>
          <p className="field-help">지도에서 노드 두 개를 선택하는 기존 방식도 함께 사용할 수 있습니다.</p>
        </div>}
        {selectedEdge ? <div className="property-form edge-property-form">
          <h3>Edge 속성</h3>
          <small>Node {selectedEdge.startNodeId} → Node {selectedEdge.endNodeId}</small>
          <label>이동 구간 유형<select value={selectedEdge.pathType}
            onChange={e => updateEdge({ pathType: e.target.value })}>
            {[
              ['OUTDOOR', '야외 길'],
              ['ENTRANCE', '출입구'],
              ['CORRIDOR', '실내 복도'],
              ['STAIRS', '계단'],
              ['ELEVATOR', '엘리베이터'],
              ['COVERED_PATH', '지붕 있는 길'],
              ['BUILDING_CONNECTION', '건물 연결통로'],
            ].map(([value, label]) => <option value={value} key={value}>{label} ({value})</option>)}
          </select></label>
          <div className="edge-number-grid">
            <label>거리(m)<input type="number" min="0" step="1" value={selectedEdge.distanceMeters}
              onChange={e => updateEdge({ distanceMeters: Number(e.target.value) })}/></label>
            <label>예상 시간(초)<input type="number" min="0" step="1" value={selectedEdge.durationSeconds}
              onChange={e => updateEdge({ durationSeconds: Number(e.target.value) })}/></label>
            <label>비 노출도(0~1)<input type="number" min="0" max="1" step="0.1" value={selectedEdge.rainExposure}
              onChange={e => updateEdge({ rainExposure: Number(e.target.value) })}/></label>
            <label>계단 수<input type="number" min="0" step="1" value={selectedEdge.stairCount}
              onChange={e => updateEdge({ stairCount: Number(e.target.value) })}/></label>
          </div>
          <label className="check-field"><input type="checkbox" checked={selectedEdge.indoor}
            onChange={e => updateEdge({ indoor: e.target.checked })}/> 실내 구간</label>
          <label className="check-field"><input type="checkbox" checked={selectedEdge.wheelchairAccessible}
            onChange={e => updateEdge({ wheelchairAccessible: e.target.checked })}/> 휠체어 통행 가능</label>
          <label className="check-field"><input type="checkbox" checked={selectedEdge.bidirectional}
            onChange={e => updateEdge({ bidirectional: e.target.checked })}/> 양방향 통행</label>
          {isConnectorEdge && <fieldset className="connection-floor-field">
            <legend>연결되는 층</legend>
            <div>{connectionFloorOptions.map(floor =>
              <label key={floor}><input type="checkbox"
                checked={(selectedEdge.connectionFloors ?? []).includes(floor)}
                onChange={e => {
                  const floors = selectedEdge.connectionFloors ?? []
                  updateEdge({ connectionFloors: e.target.checked
                    ? [...floors, floor].sort((a, b) => a - b)
                    : floors.filter(value => value !== floor) })
                }}/>{floor < 0 ? `B${Math.abs(floor)}` : `${floor}층`}</label>)}</div>
            <small>선택한 각 층에서 두 건물의 연결통로를 이용할 수 있습니다.</small>
          </fieldset>}
          <p className="field-help">같은 건물의 Node끼리 연결된 Edge는 저장할 때 자동으로 실내 처리됩니다.</p>
        </div> : selectedBuilding ? <div className="property-form">
          <h3>건물 속성</h3>
          <label>건물명<input value={selectedBuilding.name}
            onChange={e => updateBuilding({ name: e.target.value })}/></label>
          <label>설명<input value={selectedBuilding.detail}
            onChange={e => updateBuilding({ detail: e.target.value })}/></label>
          <label>지상 층수<input type="number" min="1" value={selectedBuilding.floorCount}
            onChange={e => updateBuilding({ floorCount: Math.max(1, Number(e.target.value) || 1) })}/></label>
          <label>지하 층수<input type="number" min="0" value={selectedBuilding.basementFloorCount ?? 0}
            onChange={e => updateBuilding({ basementFloorCount: Math.max(0, Number(e.target.value) || 0) })}/></label>
          <div className="virtual-node-list">
            <b>층별 가상 노드 ID</b>
            {data.nodes.filter(node => node.virtualNode && node.buildingId === selectedBuilding.id)
              .sort((a, b) => (a.floor ?? 0) - (b.floor ?? 0))
              .map(node => <span key={node.id}><em>{(node.floor ?? 1) < 0 ? `B${Math.abs(node.floor ?? 1)}` : `${node.floor}층`}</em><code>{node.id}</code></span>)}
            {!data.nodes.some(node => node.virtualNode && node.buildingId === selectedBuilding.id)
              && <small>저장하고 게시하면 층별 가상 노드가 생성됩니다.</small>}
            <p className="field-help">실제 DB Node ID이며 ADD_EDGE에 입력할 수 있습니다. 지도 마커로는 표시되지 않습니다.</p>
          </div>
          <small>{selectedBuilding.latitude.toFixed(7)}, {selectedBuilding.longitude.toFixed(7)}</small>
        </div> : selectedNode ? <div className="property-form">
          <label>이름<input value={selectedNode.name} onChange={e => updateNode({ name: e.target.value })}/></label>
          <label>유형<select value={selectedNode.nodeType} onChange={e => updateNode({ nodeType: e.target.value })}>
            {[
              ['OUTDOOR', '외부'],
              ['ENTRANCE', '정문·출입구'],
              ['DOOR', '문'],
              ['LOBBY', '로비'],
              ['CORRIDOR', '복도'],
              ['STAIRS', '계단'],
              ['ELEVATOR', '엘리베이터'],
              ['CONNECTOR', '연결통로'],
            ].map(([value, label]) => <option value={value} key={value}>{label} ({value})</option>)}</select></label>
          <label>건물<select value={selectedNode.buildingId ?? ''} onChange={e => updateNode({ buildingId: e.target.value ? Number(e.target.value) : null })}>
            <option value="">없음</option>{data.buildings.map(b => <option value={b.id} key={b.id}>{b.name}</option>)}</select></label>
          <label>층<input type="number" value={selectedNode.floor ?? ''} onChange={e => updateNode({ floor: e.target.value ? Number(e.target.value) : null })}/></label>
          <small>{selectedNode.latitude.toFixed(7)}, {selectedNode.longitude.toFixed(7)}</small>
        </div> : <p>ADD_BUILDING과 ADD_NODE는 지도를 클릭해 추가하고, ADD_EDGE는 노드 두 개를 순서대로 선택합니다.</p>}
        <h3>건물 {data.buildings.length}개</h3>
        <div className="building-list">{data.buildings.map(building =>
          <div className={selectedBuildingId === building.id ? 'active' : ''} key={building.id}>
            <button className="building-select" onClick={() => { setSelectedBuildingId(building.id); setSelected([]) }}>
              <b>{building.name}</b><small>{building.detail}</small>
            </button>
            <button className="building-delete" aria-label={`${building.name} 삭제`}
              onClick={() => void deleteBuilding(building.id, building.name)}>삭제</button>
          </div>)}</div>
        <h3>Edge {data.edges.length}개</h3>
        <div className="edge-list">{data.edges.map(edge => <div
          className={selectedEdgeId === edge.id ? 'active' : ''} key={edge.id}
          onClick={() => { setSelectedEdgeId(edge.id); setSelected([]); setSelectedBuildingId(undefined) }}>
          <span>{edge.startNodeId} → {edge.endNodeId}</span><b>{edge.pathType} · {Math.round(edge.distanceMeters)}m{edge.connectionFloors?.length ? ` · ${edge.connectionFloors.map(floor => floor < 0 ? `B${Math.abs(floor)}` : `${floor}층`).join(', ')}` : ''}</b>
          <button onClick={event => {
            event.stopPropagation()
            setData(v => ({...v, edges: v.edges.filter(e => e.id !== edge.id)}))
            if(edge.id > 0) setDeletedEdgeIds(v => [...v, edge.id])
            if (selectedEdgeId === edge.id) setSelectedEdgeId(undefined)
          }}>삭제</button>
        </div>)}</div>
      </aside>
    </main>
  </div>
}

function haversine(lat1: number, lon1: number, lat2: number, lon2: number) {
  const p = Math.PI / 180
  const a = .5 - Math.cos((lat2-lat1)*p)/2 + Math.cos(lat1*p)*Math.cos(lat2*p)*(1-Math.cos((lon2-lon1)*p))/2
  return 12742000 * Math.asin(Math.sqrt(a))
}
