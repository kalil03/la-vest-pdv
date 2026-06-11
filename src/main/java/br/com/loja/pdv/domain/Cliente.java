package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** CPF: necessário na NFC-e quando o cliente pede nota identificada. */
    @Column(unique = true)
    private String cpf;

    private String telefone;
    private String email;

    // ---- endereço (NFC-e / cobrança) ----
    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;
    private String uf;
    private String cep;

    @Column(name = "data_criacao", nullable = false)
    private Instant dataCriacao = Instant.now();

    
    // -- Legado: Documentos e PJ --
    private String tipo;
    private String razao;
    private String fantasia;
    private String rg;
    private String ie;

    // -- Legado: Dados Pessoais/Demográficos --
    private LocalDate dataNasc;
    private String idade;
    private String sexo;

    // -- Legado: Familiares e Filiação --
    private String pfisNomePai;
    private String pfisNomeMae;
    private String pfisNomeConj;
    private String pfisEmpresaConj;
    private BigDecimal pfisRendaConj;
    private String pfisFoneConj;

    // -- Legado: Profissionais / Referência --
    private String pfisLocalTrab;
    private String pfisProfissao;
    private String refComerciais;

    // -- Legado: Crediário / Risco --
    private BigDecimal limiteCred;
    private String bloqueado;
    private Integer diaVencimento;

    // -- Legado: Contatos Extras --
    @Column(name = "fone2")
    private String fone2;
    @Column(name = "fone3")
    private String fone3;
    @Column(name = "fone1_tipo")
    private String fone1Tipo;
    @Column(name = "whats_fone1")
    private String whatsFone1;

    // -- Legado: Endereço Adicional --
    private String complemento;
    private String entComplemento;

    // -- Legado: Observações --
    @Column(length = 5000)
    private String anotacoes;
    @Column(name = "campo1")
    private String campo1;
    @Column(name = "campo2")
    private String campo2;

    // -- Legado: Vínculos de Venda --
    @Column(name = "tab_preco1")
    private String tabPreco1;
    @Column(name = "tab_preco2")
    private String tabPreco2;
    @Column(name = "tab_preco3")
    private String tabPreco3;

    // -- Legado: Auditoria --
    private Instant dataCad;
    private Instant dataAlt;
    private String usuarioCad;
    private String usuarioAlt;


    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getRazao() { return razao; }
    public void setRazao(String razao) { this.razao = razao; }
    public String getFantasia() { return fantasia; }
    public void setFantasia(String fantasia) { this.fantasia = fantasia; }
    public String getRg() { return rg; }
    public void setRg(String rg) { this.rg = rg; }
    public String getIe() { return ie; }
    public void setIe(String ie) { this.ie = ie; }
    public LocalDate getDataNasc() { return dataNasc; }
    public void setDataNasc(LocalDate dataNasc) { this.dataNasc = dataNasc; }
    public String getIdade() { return idade; }
    public void setIdade(String idade) { this.idade = idade; }
    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }
    public String getPfisNomePai() { return pfisNomePai; }
    public void setPfisNomePai(String pfisNomePai) { this.pfisNomePai = pfisNomePai; }
    public String getPfisNomeMae() { return pfisNomeMae; }
    public void setPfisNomeMae(String pfisNomeMae) { this.pfisNomeMae = pfisNomeMae; }
    public String getPfisNomeConj() { return pfisNomeConj; }
    public void setPfisNomeConj(String pfisNomeConj) { this.pfisNomeConj = pfisNomeConj; }
    public String getPfisEmpresaConj() { return pfisEmpresaConj; }
    public void setPfisEmpresaConj(String pfisEmpresaConj) { this.pfisEmpresaConj = pfisEmpresaConj; }
    public BigDecimal getPfisRendaConj() { return pfisRendaConj; }
    public void setPfisRendaConj(BigDecimal pfisRendaConj) { this.pfisRendaConj = pfisRendaConj; }
    public String getPfisFoneConj() { return pfisFoneConj; }
    public void setPfisFoneConj(String pfisFoneConj) { this.pfisFoneConj = pfisFoneConj; }
    public String getPfisLocalTrab() { return pfisLocalTrab; }
    public void setPfisLocalTrab(String pfisLocalTrab) { this.pfisLocalTrab = pfisLocalTrab; }
    public String getPfisProfissao() { return pfisProfissao; }
    public void setPfisProfissao(String pfisProfissao) { this.pfisProfissao = pfisProfissao; }
    public String getRefComerciais() { return refComerciais; }
    public void setRefComerciais(String refComerciais) { this.refComerciais = refComerciais; }
    public BigDecimal getLimiteCred() { return limiteCred; }
    public void setLimiteCred(BigDecimal limiteCred) { this.limiteCred = limiteCred; }
    public String getBloqueado() { return bloqueado; }
    public void setBloqueado(String bloqueado) { this.bloqueado = bloqueado; }
    public Integer getDiaVencimento() { return diaVencimento; }
    public void setDiaVencimento(Integer diaVencimento) { this.diaVencimento = diaVencimento; }
    public String getFone2() { return fone2; }
    public void setFone2(String fone2) { this.fone2 = fone2; }
    public String getFone3() { return fone3; }
    public void setFone3(String fone3) { this.fone3 = fone3; }
    public String getFone1Tipo() { return fone1Tipo; }
    public void setFone1Tipo(String fone1Tipo) { this.fone1Tipo = fone1Tipo; }
    public String getWhatsFone1() { return whatsFone1; }
    public void setWhatsFone1(String whatsFone1) { this.whatsFone1 = whatsFone1; }
    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }
    public String getEntComplemento() { return entComplemento; }
    public void setEntComplemento(String entComplemento) { this.entComplemento = entComplemento; }
    public String getAnotacoes() { return anotacoes; }
    public void setAnotacoes(String anotacoes) { this.anotacoes = anotacoes; }
    public String getCampo1() { return campo1; }
    public void setCampo1(String campo1) { this.campo1 = campo1; }
    public String getCampo2() { return campo2; }
    public void setCampo2(String campo2) { this.campo2 = campo2; }
    public String getTabPreco1() { return tabPreco1; }
    public void setTabPreco1(String tabPreco1) { this.tabPreco1 = tabPreco1; }
    public String getTabPreco2() { return tabPreco2; }
    public void setTabPreco2(String tabPreco2) { this.tabPreco2 = tabPreco2; }
    public String getTabPreco3() { return tabPreco3; }
    public void setTabPreco3(String tabPreco3) { this.tabPreco3 = tabPreco3; }
    public Instant getDataCad() { return dataCad; }
    public void setDataCad(Instant dataCad) { this.dataCad = dataCad; }
    public Instant getDataAlt() { return dataAlt; }
    public void setDataAlt(Instant dataAlt) { this.dataAlt = dataAlt; }
    public String getUsuarioCad() { return usuarioCad; }
    public void setUsuarioCad(String usuarioCad) { this.usuarioCad = usuarioCad; }
    public String getUsuarioAlt() { return usuarioAlt; }
    public void setUsuarioAlt(String usuarioAlt) { this.usuarioAlt = usuarioAlt; }
public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public Instant getDataCriacao() { return dataCriacao; }
}
