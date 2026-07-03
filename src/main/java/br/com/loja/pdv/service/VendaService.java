package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.repository.*;
import br.com.loja.pdv.web.dto.FecharVendaRequest;
import br.com.loja.pdv.web.dto.VendaResumo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;

@Service
public class VendaService {

    private final VendaRepository vendaRepository;
    private final VariacaoRepository variacaoRepository;
    private final ClienteRepository clienteRepository;
    private final VendedorRepository vendedorRepository;
    private final PagamentoFiadoRepository pagamentoFiadoRepository;
    private final EstornoRepository estornoRepository;
    private final NfceRepository nfceRepository;

    public VendaService(VendaRepository vendaRepository,
                        VariacaoRepository variacaoRepository,
                        ClienteRepository clienteRepository,
                        VendedorRepository vendedorRepository,
                        PagamentoFiadoRepository pagamentoFiadoRepository,
                        EstornoRepository estornoRepository,
                        NfceRepository nfceRepository) {
        this.vendaRepository = vendaRepository;
        this.variacaoRepository = variacaoRepository;
        this.clienteRepository = clienteRepository;
        this.vendedorRepository = vendedorRepository;
        this.pagamentoFiadoRepository = pagamentoFiadoRepository;
        this.estornoRepository = estornoRepository;
        this.nfceRepository = nfceRepository;
    }

    /**
     * Fechar venda é atômico: Venda + Itens + parcelas + entrada — tudo numa
     * transação, qualquer falha desfaz tudo.
     *
     * O fiado não tem saldo armazenado em lugar nenhum: a linha da Venda FIADO
     * é o débito e a entrada vira um PagamentoFiado normal; a dívida é sempre
     * SUM(vendas FIADO) - SUM(pagamentos). As parcelas são só o cronograma
     * combinado, impresso no carnê.
     */
    @Transactional
    public VendaResumo fechar(FecharVendaRequest req) {
        Cliente cliente = resolverCliente(req);
        if (req.formaPagamento() == FormaPagamento.FIADO && cliente == null) {
            throw new RegraNegocioException("Venda fiado precisa de um cliente");
        }

        if (req.vendedorId() == null) {
            throw new RegraNegocioException("Selecione o vendedor antes de fechar a venda");
        }

        String tipoNotinha = req.tipoNotinha() == null ? "" : req.tipoNotinha().trim();
        if (!tipoNotinha.equals("Geral") && !tipoNotinha.equals("Tênis")) {
            throw new RegraNegocioException("Informe o tipo da notinha (Geral ou Tênis)");
        }

        Venda venda = new Venda();
        venda.setTipoNotinha(tipoNotinha);
        if (req.data() != null) {
            java.time.ZoneId fuso = br.com.loja.pdv.Fuso.LOJA;
            java.time.LocalDate hoje = java.time.LocalDate.now(fuso);
            if (req.data().isAfter(hoje)) {
                throw new RegraNegocioException("Data da venda não pode estar no futuro");
            }
            // o caixa manda a data SEMPRE (o campo vem preenchido com hoje): só é
            // retroativa se for de dia anterior — aí vale a convenção do meio-dia.
            // Data de hoje mantém o agora, senão toda promissória sairia com 12:00.
            if (req.data().isBefore(hoje)) {
                venda.setData(req.data().atTime(12, 0).atZone(fuso).toInstant());
            }
        }
        venda.setCliente(cliente);
        venda.setFormaPagamento(req.formaPagamento());
        venda.setVendedor(resolverVendedor(req.vendedorId()));
        venda.setObservacao(req.observacao() != null && !req.observacao().isBlank()
                ? req.observacao().trim() : null);
        if (req.formaPagamento() == FormaPagamento.CARTAO) {
            venda.setParcelasCartao(req.parcelasCartao());
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (FecharVendaRequest.Item itemReq : req.itens()) {
            Variacao variacao = variacaoRepository.findById(itemReq.variacaoId())
                    .orElseThrow(() -> new RegraNegocioException(
                            "Produto/variação não encontrado (id " + itemReq.variacaoId() + ")"));

            // Baixa silenciosa: o estoque não aparece nas telas (decisão de
            // negócio), mas continua sendo decrementado para o dia em que a
            // loja quiser retomar o controle. Nunca bloqueia a venda.
            variacaoRepository.baixarEstoque(variacao.getId(), itemReq.quantidade());

            BigDecimal precoUnit = itemReq.precoUnit() != null
                    ? itemReq.precoUnit()
                    : variacao.getProduto().getPreco();

            ItemVenda item = new ItemVenda();
            item.setVariacao(variacao);
            item.setQuantidade(itemReq.quantidade());
            item.setPrecoUnit(precoUnit);
            venda.adicionarItem(item);

            subtotal = subtotal.add(item.getSubtotal());
        }

        BigDecimal desconto = req.desconto() != null ? req.desconto() : BigDecimal.ZERO;
        if (desconto.compareTo(subtotal) > 0) {
            throw new RegraNegocioException("Desconto maior que o valor da venda");
        }
        venda.setDesconto(desconto);
        BigDecimal total = subtotal.subtract(desconto);
        venda.setTotal(total);

        BigDecimal entrada = BigDecimal.ZERO;
        if (req.formaPagamento() == FormaPagamento.FIADO) {
            entrada = montarFiado(venda, req.fiado(), total);
        }

        // flush: o INSERT precisa estar no banco antes da query nativa de saldo
        vendaRepository.saveAndFlush(venda);

        if (entrada.signum() > 0) {
            PagamentoFiado pagamento = new PagamentoFiado();
            pagamento.setCliente(cliente);
            pagamento.setVenda(venda);
            pagamento.setValor(entrada);
            pagamento.setTipo(req.fiado().entradaTipo());
            pagamentoFiadoRepository.saveAndFlush(pagamento);
        }

        return montarResumo(venda);
    }

    /** Valida entrada e cronograma; devolve o valor da entrada. */
    private BigDecimal montarFiado(Venda venda, FecharVendaRequest.Fiado fiado, BigDecimal total) {
        BigDecimal entrada = fiado != null && fiado.entradaValor() != null
                ? fiado.entradaValor() : BigDecimal.ZERO;
        if (entrada.compareTo(total) >= 0) {
            throw new RegraNegocioException("Entrada deve ser menor que o total (senão não é fiado)");
        }
        if (entrada.signum() > 0 && (fiado == null || fiado.entradaTipo() == null)) {
            throw new RegraNegocioException("Informe como a entrada foi paga (dinheiro, PIX ou cartão)");
        }

        BigDecimal restante = total.subtract(entrada);

        List<FecharVendaRequest.Fiado.Parcela> parcelas =
                fiado != null && fiado.parcelas() != null && !fiado.parcelas().isEmpty()
                        ? fiado.parcelas()
                        // sem cronograma informado: parcela única em 30 dias
                        : List.of(new FecharVendaRequest.Fiado.Parcela(
                                1, restante, LocalDate.now(br.com.loja.pdv.Fuso.LOJA).plusDays(30)));

        BigDecimal soma = BigDecimal.ZERO;
        for (var p : parcelas) {
            if (p.valor().signum() <= 0) {
                throw new RegraNegocioException("Parcela " + p.numero() + " com valor inválido");
            }
            ParcelaFiado parcela = new ParcelaFiado();
            parcela.setNumero(p.numero());
            parcela.setValor(p.valor());
            parcela.setValorAberto(p.valor()); // parcela nasce 100% em aberto
            parcela.setVencimento(p.vencimento());
            venda.adicionarParcela(parcela);
            soma = soma.add(p.valor());
        }
        if (soma.compareTo(restante) != 0) {
            throw new RegraNegocioException(
                    "Parcelas somam " + soma + " mas o restante após a entrada é " + restante);
        }
        return entrada;
    }

    @Transactional(readOnly = true)
    public VendaResumo buscarResumo(Long id) {
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Venda não encontrada (id " + id + ")"));
        return montarResumo(venda);
    }

    private Cliente resolverCliente(FecharVendaRequest req) {
        if (req.clienteId() != null) {
            return clienteRepository.findById(req.clienteId())
                    .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado (id " + req.clienteId() + ")"));
        }
        // Cadastro na hora, só com o nome: nada de exigir ficha completa no meio da venda
        if (req.clienteNome() != null && !req.clienteNome().isBlank()) {
            Cliente novo = new Cliente();
            novo.setNome(req.clienteNome().trim());
            novo.setTelefone(req.clienteTelefone());
            return clienteRepository.save(novo);
        }
        return null;
    }

    private Vendedor resolverVendedor(Long vendedorId) {
        if (vendedorId == null) return null;
        return vendedorRepository.findById(vendedorId)
                .orElseThrow(() -> new RegraNegocioException("Vendedor não encontrado (id " + vendedorId + ")"));
    }

    private VendaResumo montarResumo(Venda venda) {
        var itens = venda.getItens().stream()
                .map(i -> new VendaResumo.Item(
                        i.getVariacao().getId(), i.getVariacao().getProduto().getCodigo(),
                        descricao(i.getVariacao()), i.getQuantidade(), i.getPrecoUnit(), i.getSubtotal()))
                .toList();

        var parcelas = venda.getParcelas().stream()
                .map(p -> new VendaResumo.Parcela(p.getNumero(), p.getValor(), p.getVencimento()))
                .toList();

        Cliente cliente = venda.getCliente();
        boolean fiado = venda.getFormaPagamento() == FormaPagamento.FIADO;

        BigDecimal saldoDevedor = fiado ? clienteRepository.saldoDevedor(cliente.getId()) : null;
        BigDecimal entrada = fiado
                ? pagamentoFiadoRepository.findByVendaId(venda.getId()).stream()
                    .map(PagamentoFiado::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;

        return new VendaResumo(
                venda.getId(), venda.getData(), venda.getFormaPagamento(),
                venda.getTotal().add(venda.getDesconto()), venda.getDesconto(), venda.getTotal(),
                cliente != null ? cliente.getId() : null,
                cliente != null ? cliente.getNome() : null,
                venda.getVendedor() != null ? venda.getVendedor().getId() : null,
                venda.getVendedor() != null ? venda.getVendedor().getNome() : null,
                venda.getParcelasCartao(),
                venda.getObservacao(),
                entrada != null && entrada.signum() > 0 ? entrada : null,
                saldoDevedor, itens, parcelas);
    }

    /**
     * Cancela a venda: devolve o estoque e a MARCA como cancelada — nunca
     * deleta. A venda (com sua numeração e data originais), a entrada de fiado
     * e as parcelas permanecem no banco; todas as somas financeiras as ignoram
     * pelo filtro cancelada_em IS NULL. Recusado se alguma parcela do fiado já
     * recebeu pagamento (o carnê já andou) ou se há NFC-e AUTORIZADA
     * (cancelamento fiscal é evento na SEFAZ, não marcação local).
     */
    @Transactional
    public void cancelar(Long id, String operador, String motivo) {
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Venda não encontrada (id " + id + ")"));
        if (venda.getCanceladaEm() != null) {
            throw new RegraNegocioException("Esta venda já foi estornada");
        }

        boolean carneAndou = venda.getParcelas().stream()
                .anyMatch(p -> p.getValorAberto().compareTo(p.getValor()) < 0);
        if (carneAndou) {
            throw new RegraNegocioException(
                    "Esta venda já tem parcela recebida no carnê — não dá mais para cancelar");
        }

        boolean nfceAutorizada = nfceRepository.findByVendaId(id)
                .filter(n -> n.getStatus() == Nfce.Status.AUTORIZADO)
                .isPresent();
        if (nfceAutorizada) {
            throw new RegraNegocioException(
                    "Esta venda tem NFC-e autorizada — cancele a nota na SEFAZ antes de estornar");
        }

        // marcação atômica: se outro estorno passou na frente, 0 linhas e
        // abortamos ANTES de devolver o estoque em dobro
        if (vendaRepository.marcarCancelada(id, java.time.Instant.now(), operador, motivo) == 0) {
            throw new RegraNegocioException("Esta venda já foi estornada");
        }

        // auditoria imutável: quem desfez o quê, para sempre
        Estorno estorno = new Estorno();
        estorno.setVendaId(venda.getId());
        estorno.setOperador(operador);
        estorno.setMotivo(motivo);
        estorno.setClienteNome(venda.getCliente() != null ? venda.getCliente().getNome() : null);
        estorno.setFormaPagamento(venda.getFormaPagamento().name());
        estorno.setTotal(venda.getTotal());
        estorno.setResumo(venda.getItens().stream()
                .map(i -> i.getQuantidade() + "x " + descricao(i.getVariacao()))
                .collect(java.util.stream.Collectors.joining("; ")));
        estornoRepository.save(estorno);

        for (ItemVenda item : venda.getItens()) {
            variacaoRepository.devolverEstoque(item.getVariacao().getId(), item.getQuantidade());
        }
        // entrada de fiado e parcelas PERMANECEM: saem das somas pelo filtro
    }

    private String descricao(Variacao v) {
        StringJoiner sj = new StringJoiner(" ");
        sj.add(v.getProduto().getNome());
        if (v.getTamanho() != null) sj.add(v.getTamanho());
        if (v.getCor() != null) sj.add(v.getCor());
        return sj.toString();
    }
}
