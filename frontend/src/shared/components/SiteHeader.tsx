import { useRef, useState } from 'react'
import { PortalIcon } from './PortalIcon'

export function SiteBrand({ footer = false }: { footer?: boolean }) {
  return (
    <a className={`brand${footer ? ' brand--footer' : ''}`} href="#inicio" aria-label="Consultas SRI ANT, inicio">
      <span className="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 38 38" fill="none"><path d="M8 6.5h15.5L30 13v18.5H8z" /><path d="M23 6.5V13h7M13 19h12M13 24h12" /></svg>
      </span>
      <span className="brand-copy">
        <span className="brand-name">Consultas <b>SRI · ANT</b></span>
        <span className="brand-caption">Proyecto académico independiente</span>
      </span>
    </a>
  )
}

export function SiteHeader({
  antDemoAvailable,
  onOpenAntDemo,
}: {
  antDemoAvailable: boolean
  onOpenAntDemo: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)

  function closeMenu(restoreFocus = false) {
    setMenuOpen(false)
    if (restoreFocus) window.requestAnimationFrame(() => menuButtonRef.current?.focus())
  }

  return (
    <header className="site-header">
      <div className="site-header__inner">
        <SiteBrand />
        <button
          ref={menuButtonRef}
          className="menu-toggle"
          type="button"
          aria-controls="primary-navigation"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <PortalIcon name="menu" />
          <span>{menuOpen ? 'Cerrar' : 'Menú'}</span>
        </button>
        <nav
          className={`site-nav${menuOpen ? ' site-nav--open' : ''}`}
          id="primary-navigation"
          aria-label="Navegación principal"
          onKeyDown={(event) => {
            if (event.key === 'Escape') closeMenu(true)
          }}
        >
          <a href="#servicios" onClick={() => closeMenu()}>Servicios</a>
          <a href="#consulta" onClick={() => closeMenu()}>Cómo consultar</a>
          <a href="#estado-ant" onClick={() => closeMenu()}>Estado ANT</a>
          {antDemoAvailable && (
            <button className="nav-demo-link" type="button" onClick={() => { onOpenAntDemo(); closeMenu() }}>
              Demo local
            </button>
          )}
          <a className="site-nav__cta" href="#consulta" onClick={() => closeMenu()}>
            Iniciar consulta <PortalIcon name="arrow-right" />
          </a>
        </nav>
      </div>
    </header>
  )
}
