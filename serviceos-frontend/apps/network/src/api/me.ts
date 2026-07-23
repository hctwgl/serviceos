import { apiGet } from './client'

export type MeProfile = { principalId: string; tenantId: string; displayName: string; contextVersion: string; asOf: string }
export function getMe() { return apiGet<MeProfile>('/me') }
