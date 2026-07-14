const KEY = 'aegisops.profile.avatar'
export const PROFILE_AVATAR_CHANGED = 'aegisops-profile-avatar-changed'

export function loadProfileAvatar() {
  return localStorage.getItem(KEY) ?? ''
}

export function saveProfileAvatar(value: string) {
  // ponytail: browser-local avatar; move to object storage when cross-device sync is required.
  if (!/^data:image\/(png|jpeg|webp);base64,/.test(value)) {
    throw new Error('仅支持 PNG、JPG 或 WebP 图片')
  }
  if (value.length > 512_000) throw new Error('头像不能超过 384 KB')
  localStorage.setItem(KEY, value)
  window.dispatchEvent(new Event(PROFILE_AVATAR_CHANGED))
}
