#!/usr/bin/env bash
#
# Sobe o ambiente local na ordem da ADR-051: o jar primeiro, a imagem depois.
# Rode de dentro do WSL, da raiz do repositório, DEPOIS do bootJar no Windows.
#
#   ./scripts/subir-local.sh              # confere jars, imagens, infraestrutura, serviços
#   ./scripts/subir-local.sh --sem-build  # só sobe; usa as imagens que já existem
#   ./scripts/subir-local.sh --so-infra   # só postgres, mongo, redis, rabbit, minio
#
# Este script não lê e não imprime o .env. Quem lê o .env é o compose.

set -euo pipefail

cd "$(dirname "$0")/.."

BASE_JRE="eclipse-temurin:21-jre-alpine"
COM_BUILD=1
SO_INFRA=0

# Sem perfil, o compose não enxerga serviço nenhum — todos têm `profiles:`. E
# `services` sozinho é projeto inválido: os serviços dependem do postgres, que
# é do `core`. Os dois perfis vão juntos em todo comando.
CORE="--profile core"
TUDO="--profile core --profile services"

for arg in "$@"; do
  case "$arg" in
    --sem-build) COM_BUILD=0 ;;
    --so-infra)  SO_INFRA=1 ;;
    -h|--help)   sed -n '3,10p' "$0"; exit 0 ;;
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

  # Os nove módulos executáveis: oito em services/ e o gateway em infra/.
  faltando=0
  for d in backend/services/*/ backend/infra/gateway/; do
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
  docker compose $TUDO build
fi

# ------------------------------------------------------------------- 4. de pé

if [ "$SO_INFRA" -eq 1 ]; then
  passo "subindo a infraestrutura"
  docker compose $CORE up -d
  PERFIS="$CORE"
else
  passo "subindo infraestrutura e serviços"
  docker compose $TUDO up -d
  PERFIS="$TUDO"
fi

# -------------------------------------------------------------- 5. a espera

passo "esperando ficar saudável"
limite=$(( $(date +%s) + 240 ))
while :; do
  # Separador explícito: contêiner sem HEALTHCHECK tem Health vazio, e com
  # espaço como separador as colunas escorregariam.
  estado="$(docker compose $PERFIS ps --format '{{.Service}}|{{.Health}}|{{.State}}')"
  pendentes="$(printf '%s\n' "$estado" | awk -F'|' '$2=="starting" || $3=="restarting" {print $1}')"
  quebrados="$(printf '%s\n' "$estado" | awk -F'|' '$2=="unhealthy" {print $1}')"

  if [ -n "$quebrados" ]; then
    erro "não subiu: $(printf '%s' "$quebrados" | tr '\n' ' ')"
    for s in $quebrados; do
      printf '\n--- %s ---\n' "$s"
      docker compose $PERFIS logs --tail=40 "$s"
    done
    exit 1
  fi

  [ -z "$pendentes" ] && break

  if [ "$(date +%s)" -gt "$limite" ]; then
    erro "tempo esgotado esperando: $(printf '%s' "$pendentes" | tr '\n' ' ')"
    docker compose $PERFIS ps
    exit 1
  fi
  sleep 3
done

# --------------------------------------------------------------- 6. a prova

passo "a prova"
docker compose $PERFIS ps

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

Pronto. O roteiro inteiro, com os tempos medidos e o que fazer quando der
errado, está em docs/como-subir-local.md.

O front: cd frontend && npm run dev — em http://localhost:5173, e
CORS_ALLOWED_ORIGINS no .env precisa incluir essa porta.
FIM
