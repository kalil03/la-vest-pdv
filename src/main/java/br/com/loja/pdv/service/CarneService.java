package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.repository.*;
import br.com.loja.pdv.web.dto.CarneDTO;
import br.com.loja.pdv.web.dto.ClienteDTO;
import br.com.loja.pdv.web.dto.ReceberRequest;
import br.com.loja.pdv.web.dto.ReciboRecebimento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;

@Service
public class CarneService {

    private static final ZoneId FUSO = br.com.loja.pdv.Fuso.LOJA;
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final EnumSet<TipoPagamentoFiado> TIPOS_RECEBIMENTO =
            EnumSet.of(TipoPagamentoFiado.DINHEIRO, TipoPagamentoFiado.PIX, TipoPagamentoFiado.CARTAO);

    private final ClienteRepository clienteRepository;
    private final PagamentoFiadoRepository pagamentoRepository;
    private final ParcelaFiadoRepository parcelaRepository;
    private final VendedorRepository vendedorRepository;

    public CarneService(ClienteRepository clienteRepository,
                        PagamentoFiadoRepository pagamentoRepository,
                        ParcelaFiadoRepository parcelaRepository,
                        VendedorRepository vendedorRepository) {
        this.clienteRepository = clienteRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.parcelaRepository = parcelaRepository;
        this.vendedorRepository = vendedorRepository;
    }

    /**
     * O saldo devedor continua 100% calculado (vendas FIADO - pagamentos).
     * O valor_aberto de cada parcela é o rateio dos recebimentos feitos no
     * balcão, POR ORDEM DE SELEÇÃO da atendente — invariante:
     * SUM(valor_aberto) == saldo devedor.
     */
    @Transactional(readOnly = true)
    public CarneDTO montar(Long clienteId) {
        Cliente cliente = buscarCliente(clienteId);
        BigDecimal saldo = clienteRepository.saldoDevedor(clienteId);

        List<CarneDTO.Parcela> abertas = parcelasAbertas(clienteId);

        var ultimos = pagamentoRepository
                .findTop3ByClienteIdAndTipoNotOrderByDataDesc(clienteId, TipoPagamentoFiado.DEBITO_INICIAL)
                .stream()
                .map(p -> new CarneDTO.Pagamento(p.getData(), p.getValor(), p.getTipo().name(),
                        p.getVendedor() != null ? p.getVendedor().getNome() : null, p.getDetalhe()))
                .toList();

        return new CarneDTO(
                ClienteDTO.de(cliente, saldo), saldo, abertas.size(),
                abertas.isEmpty() ? null : abertas.get(0).vencimento(),
                abertas, ultimos);
    }

    /**
     * Recebimento atômico com rateio por ordem de seleção: a tela manda
     * quanto abater de cada parcela; aqui validamos que a soma fecha com o
     * valor recebido e que nenhuma parcela recebe mais do que deve.
     */
    @Transactional
    public ReciboRecebimento receber(ReceberRequest req) {
        Cliente cliente = buscarCliente(req.clienteId());
        if (!TIPOS_RECEBIMENTO.contains(req.tipo())) {
            throw new RegraNegocioException("Forma de recebimento inválida: " + req.tipo());
        }
        Vendedor vendedor = vendedorRepository.findById(req.vendedorId())
                .orElseThrow(() -> new RegraNegocioException("Funcionário não encontrado"));

        BigDecimal saldoAnterior = clienteRepository.saldoDevedor(cliente.getId());

        BigDecimal somaAlocacoes = req.alocacoes().stream()
                .map(ReceberRequest.Alocacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaAlocacoes.compareTo(req.valor()) != 0) {
            throw new RegraNegocioException("As parcelas somam R$ " + somaAlocacoes
                    + " mas o valor recebido é R$ " + req.valor());
        }

        // a mesma parcela duas vezes abateria em dobro (dois UPDATEs válidos)
        long parcelasDistintas = req.alocacoes().stream()
                .map(ReceberRequest.Alocacao::parcelaId).distinct().count();
        if (parcelasDistintas < req.alocacoes().size()) {
            throw new RegraNegocioException("A mesma parcela aparece mais de uma vez no recebimento");
        }

        List<ReciboRecebimento.Item> itens = new ArrayList<>();
        StringJoiner detalhe = new StringJoiner("; ");
        for (ReceberRequest.Alocacao aloc : req.alocacoes()) {
            itens.add(abater(cliente, aloc, detalhe));
        }

        PagamentoFiado pagamento = new PagamentoFiado();
        pagamento.setCliente(cliente);
        pagamento.setValor(req.valor());
        pagamento.setTipo(req.tipo());
        pagamento.setVendedor(vendedor);
        pagamento.setDetalhe(detalhe.toString());
        pagamentoRepository.saveAndFlush(pagamento);

        return new ReciboRecebimento(
                pagamento.getId(), pagamento.getData(), cliente.getNome(), vendedor.getNome(),
                req.valor(), req.tipo().name(),
                saldoAnterior, clienteRepository.saldoDevedor(cliente.getId()), itens);
    }

    /**
     * Abate uma alocação numa parcela ("L.." = carnê SET, "V.." = parcela de venda).
     *
     * A entidade é carregada só para o check de dono e a descrição; a ESCRITA é
     * um UPDATE atômico condicional (padrão do baixarEstoque). Se retornar 0,
     * outra operação abateu no meio — aborta, e o rollback da transação desfaz
     * os abates já feitos neste recebimento. O restante impresso no recibo é
     * calculado em Java: a entidade fica stale após o UPDATE em massa (e, como
     * nenhum setter é chamado, o dirty-checking não regrava o valor velho).
     */
    private ReciboRecebimento.Item abater(Cliente cliente, ReceberRequest.Alocacao aloc,
                                          StringJoiner detalhe) {
        String id = aloc.parcelaId();
        if (id.startsWith("L")) {
            PagamentoFiado debito = pagamentoRepository.findById(idNumerico(id))
                    .filter(p -> p.getTipo() == TipoPagamentoFiado.DEBITO_INICIAL
                            && p.getCliente().getId().equals(cliente.getId()))
                    .orElseThrow(() -> new RegraNegocioException("Parcela não encontrada: " + id));
            BigDecimal aberto = debito.getValorAberto();
            validarValor(aloc.valor(), aberto, id);
            if (pagamentoRepository.abaterDebito(debito.getId(), aloc.valor()) == 0) {
                throw new RegraNegocioException(
                        "Parcela " + id + " foi recebida por outra operação — confira e refaça");
            }
            LocalDate venc = LocalDate.ofInstant(debito.getData(), FUSO);
            String desc = descricaoLegada(debito) + " " + DATA_BR.format(venc);
            detalhe.add(desc);
            return new ReciboRecebimento.Item(desc, null, venc, aloc.valor(),
                    aberto.subtract(aloc.valor()));
        }
        if (id.startsWith("V")) {
            ParcelaFiado parcela = parcelaRepository.findById(idNumerico(id))
                    .filter(p -> p.getVenda().getCliente().getId().equals(cliente.getId()))
                    .orElseThrow(() -> new RegraNegocioException("Parcela não encontrada: " + id));
            BigDecimal aberto = parcela.getValorAberto();
            validarValor(aloc.valor(), aberto, id);
            if (parcelaRepository.abater(parcela.getId(), aloc.valor()) == 0) {
                throw new RegraNegocioException(
                        "Parcela " + id + " foi recebida por outra operação — confira e refaça");
            }
            String desc = "Venda nº " + parcela.getVenda().getId()
                    + " — " + parcela.getNumero() + "/" + parcela.getVenda().getParcelas().size();
            detalhe.add(desc);
            return new ReciboRecebimento.Item(desc, parcela.getVenda().getId(),
                    parcela.getVencimento(), aloc.valor(), aberto.subtract(aloc.valor()));
        }
        throw new RegraNegocioException("Parcela inválida: " + id);
    }

    private void validarValor(BigDecimal valor, BigDecimal aberto, String id) {
        if (aberto == null || aberto.signum() <= 0) {
            throw new RegraNegocioException("Parcela " + id + " já está quitada");
        }
        if (valor.compareTo(aberto) > 0) {
            throw new RegraNegocioException(
                    "Valor maior que o que resta da parcela (R$ " + aberto + ")");
        }
    }

    private long idNumerico(String id) {
        try {
            return Long.parseLong(id.substring(1));
        } catch (NumberFormatException e) {
            throw new RegraNegocioException("Parcela inválida: " + id);
        }
    }

    /** Parcelas com valor_aberto > 0 (carnê SET + vendas fiado), mais antigas primeiro. */
    private List<CarneDTO.Parcela> parcelasAbertas(Long clienteId) {
        LocalDate hoje = LocalDate.now(FUSO);
        List<CarneDTO.Parcela> abertas = new ArrayList<>();

        for (PagamentoFiado p : pagamentoRepository
                .findByClienteIdAndTipoOrderByDataAsc(clienteId, TipoPagamentoFiado.DEBITO_INICIAL)) {
            if (p.getValorAberto() == null || p.getValorAberto().signum() <= 0) continue;
            LocalDate venc = LocalDate.ofInstant(p.getData(), FUSO);
            abertas.add(new CarneDTO.Parcela("L" + p.getId(), descricaoLegada(p), null, null,
                    venc, p.getValor().negate(), p.getValorAberto(),
                    ChronoUnit.DAYS.between(venc, hoje)));
        }
        for (ParcelaFiado p : parcelaRepository.doCliente(clienteId)) {
            if (p.getValorAberto().signum() <= 0) continue;
            Venda venda = p.getVenda();
            abertas.add(new CarneDTO.Parcela("V" + p.getId(),
                    "Venda nº " + venda.getId() + " — " + p.getNumero() + "/" + venda.getParcelas().size(),
                    venda.getId(), venda.getObservacao(),
                    p.getVencimento(), p.getValor(), p.getValorAberto(),
                    ChronoUnit.DAYS.between(p.getVencimento(), hoje)));
        }
        abertas.sort(java.util.Comparator.comparing(CarneDTO.Parcela::vencimento));
        return abertas;
    }

    /** "Carnê SET nº 66/01" — o nº que a loja conhece do sistema antigo. */
    private String descricaoLegada(PagamentoFiado p) {
        String base = p.getDocumento() != null ? "Carnê SET nº " + p.getDocumento() : "Carnê SET";
        return p.getTipoNotinha() != null ? base + " · " + p.getTipoNotinha() : base;
    }

    private Cliente buscarCliente(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado"));
    }
}
