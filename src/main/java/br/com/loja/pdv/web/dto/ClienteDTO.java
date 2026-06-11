package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.Cliente;

public record ClienteDTO(Long id, String nome, String telefone) {
    public static ClienteDTO de(Cliente c) {
        return new ClienteDTO(c.getId(), c.getNome(), c.getTelefone());
    }
}
