package br.com.loja.pdv.web;

import br.com.loja.pdv.domain.Marca;
import br.com.loja.pdv.repository.MarcaRepository;
import br.com.loja.pdv.service.RegraNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Marcas: leitura para filtros/autocomplete e cadastro direto (tela de
 * Ajustes). No cadastro de produto, marca digitada que não existe continua
 * sendo criada na hora — sem fricção.
 */
@RestController
@RequestMapping("/api/marcas")
public class MarcaController {

    private final MarcaRepository marcaRepository;

    public MarcaController(MarcaRepository marcaRepository) {
        this.marcaRepository = marcaRepository;
    }

    public record NovaMarca(@NotBlank(message = "Nome da marca é obrigatório") String nome) {}

    @GetMapping
    public List<Map<String, Object>> listar() {
        return marcaRepository.findAll(Sort.by("nome")).stream()
                .map(m -> Map.<String, Object>of("id", m.getId(), "nome", m.getNome()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@RequestBody @Valid NovaMarca req) {
        String nome = req.nome().trim();
        if (marcaRepository.findByNomeIgnoreCase(nome).isPresent()) {
            throw new RegraNegocioException("Já existe a marca " + nome);
        }
        Marca m = new Marca();
        m.setNome(nome);
        marcaRepository.save(m);
        return Map.of("id", m.getId(), "nome", m.getNome());
    }
}
