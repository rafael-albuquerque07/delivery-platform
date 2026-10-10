#!/usr/bin/env bash
#
# Sobe o marco 2 na ordem da ADR-051 — o jar primeiro, a imagem depois — e em
# quatro grupos, cada um esperando o anterior ficar saudável (I-B).
# Rode de dentro do WSL, da raiz do repositório, DEPOIS do bootJar no Windows.
#
#   ./scripts/subir-local.sh              # confere jars, imagens, e os quatro grupos
#   ./scripts/subir-local.sh --sem-build  # só sobe; usa as imagens que já existem
#   ./scripts/subir-local.sh --so-infra   # só o grupo 1: postgres, mongodb, rabbitmq
#
# Este script não lê e não imprime o .env. Quem lê o .env é o compose.

set -euo pipefail

cd "$(dirname "$0")/.."

BASE_JRE="eclipse-temurin:21-jre-alpine"
COM_BUILD=1
SO_INFRA=0

# O perfil `marco2` é o que o marco 2 usa de verdade: postgres, mongodb, rabbitmq
# e os quatro serviços da prova. Os cinco esqueletos, e o redis e o minio, que
# nada usa, ficam de fora — os quinze juntos derrubaram a VM em 01/10
# (docs/como-subir-local.md §2.4).
PERFIL="--profile marco2"
# Os quatro módulos do marco 2: três em services/ e o gateway em infra/.
MODULOS="backend/infra/gateway/ backend/services/identity-service/ backend/services/merchant-service/ backend/services/catalog-service/"

for arg in "$@"; do
  case "$arg" in
    --sem-build) COM_BUILD=0 ;;
    --so-infra)  SO_INFRA=1 ;;
    -h|--help)   sed -n '3,11p' "$0"; exit 0 ;;
    *) echo "argumento desconhecido: $arg" >&2; exit 2 ;;
  esac
done

passo() { printf '\n\033[1m== %s\033[0m\n' "$1"; }
erro()  { printf '\n\033[31m!! %s\033[0m\n' "$1" >&2; }

# ---------------------------------------------------------------- 0. o ambiente

if ! command -v docker >/dev/null 2>&1; then
  erro "docker não está no PATH. Este script roda de dentro do WSL, não do PowerShell."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  erro "o daemon do Docker não responde. Ver docs/como-subir-local.md §5."
  exit 1
fi

if [ ! -f .env ]; then
  erro ".env não existe. Copie o .env.example e preencha."
  exit 1
fi

if [ "$SO_INFRA" -eq 0 ] && [ ! -f secrets/jwt-private.pem ]; then
  # O compose monta este arquivo no identity, somente-leitura. Sem ele o Docker
  # cria um DIRETÓRIO com esse nome no lugar, e o identity não sobe.
  erro "secrets/jwt-private.pem não existe. O .env.example diz como gerar o par."
  exit 1
fi

# ------------------------------------------------------- 1. a imagem-base

if [ "$SO_INFRA" -eq 0 ] && ! docker image inspect "$BASE_JRE" >/dev/null 2>&1; then
  passo "baixando $BASE_JRE"
  # Separado e antes: o Docker Hub resolve por IPv6 aqui e falha de forma
  # intermitente. Falhar num pull custa um pull; falhar num build custa o build.
  docker pull "$BASE_JRE"
fi

# ------------------------------------------------------------------ 2. os jars

# Este script NÃO constrói os jars. O Gradle que funciona nesta máquina é o do
# Windows (CLAUDE.md, "Configuração de ambiente que funciona"): o Ubuntu não tem
# JDK, e o build não provisiona um sozinho — não há resolvedor de toolchain. Os
# jars vêm antes, do PowerShell:
#
#   cd C:\dev\delivery-platform\backend; .\gradlew.bat bootJar
#
# Aqui só se confere que eles existem, e que são um por módulo.

if [ "$COM_BUILD" -eq 1 ] && [ "$SO_INFRA" -eq 0 ]; then
  passo "conferindo os jars (ADR-051: a imagem carrega o jar)"

  faltando=0
  for d in $MODULOS; do
    modulo="$(basename "$d")"
    if [ ! -f "${d}build/libs/app.jar" ]; then
      erro "${modulo}: build/libs/app.jar não existe — rode o bootJar no PowerShell (ver acima)"
      faltando=1
    fi
    extras="$(find "${d}build/libs" -maxdepth 1 -name '*.jar' ! -name 'app.jar' 2>/dev/null | wc -l)"
    if [ "$extras" -gt 0 ]; then
      erro "${modulo}: há jar além do app.jar em build/libs — sobra de build antigo? No PowerShell: .\gradlew.bat clean bootJar"
      faltando=1
    fi
  done
  [ "$faltando" -eq 0 ] || exit 1
fi

# --------------------------------------------------------------- 3. as imagens

if [ "$COM_BUILD" -eq 1 ] && [ "$SO_INFRA" -eq 0 ]; then
  passo "construindo as imagens"
  docker compose $PERFIL build
fi

# ------------------------------------------------------------------- 4. de pé

# `--wait` espera o healthcheck de cada contêiner do grupo, e falha se algum
# ficar `unhealthy` ou se o prazo vencer. Substitui o laço que estava aqui, que
# tratava `Health` vazio como saudável — um "subiu" falso para quem não tem
# healthcheck. O `mongo-init`, que roda uma vez e sai com 0, passa pelo `--wait`
# (medido em 10/10/2026).
grupo() {
  local nome="$1"; shift
  passo "$nome"
  local inicio; inicio=$(date +%s)
  if ! docker compose $PERFIL up -d --wait --wait-timeout 300 "$@"; then
    erro "não ficou saudável: $*"
    docker compose $PERFIL ps
    for s in "$@"; do
      printf '\n--- %s ---\n' "$s"
      docker compose $PERFIL logs --tail=40 "$s"
    done
    exit 1
  fi
  echo "saudável em $(( $(date +%s) - inicio )) s"
}

# 1. a infraestrutura que o marco 2 usa
grupo "grupo 1 · infraestrutura" postgres mongodb mongo-init rabbitmq

if [ "$SO_INFRA" -eq 0 ]; then
  # 2. o identity sozinho: todos os outros validam token pelo JWKS dele, e subir
  #    junto mistura "não subiu" com "subiu e não achou o JWKS"
  grupo "grupo 2 · identity" identity-service
  # 3. os dois lados do circuito do expediente
  grupo "grupo 3 · merchant e catalog" merchant-service catalog-service
  # 4. a porta de entrada
  grupo "grupo 4 · gateway" gateway
fi

# --------------------------------------------------------------- 5. a prova

passo "a prova"
docker compose $PERFIL ps
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}'

if [ "$SO_INFRA" -eq 0 ]; then
  if curl -fsS -o /dev/null http://127.0.0.1:8080/actuator/health; then
    echo "gateway: saudável"
  else
    erro "o gateway não respondeu em 127.0.0.1:8080/actuator/health"
    exit 1
  fi

  # O merchant, e não o identity: a documentação aberta por flag (ADR-050)
  # existe no merchant e no catalog. Fechada, a resposta é 401, não 404.
  codigo="$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8082/swagger-ui.html || true)"
  echo "merchant /swagger-ui.html: $codigo"
  if [ "$codigo" = "401" ]; then
    echo "  (401 é DELIVERY_DOCS_ABERTAS desligada no .env — ADR-050)"
  fi
fi

cat <<'FIM'

Pronto. Saúde não exercita o JWKS entre contêineres: a prova que exercita é uma
rota protegida pelo gateway com token emitido pelo identity —
docs/como-subir-local.md §2.5. O roteiro inteiro, com os tempos medidos e o que
fazer quando der errado, está lá.

O front: cd frontend && npm run dev — em http://localhost:5173, e
CORS_ALLOWED_ORIGINS no .env precisa incluir essa porta.
FIM
