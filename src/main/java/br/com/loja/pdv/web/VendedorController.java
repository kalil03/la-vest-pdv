package br.com.loja.pdv.web;

import br.com.loja.pdv.domain.Vendedor;
import br.com.loja.pdv.repository.VendedorRepository;
import br.com.loja.pdv.service.RegraNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vendedores")
public class VendedorController {

    private final VendedorRepository vendedorRepository;

    public VendedorController(VendedorRepository vendedorRepository) {
        this.vendedorRepository = vendedorRepository;
    }

    public record NovoVendedor(@NotBlank(message = "Nome do vendedor é obrigatório") String nome, String cpf) {
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return vendedorRepository.findByAtivoTrueOrderByNome().stream()
                .map(this::dto)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@RequestBody @Valid NovoVendedor req) {
        Vendedor v = new Vendedor();
        v.setNome(req.nome().trim());
        v.setCpf(req.cpf() == null || req.cpf().isBlank() ? null : req.cpf().trim());
        vendedorRepository.save(v);
        return dto(v);
    }

    @PutMapping("/{id}")
    public Map<String, Object> atualizar(@PathVariable Long id, @RequestBody @Valid NovoVendedor req) {
        Vendedor v = vendedorRepository.findById(id)
                .filter(Vendedor::isAtivo)
                .orElseThrow(() -> new RegraNegocioException("Vendedor não encontrado"));
        v.setNome(req.nome().trim());
        v.setCpf(req.cpf() == null || req.cpf().isBlank() ? null : req.cpf().trim());
        vendedorRepository.save(v);
        return dto(v);
    }

    /** Desativa (soft-delete): vendas antigas continuam apontando pro vendedor, só some da lista. */
    @PostMapping("/{id}/desativar")
    public void desativar(@PathVariable Long id) {
        Vendedor v = vendedorRepository.findById(id)
                .filter(Vendedor::isAtivo)
                .orElseThrow(() -> new RegraNegocioException("Vendedor não encontrado"));
        v.setAtivo(false);
        vendedorRepository.save(v);
    }

    private Map<String, Object> dto(Vendedor v) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("nome", v.getNome());
        m.put("cpf", v.getCpf());
        return m;
    }
}
