# Guia de estudos etapa a etapa — Compilador Go com JavaCC

Este documento explica o projeto como um fluxo real de execução, para estudo e para leitura no GitHub.

## Objetivo do projeto

Implementar um analisador de **subconjunto da linguagem Go** com:

- análise **léxica** (transformar texto em tokens)
- análise **sintática** (validar se a sequência de tokens segue a gramática)

Tudo parte de `src/compiladorGo/GramaticaGO.jj`.

---

## Visão geral da arquitetura

O projeto funciona em pipeline:

```text
arquivo .go
   ↓
TokenManager (léxico)
   ↓
sequência de tokens
   ↓
Parser (sintático, descida recursiva LL)
   ↓
aceito (EOF consumido) ou erro
```

Arquivos principais:

- `src/compiladorGo/GramaticaGO.jj`: fonte da gramática (o coração do projeto)
- `src/compiladorGo/GoGramaticaTokenManager.java`: léxico gerado
- `src/compiladorGo/GoGramatica.java`: parser gerado
- `src/compiladorGo/DemoHttpServer.java`: servidor web da demo
- `web/index.html`: interface local

---

## Exemplo guiado (entrada → tokens → parse)

Entrada de exemplo:

```go
var x int = 10
```

Leitura léxica esperada:

1. `var` → token `VAR`
2. `x` → token `IDENTIFICADOR`
3. `int` → token `TIPO`
4. `=` → token `IGUAL`
5. `10` → token `INTEIRO`

Consumo sintático (resumo):

1. `Inicio()` chama `Comando()`
2. `Comando()` escolhe ramo de declaração por começar com `VAR`
3. Consome `VAR IDENTIFICADOR TIPO`
4. Chama `DeclaracaoVariavel()`
5. `DeclaracaoVariavel()` consome opcionalmente `= Expressao()`
6. `Expressao()` reduz até `Numero()` e aceita `10`
7. `Inicio()` fecha com `EOF`

Resultado: **entrada aceita sintaticamente**.

---

## Etapa 1 — ponto de entrada do parser

No topo do `.jj`, dentro de `PARSER_BEGIN(GoGramatica)`, existe um `main` que:

1. recebe o caminho de um arquivo `.go`
2. cria `new GoGramatica(fis)`
3. chama `parser.Inicio()`
4. se não houver exceção, imprime `Sintaxe válida.`

Ideia-chave: **a validação sintática inteira começa em `Inicio()`**.

---

## Etapa 2 — definição léxica (`SKIP` e `TOKEN`)

### 2.1 `SKIP`

```
SKIP : { " " | "\t" | "\n" | "\r" }
```

Esses caracteres são ignorados e não viram token.

### 2.2 Tokens de operadores/símbolos

Exemplos: `+`, `-`, `*`, `/`, `=`, `==`, `!=`, `<`, `>`, `<=`, `>=`, `&&`, `||`, `!`, `&`, `:`.

Observação importante: `:=` é interpretado como **dois tokens** (`:` e `=`), não como um único token.

### 2.3 Tokens de palavras-chave

`package`, `import`, `func`, `var`, `if`, `else`, `for`, `range`, `true`, `false`, `return`.

No projeto, também foram fixados como token único:

- `fmt.Println`
- `fmt.Scanln`

Isso simplifica o parser.

### 2.4 Tokens de literais e identificadores

- `TIPO`: `int`, `string`, `bool`, `float64`
- `INTEIRO`
- `REAL`
- `STRING`
- `IDENTIFICADOR`

Resumo da etapa: **se o texto não casa com nenhum token, ocorre `TokenMgrError` (erro léxico).**

---

## Etapa 3 — regra inicial do programa (`Inicio`)

`Inicio()` modela o arquivo completo:

- `Header()` opcional (`package ...`)
- zero ou mais `Import()`
- zero ou mais `Comando()`
- obrigatoriamente `EOF`

Ou seja, o programa só é aceito quando tudo for consumido até o fim.

---

## Etapa 4 — estrutura de alto nível

### 4.1 `Header()`

Aceita: `package` + `IDENTIFICADOR`.

### 4.2 `Import()`

Aceita: `import` + `STRING`.

### 4.3 `Comando()`

Define o que pode existir no corpo:

- declaração com `var`
- declaração de função (`func`)
- linha iniciada por identificador (atribuição ou chamada)
- condicional (`if` / `else`)
- laço (`for`)
- saída (`fmt.Println`)
- entrada (`fmt.Scanln`)

Esta regra é o núcleo da sintaxe.

---

## Etapa 5 — atribuição, declaração e funções

### 5.1 `Atribuicao()`

Aceita:

- `= Expressao()`
- `: = Expressao()` (na gramática, `<DOISPONTOS> <IGUAL>`)

### 5.2 `DeclaracaoVariavel()`

Após `var nome tipo`, permite inicialização opcional com `= Expressao()`.

### 5.3 `criarFuncao()`

Modela:

- parâmetros opcionais tipados
- tipo de retorno opcional
- bloco com comandos
- `return` opcional com expressão

### 5.4 `chamarFuncao()`

Modela lista de argumentos opcionais entre parênteses.

---

## Etapa 6 — condicionais

### 6.1 `Condicional()`

Estrutura:

- `if (Expressao()) { ... }`
- parte `else` opcional

### 6.2 `ElsePart()`

Pode ser:

- `else if ...` (recursão chamando `Condicional()` de novo)
- `else { ... }`

Isso permite cadeias de `else if`.

---

## Etapa 7 — laços (`for`) e `LOOKAHEAD`

`LoopFor()` trata formas diferentes de `for`, usando `LOOKAHEAD` para desambiguar:

1. `for (init; cond; passo) { ... }`
2. `for ... := range ... { ... }` (`ForRange()`)
3. `for Expressao() { ... }`

`LOOKAHEAD` é necessário porque vários ramos começam parecido.

`ForRange()` aceita variável, opcional segunda variável, `:=`, `range`, identificador e bloco.

Limitação relevante: o `for { ... }` puro (sem expressão) não está claramente modelado como no Go completo.

---

## Etapa 8 — entrada e saída

### `Saida()`

`fmt.Println` com `STRING` ou `IDENTIFICADOR`.

### `Entrada()`

`fmt.Scanln(&identificador)`, exigindo `&` (`ANDPER`).

---

## Etapa 9 — expressões e precedência

A hierarquia usada:

1. `Expressao()`
2. `ExpressaoAritmetica()`
3. `Termo()`
4. `Fator()`
5. `Numero()`

Precedência:

- `*` e `/` em `Termo()` (mais forte)
- `+` e `-` em `ExpressaoAritmetica()` (menos forte)

`Fator()` aceita número com sinal opcional, identificador, string ou parênteses.

---

## Etapa 10 — como o erro aparece

Há dois cenários principais:

- **Erro léxico (`TokenMgrError`)**: caractere/sequência não reconhecida por `TOKEN`.
- **Erro sintático (`ParseException`)**: tokens válidos, mas em ordem inválida para a regra atual.

Exemplo clássico: `banana = lucas ;`  
o `;` pode existir como token, mas se a gramática não espera `;` naquele ponto, o parser lança `ParseException`.

---

## Casos aceitos e não aceitos (subconjunto atual)

### Geralmente aceitos

- `var x int`
- `var x int = 10`
- `x = 1`
- `x := 1`
- `if (x > 0) { ... } else { ... }`
- `for resultado > 2 { ... }`
- `for _, letra := range palavra { ... }`

### Podem falhar neste subconjunto

- `banana = lucas ;` (ponto e vírgula ao final de comando nesse contexto)
- `for { ... }` (loop infinito puro do Go completo não modelado de forma explícita)
- usos fora dos formatos previstos em `Saida()` e `Entrada()`
- construções Go avançadas não descritas em `Comando()`/`Expressao()`

---

## Etapa 11 — comandos para demonstrar no GitHub/README

Compilar:

```powershell
javac -d bin -encoding UTF-8 src\module-info.java src\compiladorGo\*.java
```

Rodar parser no exemplo:

```powershell
java --module-path bin --module ProjetoCompiladores/compiladorGo.GoGramatica teste.go
```

Rodar demo web:

```powershell
java --module-path bin --module ProjetoCompiladores/compiladorGo.DemoHttpServer
```

---

## Mapeamento rápido: regra da gramática → onde executa

| Origem | Onde aparece no projeto | Função |
|---|---|---|
| `SKIP` / `TOKEN` no `GramaticaGO.jj` | `GoGramaticaTokenManager.java` | Reconhecer e classificar lexemas em tokens |
| Produções como `Inicio`, `Comando`, `Expressao` | `GoGramatica.java` | Validar a ordem/estrutura dos tokens |
| Nomes/códigos dos tokens | `GoGramaticaConstants.java` | Identificar token por constante e `tokenImage` |
| Erro léxico | `TokenMgrError` | Falha em reconhecer caractere/sequência |
| Erro sintático | `ParseException` | Sequência de tokens não encaixa na produção esperada |

