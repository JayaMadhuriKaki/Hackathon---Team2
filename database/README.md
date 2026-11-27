# Database

Schema migrations and seed data. Design reference: `../architecture/database-design.md`.

## Layout
```
migrations/   ordered schema scripts (001_create_users.sql, …)
seeds/        demo data that supports the demo script
```

## Commands
```bash
<docker compose up -d db>
<migration command>
<seed command>
```
