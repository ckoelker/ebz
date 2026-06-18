import type { Order } from '~~/server/utils/order'

// Warenkorb-Client: hält den activeOrder in einem SSR-sicheren useState und ruft die
// Nuxt-Server-Routen (server/api/cart*) auf. Die Vendure-Session hängt an der httpOnly
// vendure_token-Cookie, die der Server verwaltet → der Browser sendet sie automatisch mit.
export function useCart() {
  const order = useState<Order | null>('cart', () => null)
  const busy = useState<boolean>('cart-busy', () => false)
  const count = computed(() => order.value?.totalQuantity ?? 0)
  const isEmpty = computed(() => count.value === 0)

  async function refresh() {
    order.value = await useRequestFetch()<Order | null>('/api/cart')
  }

  async function add(variantId: string, opts: { quantity?: number; participantName?: string; participantEmail?: string } = {}) {
    busy.value = true
    try {
      order.value = await $fetch<Order>('/api/cart', { method: 'POST', body: { variantId, ...opts } })
    } finally {
      busy.value = false
    }
  }

  async function adjust(lineId: string, quantity: number, participant: { participantName?: string; participantEmail?: string } = {}) {
    busy.value = true
    try {
      order.value = await $fetch<Order>('/api/cart-line', { method: 'PATCH', body: { lineId, quantity, ...participant } })
    } finally {
      busy.value = false
    }
  }

  async function remove(lineId: string) {
    busy.value = true
    try {
      order.value = await $fetch<Order>('/api/cart-line', { method: 'DELETE', query: { lineId } })
    } finally {
      busy.value = false
    }
  }

  function clearLocal() {
    order.value = null
  }

  return { order, busy, count, isEmpty, refresh, add, adjust, remove, clearLocal }
}
