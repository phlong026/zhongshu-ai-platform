interface LoginOverlay {
  close(): void
}

/** Keep validation failures, rejected requests and repeated clicks retryable. */
export async function runLoginAttempt(
  pending: { value: boolean },
  loading: { value: LoginOverlay | undefined },
  action: () => Promise<void>
): Promise<void> {
  if (pending.value) return
  pending.value = true
  try {
    await action()
  } finally {
    pending.value = false
    const overlay = loading.value
    loading.value = undefined
    overlay?.close()
  }
}
