import { ModuleDirective } from './module.directive';
import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';
import { ModuleListComponent } from './components/module-list/module-list.component';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './shared/authconfig.interceptor';
import { AppRoutingModule } from './app-routing.module';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { SearchbarModule } from './components/searchbar/searchbar.module';
import { SqlInjectionTutorialComponent } from './components/sql-injection-tutorial/sql-injection-tutorial.component';
import { ModuleItemComponent } from './components/module-item/module-item.component';
import { XssTutorialComponent } from './components/xss-tutorial/xss-tutorial.component';
import { CsrfTutorialComponent } from './components/csrf-tutorial/csrf-tutorial.component';
import { HomeComponent } from './components/home/home.component';
import { AlertComponent } from './components/alert/alert.component';
import { ErrorInterceptor } from './shared/error.interceptor';
import { ScoreboardComponent } from './components/scoreboard/scoreboard.component';
import { UserScoreComponent } from './components/user-score/user-score.component';
import { FlagTutorialComponent } from './components/flag-tutorial/flag-tutorial.component';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    SignupComponent,
    HomeComponent,
    ModuleListComponent,
    AlertComponent,
    SqlInjectionTutorialComponent,
    ModuleItemComponent,
    XssTutorialComponent,
    CsrfTutorialComponent,
    FlagTutorialComponent,
    ModuleDirective,
    ScoreboardComponent,
    UserScoreComponent,
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    AppRoutingModule,
    ReactiveFormsModule,
    FormsModule,
    SearchbarModule,
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
