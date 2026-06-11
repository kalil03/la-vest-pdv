package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.Cliente;

import java.math.BigDecimal;

public record ClienteDTO(
        Long id,
        String nome,
        String cpf,
        String telefone,
        String email,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep,
        BigDecimal saldoDevedor) {

    public static ClienteDTO de(Cliente c, BigDecimal saldoDevedor) {
        return new ClienteDTO(c.getId(), c.getNome(), c.getCpf(), c.getTelefone(), c.getEmail(),
                c.getLogradouro(), c.getNumero(), c.getBairro(), c.getCidade(), c.getUf(), c.getCep(),
                saldoDevedor);
    }
}
