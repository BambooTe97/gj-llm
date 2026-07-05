/** Token 存储键 */
export const TOKEN_KEY = 'ACCESS_TOKEN'

/** 用户信息存储键 */
export const USER_INFO_KEY = 'USER_INFO'

/** 消息角色 */
export enum MessageRole {
  USER = 'user',
  ASSISTANT = 'assistant',
  SYSTEM = 'system',
}

/** HTTP 状态码 */
export enum HttpStatus {
  OK = 200,
  CREATED = 201,
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  INTERNAL_ERROR = 500,
}
