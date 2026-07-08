# OpenBook

OpenBook e uma aplicacao web para pesquisar livros em diferentes fontes digitais e comparar titulo, autor, capa, disponibilidade e origem do resultado. A ideia principal e ajudar o usuario a descobrir onde um livro pode ser encontrado de forma legal, incluindo catalogos abertos, dominio publico, pre-visualizacoes e acervos digitais.

## Funcionalidades

- Busca por titulo, autor ou tema.
- Resultados paginados para evitar carregar muitos livros de uma vez.
- Filtro por disponibilidade: todos, gratuitos, pagos ou a verificar.
- Filtro por fonte de dados.
- Cards com capa, autor, resumo, tipo de acesso e link da fonte.
- Integracao com fontes externas usando backend Spring Boot.
- Catalogo local inicial para fallback quando nenhuma fonte externa retorna dados.

## Fontes Integradas

Atualmente o projeto trabalha com estas fontes:

- Project Gutenberg: livros em dominio publico via API Gutendex.
- Open Library: catalogo aberto de livros, autores, capas e disponibilidade.
- Internet Archive: busca em textos digitais publicos/nao restritos.
- Google Books: suporte opcional, ativado apenas com chave de API.

Tambem existe uma fonte Selenium opcional para testes locais com paginas permitidas, mas ela fica desligada por padrao.

## Tecnologias

- Java 21
- Spring Boot 4.1.0
- Gradle
- Jackson
- HTML, CSS e JavaScript
- Selenium e WebDriverManager, apenas para fonte opcional

## Como Rodar

No Windows, dentro da pasta do projeto:

```powershell
.\gradlew.bat bootRun
```

Depois abra:

```text
http://localhost:8080
```

Para rodar os testes:

```powershell
.\gradlew.bat test
```

## Configuracao

Nenhuma configuracao obrigatoria e necessaria para rodar o projeto localmente.

Opcionalmente, o Google Books pode ser ativado com uma chave de API definida na variavel `GOOGLE_BOOKS_API_KEY`.

## API

Buscar livros:

```text
GET /api/books?term=dom%20casmurro&access=ALL&source=ALL&page=1&size=18
```

Listar fontes disponiveis:

```text
GET /api/books/sources
```

Exemplo filtrando pelo Internet Archive:

```text
GET /api/books?term=dom%20casmurro&access=ALL&source=Internet%20Archive&page=1&size=18
```

## Estrutura Principal

```text
src/main/java/com/MageLab/OpenBook
  controller/      Endpoints HTTP
  model/           Records e enums do dominio
  repository/      Catalogo local inicial
  service/         Regras de busca, filtros e paginacao
  service/source/  Integracoes com fontes externas

src/main/resources/static
  index.html       Estrutura da pagina
  styles.css       Visual da interface
  app.js           Busca, filtros, renderizacao e paginacao
```

## Observacoes

O projeto evita indexar copias nao autorizadas. O foco e mostrar fontes legais, acervos publicos, obras em dominio publico, pre-visualizacoes e links oficiais ou permitidos.
