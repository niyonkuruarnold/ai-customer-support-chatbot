import { reactive } from 'vue'

/**
 * Lightweight toast notifications.
 *
 * The toast list is module-scoped, so every component using useToasts()
 * shares the same list — convenient for views composed of several
 * components. Toasts auto-dismiss after `duration` ms (pass 0 to keep
 * them until dismissed manually).
 */
const toasts = reactive([])
let nextId = 0

export function useToasts() {
  function push(type, message, duration = 4000) {
    const id = ++nextId
    toasts.push({ id, type, message })
    if (duration > 0) {
      setTimeout(() => remove(id), duration)
    }
    return id
  }

  function remove(id) {
    const index = toasts.findIndex((t) => t.id === id)
    if (index !== -1) toasts.splice(index, 1)
  }

  function clear() {
    toasts.splice(0, toasts.length)
  }

  return { toasts, push, remove, clear }
}
