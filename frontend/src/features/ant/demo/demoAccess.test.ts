import { describe, expect, it } from 'vitest'
import { isAntDemoAvailable } from './demoAccess'

describe('disponibilidad de la demo ANT', () => {
  it('requiere desarrollo y la bandera explícita true', () => {
    expect(isAntDemoAvailable(true, 'true')).toBe(true)
    expect(isAntDemoAvailable(false, 'true')).toBe(false)
    expect(isAntDemoAvailable(true, undefined)).toBe(false)
    expect(isAntDemoAvailable(true, 'false')).toBe(false)
  })
})
