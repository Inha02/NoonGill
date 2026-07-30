import { useEffect, useRef, useState } from 'react'

export type MapPlace = {
  id: number
  name: string
  detail: string
  latitude: number
  longitude: number
}

type MapInstance = {
  fitBounds: (bounds: unknown, options?: Record<string, number>) => void
}

type MapConstructor = new (
  element: HTMLElement,
  options: Record<string, unknown>,
) => MapInstance

type LatLngConstructor = new (latitude: number, longitude: number) => unknown
type LatLngBoundsInstance = { extend: (coordinate: unknown) => void }
type LatLngBoundsConstructor = new () => LatLngBoundsInstance
type OverlayConstructor = new (options: Record<string, unknown>) => { setMap: (map: null) => void }
type EventApi = {
  addListener: (target: unknown, eventName: string, callback: (event: { coord: { x: number; y: number } }) => void) => unknown
  removeListener: (listener: unknown) => void
}

type NaverMaps = {
  Map: MapConstructor
  LatLng: LatLngConstructor
  LatLngBounds: LatLngBoundsConstructor
  Marker: OverlayConstructor
  Polyline: OverlayConstructor
  Event: EventApi
}

declare global {
  interface Window {
    naver?: { maps: NaverMaps }
  }
}

type NaverMapProps = {
  places: MapPlace[]
  start: MapPlace
  end: MapPlace
  routePoints?: Array<{ latitude: number; longitude: number }>
  buildingPlaces?: MapPlace[]
  selectedBuildingId?: number
  graphNodes?: Array<{ id: number; name: string; latitude: number; longitude: number }>
  graphEdges?: Array<{ startNodeId: number; endNodeId: number; indoor: boolean }>
  compactMarkers?: boolean
  onMapClick?: (latitude: number, longitude: number) => void
  onPlaceClick?: (id: number) => void
  onBuildingClick?: (id: number) => void
}

const EMPTY_ROUTE_POINTS: Array<{ latitude: number; longitude: number }> = []
const EMPTY_BUILDING_PLACES: MapPlace[] = []
const EMPTY_GRAPH_NODES: Array<{ id: number; name: string; latitude: number; longitude: number }> = []
const EMPTY_GRAPH_EDGES: Array<{ startNodeId: number; endNodeId: number; indoor: boolean }> = []

function markerIcon(place: MapPlace, selected: boolean) {
  const color = selected ? '#335fda' : '#5f6b65'

  return {
    content: `
      <div class="naver-marker">
        <span style="background:${color}">${selected ? '●' : place.id}</span>
        <b>${place.name}</b>
      </div>
    `,
    anchor: { x: 15, y: 36 },
  }
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;',
  })[character]!)
}

function NaverMap({
  places, start, end, routePoints = EMPTY_ROUTE_POINTS,
  buildingPlaces = EMPTY_BUILDING_PLACES, selectedBuildingId,
  graphNodes = EMPTY_GRAPH_NODES, graphEdges = EMPTY_GRAPH_EDGES,
  compactMarkers = false,
  onMapClick, onPlaceClick, onBuildingClick,
}: NaverMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [loadError, setLoadError] = useState(false)

  useEffect(() => {
    const container = containerRef.current
    const maps = window.naver?.maps

    if (!container || !maps) {
      setLoadError(true)
      return
    }

    setLoadError(false)
    const center = new maps.LatLng(37.54525, 126.96455)
    const map = new maps.Map(container, {
      center,
      zoom: 18,
      minZoom: 16,
      maxZoom: 21,
      mapTypeControl: false,
      scaleControl: true,
      logoControlOptions: { position: 2 },
    })

    const overlays: Array<{ setMap: (map: null) => void }> = []
    const bounds = new maps.LatLngBounds()

    places.forEach((place) => {
      const position = new maps.LatLng(place.latitude, place.longitude)
      bounds.extend(position)
      const marker = new maps.Marker({
        position,
        map,
        zIndex: 30,
        title: `${place.name} · ${place.detail}`,
        icon: {
          ...markerIcon(place, place.id === start.id || place.id === end.id),
          anchor: compactMarkers ? { x: 11, y: 29 } : { x: 15, y: 36 },
        },
      })
      overlays.push(marker)
      if (onPlaceClick) {
        maps.Event.addListener(marker, 'click', () => onPlaceClick(place.id))
      }
    })

    buildingPlaces.forEach((building) => {
      const position = new maps.LatLng(building.latitude, building.longitude)
      bounds.extend(position)
      const selected = building.id === selectedBuildingId
      const marker = new maps.Marker({
        position,
        map,
        zIndex: 10,
        title: `${building.name} · ${building.detail}`,
        icon: {
          content: `<div class="building-marker${selected ? ' selected' : ''}"><span>건</span><b>${escapeHtml(building.name)}</b></div>`,
          anchor: compactMarkers ? { x: 11, y: 29 } : { x: 15, y: 36 },
        },
      })
      overlays.push(marker)
      if (onBuildingClick) {
        maps.Event.addListener(marker, 'click', () => onBuildingClick(building.id))
      }
    })

    const graphNodeById = new Map(graphNodes.map(node => [node.id, node]))
    graphEdges.forEach(edge => {
      const from = graphNodeById.get(edge.startNodeId)
      const to = graphNodeById.get(edge.endNodeId)
      if (!from || !to) return
      overlays.push(new maps.Polyline({
        map,
        path: [
          new maps.LatLng(from.latitude, from.longitude),
          new maps.LatLng(to.latitude, to.longitude),
        ],
        strokeColor: edge.indoor ? '#5b73b9' : '#8b948e',
        strokeWeight: 2,
        strokeOpacity: 0.32,
        strokeStyle: edge.indoor ? 'solid' : 'shortdash',
      }))
    })

    const sourcePoints = routePoints.length > 1 ? routePoints : [start, end]
    const routeCoordinates = sourcePoints.map(point => new maps.LatLng(point.latitude, point.longitude))
    routeCoordinates.forEach(coordinate => bounds.extend(coordinate))

    overlays.push(new maps.Polyline({
      map,
      path: routeCoordinates,
      strokeColor: '#ffffff',
      strokeWeight: 10,
      strokeOpacity: 0.9,
      strokeLineCap: 'round',
      strokeLineJoin: 'round',
    }))
    overlays.push(new maps.Polyline({
      map,
      path: routeCoordinates,
      strokeColor: '#335fda',
      strokeWeight: 6,
      strokeOpacity: 1,
      strokeStyle: 'shortdash',
      strokeLineCap: 'round',
      strokeLineJoin: 'round',
    }))

    map.fitBounds(bounds, { top: 70, right: 70, bottom: 70, left: 70 })
    const mapListener = onMapClick
      ? maps.Event.addListener(map, 'click', (event) => onMapClick(event.coord.y, event.coord.x))
      : null

    return () => {
      if (mapListener) maps.Event.removeListener(mapListener)
      overlays.forEach((overlay) => overlay.setMap(null))
      container.replaceChildren()
    }
  }, [
    places, start, end, routePoints, buildingPlaces, selectedBuildingId,
    graphNodes, graphEdges, compactMarkers, onMapClick, onPlaceClick, onBuildingClick,
  ])

  return (
    <>
      <div ref={containerRef} className="naver-map" />
      {loadError && (
        <div className="map-load-error">
          <strong>지도를 불러오지 못했습니다.</strong>
          <span>네이버 Cloud의 Web 서비스 URL에 http://localhost:5173을 등록했는지 확인해 주세요.</span>
        </div>
      )}
    </>
  )
}

export default NaverMap
