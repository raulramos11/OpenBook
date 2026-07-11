# OpenBook

OpenBook e uma aplicacao web para pesquisar livros em diferentes fontes digitais e comparar titulo, autor, capa, disponibilidade e origem do resultado. A ideia principal e ajudar o usuario a descobrir onde um livro pode ser encontrado de forma legal, incluindo catalogos abertos, dominio publico, pre-visualizacoes e acervos digitais.

## Funcionalidades

- Busca por titulo, autor ou tema.
- Resultados paginados para evitar carregar muitos livros de uma vez.
- Filtro por disponibilidade: todos, gratuitos, pagos ou a verificar.
- Filtro por fonte de dados.
- Ordenacao por relevancia, popularidade, nota, data de publicacao e titulo.
- Filtros combinaveis por intervalo de ano, nota minima, presenca de capa e confirmacao em varias fontes.
- Cards com capa, autor, resumo, tipo de acesso e link da fonte.
- Detalhes completos ao clicar no card, incluindo sinopse sem cortes e todos os catalogos encontrados.
- Avaliacao da comunidade OpenBook de 1 a 5 estrelas, com um voto atualizavel por navegador.
- Nota geral inicial preenchida com as avaliacoes publicas da Open Library e do Google Books quando disponiveis.
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

Nenhuma configuracao obrigatoria e necessaria para rodar o projeto localmente. Sem banco, as avaliacoes usam memoria apenas para desenvolvimento e somem quando a aplicacao reinicia.

Opcionalmente, o Google Books pode ser ativado com uma chave de API definida na variavel `GOOGLE_BOOKS_API_KEY`.

Para manter os votos entre reinicios e deploys, configure um PostgreSQL:

```text
DATABASE_URL=postgresql://usuario:senha@host/banco?sslmode=require
RATINGS_REQUIRE_DATABASE=true
```

O schema `book_ratings` e criado automaticamente no primeiro start. No Render, `RENDER=true` ja ativa a exigencia de persistencia: se `DATABASE_URL` estiver ausente, a busca continua funcionando, mas o endpoint de voto responde como indisponivel em vez de fingir que salvou um voto que seria perdido. Isso e importante porque o filesystem do Render Free e efemero. Para um projeto gratuito duradouro, conecte o servico a um PostgreSQL externo; o PostgreSQL gratuito do proprio Render expira depois do periodo informado pela plataforma.

## API

Buscar livros:

```text
GET /api/books?term=dom%20casmurro&access=ALL&source=ALL&sort=RATING&yearFrom=1800&yearTo=2026&minRating=4&hasCover=true&multipleSources=false&page=1&size=18
```

Listar fontes disponiveis:

```text
GET /api/books/sources
```

Exemplo filtrando pelo Internet Archive:

```text
GET /api/books?term=dom%20casmurro&access=ALL&source=Internet%20Archive&page=1&size=18
```

Registrar ou atualizar a avaliacao do navegador:

```text
POST /api/ratings
Content-Type: application/json

{
  "bookKey": "chave SHA-256 devolvida pelo resultado da busca",
  "title": "Dom Casmurro",
  "author": "Machado de Assis",
  "score": 5,
  "voterKey": "UUID anonimo criado pelo navegador"
}
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
