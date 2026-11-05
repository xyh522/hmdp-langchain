import http from './http'
export const listTypes = () => http.get('/shop-type/list')
export const listShops = (typeId, current = 1) => http.get('/shop/of/type', { params: { typeId, current, x: 120.149993, y: 30.334229 } })
export const getShop = id => http.get(`/shop/${id}`)
export const searchShops = (name, current = 1) => http.get('/shop/of/name', { params: { name, current } })
export const listVouchers = shopId => http.get(`/voucher/list/${shopId}`)
export const buyVoucher = id => http.post(`/voucher-order/seckill/${id}`)
export const listOrders = (current = 1) => http.get('/voucher-order/list', { params: { current } })
export const getOrder = id => http.get(`/voucher-order/${id}`)
