package ec.edu.udla.consultas.contribuyentes.application;

import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEncontradoException;
import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEsPersonaNaturalException;
import ec.edu.udla.consultas.contribuyentes.application.exception.TipoPersonaNoDeterminadaException;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteQuery;
import ec.edu.udla.consultas.contribuyentes.application.service.ConsultarContribuyenteService;
import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;
import ec.edu.udla.consultas.contribuyentes.domain.Ruc;
import ec.edu.udla.consultas.contribuyentes.domain.TipoPersona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsultarContribuyenteServiceTests {

    private static final Ruc RUC = new Ruc("0000000000000");

    private FakeContribuyenteConsultaPort port;
    private ConsultarContribuyenteService service;

    @BeforeEach
    void setUp() {
        port = new FakeContribuyenteConsultaPort();
        service = new ConsultarContribuyenteService(port);
    }

    @Test
    void returnsNaturalPerson() {
        Contribuyente naturalPerson = new Contribuyente(RUC, TipoPersona.NATURAL);
        port.returns(Optional.of(naturalPerson));

        Contribuyente result = service.consultar(new ConsultarContribuyenteQuery(RUC));

        assertSame(naturalPerson, result);
    }

    @Test
    void throwsNotFoundWhenPortConfirmsAbsence() {
        port.returns(Optional.empty());

        assertThrows(
                ContribuyenteNoEncontradoException.class,
                () -> service.consultar(new ConsultarContribuyenteQuery(RUC)));
    }

    @Test
    void rejectsLegalEntity() {
        port.returns(Optional.of(new Contribuyente(RUC, TipoPersona.JURIDICA)));

        assertThrows(
                ContribuyenteNoEsPersonaNaturalException.class,
                () -> service.consultar(new ConsultarContribuyenteQuery(RUC)));
    }

    @Test
    void rejectsUndeterminedPersonType() {
        port.returns(Optional.of(new Contribuyente(RUC, TipoPersona.NO_DETERMINADA)));

        assertThrows(
                TipoPersonaNoDeterminadaException.class,
                () -> service.consultar(new ConsultarContribuyenteQuery(RUC)));
    }

    @Test
    void queriesPortWithRucFromQuery() {
        port.returns(Optional.of(new Contribuyente(RUC, TipoPersona.NATURAL)));
        Ruc queryRuc = new Ruc("1234567890123");

        service.consultar(new ConsultarContribuyenteQuery(queryRuc));

        assertEquals(queryRuc, port.requestedRuc());
    }

    @Test
    void propagatesTechnicalPortExceptionsWithoutConvertingThemToNotFound() {
        RuntimeException providerFailure = new IllegalStateException("Provider timeout");
        port.throwsException(providerFailure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.consultar(new ConsultarContribuyenteQuery(RUC)));

        assertSame(providerFailure, thrown);
    }
}
