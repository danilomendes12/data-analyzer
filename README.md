# data-analyzer

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/danilomendes12/data-analyzer/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/danilomendes12/data-analyzer/tree/main)
[![codecov](https://codecov.io/gh/danilomendes12/data-analyzer/branch/main/graph/badge.svg)](https://codecov.io/gh/danilomendes12/data-analyzer)

Sistema que monitora um diretório em busca de arquivos `.dat` de vendas, processa-os de forma concorrente
e gera relatórios automaticamente no diretório de saída.

## Como executar

_Em construção._

## Como rodar os testes

_Em construção._

## Regras de negócio (Fase 2 — Análise)

Assumidas nesta fase, sujeitas a confirmação com o avaliador:

- **Valor de uma venda** = Σ (quantidade × preço) por item, em `BigDecimal` (sem erro de ponto flutuante).
- **Contagem de clientes** = CNPJs distintos em registros `002`; **contagem de vendedores** = CPFs distintos em `001`.
- **Pior vendedor** = entre os cadastrados em `001`, o de menor volume total de vendas. Vendedor cadastrado
  sem nenhuma venda tem volume zero (e portanto tende a ser o pior). Uma venda cujo nome de vendedor não
  possui `001` correspondente **não** vira candidato a pior vendedor, mas seu valor **ainda concorre** à venda mais cara.
- **Venda mais cara** = maior valor entre todas as vendas, independentemente de o vendedor estar cadastrado.
- **Desempates** (determinísticos e independentes da ordem de leitura): venda mais cara → menor ID;
  pior vendedor → menor nome (ordem alfabética).
- **Dataset vazio** → contagens zero e `Optional.empty()` para venda mais cara e pior vendedor.
- **ID de venda é numérico** (`Long`): `08` e `8` são a mesma venda; zeros à esquerda **não** são preservados
  na saída. Alternativa descartada: manter o ID como `String` para preservar a grafia original — rejeitada porque
  o desempate da venda mais cara é numérico (`8 < 10`) e um ID textual exigiria conversões espalhadas.
- **Vínculo venda → vendedor é por nome** (o registro `003` referencia o vendedor pelo nome, não pelo CPF).
  Consequência assumida do formato: vendedores homônimos têm seus volumes somados como se fossem um só.
  É uma limitação do formato de entrada, não uma escolha — não há chave melhor disponível na linha de venda.

## Decisões de arquitetura

- **Charset:** o delimitador `ç` (U+00E7) exige ler os arquivos `.dat` explicitamente como **UTF-8** em qualquer
  ponto de leitura; em outro charset o byte do `ç` quebra o `split`.
- **`sealed interface DataRecord permits Seller, Customer, Sale`:** modela os três registros de topo como um tipo
  fechado. `SaleItem` fica de fora por ser aninhado em `Sale`. `LineParser<T extends DataRecord>` e
  `ParserRegistry.parse` (`Optional<DataRecord>`) ficam type-safe sem `Object`.
- **`ParserRegistry` por prefixo:** mapa `prefixo → parser` construído a partir da lista de beans `LineParser`
  injetada pelo Spring. Um tipo `004` futuro é só uma nova implementação registrada — nenhuma classe existente muda.
  Prefixo desconhecido / linha malformada nunca lançam exceção para fora: retornam `Optional.empty()` + log de warning (SLF4J).
- **`DataAnalyzer` como acumulador de passada única, um por arquivo:** não é bean singleton. Cada arquivo tem seu
  próprio analyzer, então o estado é isolado por design e dispensa sincronização. Mantém só estado agregado
  (sets de documentos, mapa de volume por vendedor, venda mais cara corrente) — não materializa listas de registros.
- **Ordem de chegada irrelevante:** o volume é acumulado por nome de vendedor mesmo antes de o `001` correspondente
  chegar; a filtragem "só cadastrados" acontece no `summarize()`. Assim um `003` pode ser lido antes do seu `001`.
- **`Optional` nos campos derivados do `Report`:** venda mais cara e pior vendedor podem legitimamente não existir
  (sem vendas / sem vendedores cadastrados); as contagens são sempre definidas, logo primitivas `long`.

### I/O e orquestração (Fase 3)

- **Nomes de saída — fonte única (`OutputPathResolver`):** `vendas.dat` vira `<out>/vendas.done.dat` (troca a
  extensão, não concatena `vendas.dat.done.dat`). A regra vive num componente próprio porque o `ReportWriter` (escreve)
  e o `ProcessedFileChecker` (decide se já foi processado) dependem dela; se cada um tivesse sua cópia, o mecanismo de
  skip quebraria em silêncio no dia em que divergissem.
- **`N/A` para dado derivado ausente:** arquivo sem vendas e/ou sem vendedores `001` imprime `N/A` no campo
  correspondente, preservando o formato fixo de 4 linhas do enunciado. Alternativa descartada: omitir a linha —
  quebraria o formato.
- **Escrita atômica:** o relatório é gravado num `.tmp` **no mesmo diretório de saída** e movido com `ATOMIC_MOVE`;
  se o sistema de arquivos não suportar, capturamos `AtomicMoveNotSupportedException` e caímos para um move simples
  com log. Racional: um `.done.dat` truncado por crash seria lido como "já processado" pela varredura de subida —
  a atomicidade protege o mecanismo de skip. O `.tmp` residual de uma falha é limpo no `finally`.
- **Predicado único de "já processado":** um arquivo está processado sse seu `.done.dat` existe. O mesmo
  `ProcessedFileChecker` é usado pela varredura de subida (Opção B) e pelo watcher antes de submeter — assim um
  `ENTRY_MODIFY` tardio de um arquivo já pronto é ignorado (coerente com "entrada imutável").
- **Corrida subida × watcher:** na subida o `WatchService` é registrado **antes** da varredura inicial. Um arquivo
  que caia entre os dois passos gera evento e não se perde; a deduplicação impede que ele seja processado duas vezes.
- **Deduplicação em voo:** submissões passam por um `ConcurrentHashMap.newKeySet()` de caminhos normalizados; um path
  só é submetido se `add` retornar `true`, e é removido no `finally` da tarefa. Cobre tanto varredura × evento quanto
  vários `ENTRY_MODIFY` do mesmo arquivo.
- **Tamanho estável (arquivo ainda em escrita):** antes de processar, esperamos duas leituras consecutivas de tamanho
  iguais (até 5 tentativas × 200 ms ≈ 800 ms). Ao estourar, `log.error` e desistência — o arquivo é recuperado na
  próxima subida pela própria varredura. A checagem roda **dentro da tarefa do pool** (não no loop de eventos): mantém
  o loop fino e não bloqueante, e cobre também um arquivo sendo copiado no exato momento do boot (visto pela varredura).
- **`OVERFLOW`:** `log.warn` + reexecução da varredura da Opção B — nenhum mecanismo novo.
- **Pool e loop:** pool fixo de `Runtime.getRuntime().availableProcessors()` threads com `ThreadFactory` nomeando as
  threads (`file-processor-N`, facilita ler logs concorrentes); fila ilimitada de propósito, já que cada tarefa carrega
  só um `Path`. O loop de eventos é fino, delegando a colaboradores testáveis (checker, varredura, submitter).
- **Bootstrap com `ApplicationRunner` + `@PreDestroy` (em vez de `SmartLifecycle`):** escolha por simplicidade
  (critério do desafio). O desligamento ordenado ainda sai de graça pela ordem de destruição de beans — o watcher
  depende do submitter, então é destruído primeiro (para de aceitar eventos) e só então o pool é drenado
  (`shutdown` + `awaitTermination`).
- **Thread do watch loop é não-daemon:** de propósito. Sendo um app não-web, sem uma thread não-daemon o JVM
  encerraria assim que `main()` retornasse e o watcher morreria logo após subir. A thread do loop é a razão de a
  aplicação continuar viva; no shutdown, fechar o `WatchService` faz o `take()` lançar e a thread termina, liberando
  o JVM. (As threads do pool seguem daemon; são drenadas pelo `@PreDestroy` do submitter.)

## Uso de IA

_Em construção._
