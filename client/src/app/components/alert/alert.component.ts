import { Component, OnInit, OnDestroy } from '@angular/core';

import { Subscription } from 'rxjs';
import { AlertService } from 'src/app/service/alert.service';

interface Alert {
  type: string;
  message: string;
}

@Component({ selector: 'app-alert', templateUrl: './alert.component.html' })
export class AlertComponent implements OnInit, OnDestroy {
  private subscription: Subscription;
  message: any;

  alerts: Alert[] = [];

  constructor(private alertService: AlertService) {}

  ngOnInit() {
    this.alerts;
    this.subscription = this.alertService.getAlert().subscribe((alert) => {
      console.log(alert);
      this.alerts.push(alert);
      console.log(this.alerts);
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  close(alert: Alert) {
    this.alerts.splice(this.alerts.indexOf(alert), 1);
  }
}
