package ec.edu.udla.consultas.contribuyentes.application.port.in;

import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;

public interface ConsultarContribuyenteUseCase {

    Contribuyente consultar(ConsultarContribuyenteQuery query);
}
