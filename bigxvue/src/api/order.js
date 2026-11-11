import request from './request'

export function getOrderPage(params) {
  const mapped = {
    current: params.pageNum ?? 1,
    size: params.pageSize ?? 10,
    keyword: params.name || params.keyword || undefined
  }
  return request.get('/api/order/page', { params: mapped })
}

export function getOrderById(id) {
  return request.get('/api/order/' + id)
}

export function createOrder(data) {
  return request.post('/api/order', data)
}

export function updateOrder(data) {
  return request.put('/api/order', data)
}

export function deleteOrder(id) {
  return request.delete('/api/order/' + id)
}

export function confirmOrder(id) {
  return request.post('/api/order/' + id + '/confirm')
}

export function cancelOrder(id) {
  return request.post('/api/order/' + id + '/cancel')
}