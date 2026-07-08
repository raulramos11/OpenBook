# Aprendizado do Projeto OpenBook

Este documento resume o que da para aprender com o OpenBook ate agora e quais sintaxes vale reconhecer de memoria.

## Ideia principal

OpenBook e um esboco de aplicacao web para buscar e organizar livros. A pagina mostra um catalogo inicial com titulo, autor, tema, resumo, fonte e disponibilidade. O usuario pode pesquisar por palavras-chave e filtrar as obras entre gratuitas, pagas ou a verificar.

## O que o projeto ensina

### 1. Estrutura de um projeto Spring Boot

O projeto mostra a separacao basica de responsabilidades:

- `controller`: recebe as requisicoes HTTP.
- `service`: guarda a regra de negocio da busca.
- `repository`: fornece os dados do catalogo.
- `model`: define os tipos usados pela aplicacao.
- `static`: guarda HTML, CSS e JavaScript da pagina.

Essa organizacao e uma das bases de aplicacoes Java com Spring.

### 2. API REST simples

O endpoint principal fica em:

```text
GET /api/books?term=machado&access=FREE
```

Ele recebe filtros pela URL e devolve uma lista de livros em JSON. Isso ensina a ligacao entre frontend e backend.

Sintaxes importantes:

```java
@RestController
@RequestMapping("/api/books")
@GetMapping
@RequestParam(defaultValue = "")
```

### 3. Busca com regra de negocio

O `BookService` normaliza o texto antes de buscar. Isso permite comparar palavras sem depender tanto de maiusculas, minusculas ou acentos.

Conceitos aprendidos:

- tratar entrada do usuario;
- transformar texto antes da comparacao;
- filtrar listas;
- separar regra de negocio do controller.

Sintaxe importante:

```java
bookRepository.findAll().stream()
		.filter(book -> matchesTerm(book, normalizedTerm))
		.filter(book -> matchesAccess(book, selectedAccess))
		.toList();
```

### 4. Modelos em Java

O projeto usa `record` para representar um livro. `record` e uma forma curta de criar objetos simples de dados.

Sintaxe importante:

```java
public record Book(
		Long id,
		String title,
		String author
) {}
```

Tambem usa `enum` para limitar os tipos de disponibilidade:

```java
public enum AccessType {
	FREE,
	PAID,
	UNKNOWN
}
```

### 5. Frontend com HTML, CSS e JavaScript

O HTML organiza a pagina em cabecalho, area de busca, lista de resultados e secao de fontes. O CSS cuida do visual. O JavaScript busca os dados no backend e cria os cards dinamicamente.

Sintaxes importantes:

```javascript
const response = await fetch(`/api/books?${params.toString()}`);
const books = await response.json();
```

```javascript
document.createElement("article");
element.textContent = book.title;
bookGrid.append(card);
```

### 6. Acessibilidade e SEO

O Lighthouse mostrou que a acessibilidade esta boa. O ajuste essencial era adicionar uma `meta description`, porque ela ajuda buscadores e ferramentas a entenderem melhor o conteudo da pagina.

Sintaxe importante:

```html
<meta name="description" content="Descricao curta da pagina.">
```

### 7. Git e GitHub

Neste projeto tambem deu para aprender o fluxo basico:

```bash
git init
git add .
git commit -m "Mensagem"
git remote add origin URL_DO_REPOSITORIO
git push -u origin main
```

Tambem apareceu um caso real de conflito de merge no `.gitignore`, que acontece quando o arquivo local e o arquivo remoto foram alterados de formas diferentes.

## Preciso decorar sintaxe?

Voce nao precisa decorar tudo. O melhor e decorar o minimo necessario para reconhecer os padroes e saber o que pesquisar quando esquecer.

Vale decorar ou reconhecer rapidamente:

- estrutura basica de HTML;
- seletores CSS principais: classe, id e tag;
- `document.querySelector`;
- `addEventListener`;
- `fetch` e `response.json()`;
- anotacoes Spring como `@RestController`, `@GetMapping` e `@Service`;
- `record` e `enum`;
- comandos Git mais comuns: `git status`, `git add`, `git commit`, `git push` e `git pull`.

Nao vale tentar decorar:

- todos os atributos HTML;
- todas as propriedades CSS;
- todos os metodos do JavaScript;
- todas as anotacoes do Spring;
- comandos raros do Git.

O ideal e entender o papel de cada parte. A sintaxe vai ficando natural conforme voce repete projetos pequenos como este.

## Ordem recomendada de estudo

1. Entender o caminho completo: pagina -> JavaScript -> API -> service -> repository -> JSON -> pagina.
2. Praticar alterando o catalogo de livros.
3. Criar novos filtros simples.
4. Melhorar o README do GitHub.
5. Depois pensar em banco de dados, login ou integracoes externas.

## Resumo curto

O OpenBook ja ensina o ciclo principal de uma aplicacao web: interface, chamada HTTP, backend, dados, resposta em JSON e renderizacao na tela. Para estudar bem, foque mais em entender o fluxo do que em decorar tudo.
