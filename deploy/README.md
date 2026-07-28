# 눈길 AWS EC2 배포

## 구성

- EC2 Ubuntu 24.04
- Docker Compose
- Nginx + React
- Spring Boot
- PostgreSQL 18
- GitHub Actions

## 1. EC2 준비

권장 시작 사양은 `t3.small` 이상입니다. 보안 그룹 인바운드는 다음만 엽니다.

- SSH `22`: 본인 IP만
- HTTP `80`: `0.0.0.0/0`, `::/0`
- PostgreSQL `5432`: 열지 않음
- Spring Boot `8080`: 열지 않음

EC2에 접속해 저장소의 `deploy/bootstrap-ubuntu.sh`를 실행합니다.

## 2. 운영 환경변수

EC2에서 다음 파일을 직접 만듭니다.

```bash
sudo nano /opt/noongill/shared/.env.production
```

내용은 루트의 `.env.production.example`을 복사하고 실제 값으로 교체합니다.

```dotenv
POSTGRES_DB=sookmyung_map
POSTGRES_USER=map_user
POSTGRES_PASSWORD=충분히_긴_랜덤_비밀번호
VITE_NAVER_MAP_CLIENT_ID=네이버_지도_Client_ID
APP_CORS_ALLOWED_ORIGINS=http://EC2_PUBLIC_IP
```

이 파일은 Git에 커밋하지 않습니다.

## 3. GitHub Secrets

GitHub 저장소의 `Settings → Secrets and variables → Actions`에 등록합니다.

- `EC2_HOST`: EC2 퍼블릭 IP 또는 도메인
- `EC2_USER`: Ubuntu AMI라면 `ubuntu`
- `EC2_SSH_PRIVATE_KEY`: EC2 접속용 private key 전체 내용

GitHub의 `production` Environment도 생성합니다. 필요하면 배포 승인 규칙을 설정합니다.

## 4. 첫 배포

프로젝트 루트를 하나의 Git 저장소로 만든 뒤 GitHub에 올립니다.

```bash
cd /Users/choeinha/Projects/sookmap
git init
git add .
git commit -m "Configure AWS deployment"
git branch -M main
git remote add origin YOUR_GITHUB_REPOSITORY_URL
git push -u origin main
```

`main`에 push하면 다음 순서로 실행됩니다.

1. PostgreSQL 서비스 컨테이너 시작
2. Spring Boot 테스트
3. React 프로덕션 빌드
4. 소스를 EC2에 업로드
5. Docker 이미지 빌드
6. 운영 컨테이너 교체

## 5. 네이버 지도 설정

Naver Cloud Platform 애플리케이션의 Web 서비스 URL에 다음을 추가합니다.

```text
http://EC2_PUBLIC_IP
```

도메인과 HTTPS를 적용한 뒤에는 최종 주소도 추가합니다.

## 운영 명령

```bash
cd /opt/noongill/current
sudo docker compose --env-file /opt/noongill/shared/.env.production -f compose.prod.yml ps
sudo docker compose --env-file /opt/noongill/shared/.env.production -f compose.prod.yml logs -f backend
```
