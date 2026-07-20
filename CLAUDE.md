# CLAUDE.md

Guia para agentes de IA trabalhando neste repositório. O `README.md` é a fonte canônica de
**decisões de arquitetura e regras de negócio** (com o racional de cada escolha) — leia-o antes de
mexer em qualquer coisa não trivial. Este arquivo cobre o que um agente precisa para **buildar,
testar e navegar** o código sem repetir o README.

## O que é

Aplicação Spring Boot (sem web) que monitora um diretório em busca de arquivos `.dat` de vendas,
processa-os concorrentemente e gera um relatório `<nome>.done.dat` no diretório de saída. Fluxo
único: `arquivo → parsing → agregação → relatório`.

## Build e testes

⚠️ **Sempre force JDK 17 ou 21 no `verify`.** O JaCoCo 0.8.13 não instrumenta sob JDK 26
(`Unsupported class file major version 70`), e o `mvn` do Homebrew roda sob a sua própria JVM 26.
`mvn test` passa sob 26 (o JaCoCo só roda no `verify`), mas `mvn verify` quebra. Use:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify        # build + suíte + gate de cobertura
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q clean test # só a suíte, mais rápido
```

- **Gate de cobertura:** o `verify` falha se a cobertura de **linhas** cair abaixo de **90%**
  (regra `BUNDLE` do `jacoco-maven-plugin` no `pom.xml`; `DataAnalyzerApplication` é excluída).
  Ao adicionar código de produção, adicione testes ou o build quebra.
- **Relatório de cobertura:** `target/site/jacoco/` — `jacoco.csv` (por classe) e `jacoco.xml`
  (por linha, útil para localizar exatamente quais linhas faltam).
- **Empacotar/rodar:** `mvn clean package` → `java -jar target/data-analyzer-0.0.1-SNAPSHOT.jar`
  (aponte diretórios com `--app.input-dir=...`/`--app.output-dir=...`).

## Estrutura (pacote por camada técnica)

`src/main/java/com/danilomendes/dataanalyzer/`

| Pacote      | Responsabilidade                                              | Conhece Spring/I-O? |
|-------------|--------------------------------------------------------------|---------------------|
| `domain`    | Registros do formato de entrada (`Seller`, `Customer`, `Sale`, `SaleItem`, `Report`; `sealed DataRecord`) | Não (puro) |
| `parser`    | Linha de texto → registro (`LineParser`, `ParserRegistry`)   | Não (puro)          |
| `analysis`  | Agregação em passada única (`DataAnalyzer`)                   | Não (puro)          |
| `io`        | Watcher, pool, escrita atômica, orquestração                 | Sim (borda)         |
| `config`    | `AppProperties` (`@ConfigurationProperties`)                 | Sim                 |

Dependências apontam **para dentro**: `io → analysis → domain` e `io → parser → domain`. Mantenha
`domain`/`parser`/`analysis` **livres de Spring e de I/O** — é o que os torna testáveis sem contexto.
Não introduza import de `io`/Spring nesses três pacotes.

## Convenções ao editar

- **UTF-8 é obrigatório em toda leitura de `.dat`:** o delimitador é `ç` (U+00E7). Ler noutro charset
  quebra o `split`. Sempre passe `StandardCharsets.UTF_8` explicitamente.
- **Valores monetários em `BigDecimal`**, nunca `double` (desempate por igualdade exata de valor).
- **Novo tipo de registro (ex.: `004`):** basta uma nova `@Component implements LineParser` — o
  `ParserRegistry` a descobre pela lista injetada. Nenhuma classe existente muda.
- **Nomes de saída passam pelo `OutputPathResolver`** (fonte única de `.dat`→`.done.dat` e de
  `isInputFile`). Não recrie essa regra em outro lugar: `ReportWriter` e `ProcessedFileChecker`
  dependem dela e divergir quebraria o skip de reprocessamento em silêncio.
- **Erros por arquivo são contidos, nunca propagados:** o processamento de um `.dat` loga e segue
  (não pode derrubar a thread não-daemon do watcher nem uma thread do pool). Mantenha esse contrato.
- Comentários e mensagens de log/teste estão em **português** — siga o idioma do arquivo que editar.

## Convenções de teste

- JUnit 5 + AssertJ + Mockito; Awaitility para asserções assíncronas (nunca `Thread.sleep` fixo em
  teste). Nomes de teste descritivos em camelCase (`reportsUnstableForAMissingFile`).
- **Parsers/análise:** teste puro, sem Spring. **Borda de I/O:** mocke só a fronteira (ex.: mockar
  `ReportWriter`, usar `ParserRegistry` real).
- **Integração (`@SpringBootTest`):** sempre `@DirtiesContext(AFTER_CLASS)` e diretórios temporários
  próprios; arquivos que precisam existir antes do boot vão no `@DynamicPropertySource`. Helpers de
  I/O compartilhados ficam em `IntegrationTestFiles` — reutilize, não duplique.
- Datasets de teste são recursos `.dat` em `src/test/resources`, não listas inline.
- Ao cobrir um ramo de erro difícil, prefira uma condição real (diretório inexistente → `IOException`;
  thread pré-interrompida → `InterruptedException`) a mocks estáticos frágeis.

## Git

Não commite nem faça push sem o usuário pedir. Rode o `verify` (com `JAVA_HOME` na 21) antes de
considerar uma mudança pronta.
