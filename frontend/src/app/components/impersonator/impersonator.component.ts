import { Component, OnInit } from '@angular/core';
import { ApiService } from '../../service/api.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { AlertService } from 'src/app/service/alert.service';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-impersonator',
  templateUrl: './impersonator.component.html',
})
export class ImpersonatorComponent implements OnInit {
  queryForm: FormGroup;
  errorResult: string;
  submitted = false;
  loading = false;

  constructor(
    private route: ActivatedRoute,
    private apiService: ApiService,
    public fb: FormBuilder,
    private alertService: AlertService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const impersonatedUserId = params.get('userId');
      this.apiService.impersonate(impersonatedUserId).subscribe();
    });
  }
}
