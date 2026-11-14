package ec.edu.udla.consultas.contribuyentes.application;

import ec.edu.udla.consultas.contribuyentes.application.port.out.ContribuyenteConsultaPort;
import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;
import ec.edu.udla.consultas.contribuyentes.domain.Ruc;

import java.util.Optional;

final class FakeContribuyenteConsultaPort implements ContribuyenteConsultaPort {

    private Optional<Contribuyente> response = Optional.empty();
    private RuntimeException failure;
    private Ruc requestedRuc;

    void returns(Optional<Contribuyente> response) {
        this.response = response;
    }

    void throwsException(RuntimeException failure) {
        this.failure = failure;
    }

    Ruc requestedRuc() {
        return requestedRuc;
    }

    @Override
    public Optional<Contribuyente> consultarPor(Ruc ruc) {
        requestedRuc = ruc;
        if (failure != null) {
            throw failure;
        }
        return response;
    }
}
