# data-analyzer

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/danilomendes12/data-analyzer/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/danilomendes12/data-analyzer/tree/main)
[![codecov](https://codecov.io/gh/danilomendes12/data-analyzer/branch/main/graph/badge.svg)](https://codecov.io/gh/danilomendes12/data-analyzer)

Sistema que monitora um diretório em busca de arquivos `.dat` de vendas, processa-os de forma concorrente
e gera relatórios automaticamente no diretório de saída.

## Como compilar e executar

Requer **JDK 17+** (validado em 17 e 21) e Maven.

```
mvn clean package
java -jar target/data-analyzer-0.0.1-SNAPSHOT.jar
```

Sem configuração adicional a aplicação usa os diretórios padrão, criados de forma idempotente na subida:

| Propriedade       | Default                  | Descrição                                    |
|-------------------|--------------------------|----------------------------------------------|
| `app.input-dir`   | `${user.home}/data/in`   | Diretório monitorado para arquivos `.dat`.   |
| `app.output-dir`  | `${user.home}/data/out`  | Onde os relatórios `.done.dat` são gravados. |

Para apontar outros diretórios, passe as propriedades na linha de comando (ou via variável de ambiente /
`application.properties`):

```
java -jar target/data-analyzer-0.0.1-SNAPSHOT.jar \
  --app.input-dir=/caminho/para/entrada \
  --app.output-dir=/caminho/para/saida
```

Com a aplicação de pé, basta soltar um `.dat` no diretório de entrada: em instantes o relatório
`<nome>.done.dat` aparece na saída. Arquivos já presentes na subida também são processados (varredura
inicial). A aplicação segue viva até ser encerrada (`Ctrl+C`), quando drena o pool antes de sair.

## Como rodar os testes

```
mvn verify
```

Compila, roda a suíte e falha se a cobertura de linhas cair abaixo de 90% (gate do JaCoCo).

> **Nota (JDK 26):** o JaCoCo 0.8.13 não gera o relatório de cobertura sob o JDK 26, então o `verify`
> quebra nessa versão (o código em si roda em 17+). Se o seu `mvn` estiver sob uma JVM 26 (o Maven do
> Homebrew, por exemplo), force uma 17/21:
>
> ```
> JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify
> ```

## Decisões de arquitetura

![Diagrama de arquitetura: fluxo de um `.dat` da entrada à saída, com os componentes coloridos por pacote (io, parser, analysis, domain, config).](docs/arquitetura.png)

### Estrutura de pacotes

**Organização por camada técnica** (`domain`, `parser`, `analysis`, `io`, `config`), não por feature: com
um único fluxo de negócio (arquivo → relatório) não há features paralelas a isolar, então o recorte por
camada é o que dá nome às fronteiras que importam. `domain` (registros do formato), `parser` (linha →
registro), `analysis` (agregação em passada única), `io` (watcher, pool, escrita atômica) e `config`.

**A dependência aponta para dentro:** `io → analysis → domain` e `io → parser → domain`. `domain`,
`parser` e `analysis` não conhecem `io` nem Spring — o núcleo de regra de negócio fica puro e testável em
isolamento (sem `WatchService`, sem pool, sem contexto), e todo o acoplamento a framework e I/O se
concentra na borda (`io` + `config`).

### Spring mínimo

Spring deliberadamente enxuto: `spring-boot-starter` (sem web/persistência), injeção por construtor,
`@ConfigurationProperties` e um `ApplicationRunner` no bootstrap. O que ele compra: **descoberta automática
dos parsers** (um tipo novo `004` é só uma nova `@Component` — nenhuma classe existente muda) e o **ciclo de
vida** — a ordem de destruição de beans dá o desligamento ordenado de graça. Sem ele o projeto seria
viável, mas eu teria que ligar os colaboradores num `main`, montar o mapa `prefixo → parser` na mão e
coordenar o shutdown manualmente. Aceito a dependência em nome de menos código de encanamento.

### Controle de reprocessamento — `.done.dat` (Opção B)

Um arquivo está **processado se, e somente se, o seu `<nome>.done.dat` existe**. Esse predicado único
(`ProcessedFileChecker`) é consultado pela varredura de subida e pelo watcher antes de submeter, então um
`ENTRY_MODIFY` tardio de arquivo já pronto é ignorado.

**Limitação assumida:** um arquivo já processado que seja modificado depois **não** é reprocessado — o
predicado só olha a existência da saída, não o conteúdo. É coerente com "cada `.dat` é uma entrega imutável".

**Alternativas descartadas:** *reprocessar sempre* (desperdício + leitura de saída meio-escrita);
*mover/arquivar a entrada* (efeito destrutivo no diretório do usuário, estado ambíguo em caso de crash);
*estado dedicado* (banco/índice — peso desproporcional para o desafio); *comparar `lastModified`* (frágil a
granularidade de timestamp e a cópias que preservam mtime). A existência do `.done.dat` é binária e inequívoca.

### I/O e concorrência

- **Escrita atômica:** o relatório é gravado num `.tmp` no mesmo diretório de saída e movido com
  `ATOMIC_MOVE` (fallback para move simples + log se não suportado). Evita que um `.done.dat` truncado por
  crash seja lido como "já processado"; o `.tmp` residual de uma falha é limpo no `finally`.
- **Pool e fila:** pool fixo de `availableProcessors()` threads nomeadas (`file-processor-N`); fila
  ilimitada, já que cada tarefa carrega só um `Path`.
- **Convenção de nomes — fonte única (`OutputPathResolver`):** `vendas.dat` → `<out>/vendas.done.dat`
  (troca a extensão). A regra e o literal `.dat` de entrada vivem num só lugar porque `ReportWriter` e
  `ProcessedFileChecker` dependem dela — cópias divergentes quebrariam o skip em silêncio.
- **`N/A` para dado derivado ausente:** arquivo sem vendas e/ou sem `001` imprime `N/A`, preservando o
  formato fixo de 4 linhas.
- **Corrida subida × watcher:** o `WatchService` é registrado **antes** da varredura; um arquivo que caia
  entre os dois passos gera evento e não se perde (a deduplicação evita processá-lo duas vezes).
- **Deduplicação em voo:** submissões passam por um `ConcurrentHashMap.newKeySet()` de caminhos
  normalizados; um path só é submetido se `add` retornar `true`, e é removido no `finally`.
- **Tamanho estável:** antes de processar, espera duas leituras de tamanho iguais (até 5 × 200 ms). A
  checagem roda **dentro da tarefa do pool** (loop fino) e cobre também um arquivo copiado no boot.
- **`OVERFLOW`:** `log.warn` + reexecução da varredura — nenhum mecanismo novo.
- **Bootstrap `ApplicationRunner` + `@PreDestroy`** (em vez de `SmartLifecycle`): simplicidade; o
  desligamento ordenado sai da ordem de destruição de beans (watcher destruído antes do pool).
- **Thread do watch loop é não-daemon:** sendo app não-web, é ela que mantém o JVM vivo; no shutdown,
  fechar o `WatchService` faz o `take()` lançar e o loop encerra. Cada evento é blindado por
  `try/catch (RuntimeException)` para uma falha inesperada não derrubar essa thread.

### Domínio, parsing e análise

- **Charset UTF-8:** o delimitador `ç` (U+00E7) exige ler os `.dat` explicitamente como **UTF-8**.
- **`BigDecimal` nos valores monetários** (não `double`): o desempate da venda mais cara é por igualdade
  exata de valor, que `double` não garante.
- **`sealed interface DataRecord permits Seller, Customer, Sale`:** tipo fechado dos registros de topo
  (`SaleItem` fica de fora por ser aninhado em `Sale`); mantém `LineParser`/`parse` type-safe sem `Object`.
- **`ParserRegistry` por prefixo:** mapa `prefixo → parser` a partir dos beans injetados; um `004` futuro é
  só uma nova implementação. Prefixo desconhecido / linha malformada retornam `Optional.empty()` + warning,
  nunca lançam.
- **`DataAnalyzer` um por arquivo** (não singleton): estado isolado por design, sem sincronização; mantém só
  os agregados, não materializa listas de registros.
- **Ordem de chegada irrelevante:** o volume é acumulado por nome mesmo antes de o `001` correspondente
  chegar; a filtragem "só cadastrados" acontece no `summarize()`.
- **`Optional` nos campos derivados do `Report`:** venda mais cara e pior vendedor podem não existir; as
  contagens são sempre `long`.

### Estratégia de testes

- **Recursos `.dat` em vez de listas inline:** os testes de integração usam arquivos reais em
  `src/test/resources`, exercitando o parsing (incluindo o `ç` em UTF-8). Helpers compartilhados em
  `IntegrationTestFiles`.
- **Robustez a linhas malformadas:** um `.dat` com prefixo desconhecido, item inválido e linha em branco
  intercalados produz **o mesmo relatório** do arquivo limpo.
- **Concorrência com isolamento:** dois arquivos com relatórios distintos soltos juntos validam o pool e o
  fato de cada arquivo ter o seu próprio `DataAnalyzer`.
- **Contexto isolado por classe (`@DirtiesContext(AFTER_CLASS)`):** cada `@SpringBootTest` monitora os seus
  próprios diretórios temporários (e ainda exercita o desligamento ordenado).
- **Semeadura no `@DynamicPropertySource`:** arquivos que precisam existir antes do boot são criados no
  bloco estático — o único ponto garantidamente anterior à subida (varredura inicial e teste de skip).
- **OVERFLOW e filtro em teste de unidade:** `handleEvent` é package-private para verificar o despacho com
  um `WatchEvent` stub, sem `WatchService` real.

## Regras de negócio e casos de borda

Assumidas na fase de análise, sujeitas a confirmação com o avaliador:

- **Valor de uma venda** = Σ (quantidade × preço) por item, em `BigDecimal`.
- **Contagem de clientes** = CNPJs distintos em `002`; **de vendedores** = CPFs distintos em `001`.
- **Pior vendedor** = entre os cadastrados em `001`, o de menor volume total (cadastrado sem venda tem
  volume zero). Uma venda cujo vendedor não tem `001` não vira candidato a pior vendedor, mas seu valor
  **ainda concorre** à venda mais cara.
- **Venda mais cara** = maior valor entre todas as vendas, cadastrado o vendedor ou não.
- **Desempates** (determinísticos): venda mais cara → menor ID; pior vendedor → menor nome (alfabético).
- **Dataset vazio** → contagens zero e `Optional.empty()` para venda mais cara e pior vendedor (impresso `N/A`).
- **ID de venda é numérico** (`Long`): `08` e `8` são a mesma venda; zeros à esquerda não são preservados.
  Manter como `String` foi descartado porque o desempate é numérico (`8 < 10`).
- **Vínculo venda → vendedor é por nome** (o `003` referencia o vendedor pelo nome, não pelo CPF).
  Consequência assumida do formato: homônimos têm seus volumes somados — limitação da entrada, não escolha.

## Uso de IA

Este projeto usou assistência de IA (Claude) em três estágios, sempre com a decisão final e a validação minhas.

**1. Planejamento.** A partir das especificações do desafio e da arquitetura que desenhei, um agente num
Claude Project transformou a solução num **plano de desenvolvimento de 6 etapas**. A IA acelerou o desenho e
mapeou alternativas com trade-offs (ex.: controle de reprocessamento); as decisões de arquitetura e negócio
— Opção B, escrita atômica, modelo de concorrência, regras do relatório — foram minhas.

**2. Execução.** Cada etapa rodou num ciclo fechado: o agente do Claude Project gera o prompt → o Claude
Code gera o código → eu **valido** (leitura + testes manuais) **antes do commit** → após o push, envio o
link do repositório ao agente, que **valida a etapa** e produz o prompt da próxima. Nenhum código entrou sem
minha leitura linha a linha e sem `mvn verify` (build + suíte + gate de 90%).

**3. Revisão.** Ao fim das etapas: **execução manual** com casos de borda (massa de testes `.dat` gerada
pela IA) e redação deste README **pela IA, revisado por mim**. A IA também varreu código morto / imports /
dependências sem uso (nada no `main/`) e extraiu o helper `IntegrationTestFiles` da duplicação entre testes.

**Onde divergi das sugestões.** Mantive de propósito os acessores de domínio não lidos (`SaleItem.id()`,
campos de `Customer`/`Seller`) que uma varredura ingênua de "código morto" removeria: eles modelam
fielmente o formato de entrada de quatro campos, e removê-los enfraqueceria a validação dos parsers.
