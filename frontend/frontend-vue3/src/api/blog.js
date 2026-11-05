import http from './http'
export const hotBlogs = () => http.get('/blog/hot', { params: { current: 1 } })
export const getBlog = id => http.get(`/blog/${id}`)
export const myBlogs = () => http.get('/blog/of/me')
export const likeBlog = id => http.put(`/blog/like/${id}`)
