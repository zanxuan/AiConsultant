import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type { UserInfo } from '@/types/user'

/** GET /api/v1/users/me */
export function getProfileApi() {
  return request<UserInfo>({
    url: API.USER.ME,
    method: 'get',
  })
}

/** PUT /api/v1/users/me */
export function updateProfileApi(data: Partial<UserInfo>) {
  return request<UserInfo>({
    url: API.USER.ME,
    method: 'put',
    data,
  })
}
