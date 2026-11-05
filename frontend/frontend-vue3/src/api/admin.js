import http from './http'

export const adminTypes = () => http.get('/admin/shop-types')
export const adminShops = (typeId, page, size = 10) => http.get('/admin/shops', { params: { typeId, page, size } })
export const adminShop = id => http.get(`/admin/shops/${id}`)
export const updateAdminShop = (id, data) => http.put(`/admin/shops/${id}`, data)
export const adminVouchers = id => http.get(`/admin/shops/${id}/vouchers`)
export const addAdminVoucher = (id, data) => http.post(`/admin/shops/${id}/vouchers`, data)
export const deleteAdminVoucher = (shopId, voucherId) => http.delete(`/admin/shops/${shopId}/vouchers/${voucherId}`)
