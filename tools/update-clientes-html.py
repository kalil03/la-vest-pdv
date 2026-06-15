with open('src/main/resources/static/clientes.html', 'r') as f:
    c = f.read()

# I will add a new details section for "Ficha Pessoal e Crediário"
new_fields = """
        <details style="margin: 10px 0; background: #fff; padding: 10px; border: 1px solid #ddd; border-radius: 4px;">
          <summary style="cursor: pointer; font-weight: bold; color: #1b3a5c;">Ficha Completa e Crediário</summary>
          <div class="form-grid" style="margin-top: 10px; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));">
            <div><label for="tipo">Tipo</label><select id="tipo"><option value="FISICA">Física</option><option value="JURIDICA">Jurídica</option></select></div>
            <div><label for="rg">RG</label><input id="rg" type="text"></div>
            <div><label for="dataNasc">Data de Nasc.</label><input id="dataNasc" type="date"></div>
            <div><label for="limiteCred">Limite Cred (R$)</label><input id="limiteCred" type="number" step="0.01"></div>
            <div><label for="bloqueado">Bloqueado</label><select id="bloqueado"><option value="">Não</option><option value="SIM">Sim</option></select></div>
            <div><label for="pfisProfissao">Profissão</label><input id="pfisProfissao" type="text"></div>
            <div><label for="pfisRendaConj">Renda Cônjuge</label><input id="pfisRendaConj" type="number" step="0.01"></div>
            <div style="grid-column: span 2;"><label for="anotacoes">Anotações</label><input id="anotacoes" type="text"></div>
          </div>
        </details>
        <div class="form-acoes">
"""

c = c.replace('<div class="form-acoes">', new_fields)

with open('src/main/resources/static/clientes.html', 'w') as f:
    f.write(c)

