# Projeto Compiladores — Analisador Go (JavaCC)

Subconjunto da sintaxe Go analisado com **JavaCC**: análise **léxica** e **sintática** (`GramaticaGO.jj`). Há um servidor HTTP opcional que serve a interface em `web/` e expõe a API de análise.

## Requisitos

- **JDK 21** (ou compatível com o módulo definido em `module-info.java`)
- Terminal na pasta raiz do projeto (onde estão `src/`, `bin/`, `web/` e `teste.go`)

Verifique a versão instalada:

```powershell
java -version
```

---

## Rodar do zero (passo a passo)

```powershell
cd caminho\para\ProjetoCompiladores
javac -d bin -encoding UTF-8 src\module-info.java src\compiladorGo\*.java
java --module-path bin --module ProjetoCompiladores/compiladorGo.GoGramatica teste.go
```

Saída esperada no caso de sucesso:

```text
Sintaxe válida.
```

Se houver erro de sintaxe/léxico, a execução mostra a mensagem do JavaCC (`ParseException` ou `TokenMgrError`).

---

## Compilar

```powershell
cd caminho\para\ProjetoCompiladores
javac -d bin -encoding UTF-8 src\module-info.java src\compiladorGo\*.java
```

Os `.class` ficam em `bin/`.

## Rodar só o analisador (linha de comando)

Lê um arquivo `.go` e imprime se a sintaxe foi aceita pela gramática:

```powershell
java --module-path bin --module ProjetoCompiladores/compiladorGo.GoGramatica teste.go
```

Substitua `teste.go` pelo caminho do arquivo que quiser testar.

## Rodar o frontend (interface web + API)

O programa `DemoHttpServer` serve o `web/index.html` e as rotas `/api/analyze` e `/api/sample`. **Execute sempre a partir da raiz do projeto** (para achar a pasta `web` e o arquivo `teste.go`).

```powershell
java --module-path bin --module ProjetoCompiladores/compiladorGo.DemoHttpServer
```

Porta padrão: **8787**. Abra no navegador:

**http://127.0.0.1:8787/**

Para usar outra porta (ex.: `9090`):

```powershell
java --module-path bin --module ProjetoCompiladores/compiladorGo.DemoHttpServer 9090
```

Para encerrar o servidor no terminal atual: `Ctrl+C`.

### Problemas comuns

- **Porta 8787 ocupada:** rode com outra porta (ex.: `9090`) e abra `http://127.0.0.1:9090/`.
- **Erro ao achar `web/` ou `teste.go`:** execute os comandos na **raiz do projeto**.
- **Comando `javac` não reconhecido:** confira instalação/configuração do JDK com `java -version` e `javac -version`.

## Regenerar o parser a partir do `.jj` (opcional)

Se alterar `src/compiladorGo/GramaticaGO.jj`, use o **JavaCC** no Eclipse ou a linha de comando do JavaCC para gerar de novo `GoGramatica.java`, `GoGramaticaTokenManager.java`, etc. Não edite manualmente os arquivos marcados como gerados.

## Estrutura relevante

| Caminho | Descrição |
|--------|-----------|
| `src/compiladorGo/GramaticaGO.jj` | Gramática fonte (JavaCC) |
| `src/compiladorGo/GoGramatica.java` | Parser gerado |
| `src/compiladorGo/DemoHttpServer.java` | Servidor HTTP da demo |
| `web/index.html` | Interface do navegador |
| `teste.go` | Exemplo de entrada |
