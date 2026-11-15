package ec.edu.udla.consultas.contribuyentes.application.service;

import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEncontradoException;
import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEsPersonaNaturalException;
import ec.edu.udla.consultas.contribuyentes.application.exception.TipoPersonaNoDeterminadaException;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteQuery;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteUseCase;
import ec.edu.udla.consultas.contribuyentes.application.port.out.ContribuyenteConsultaPort;
import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;
import ec.edu.udla.consultas.contribuyentes.domain.TipoPersona;

import java.util.Objects;

public class ConsultarContribuyenteService implements ConsultarContribuyenteUseCase {

    private final ContribuyenteConsultaPort contribuyenteConsultaPort;

    public ConsultarContribuyenteService(ContribuyenteConsultaPort contribuyenteConsultaPort) {
        this.contribuyenteConsultaPort = Objects.requireNonNull(
                contribuyenteConsultaPort,
                "El puerto de consulta de contribuyentes es obligatorio.");
    }

    @Override
    public Contribuyente consultar(ConsultarContribuyenteQuery query) {
        Objects.requireNonNull(query, "La consulta de contribuyente es obligatoria.");

        Contribuyente contribuyente = contribuyenteConsultaPort.consultarPor(query.ruc())
                .orElseThrow(ContribuyenteNoEncontradoException::new);

        return switch (contribuyente.tipoPersona()) {
            case NATURAL -> contribuyente;
            case JURIDICA -> throw new ContribuyenteNoEsPersonaNaturalException();
            case NO_DETERMINADA -> throw new TipoPersonaNoDeterminadaException();
        };
    }
}
