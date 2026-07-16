SHELL := /bin/bash
export LANG := C.UTF-8
export LC_ALL := C.UTF-8
.DEFAULT_GOAL := help
.PHONY: continuity-doctor continuity-self-test continuity-lifecycle-test  help setup install generate doctor legacy-doctor backend-test web-install web-test web-build db-test android-test verify verify-all infra-up infra-down clean

help:
	@printf '%s\n' 'setup install generate doctor legacy-doctor backend-test web-test web-build db-test android-test verify verify-all infra-up infra-down clean'

setup: install generate doctor

install:
	corepack enable
	pnpm install --frozen-lockfile

generate:
	pnpm generate

doctor:
	python3 scripts/check_v122_documentation.py --strict

legacy-doctor:
	python3 scripts/project-doctor.py --strict

backend-test:
	cd services/backend && ./mvnw clean verify

web-install: install

web-test:
	pnpm typecheck
	pnpm test

web-build:
	pnpm build

db-test:
	HHY_DB_SMOKE_CONFIRM=YES scripts/run_postgres_migration_smoke.sh

android-test:
	cd apps/android && ./gradlew clean lintDebug testDebugUnitTest assembleDebug

verify: doctor backend-test web-test web-build

verify-all: verify db-test android-test

infra-up:
	docker compose -f infra/docker-compose.yml up -d --wait

infra-down:
	docker compose -f infra/docker-compose.yml down -v

clean:
	rm -rf node_modules apps/*/node_modules packages/*/node_modules apps/*/dist services/backend/*/target services/backend/target apps/android/.gradle apps/android/**/build


continuity-doctor:
	python3 scripts/check_v123_continuity.py --strict

continuity-self-test:
	python3 scripts/run_continuity_self_test.py

continuity-lifecycle-test:
	python3 scripts/test_continuity_protocol.py
