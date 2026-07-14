package br.com.loja.pdv.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Usado na criação e edição de cliente. Só o nome é obrigatório (sem fricção). */
public record NovoClienteRequest(
        @NotBlank(message = "Nome é obrigatório") String nome,
        String cpf,
        String telefone,
        String email,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep,
        String tipo,
        String rg,
        java.time.LocalDate dataNasc,
        java.math.BigDecimal limiteCred,
        String bloqueado,
        String pfisProfissao,
        java.math.BigDecimal pfisRendaConj,
        String anotacoes,
        String pfisNomePai,
        String pfisNomeMae,
        String refComerciais) {
}
