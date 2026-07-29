import { useEffect, useMemo, useState } from 'react'
import './App.css'
import NaverMap from './NaverMap'
import AdminMapEditor from './AdminMapEditor'
import { getBuildings, type Building } from './api/buildingApi'
import { searchRoutes, type RouteResult, type RouteType } from './api/routeApi'

const modes: { id: RouteType; label: string; icon: string }[] = [
  { id: 'FASTEST', label: '가장 빠른 길', icon: '↗' },
  { id: 'RAIN_FREE', label: '실내 위주', icon: '⌂' },
  { id: 'ACCESSIBLE', label: '계단 없는 길', icon: '◿' },
]

export default function App() {
  if (window.location.pathname === '/admin/map-editor') return <AdminMapEditor />
  return <RoutePage />
}

function RoutePage() {
  const [places, setPlaces] = useState<Building[]>([])
  const [start, setStart] = useState<number>()
  const [end, setEnd] = useState<number>()
  const [mode, setMode] = useState<RouteType>('RAIN_FREE')
  const [route, setRoute] = useState<RouteResult>()
  const [error, setError] = useState('')

  useEffect(() => {
    const refreshBuildings = () => {
      getBuildings().then(values => {
        setPlaces(values)
        setStart(current => values.some(place => place.id === current) ? current : (values[1]?.id ?? values[0]?.id))
        setEnd(current => values.some(place => place.id === current) ? current : (values[2]?.id ?? values[1]?.id))
      }).catch(() => setError('백엔드에 연결할 수 없습니다. Spring Boot 실행 상태를 확인해 주세요.'))
    }
    const onVisibilityChange = () => { if (document.visibilityState === 'visible') refreshBuildings() }
    const onStorage = (event: StorageEvent) => {
      if (event.key === 'noongill-map-published-at') refreshBuildings()
    }
    refreshBuildings()
    window.addEventListener('focus', refreshBuildings)
    window.addEventListener('storage', onStorage)
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      window.removeEventListener('focus', refreshBuildings)
      window.removeEventListener('storage', onStorage)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [])
  const startPlace = useMemo(() => places.find(p => p.id === start), [places, start])
  const endPlace = useMemo(() => places.find(p => p.id === end), [places, end])
  const findRoute = async () => {
    if (!start || !end) return
    try { setError(''); setRoute((await searchRoutes(start, end, [mode]))[0]) }
    catch { setError('이 조건으로 이동 가능한 경로가 없습니다.') }
  }
  useEffect(() => { if (start && end) void findRoute() }, [start, end, mode])
  if (!startPlace || !endPlace) return <div className="loading-state">{error || '캠퍼스 데이터를 불러오는 중입니다…'}</div>

  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="/"><span className="brand-mark"><img src="/noongill-logo-white.png" alt="눈길 로고" /></span><span><strong>눈길</strong><small>숙명여대 지름길</small></span></a>
      <nav><a className="active" href="#route">길찾기</a><a href="#places">장소</a></nav></header>
    <main>
      <section className="route-panel" id="route"><div className="route-panel-heading"><div><span className="eyebrow">CAMPUS ROUTE</span><h1>어디로 갈까요?</h1></div></div>
        <div className="route-controls"><div className="place-fields">
          <label><span className="dot start-dot"/><span><small>출발</small><select value={start} onChange={e=>setStart(Number(e.target.value))}>{places.map(p=><option value={p.id} key={p.id}>{p.name} · {p.detail}</option>)}</select></span></label>
          <div className="field-line"/><label><span className="dot end-dot"/><span><small>도착</small><select value={end} onChange={e=>setEnd(Number(e.target.value))}>{places.map(p=><option value={p.id} key={p.id}>{p.name} · {p.detail}</option>)}</select></span></label>
          <button className="swap-button" onClick={()=>{setStart(end);setEnd(start)}}>⇅</button></div>
          <div className="mode-fields">{modes.map(item=><button className={mode===item.id?'selected':''} onClick={()=>setMode(item.id)} key={item.id}><span>{item.icon}</span>{item.label}</button>)}</div>
          <button className="search-button" onClick={()=>void findRoute()}>길 찾기</button></div>
      </section>
      <section className="workspace"><div className="map-card"><div className="map-toolbar"><b>숙명여대 캠퍼스</b></div>
        <div className="campus-map"><NaverMap places={places} start={startPlace} end={endPlace} routePoints={route?.points}/>
          <div className="map-legend"><span><i className="indoor"/> 선택한 경로</span></div></div></div>
        <aside className="result-card"><div className="result-header"><div><span className="result-badge">{modes.find(v=>v.id===mode)?.label}</span><h2>{startPlace.name} <span>→</span> {endPlace.name}</h2></div></div>
          {error && <p className="route-error">{error}</p>}
          {route && <><div className="route-summary"><strong>약 {Math.max(1,Math.ceil(route.estimatedSeconds/60))}분</strong><span>{Math.round(route.totalDistanceMeters)}m</span><span>실내 {Math.round(route.indoorRatio*100)}%</span></div>
            <ol className="segment-list">{route.segments.map((s,i)=><li key={s.edgeId}><i>{i+1}</i><div><strong>{s.instruction}</strong><small>{s.indoor?'실내':'실외'} · {s.pathType} · {Math.round(s.estimatedSeconds)}초</small></div></li>)}</ol></>}
        </aside></section>
      <section className="quick-places" id="places"><div><span className="eyebrow">QUICK ACCESS</span><h2>자주 찾는 장소</h2></div><div className="place-chips">{places.slice(1,5).map(p=><button onClick={()=>setEnd(p.id)} key={p.id}><span>{p.name[0]}</span><b>{p.name}</b><small>{p.detail}</small></button>)}</div></section>
    </main><footer><strong>눈길</strong><span>네이버 지도는 배경과 경로 시각화에만 사용합니다.</span><small>실제 통행 가능 여부를 확인해 주세요.</small></footer>
  </div>
}
