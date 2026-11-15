/** The demo is opt-in in Vite development mode and cannot be enabled in production builds. */
export const ANT_DEMO_AVAILABLE = import.meta.env.DEV && import.meta.env.VITE_ANT_DEMO === 'true'

export function isAntDemoAvailable(isDevelopment: boolean, explicitFlag: string | undefined): boolean {
  return isDevelopment && explicitFlag === 'true'
}
