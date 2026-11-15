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
          La consulta automática directa de puntos de licencia todavía no está integrada.
          La ANT dispone de un trámite oficial para obtener el certificado de información del
          conductor, que incluye los puntos de la licencia.
        </p>
        <p>
          <a
            className="ant-card__official-link"
            href="https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor"
            target="_blank"
            rel="noopener noreferrer"
          >
            Consultar el trámite oficial en Gob.ec <span aria-hidden="true">↗</span>
          </a>
        </p>
        <p>
          El trámite del portal es independiente de una API que esta aplicación pueda consumir.
          La consulta automática estará disponible cuando se confirme un mecanismo de integración autorizado.
        </p>
        <p className="ant-card__footnote">
          Esta aplicación no consulta la ANT, no solicita datos personales para esta función y no muestra puntos simulados.
        </p>
      </div>
    </section>
  )
}
