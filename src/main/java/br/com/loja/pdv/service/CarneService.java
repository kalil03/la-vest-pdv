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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service
public class CarneService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
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
     * O status de cada parcela é CALCULADO, nunca gravado: os pagamentos do
     * cliente são alocados nas parcelas mais antigas primeiro (FIFO, como
     * carnê funciona no balcão). Parcela coberta some da lista; coberta pela
     * metade aparece com valorAberto menor. Mutação zero — regra de ouro nº 1.
     */
    @Transactional(readOnly = true)
    public CarneDTO montar(Long clienteId) {
        Cliente cliente = buscarCliente(clienteId);
        BigDecimal saldo = clienteRepository.saldoDevedor(clienteId);

        List<CarneDTO.Parcela> abertas = parcelasAbertas(clienteId, pagamentoRepository.creditoAvulso(clienteId));

        var ultimos = pagamentoRepository
                .findTop3ByClienteIdAndTipoNotOrderByDataDesc(clienteId, TipoPagamentoFiado.DEBITO_INICIAL)
                .stream()
                .map(p -> new CarneDTO.Pagamento(p.getData(), p.getValor(), p.getTipo().name(),
                        p.getVendedor() != null ? p.getVendedor().getNome() : null))
                .toList();

        return new CarneDTO(
                ClienteDTO.de(cliente, saldo, cliente.getTipo(), cliente.getRg(), cliente.getDataNasc(), cliente.getLimiteCred(), cliente.getBloqueado(), cliente.getPfisProfissao(), cliente.getPfisRendaConj(), cliente.getAnotacoes()), saldo, abertas.size(),
                abertas.isEmpty() ? null : abertas.get(0).vencimento(),
                abertas, ultimos);
    }

    /** Recebimento atômico: valida, grava o pagamento e devolve o recibo. */
    @Transactional
    public ReciboRecebimento receber(ReceberRequest req) {
        Cliente cliente = buscarCliente(req.clienteId());
        if (!TIPOS_RECEBIMENTO.contains(req.tipo())) {
            throw new RegraNegocioException("Forma de recebimento inválida: " + req.tipo());
        }
        Vendedor vendedor = vendedorRepository.findById(req.vendedorId())
                .orElseThrow(() -> new RegraNegocioException("Funcionário não encontrado"));

        BigDecimal saldoAnterior = clienteRepository.saldoDevedor(cliente.getId());
        if (req.valor().compareTo(saldoAnterior) > 0) {
            throw new RegraNegocioException(
                    "Valor maior que o saldo devedor (R$ " + saldoAnterior + ")");
        }

        // O que este recebimento quita: diferença entre a visão FIFO antes e depois
        BigDecimal creditoAntes = pagamentoRepository.creditoAvulso(cliente.getId());
        List<CarneDTO.Parcela> abertasAntes = parcelasAbertas(cliente.getId(), creditoAntes);
        List<CarneDTO.Parcela> abertasDepois = parcelasAbertas(cliente.getId(), creditoAntes.add(req.valor()));

        var idsDepois = abertasDepois.stream().map(CarneDTO.Parcela::id).toList();
        List<CarneDTO.Parcela> quitadas = abertasAntes.stream()
                .filter(p -> !idsDepois.contains(p.id()))
                .toList();
        CarneDTO.Parcela parcial = abertasDepois.stream()
                .filter(p -> p.valorAberto().compareTo(p.valor()) < 0)
                .findFirst().orElse(null);

        PagamentoFiado pagamento = new PagamentoFiado();
        pagamento.setCliente(cliente);
        pagamento.setValor(req.valor());
        pagamento.setTipo(req.tipo());
        pagamento.setVendedor(vendedor);
        pagamentoRepository.saveAndFlush(pagamento);

        return new ReciboRecebimento(
                pagamento.getId(), pagamento.getData(), cliente.getNome(), vendedor.getNome(),
                req.valor(), req.tipo().name(),
                saldoAnterior, clienteRepository.saldoDevedor(cliente.getId()),
                quitadas, parcial);
    }

    /** Parcelas (carnê SET + vendas fiado nossas) ainda abertas após alocar o crédito FIFO. */
    private List<CarneDTO.Parcela> parcelasAbertas(Long clienteId, BigDecimal credito) {
        record Debito(String id, String descricao, LocalDate vencimento, BigDecimal valor) {}
        List<Debito> debitos = new ArrayList<>();

        for (PagamentoFiado p : pagamentoRepository
                .findByClienteIdAndTipoOrderByDataAsc(clienteId, TipoPagamentoFiado.DEBITO_INICIAL)) {
            debitos.add(new Debito("L" + p.getId(), "Carnê SET",
                    LocalDate.ofInstant(p.getData(), FUSO), p.getValor().negate()));
        }
        for (ParcelaFiado p : parcelaRepository.doCliente(clienteId)) {
            int total = p.getVenda().getParcelas().size();
            debitos.add(new Debito("V" + p.getId(),
                    "Venda nº " + p.getVenda().getId() + " — " + p.getNumero() + "/" + total,
                    p.getVencimento(), p.getValor()));
        }
        debitos.sort(java.util.Comparator.comparing(Debito::vencimento));

        LocalDate hoje = LocalDate.now(FUSO);
        List<CarneDTO.Parcela> abertas = new ArrayList<>();
        BigDecimal restante = credito;
        for (Debito d : debitos) {
            BigDecimal coberto = restante.min(d.valor()).max(BigDecimal.ZERO);
            restante = restante.subtract(coberto);
            BigDecimal aberto = d.valor().subtract(coberto);
            if (aberto.signum() > 0) {
                long atraso = Math.max(0, ChronoUnit.DAYS.between(d.vencimento(), hoje));
                abertas.add(new CarneDTO.Parcela(d.id(), d.descricao(), d.vencimento(),
                        d.valor(), aberto, atraso));
            }
        }
        return abertas;
    }

    private Cliente buscarCliente(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado"));
    }
}
