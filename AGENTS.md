# agent instructions (AI2), depending on the tool

# AGENTS.md — EcoDelivery Project Context & Agent Guidelines

Este documento fornece o contexto global, arquitetural, de domínio e operacional do projeto **EcoDelivery** para orientar agentes de IA em futuras tarefas, desenvolvimento de User Stories e testes.

---

## 1. Visão Geral do Projeto

O **EcoDelivery** é uma plataforma de gestão de frotas de bicicletas elétricas de carga (*cargo e-bikes*) para entregas urbanas de encomendas (âmbito da disciplina de **TQS 2026** — Testes e Qualidade de Software, Universidade de Aveiro).

O sistema gere:
- **Hubs:** Pontos físicos de recolha/entrega com coordenadas GPS, raio de geofencing e capacidade máxima de veículos.
- **E-Bikes de Carga:** Veículos com número de série único, capacidade de carga (kg), nível de bateria e estados de ciclo de vida (`AVAILABLE`, `RESERVED`, `IN_USE`, `MAINTENANCE`, `RETIRED`).
- **Empresas e Estafetas:** Clientes institucionais registados (`Company`) e respetivos estafetas (`Courier`) que reservam e utilizam as e-bikes.
- **Reservas e Sessões de Uso:** Retenções temporárias (*temporal holds*), reservas agendadas e sessões de utilização em tempo real.
- **Auditoria e Logs:** Registo imutável de eventos de inventário e alterações de estado.

---

## 2. Stack Tecnológica

- **Java:** Versão 21 (LTS)
- **Framework:** Spring Boot 4.1.1 (Spring Data JPA, Spring WebMvc, Spring Validation, Spring Actuator)
- **Base de Dados:**
  - Produção/Dev: PostgreSQL 16 (orquestrado via Docker Compose)
  - Testes: H2 In-Memory (modo PostgreSQL)
- **Documentação API:** Springdoc OpenAPI 3.0.0 (Swagger UI em `/swagger-ui.html`)
- **Frontend:** HTML5, CSS3 moderno (variáveis CSS), Vanilla JavaScript assíncrono (SPA leve servida por Nginx ou recursos estáticos Spring Boot).
- **Testes & Análise Estática:**
  - JUnit 5 (Jupiter), AssertJ, Hamcrest
  - Mockito com Java Agent (compatível com Java 21+: `-javaagent:${org.mockito:mockito-core:jar}`)
  - Spring Boot Test (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`)
  - Testcontainers (PostgreSQL) e REST-Assured
  - Cucumber (BDD)
  - JaCoCo 0.8.15 (relatório de cobertura unificado em `target/site/jacoco/jacoco.xml`)
  - SonarQube Cloud Scanner & OWASP Dependency-Check

---

## 3. Estrutura de Diretórios

```text
EcoDelivery/
├── .github/workflows/          # CI Pipelines (backend-ci.yml, ci.yml)
├── docker-compose.yml          # Serviços PostgreSQL e aplicação
├── docs/                       # Templates de Quality Manual e Product Specs
└── ProjX/
    ├── docker-compose.yml
    ├── backend/
    │   ├── pom.xml             # Dependências, Surefire, Failsafe, JaCoCo
    │   └── src/
    │       ├── main/
    │       │   ├── java/pt/ua/tqs/ecodelivery/
    │       │   │   ├── adapter/        # NominatimGeocodingAdapter, LoggingHubAdapter
    │       │   │   ├── controller/     # BikeController, HubController, InventoryLogController, OperatorController
    │       │   │   ├── dto/            # Requests e Responses (Java Records)
    │       │   │   ├── exception/      # GlobalExceptionHandler, BusinessRuleException, ResourceNotFoundException
    │       │   │   ├── model/          # Entidades JPA (Bike, Hub, Courier, Company, Reservation, etc.)
    │       │   │   ├── payment/        # BillingService, PaymentController
    │       │   │   ├── policy/         # PolicyService (regras parametrizadas)
    │       │   │   ├── port/           # GeocodingPort, HubPort
    │       │   │   ├── repository/     # Spring Data Repositories
    │       │   │   ├── reservation/    # SessionService, ReservationController
    │       │   │   └── service/        # BikeService, HubService, InventoryLogService
    │       │   └── resources/
    │       │       ├── application.yml
    │       │       └── static/         # Espelho dos ficheiros frontend para o Spring Boot
    │       └── test/                   # Testes unitários (*Test) e de integração (*IT)
    └── frontend/                       # Código-fonte Web (index.html, app.js, style.css, Dockerfile, nginx.conf)
```

---

## 4. Regras de Negócio (BR) e Decisões de Arquitetura (DEC)

| Identificador | Descrição da Regra / Decisão |
| :--- | :--- |
| **BR1** | **Bateria Mínima:** Uma e-bike só pode ser reservada ou utilizada se a bateria for $\ge 20\%$ (`ecodelivery.policy.min-battery-level`). |
| **BR2** | **Estado Operacional:** O veículo deve estar ativo (`active = true`) e não pode estar em `MAINTENANCE` nem `RETIRED`. |
| **BR3 / DEC-6** | **Sem Conflito de Janelas:** Não podem existir reservas ativas sobrepostas (`RESERVED` ou `IN_USE`) para a mesma e-bike no mesmo intervalo `[start, end]`. |
| **BR4 / DEC-5** | **Janela de Início:** Uma reserva agendada só pode ser iniciada dentro de uma tolerância em torno da hora marcada (por defeito 15 min: `start-window: PT15M`). |
| **BR7 / DEC-8** | **Elegibilidade do Estafeta:** O estafeta tem de pertencer obrigatoriamente a uma empresa registada na plataforma (`courier.getCompany() != null`). |
| **BR8** | **Caução / Depósito:** Quando configurado valor positivo (`deposit-amount`), é cobrada uma caução antes da libertação da e-bike. |
| **DEC-1** | **Duração Máxima:** O tempo máximo contínuo de reserva/uso é delimitado pela política (`max-reservation-duration`, por defeito 4h ou 6h em testes). |
| **DEC-4** | **Limite de Reservas Ativas:** Cada estafeta só pode ter no máximo **1** reserva/sessão ativa em simultâneo (`RESERVED` ou `IN_USE`). |
| **PROP-2** | **Localização no Hub:** Para ser reservada ou iniciada, a e-bike tem de estar fisicamente estacionada num Hub (`bike.getCurrentHub() != null`). |
| **US09 AC** | **Lock & Auto-Expire:** A reserva coloca um *temporal hold* trancando a e-bike em `RESERVED` durante a janela configurada (`hold-duration: PT15M`). Se o estafeta não iniciar a utilização a tempo, a reserva expira automaticamente (`EXPIRED`) e a e-bike volta a `AVAILABLE`. |

---

## 5. Pirâmide de Testes e Estratégia de Verificação

O projeto segue rigorosamente as boas práticas de testes da UA / TQS:
- **Unitários de Serviço / Domínio (`*Test.java`):**
  - Executados pelo `maven-surefire-plugin`.
  - Mocks estritos com Mockito (`@ExtendWith(MockitoExtension.class)`).
  - Uso de relógio determinístico via `Clock.fixed(...)`.
  - *Exemplos:* `BikeServiceTest`, `PolicyServiceTest`, `SessionServiceReserveTest`, `SessionServiceStartReservationTest`.
- **Slices de Camada Web e JPA (`*Test.java`):**
  - Controllers: `@WebMvcTest(Controller.class)` com `@MockitoBean` e `MockMvc`.
  - Repositories: `@DataJpaTest` com `TestEntityManager` e base de dados H2.
  - *Exemplos:* `ReservationControllerTest`, `BikeControllerTest`, `ReservationRepositoryTest`.
- **Integração Ponta a Ponta (`*IT.java`):**
  - Executados pelo `maven-failsafe-plugin` na fase `verify`.
  - Contexto completo com `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`.
  - *Exemplos:* `HubAndVehicleInventoryIT`, `VehicleReservationIT`.

---

## 6. Instruções e Comandos para Agentes

### Execução de Testes e Build:
```bash
# Navegar para o backend
cd ProjX/backend

# Executar apenas testes unitários e slices
mvn test

# Executar testes específicos
mvn test -Dtest=SessionServiceReserveTest

# Executar pipeline completo (Unitários + Integração Failsafe + JaCoCo)
mvn clean verify
```

### Boas Práticas Obrigatórias para Agentes:
1. **Não quebrar testes existentes:** O projeto possui 145+ testes unitários e múltiplos testes de integração que devem passar com 0 falhas e 0 erros.
2. **Backwards Compatibility:** Ao alterar construtores em classes como `BikeService` ou `PolicyService`, manter sobrecargas ou anotar construtores de injeção Spring com `@Autowired`.
3. **Sincronização do Frontend:** Qualquer alteração efetuada em `ProjX/frontend/` (HTML, JS, CSS) deve ser espelhada em `ProjX/backend/src/main/resources/static/` para garantir consistência visual no container e no arranque standalone do Spring Boot.
4. **Respeito pelo Relógio:** Utilizar sempre a injeção do bean `Clock` para obter `LocalDateTime.now(clock)`, permitindo testes determinísticos sem dependência do relógio do sistema operativo.
5. **Políticas via Configuração:** Valores numéricos e durações de regras de negócio devem residir no `application.yml` sob o prefixo `ecodelivery.policy.*` e ser acedidos através do `PolicyService`.