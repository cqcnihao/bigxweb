import request from './request'

export function getInvoicePage(params) {
  const mapped = {
    current: params.pageNum ?? 1,
    size: params.pageSize ?? 10,
    keyword: params.name || params.keyword || undefined
  }
  return request.get('/api/invoice/page', { params: mapped })
}

export function getInvoiceById(id) {
  return request.get('/api/invoice/' + id)
}

export function createInvoice(data) {
  return request.post('/api/invoice', data)
}

export function updateInvoice(data) {
  return request.put('/api/invoice', data)
}

export function deleteInvoice(id) {
  return request.delete('/api/invoice/' + id)
}

export function confirmInvoice(id) {
  return request.post('/api/invoice/' + id + '/confirm')
}

export function cancelInvoice(id) {
  return request.post('/api/invoice/' + id + '/cancel')
}