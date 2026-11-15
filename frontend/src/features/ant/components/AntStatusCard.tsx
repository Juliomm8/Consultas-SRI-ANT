export function AntStatusCard() {
  return (
    <section className="ant-card" aria-labelledby="ant-title">
      <div className="ant-card__icon" aria-hidden="true">!</div>
      <div>
        <div className="ant-card__heading">
          <h2 id="ant-title">Puntos de licencia ANT</h2>
          <span className="status-pill status-pill--pending">En revisión</span>
        </div>
        <p>
          La consulta de puntos de licencia de la ANT no está disponible en esta versión.
          Durante las pruebas de integración, el portal oficial ha respondido HTTP 502 Bad Gateway.
          Estamos documentando y trabajando en la integración.
        </p>
        <p className="ant-card__footnote">No se muestran resultados simulados ni se realizan llamadas desde el navegador.</p>
      </div>
    </section>
  )
}
