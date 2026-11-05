export const imageUrl = value => value || '/assets/imgs/icons/default-icon.png'
export const firstImage = value => imageUrl(value?.split(',')[0])