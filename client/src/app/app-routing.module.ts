import { ModuleItemComponent } from './components/module-item/module-item.component';
import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';
import { ModuleListComponent } from './components/module-list/module-list.component';

import { AuthGuard } from './shared/auth.guard';
import { HomeComponent } from './components/home/home.component';
import { ScoreboardComponent } from './components/scoreboard/scoreboard.component';
import { UserScoreComponent } from './components/user-score/user-score.component';
import { UserListComponent } from './components/userlist/userlist.component';

const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: SignupComponent },
  {
    path: 'users',
    component: UserListComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'scoreboard',
    component: ScoreboardComponent,
    canActivate: [AuthGuard],
  },
  { path: 'modules', component: ModuleListComponent, canActivate: [AuthGuard] },
  {
    path: 'module',
    children: [
      {
        path: '**',
        component: ModuleItemComponent,
        canActivate: [AuthGuard],
      },
    ],
  },
  {
    path: 'scoreboard/:id',
    component: UserScoreComponent,
    canActivate: [AuthGuard],
  },
  { path: '**', redirectTo: '' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { relativeLinkResolution: 'legacy' })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
