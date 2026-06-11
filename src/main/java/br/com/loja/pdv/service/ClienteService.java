package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.Cliente;
import br.com.loja.pdv.repository.ClienteRepository;
import br.com.loja.pdv.web.dto.ClienteDTO;
import br.com.loja.pdv.web.dto.NovoClienteRequest;
import br.com.loja.pdv.web.dto.ScoreCliente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    /** Lista com filtro (nome/CPF/telefone) e saldo devedor calculado em lote. */
    @Transactional(readOnly = true)
    public List<ClienteDTO> buscar(String q) {
        List<Cliente> clientes = clienteRepository.buscar(q == null ? "" : q.trim());
        if (clientes.isEmpty()) return List.of();

        Map<Long, BigDecimal> saldos = clienteRepository
                .saldosPorCliente(clientes.stream().map(Cliente::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        ClienteRepository.SaldoCliente::getClienteId,
                        ClienteRepository.SaldoCliente::getSaldo));

        return clientes.stream()
                .map(c -> ClienteDTO.de(c, saldos.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public ClienteDTO criar(NovoClienteRequest req) {
        Cliente cliente = new Cliente();
        aplicar(cliente, req, null);
        clienteRepository.save(cliente);
        return ClienteDTO.de(cliente, BigDecimal.ZERO);
    }

    @Transactional
    public ClienteDTO atualizar(Long id, NovoClienteRequest req) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado (id " + id + ")"));
        aplicar(cliente, req, cliente.getCpf());
        return ClienteDTO.de(cliente, clienteRepository.saldoDevedor(id));
    }

    private void aplicar(Cliente cliente, NovoClienteRequest req, String cpfAtual) {
        String cpf = limpar(req.cpf());
        if (cpf != null && !Objects.equals(cpf, cpfAtual) && clienteRepository.existsByCpf(cpf)) {
            throw new RegraNegocioException("Já existe um cliente com o CPF " + cpf);
        }
        cliente.setNome(req.nome().trim());
        cliente.setCpf(cpf);
        cliente.setTelefone(limpar(req.telefone()));
        cliente.setEmail(limpar(req.email()));
        cliente.setLogradouro(limpar(req.logradouro()));
        cliente.setNumero(limpar(req.numero()));
        cliente.setBairro(limpar(req.bairro()));
        cliente.setCidade(limpar(req.cidade()));
        cliente.setUf(limpar(req.uf()));
        cliente.setCep(limpar(req.cep()));
    }

    @Transactional(readOnly = true)
    public ScoreCliente score(Long clienteId) {
        return new ScoreCliente(
                clienteRepository.saldoDevedor(clienteId),
                clienteRepository.prazoMedioPagamentoDias(clienteId));
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
