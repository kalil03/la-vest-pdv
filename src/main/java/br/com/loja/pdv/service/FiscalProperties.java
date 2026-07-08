package br.com.loja.pdv.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dados do emitente (a loja) e padrões de tributação para a NFC-e emitida
 * direto na SEFAZ. Preenchidos em application.properties (prefixo {@code fiscal.*}).
 * Os valores reais (CNPJ/IE/CSC) vivem só no ambiente da loja, nunca no git.
 */
@Component
@ConfigurationProperties(prefix = "fiscal")
public class FiscalProperties {

    /** homologacao (tpAmb=2) ou producao (tpAmb=1). */
    private String ambiente = "homologacao";
    private String uf = "PR";
    private String cnpj = "";
    private String inscricaoEstadual = "";
    private String razaoSocial = "";
    private String nomeFantasia = "";
    /** Código de Regime Tributário: 1=Simples Nacional. */
    private String crt = "1";
    private String cnae = "";
    private String csc = "";
    private String cscId = "";
    private String csosnPadrao = "102";
    private String cfopPadrao = "5102";
    /** Pasta com os XSDs da SEFAZ (validação local antes de transmitir). */
    private String pastaSchemas = "";
    /** Caminho e senha do .pfx do certificado A1 — é o caminho usado em produção.
     *  Vazio = tenta o repositório do Windows, que dispensa senha mas NÃO assina
     *  nesta versão da lib (só serve de fallback para montar/validar). */
    private String certificadoCaminho = "";
    private String certificadoSenha = "";

    private final Endereco endereco = new Endereco();
    private final Nfce nfce = new Nfce();
    private final RespTecnico respTecnico = new RespTecnico();

    /** tpAmb da SEFAZ ("2" homologação, "1" produção). */
    public String tpAmb() {
        return "producao".equalsIgnoreCase(ambiente) ? "1" : "2";
    }

    public boolean isHomologacao() {
        return !"producao".equalsIgnoreCase(ambiente);
    }

    public static class Endereco {
        private String logradouro = "";
        private String numero = "";
        private String bairro = "";
        private String municipio = "";
        /** Código IBGE do município (7 dígitos). */
        private String codMunicipio = "";
        private String cep = "";
        private String fone = "";

        public String getLogradouro() { return logradouro; }
        public void setLogradouro(String v) { this.logradouro = v; }
        public String getNumero() { return numero; }
        public void setNumero(String v) { this.numero = v; }
        public String getBairro() { return bairro; }
        public void setBairro(String v) { this.bairro = v; }
        public String getMunicipio() { return municipio; }
        public void setMunicipio(String v) { this.municipio = v; }
        public String getCodMunicipio() { return codMunicipio; }
        public void setCodMunicipio(String v) { this.codMunicipio = v; }
        public String getCep() { return cep; }
        public void setCep(String v) { this.cep = v; }
        public String getFone() { return fone; }
        public void setFone(String v) { this.fone = v; }
    }

    /** Responsável técnico pelo sistema emissor — exigido pela SEFAZ (grupo infRespTec).
     *  Precisa ser CNPJ (não aceita CPF); o contato pode ser uma pessoa física. */
    public static class RespTecnico {
        private String cnpj = "";
        private String contato = "";
        private String email = "";
        private String fone = "";

        public String getCnpj() { return cnpj; }
        public void setCnpj(String v) { this.cnpj = v; }
        public String getContato() { return contato; }
        public void setContato(String v) { this.contato = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { this.email = v; }
        public String getFone() { return fone; }
        public void setFone(String v) { this.fone = v; }
    }

    public static class Nfce {
        private int serie = 1;
        private String urlQrcode = "";

        public int getSerie() { return serie; }
        public void setSerie(int v) { this.serie = v; }
        public String getUrlQrcode() { return urlQrcode; }
        public void setUrlQrcode(String v) { this.urlQrcode = v; }
    }

    public String getAmbiente() { return ambiente; }
    public void setAmbiente(String v) { this.ambiente = v; }
    public String getUf() { return uf; }
    public void setUf(String v) { this.uf = v; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(String v) { this.cnpj = v; }
    public String getInscricaoEstadual() { return inscricaoEstadual; }
    public void setInscricaoEstadual(String v) { this.inscricaoEstadual = v; }
    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String v) { this.razaoSocial = v; }
    public String getNomeFantasia() { return nomeFantasia; }
    public void setNomeFantasia(String v) { this.nomeFantasia = v; }
    public String getCrt() { return crt; }
    public void setCrt(String v) { this.crt = v; }
    public String getCnae() { return cnae; }
    public void setCnae(String v) { this.cnae = v; }
    public String getCsc() { return csc; }
    public void setCsc(String v) { this.csc = v; }
    public String getCscId() { return cscId; }
    public void setCscId(String v) { this.cscId = v; }
    public String getCsosnPadrao() { return csosnPadrao; }
    public void setCsosnPadrao(String v) { this.csosnPadrao = v; }
    public String getCfopPadrao() { return cfopPadrao; }
    public void setCfopPadrao(String v) { this.cfopPadrao = v; }
    public String getPastaSchemas() { return pastaSchemas; }
    public void setPastaSchemas(String v) { this.pastaSchemas = v; }
    public String getCertificadoCaminho() { return certificadoCaminho; }
    public void setCertificadoCaminho(String v) { this.certificadoCaminho = v; }
    public String getCertificadoSenha() { return certificadoSenha; }
    public void setCertificadoSenha(String v) { this.certificadoSenha = v; }
    public boolean temCertificadoArquivo() { return certificadoCaminho != null && !certificadoCaminho.isBlank(); }
    public Endereco getEndereco() { return endereco; }
    public Nfce getNfce() { return nfce; }
    public RespTecnico getRespTecnico() { return respTecnico; }
}
