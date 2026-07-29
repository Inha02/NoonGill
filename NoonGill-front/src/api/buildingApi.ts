import axios from 'axios'

export type Building = {
  id: number
  name: string
  detail: string
  latitude: number
  longitude: number
  floorCount: number
}

export async function getBuildings() {
  return (await axios.get<Building[]>('/api/buildings')).data
}
