import './App.css'

function App() {
  return (
    <main className="portal-shell">
      <header className="portal-header">
        <span className="brand-mark" aria-hidden="true">S</span>
        <div>
          <p className="eyebrow">Portal de consultas</p>
          <p className="brand-name">SRI <span>·</span> ANT</p>
        </div>
      </header>
      <section className="welcome-card" aria-labelledby="page-title">
        <p className="eyebrow">Servicios de información</p>
        <h1 id="page-title">Consulta tus datos con claridad.</h1>
        <p>Consulta de contribuyentes y vehículos conectada al backend institucional.</p>
      </section>
    </main>
  )
}

export default App
