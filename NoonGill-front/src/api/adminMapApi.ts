import axios from 'axios'
import type { Building } from './buildingApi'

export type MapNode = {
  id: number; name: string; latitude: number; longitude: number; floor: number | null
  nodeType: string; buildingId: number | null; indoorX: number | null; indoorY: number | null
}
export type MapEdge = {
  id: number; startNodeId: number; endNodeId: number; pathType: string
  distanceMeters: number; durationSeconds: number; indoor: boolean; rainExposure: number
  stairCount: number; wheelchairAccessible: boolean; bidirectional: boolean
}
export type MapData = { buildings: Building[]; nodes: MapNode[]; edges: MapEdge[] }

export async function getMapData(admin = true) {
  return (await axios.get<MapData>(admin ? '/api/admin/map/data' : '/api/map/data')).data
}
export async function publishMap(data: {
  buildings: Building[]; nodes: MapNode[]; edges: MapEdge[]
  deletedBuildingIds: number[]; deletedNodeIds: number[]; deletedEdgeIds: number[]
}) {
  await axios.post('/api/admin/map/publish', data)
}

export async function deleteBuildingById(id: number) {
  await axios.delete(`/api/admin/map/buildings/${id}`)
}
