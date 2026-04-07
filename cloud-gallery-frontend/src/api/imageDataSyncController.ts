// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** syncImageData POST /api/sync/image-data */
export async function syncImageDataUsingPost(
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/sync/image-data', {
    method: 'POST',
    ...(options || {}),
  })
}

/** getSyncStatus GET /api/sync/status */
export async function getSyncStatusUsingGet(
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseMapStringObject_>('/api/sync/status', {
    method: 'GET',
    ...(options || {}),
  })
}
