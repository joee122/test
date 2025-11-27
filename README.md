# AI Code Review - Spring Boot (core)

This repository contains a minimal Spring Boot 3 single-module implementation of core features for an AI-based code review system.

Features included in this initial delivery:
- GitLab webhook receiver (POST /webhook/gitlab)
- Simple project registration (auto-registers on first webhook)
- Review entity + endpoints to trigger a review (/api/reviews/run)
- MyBatis-Plus integration and simple MySQL schema (schema.sql)
- Stubbed AI analysis service (AIReviewService) that returns deterministic findings; replace with real model calls.

How to run:
1. Configure MySQL and update spring.datasource in application.yml.
2. Run the schema.sql to create tables (or let Spring run it if configured).
3. Build and run with `mvn spring-boot:run`.

Next steps (suggested):
- Implement repository cloning and file-level analysis
- Integrate real LLM or model inference service
- Add authentication for webhook and APIs
- Add async processing (queue) for long-running analysis
