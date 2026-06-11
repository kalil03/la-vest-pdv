package br.com.loja.pdv.web;

import br.com.loja.pdv.repository.MarcaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Marcas só são lidas por aqui: a criação acontece junto do cadastro do
 * produto (marca digitada que não existe é criada na hora — sem fricção).
 */
@RestController
@RequestMapping("/api/marcas")
public class MarcaController {

    private final MarcaRepository marcaRepository;

    public MarcaController(MarcaRepository marcaRepository) {
        this.marcaRepository = marcaRepository;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return marcaRepository.findAll(Sort.by("nome")).stream()
                .map(m -> Map.<String, Object>of("id", m.getId(), "nome", m.getNome()))
                .toList();
    }
}
