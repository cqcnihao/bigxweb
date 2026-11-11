import request from './request'

export function getProductPage(params) {
  const mapped = {
    current: params.pageNum ?? 1,
    size: params.pageSize ?? 10,
    keyword: params.name || params.keyword || undefined
  }
  return request.get('/api/product/page', { params: mapped })
}

export function getProductById(id) {
  return request.get('/api/product/' + id)
}

export function createProduct(data) {
  return request.post('/api/product', data)
}

export function updateProduct(data) {
  return request.put('/api/product', data)
}

export function deleteProduct(id) {
  return request.delete('/api/product/' + id)
}

export function confirmProduct(id) {
  return request.post('/api/product/' + id + '/confirm')
}

export function cancelProduct(id) {
  return request.post('/api/product/' + id + '/cancel')
}