// @ts-ignore
/* eslint-disable */
import request from '@/request';

/** listSyncImageVOByPage POST /api/sync-image/list/page/vo */
export async function listSyncImageVOByPageUsingPost(
  body: API.SyncImageQueryRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageSyncImageVO_>('/api/sync-image/list/page/vo', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

/** getSyncImageVOById GET /api/sync-image/get/vo */
export async function getSyncImageVOByIdUsingGet(
  params: {
    id: number;
  },
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseSyncImageVO_>('/api/sync-image/get/vo', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
