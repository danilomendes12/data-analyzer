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
Sem nenhuma configuração adicional a aplicação usa os diretórios padrão, criados de forma idempotente na
subida:

| Propriedade       | Default                  | Descrição                                  |
|-------------------|--------------------------|--------------------------------------------|
| `app.input-dir`   | `${user.home}/data/in`   | Diretório monitorado para arquivos `.dat`. |
| `app.output-dir`  | `${user.home}/data/out`  | Onde os relatórios `.done.dat` são gravados. |

Para apontar outros diretórios, passe as propriedades na linha de comando (ou via variável de ambiente /
`application.properties`):

```
java -jar target/data-analyzer-0.0.1-SNAPSHOT.jar \
  --app.input-dir=/caminho/para/entrada \
  --app.output-dir=/caminho/para/saida
```

Com a aplicação de pé, basta soltar um arquivo `.dat` no diretório de entrada: em poucos instantes o
relatório correspondente (`<nome>.done.dat`) aparece na saída. Arquivos já presentes na subida também são
processados (varredura inicial). A aplicação segue viva monitorando o diretório até ser encerrada
(`Ctrl+C`), quando faz um desligamento ordenado (drena o pool antes de sair).

## Como rodar os testes

```
mvn verify
```

Compila, roda a suíte de testes e falha se a cobertura de linhas cair abaixo de 80% (gate do JaCoCo).

> **Nota (JDK 26):** o JaCoCo 0.8.13 ainda não gera o relatório de cobertura sob o JDK 26, então o
> `verify` quebra nessa versão. O código roda em 17+; a limitação é só do agente de cobertura no `verify`.
> Se o seu `mvn` estiver apontado para uma JVM 26 (o Maven do Homebrew, por exemplo, traz a 26 como
> runtime), force uma 17/21 na invocação:
>
> ```
> JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify
> ```

## Decisões de arquitetura

![Diagrama de arquitetura: fluxo de um `.dat` da entrada à saída, com os componentes coloridos por pacote (io, parser, analysis, domain, config).](docs/arquitetura.png)

### Estrutura de pacotes

**Organização por camada técnica** (`domain`, `parser`, `analysis`, `io`, `config`) — e não por feature.
A escolha é deliberada: com um único fluxo de negócio (arquivo → relatório) não há features paralelas para
isolar, então um recorte por feature criaria pacotes de um componente só e esconderia justamente o que
importa aqui — as fronteiras entre camadas. Cada pacote agrupa uma responsabilidade do fluxo: `domain` (os
registros do formato de entrada), `parser` (linha de texto → registro), `analysis` (agregação em passada
única), `io` (monitoramento do diretório, orquestração do pool e escrita atômica) e `config` só para as
propriedades. Para o tamanho do projeto, cinco pacotes coesos são o suficiente para dar nome às fronteiras
sem virar cerimônia.

**A dependência aponta para dentro**, em direção ao domínio: `io → analysis → domain` e
`io → parser → domain`. O `domain` não conhece ninguém; `parser` e `analysis` não conhecem `io` nem Spring.
Isso mantém o núcleo de regra de negócio (parsing + análise) puro e testável em isolamento — os testes de
`parser`/`analysis` rodam sem `WatchService`, sem pool e sem contexto Spring — e concentra todo o
acoplamento a framework e a I/O numa única camada de borda (`io` + `config`).

### Spring mínimo — e o que aconteceria sem ele

O Spring aqui é deliberadamente enxuto: `spring-boot-starter` (sem web, sem persistência), injeção por
construtor, `@ConfigurationProperties` para os diretórios e um `ApplicationRunner` para o bootstrap. O que
ele realmente compra: **descoberta automática dos parsers** (o `ParserRegistry` recebe `List<LineParser>`
e um tipo novo `004` é só uma nova `@Component` — nenhuma classe existente muda) e o **ciclo de vida** —
a ordem de destruição de beans dá o desligamento ordenado de graça (ver watcher/pool abaixo).

Sem Spring, o projeto ainda seria viável, mas eu teria que escrever à mão o que hoje é declarativo:
instanciar e ligar os colaboradores num `main`, montar o mapa `prefixo → parser` manualmente (perdendo o
plug-and-play de novos tipos) e coordenar na unha o shutdown ordenado (fechar o watcher antes de drenar o
pool). A troca é consciente: aceito a dependência do framework em nome de menos código de encanamento e de
um ciclo de vida testado.

### Controle de reprocessamento — `.done.dat` (Opção B)

Um arquivo de entrada é considerado **já processado se, e somente se, o seu arquivo de saída
`<nome>.done.dat` existe**. Esse único predicado (`ProcessedFileChecker`) é consultado tanto pela varredura
de subida quanto pelo watcher antes de submeter — então um `ENTRY_MODIFY` tardio de um arquivo já pronto é
ignorado, coerente com a premissa de entrada imutável.

**Limitação assumida e documentada:** se um arquivo de entrada já processado for **modificado depois** (com
o `.done.dat` ainda presente), a modificação **não** é reprocessada — o predicado só olha a existência da
saída, não o conteúdo nem o `lastModified` da entrada. É o preço de um mecanismo simples e sem estado, e é
compatível com o modelo de "cada `.dat` é uma entrega imutável".

Alternativas descartadas:

- **Reprocessar sempre:** gastaria CPU reescrevendo relatórios idênticos a cada evento/subida e reintroduziria
  o problema de leitura concorrente de saída meio-escrita. Descartada por desperdício e por não agregar
  correção.
- **Mover/arquivar o arquivo de entrada após processar:** muda o diretório do usuário (efeito colateral
  destrutivo) e complica a reentrância; um crash entre processar e mover deixaria estado ambíguo.
- **Estado dedicado (banco/arquivo de índice de processados):** resolveria a detecção de modificação, mas
  adiciona um componente com o seu próprio ciclo de vida, consistência e recuperação de crash — peso
  desproporcional para o critério de simplicidade do desafio.
- **Comparar `lastModified` da entrada com o da saída:** detectaria reprocessamento, mas é frágil a
  granularidade de timestamp do sistema de arquivos e a cópias que preservam mtime (`cp -p`, unzip), gerando
  tanto falso-negativo quanto reprocessamento espúrio. A existência do `.done.dat` é um sinal binário e
  inequívoco.

### I/O e concorrência

- **Escrita atômica:** o relatório é gravado num `.tmp` **no mesmo diretório de saída** e movido com
  `ATOMIC_MOVE`; se o sistema de arquivos não suportar, capturamos `AtomicMoveNotSupportedException` e caímos
  para um move simples com log. Racional: um `.done.dat` truncado por crash seria lido como "já processado"
  pela varredura de subida — a atomicidade protege o mecanismo de skip. O `.tmp` residual de uma falha é
  limpo no `finally`.
- **Pool e fila:** pool fixo de `Runtime.getRuntime().availableProcessors()` threads com `ThreadFactory`
  nomeando as threads (`file-processor-N`, facilita ler logs concorrentes); fila ilimitada de propósito, já
  que cada tarefa carrega só um `Path`. O loop de eventos é fino, delegando a colaboradores testáveis
  (checker, varredura, submitter).
- **Convenção de nomes — fonte única (`OutputPathResolver`):** `vendas.dat` vira `<out>/vendas.done.dat`
  (troca a extensão, não concatena `vendas.dat.done.dat`). A regra vive num componente próprio porque o
  `ReportWriter` (escreve) e o `ProcessedFileChecker` (decide se já foi processado) dependem dela; se cada um
  tivesse sua cópia, o mecanismo de skip quebraria em silêncio no dia em que divergissem. O mesmo componente é
  o único lugar do literal `.dat` de entrada (`isInputFile`: arquivo regular com extensão `.dat`), usado tanto
  pela varredura de subida quanto pelo loop de eventos do watcher.
- **`N/A` para dado derivado ausente:** arquivo sem vendas e/ou sem vendedores `001` imprime `N/A` no campo
  correspondente, preservando o formato fixo de 4 linhas do enunciado. Alternativa descartada: omitir a linha —
  quebraria o formato.
- **Corrida subida × watcher:** na subida o `WatchService` é registrado **antes** da varredura inicial. Um
  arquivo que caia entre os dois passos gera evento e não se perde; a deduplicação impede que ele seja
  processado duas vezes.
- **Deduplicação em voo:** submissões passam por um `ConcurrentHashMap.newKeySet()` de caminhos normalizados;
  um path só é submetido se `add` retornar `true`, e é removido no `finally` da tarefa. Cobre tanto varredura ×
  evento quanto vários `ENTRY_MODIFY` do mesmo arquivo.
- **Tamanho estável (arquivo ainda em escrita):** antes de processar, esperamos duas leituras consecutivas de
  tamanho iguais (até 5 tentativas × 200 ms ≈ 800 ms). Ao estourar, `log.error` e desistência — o arquivo é
  recuperado na próxima subida pela própria varredura. A checagem roda **dentro da tarefa do pool** (não no
  loop de eventos): mantém o loop fino e não bloqueante, e cobre também um arquivo sendo copiado no exato
  momento do boot (visto pela varredura).
- **`OVERFLOW`:** `log.warn` + reexecução da varredura da Opção B — nenhum mecanismo novo.
- **Bootstrap com `ApplicationRunner` + `@PreDestroy` (em vez de `SmartLifecycle`):** escolha por simplicidade
  (critério do desafio). O desligamento ordenado ainda sai de graça pela ordem de destruição de beans — o
  watcher depende do submitter, então é destruído primeiro (para de aceitar eventos) e só então o pool é
  drenado (`shutdown` + `awaitTermination`).
- **Thread do watch loop é não-daemon:** de propósito. Sendo um app não-web, sem uma thread não-daemon o JVM
  encerraria assim que `main()` retornasse e o watcher morreria logo após subir. A thread do loop é a razão de a
  aplicação continuar viva; no shutdown, fechar o `WatchService` faz o `take()` lançar
  `ClosedWatchServiceException` (fechamento normal, sem re-interromper a thread) e o loop encerra, liberando o
  JVM. (As threads do pool seguem daemon; são drenadas pelo `@PreDestroy` do submitter.) Como essa thread é a
  que mantém o JVM vivo, o tratamento de cada evento é blindado por um `try/catch (RuntimeException)`: uma
  exceção inesperada (ex.: `RejectedExecutionException` ao submeter durante o shutdown) é logada e não derruba o
  loop.

### Domínio, parsing e análise

- **Charset UTF-8:** o delimitador `ç` (U+00E7) exige ler os arquivos `.dat` explicitamente como **UTF-8** em
  qualquer ponto de leitura; em outro charset o byte do `ç` quebra o `split`.
- **`BigDecimal` nos valores monetários:** preço e volume de vendas usam `BigDecimal` (não `double`) para
  evitar erro de ponto flutuante na soma `Σ (quantidade × preço)` — o desempate da venda mais cara é por
  igualdade exata de valor, que `double` não garante.
- **`sealed interface DataRecord permits Seller, Customer, Sale`:** modela os três registros de topo como um
  tipo fechado. `SaleItem` fica de fora por ser aninhado em `Sale`. `LineParser<T extends DataRecord>` e
  `ParserRegistry.parse` (`Optional<DataRecord>`) ficam type-safe sem `Object`.
- **`ParserRegistry` por prefixo:** mapa `prefixo → parser` construído a partir da lista de beans `LineParser`
  injetada pelo Spring. Um tipo `004` futuro é só uma nova implementação registrada — nenhuma classe existente
  muda. Prefixo desconhecido / linha malformada nunca lançam exceção para fora: retornam `Optional.empty()` +
  log de warning (SLF4J).
- **`DataAnalyzer` como acumulador de passada única, um por arquivo:** não é bean singleton. Cada arquivo tem
  seu próprio analyzer, então o estado é isolado por design e dispensa sincronização. Mantém só estado agregado
  (sets de documentos, mapa de volume por vendedor, venda mais cara corrente) — não materializa listas de
  registros.
- **Ordem de chegada irrelevante:** o volume é acumulado por nome de vendedor mesmo antes de o `001`
  correspondente chegar; a filtragem "só cadastrados" acontece no `summarize()`. Assim um `003` pode ser lido
  antes do seu `001`.
- **`Optional` nos campos derivados do `Report`:** venda mais cara e pior vendedor podem legitimamente não
  existir (sem vendas / sem vendedores cadastrados); as contagens são sempre definidas, logo primitivas `long`.

### Estratégia de testes

- **Recursos `.dat` em vez de listas inline:** os dados dos testes de integração vêm de arquivos em
  `src/test/resources` (`dados-teste.dat` — o exemplo do enunciado —, `dados-com-linhas-invalidas.dat`,
  `outro-dataset.dat`), no mesmo formato de entrada. O teste passa a exercitar o parsing do arquivo real
  (incluindo o delimitador `ç` em UTF-8), não uma lista de strings montada no próprio teste. Os helpers de
  arquivo compartilhados pelas classes de integração ficam num único `IntegrationTestFiles`.
- **Robustez a linhas malformadas:** um `.dat` com prefixo desconhecido, item de número inválido e linha em
  branco intercalados às linhas válidas produz **o mesmo relatório** do arquivo limpo — prova de que a passada
  única descarta o inválido sem contaminar o agregado.
- **Concorrência com isolamento:** dois arquivos soltos juntos, com relatórios distintos, validam o pool
  concorrente e o fato de cada arquivo ter o seu próprio `DataAnalyzer` (sem contaminação cruzada).
- **Contexto isolado por classe (`@DirtiesContext(AFTER_CLASS)`):** cada classe `@SpringBootTest` monitora os
  seus próprios diretórios temporários. Sem isolar, o cache de contexto do Spring reaproveitaria o contexto de
  outra classe e os testes que semeiam arquivos ANTES do boot passariam a monitorar o diretório errado. Marcar o
  contexto como sujo ao fim da classe ainda exercita o desligamento ordenado (`@PreDestroy`).
- **Varredura de subida e skip semeados no `@DynamicPropertySource`:** o arquivo (e, no teste de skip, o
  `.done.dat` pré-existente) é criado no bloco estático, antes de o contexto subir — o único ponto
  garantidamente anterior ao boot. O teste de skip valida que conteúdo e `lastModified` da saída não mudam numa
  janela de espera; se houvesse reprocessamento, o sentinela seria sobrescrito.
- **OVERFLOW e filtro em teste de unidade:** `handleEvent` é package-private para que o despacho de eventos seja
  verificável com um `WatchEvent` stub, sem `WatchService` real — OVERFLOW dispara nova varredura, e evento de
  não-`.dat` ou de arquivo já processado não submete nada.

## Regras de negócio e casos de borda

Assumidas na fase de análise, sujeitas a confirmação com o avaliador:

- **Valor de uma venda** = Σ (quantidade × preço) por item, em `BigDecimal` (sem erro de ponto flutuante).
- **Contagem de clientes** = CNPJs distintos em registros `002`; **contagem de vendedores** = CPFs distintos em `001`.
- **Pior vendedor** = entre os cadastrados em `001`, o de menor volume total de vendas. Vendedor cadastrado
  sem nenhuma venda tem volume zero (e portanto tende a ser o pior). Uma venda cujo nome de vendedor não
  possui `001` correspondente **não** vira candidato a pior vendedor, mas seu valor **ainda concorre** à venda mais cara.
- **Venda mais cara** = maior valor entre todas as vendas, independentemente de o vendedor estar cadastrado.
- **Desempates** (determinísticos e independentes da ordem de leitura): venda mais cara → menor ID;
  pior vendedor → menor nome (ordem alfabética).
- **Dataset vazio** → contagens zero e `Optional.empty()` para venda mais cara e pior vendedor (impresso como `N/A`).
- **ID de venda é numérico** (`Long`): `08` e `8` são a mesma venda; zeros à esquerda **não** são preservados
  na saída. Alternativa descartada: manter o ID como `String` para preservar a grafia original — rejeitada porque
  o desempate da venda mais cara é numérico (`8 < 10`) e um ID textual exigiria conversões espalhadas.
- **Vínculo venda → vendedor é por nome** (o registro `003` referencia o vendedor pelo nome, não pelo CPF).
  Consequência assumida do formato: vendedores homônimos têm seus volumes somados como se fossem um só.
  É uma limitação do formato de entrada, não uma escolha — não há chave melhor disponível na linha de venda.

## Uso de IA

Este projeto usou assistência de IA (Claude) em dois momentos, sempre com a decisão final e a revisão minhas.

**Planejamento.** A IA acelerou o desenho da solução — esboço da arquitetura, redação do plano de
desenvolvimento e diagrama no Excalidraw — e ajudou a mapear as alternativas de controle de reprocessamento
com seus trade-offs (reprocessar sempre, mover arquivos, estado dedicado, comparar `lastModified`). A escolha
pela Opção B (`.done.dat` como registro de "processado", entrada tratada como imutável) foi minha: a IA
levantou o leque de opções, não a decisão.

**Documentação e revisão final.** A IA revisou o código em busca de código morto, imports e dependências sem
uso (nada encontrado no `main/`), extraiu o helper de teste `IntegrationTestFiles` a partir da duplicação
que havia entre as classes de integração e redigiu este README seguindo a estrutura que defini no plano.

**Como revisei.** Todo código sugerido passou por `mvn verify` (build + suíte + gate de cobertura de 80%) e
por leitura linha a linha antes do commit. As decisões de negócio e de arquitetura — regras do relatório,
Opção B, escrita atômica, modelo de concorrência — são minhas; a IA as descreveu, não as escolheu.

**Onde divergi das sugestões.** Mantive de propósito os acessores de domínio não lidos (`SaleItem.id()`,
campos de `Customer`/`Seller`) que uma varredura ingênua de "código morto" removeria: eles modelam fielmente
o formato de entrada de quatro campos, e removê-los enfraqueceria a validação dos parsers.
