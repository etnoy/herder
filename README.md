[![Build Status](https://travis-ci.com/etnoy/herder.svg?branch=master)](https://travis-ci.com/etnoy/SecurityShepherd)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=etnoy_herder&metric=coverage)](https://sonarcloud.io/dashboard?id=etnoy_herder)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=etnoy_herder&metric=alert_status)](https://sonarcloud.io/dashboard?id=etnoy_herder)

# A rewrite of Security Shepherd
I have used Shepherd for many years as a teaching tool in our infosec courses at Linköping University. In this setting, we run a CTF as part of the courses, resulting in >200 users in a CTF running over several months. Lately, I've become tired of running into bugs and issues due to a codebase that has outgrown its initial design. I have contributed a bit to the github repository, but now I'm taking a fresh look at everything.

Right now, this is my playground to test some new tech and practice my Java coding. If you are interested in contributing, be aware that nothing in terms of API or design is decided upon.

# Issues with Security Shepherd 3.1
- Bad exception handling. Errors are often ignored, causing undefined behavior
- No connection pooling
- Lack of database connection pooling
- Lack of SSO support
- Code duplication
- Direct Object Reference Bank challenge runs out of money
- Hard to make custom categories
- Hard to customize
- Lack of code testing

# Key ideas for this rewrite
- Reactive programming paradigm
- Java backend based on Spring Boot
- REST api for backend<->frontend communication
- Angular 10 for the frontend
- MySQL database for the platform
- Java 11
- Spring R2DBC manages the persistence layer in a reactive way
- Auditable scoreboard. All scores are computed as a sum of the user's scores
- Flags can be static (i.e. reverse engineering challenges) or dynamic (i.e. for web challenges)
- High test coverage is a goal (>95%)
- JUnit5 is used as test runner

# For developers
This Security Shepherd rewrite is in an early state. We have a long way to go before non-developers will find it useful. For developers, however, here's a quick guide to getting started. Note that all of this is subject to change.

## Backend
The backend is written in Java and uses Maven and Spring Boot. Backend code is located in the /server folder. We use Eclipse as the editor. You will need to install the Lombok extension jar for code generation to work. Please install the google java style of code formatting for beautiful code. Unit tests are found in src/main/java and are started with the JUnit5 test runner. For integration testing, you need a MySQL 8 server running on localhost with an empty root password. The MySQL server currently requires you to create a database called "core" manually, otherwise the application won't start. Note that MySQL versions older than 8 are currently not supported.

## Frontend
The frontend code uses Angular and can be found in the /client folder. We recommend Visual Studio Code for frontend development.

The frontend code was generated with [Angular CLI](https://github.com/angular/angular-cli) version 9.0.3.

Run `ng serve` for a frontend dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

Run `ng build` to build the frontend. The build artifacts will be stored in the `client/dist/` directory. Use the `--prod` flag for a production build.

Run `ng test` to execute the frontend unit tests via [Karma](https://karma-runner.github.io).

Run `ng e2e` to execute the end-to-end tests via [Protractor](http://www.protractortest.org/).

## Continuous integration
We use Travis CI for continuous integration. Sonarcloud is used as a static code analysis tool for the backend.
