import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  updateProjectFulfillmentDraft,
  type ProjectFulfillmentDocument,
} from './project-fulfillment'

const storage = {
  getItem: vi.fn(() => null),
  removeItem: vi.fn(),
  setItem: vi.fn(),
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('项目履约草稿接口', () => {
  it('保存业务草稿时保留已绑定的运行期 Workflow 与 Bundle', async () => {
    const document: ProjectFulfillmentDocument = {
      nodes: [],
      orderTypeName: '家充安装工单',
      phases: [],
      schemaVersion: '1.0.0',
      stages: [],
      transitions: [],
    }
    const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({
      aggregateVersion: 8,
      description: '比亚迪家充履约',
      document,
      profileId: 'profile-1',
      profileName: '家充勘测安装标准履约',
      revisionId: 'revision-1',
      serviceProductCode: 'HOME_CHARGING_SURVEY_INSTALL',
      sourceBundleId: 'bundle-1',
      updatedAt: '2026-07-24T00:00:00Z',
      workflowAssetVersionId: 'workflow-1',
    }), {
      headers: { 'Content-Type': 'application/json' },
      status: 200,
    }))

    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('localStorage', storage)
    vi.stubGlobal('window', {
      location: {
        origin: 'https://serviceos.example',
        pathname: '/',
        search: '',
      },
    })

    await updateProjectFulfillmentDraft('project-1', 'profile-1', 7, {
      description: '比亚迪家充履约',
      document,
      profileName: '家充勘测安装标准履约',
      sourceBundleId: 'bundle-1',
      workflowAssetVersionId: 'workflow-1',
    })

    expect(fetchMock).toHaveBeenCalledOnce()
    const [, request] = fetchMock.mock.calls[0]!
    const headers = new Headers(request?.headers)
    expect(headers.get('X-ServiceOS-Client-Kind')).toBe('ADMIN_WEB')
    expect(headers.get('X-ServiceOS-Client-Version')).toBe('1.0.0')
    expect(JSON.parse(String(request?.body))).toMatchObject({
      sourceBundleId: 'bundle-1',
      workflowAssetVersionId: 'workflow-1',
    })
  })
})
