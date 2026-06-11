package br.com.loja.pdv.service;

import br.com.loja.pdv.repository.ClienteRepository;
import br.com.loja.pdv.web.dto.ClienteDTO;
import br.com.loja.pdv.web.dto.ScoreCliente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> buscar(String q) {
        return clienteRepository.buscar(q == null ? "" : q.trim()).stream()
                .map(ClienteDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScoreCliente score(Long clienteId) {
        return new ScoreCliente(
                clienteRepository.saldoDevedor(clienteId),
                clienteRepository.prazoMedioPagamentoDias(clienteId));
    }
}
