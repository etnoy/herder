[![Backend](https://github.com/etnoy/herder/actions/workflows/backend.yml/badge.svg)](https://github.com/etnoy/herder/actions/workflows/backend.yml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=etnoy_herder&metric=coverage)](https://sonarcloud.io/dashboard?id=etnoy_herder)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=etnoy_herder&metric=alert_status)](https://sonarcloud.io/dashboard?id=etnoy_herder)

[![Frontend](https://github.com/etnoy/herder/actions/workflows/frontend.yml/badge.svg)](https://github.com/etnoy/herder/actions/workflows/frontend.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=etnoy_herder_frontend&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=etnoy_herder_frontend)

# herder
Herder is an integrated CTF/security training platform focused on web exploits. It is heavily inspired by [OWASP Security Shepherd](https://github.com/OWASP/SecurityShepherd) but is a complete rewrite with the following aims:
* Decently written and tested code
* Flags are uniquely generated for each user, making cheating harder
* Easy to customize or to write new challenges
* Full support for users playing together in teams
* Broad coverage of the OWASP Top 10
* SSO support

Herder is for **students**. Herder can be run standalone for someone who wants to improve their knowledge about web exploits (CSRF, SQL injections, XSS).

Herder is for **teachers**. We aim for the following:
* Multi-user support. Each user has their own account
* A scoreboard spurs friendly competition which encourages learning
* Multi-team support. Users can collaborate within teams, and allowed team sizes are configurable
* Flags are unique for each user (or team) whenever possible. This ensures that students can't cheat by copying flags.

# Quick start
* Start the MongoDB server
* Start the Spring Boot backend
* Start the Angular frontend
* Login in using admin/password

# Background
We have extensively used OWASP Security Shepherd as a teaching tool in our infosec courses at Linköping University. With hundreds of students doing a CTF spanning over several months it has been very successful. However, as of 2022 Security Shepherd is essentially abandoned resulting in severe stability issues and bugs. We tried improving the code but ultimately realized that a full rewrite was needed. 

# For developers
This Security Shepherd rewrite is in an early state. We have a long way to go before non-developers will find it useful. For developers, however, here's a quick guide to getting started. Note that all of this is subject to change.

## Backend
The backend is written in Java and uses Gradle and Spring Boot. Backend code is located in the /backend folder and the primary editor we used so far is VS Code although Eclipse should work as well. Please follow the Google Java style.

## Frontend
The frontend code uses Angular and can be found in the /frontend folder. We recommend Visual Studio Code for frontend development.

The frontend code was generated with [Angular CLI](https://github.com/angular/angular-cli) version 9.0.3.

Run `ng serve` for a frontend dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

Run `ng build` to build the frontend. The build artifacts will be stored in the `client/dist/` directory. Use the `--prod` flag for a production build.

Run `ng test` to execute the frontend unit tests via [Karma](https://karma-runner.github.io).

Run `ng e2e` to execute the end-to-end tests via [Protractor](http://www.protractortest.org/).

## Continuous integration
We use Github Actions for continuous integration. Sonarcloud is used as a static code analysis tool for the backend.
