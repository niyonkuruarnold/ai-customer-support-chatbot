let accessToken = null

export function setAccessToken(token) {
  accessToken = token
}

export function clearAccessToken() {
  accessToken = null
}

export function attachBearerToken(client) {
  client.interceptors.request.use((config) => {
    config.headers = config.headers ?? {}
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    } else {
      delete config.headers.Authorization
    }
    return config
  })
}