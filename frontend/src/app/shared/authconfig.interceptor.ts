import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from '../service/api.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private apiService: ApiService) {}

  intercept(
    request: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // add authorization header with jwt token if available
    const currentUser = this.apiService.currentUserValue;

    const impersonatedUser = this.apiService.impersonatedUserValue;
    if (impersonatedUser && impersonatedUser.accessToken) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${impersonatedUser.accessToken}`,
        },
      });
    } else if (currentUser && currentUser.accessToken) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${currentUser.accessToken}`,
        },
      });
    }

    return next.handle(request);
  }
}
