import axios from 'axios'

export type RouteType = 'FASTEST' | 'RAIN_FREE' | 'ACCESSIBLE'
export type RoutePoint = {
  nodeId: number; name: string; latitude: number; longitude: number
  floor: number | null; buildingId: number | null; nodeType: string
}
export type RouteSegment = {
  edgeId: number; fromNodeId: number; toNodeId: number; instruction: string
  distanceMeters: number; estimatedSeconds: number; pathType: string; indoor: boolean
}
export type RouteResult = {
  routeType: RouteType; totalDistanceMeters: number; estimatedSeconds: number
  indoorRatio: number; points: RoutePoint[]; segments: RouteSegment[]
}

export async function searchRoutes(startBuildingId: number, destinationBuildingId: number, routeTypes: RouteType[]) {
  return (await axios.post<RouteResult[]>('/api/routes/search', {
    start: { type: 'BUILDING', buildingId: startBuildingId },
    destination: { type: 'BUILDING', buildingId: destinationBuildingId },
    routeTypes,
  })).data
}
